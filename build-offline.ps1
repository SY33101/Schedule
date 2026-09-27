[CmdletBinding()]
param(
    [string]$StudioHome = 'D:\app\Android_Studio',
    [string]$SdkHome = 'D:\app\Android\SDK'
)

$ErrorActionPreference = 'Stop'
$sourceRoot = $PSScriptRoot
$buildRoot = $sourceRoot

# aapt2 36 on Windows cannot open this project's Chinese parent path reliably.
# Build through an ASCII-only junction while keeping all files in the real project.
if ($sourceRoot -match '[^\x00-\x7F]') {
    $junction = 'D:\app\ScheduleDev\Schedule'
    $junctionParent = Split-Path -Parent $junction
    New-Item -ItemType Directory -Path $junctionParent -Force | Out-Null
    if (!(Test-Path -LiteralPath $junction)) {
        New-Item -ItemType Junction -Path $junction -Target $sourceRoot | Out-Null
    }
    $junctionItem = Get-Item -LiteralPath $junction -Force
    $target = [System.IO.Path]::GetFullPath([string]$junctionItem.Target)
    $expected = [System.IO.Path]::GetFullPath($sourceRoot)
    if ($target -ne $expected) {
        throw "Build junction already points elsewhere: $junction -> $target"
    }
    $buildRoot = $junction
}

$javaHome = Join-Path $StudioHome 'jbr'
$java = Join-Path $javaHome 'bin\java.exe'
$javac = Join-Path $javaHome 'bin\javac.exe'
$jarTool = Join-Path $javaHome 'bin\jar.exe'
$keytool = Join-Path $javaHome 'bin\keytool.exe'
$buildTools = Join-Path $SdkHome 'build-tools\36.0.0'
$platformJar = Join-Path $SdkHome 'platforms\android-37.0\android.jar'
$aapt2 = Join-Path $buildTools 'aapt2.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$d8Jar = Join-Path $buildTools 'lib\d8.jar'
$apksignerJar = Join-Path $buildTools 'lib\apksigner.jar'

foreach ($required in @($java, $javac, $jarTool, $keytool, $platformJar,
        $aapt2, $zipalign, $d8Jar, $apksignerJar)) {
    if (!(Test-Path -LiteralPath $required)) {
        throw "Required build component is missing: $required"
    }
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$work = Join-Path $buildRoot "app\build\offline\$stamp"
$generated = Join-Path $work 'generated'
$classes = Join-Path $work 'classes'
$dex = Join-Path $work 'dex'
$output = Join-Path $buildRoot 'app\build\outputs\apk'
$signing = Join-Path $buildRoot 'signing'
New-Item -ItemType Directory -Path $work,$generated,$classes,$dex,$output,$signing -Force | Out-Null

$compiledResources = Join-Path $work 'compiled-resources.zip'
& $aapt2 compile --dir (Join-Path $buildRoot 'app\src\main\res') -o $compiledResources
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed ($LASTEXITCODE)" }

$sourceManifest = Join-Path $buildRoot 'app\src\main\AndroidManifest.xml'
$manualManifest = Join-Path $work 'AndroidManifest.xml'
[xml]$manifestXml = Get-Content -LiteralPath $sourceManifest -Raw
$manifestXml.manifest.SetAttribute('package', 'com.sherry.schedule')
$xmlSettings = [System.Xml.XmlWriterSettings]::new()
$xmlSettings.Encoding = [System.Text.UTF8Encoding]::new($false)
$xmlSettings.Indent = $true
$xmlWriter = [System.Xml.XmlWriter]::Create($manualManifest, $xmlSettings)
try { $manifestXml.Save($xmlWriter) } finally { $xmlWriter.Dispose() }

$resourceApk = Join-Path $work 'resources-unsigned.apk'
& $aapt2 link -o $resourceApk -I $platformJar --manifest $manualManifest `
    --java $generated --min-sdk-version 26 --target-sdk-version 36 `
    --version-code 11 --version-name '1.0' --auto-add-overlay $compiledResources
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed ($LASTEXITCODE)" }

$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $buildRoot 'app\src\main\java') `
    -Filter '*.java' -File -Recurse | Select-Object -ExpandProperty FullName)
$sourceFiles += Join-Path $generated 'com\sherry\schedule\R.java'
& $javac -encoding UTF-8 --release 17 -cp $platformJar -d $classes $sourceFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }

$classesJar = Join-Path $work 'classes.jar'
& $jarTool --create --file $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed ($LASTEXITCODE)" }
& $java -cp $d8Jar com.android.tools.r8.D8 --lib $platformJar `
    --min-api 26 --output $dex $classesJar
if ($LASTEXITCODE -ne 0) { throw "D8 failed ($LASTEXITCODE)" }

$unaligned = Join-Path $work 'Schedule-unaligned.apk'
Copy-Item -LiteralPath $resourceApk -Destination $unaligned
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open(
    $unaligned, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $archive,
        (Join-Path $dex 'classes.dex'),
        'classes.dex',
        [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally {
    $archive.Dispose()
}

$aligned = Join-Path $work 'Schedule-aligned.apk'
& $zipalign -f -p 4 $unaligned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed ($LASTEXITCODE)" }

$keystore = Join-Path $signing 'schedule-personal.keystore'
if (!(Test-Path -LiteralPath $keystore)) {
    & $keytool -genkeypair -keystore $keystore -storepass android `
        -keypass android -alias schedule -keyalg RSA -keysize 2048 -validity 10000 `
        -dname 'CN=Schedule Personal, O=Personal, C=CN'
    if ($LASTEXITCODE -ne 0) { throw "keytool failed ($LASTEXITCODE)" }
}

$signedTemp = Join-Path $work 'Schedule-v1.0.apk'
& $java -jar $apksignerJar sign --ks $keystore --ks-key-alias schedule `
    --ks-pass pass:android --key-pass pass:android --out $signedTemp $aligned
if ($LASTEXITCODE -ne 0) { throw "APK signing failed ($LASTEXITCODE)" }
& $java -jar $apksignerJar verify --verbose $signedTemp
if ($LASTEXITCODE -ne 0) { throw "APK verification failed ($LASTEXITCODE)" }

$finalApk = Join-Path $output 'Schedule-v1.0.apk'
Copy-Item -LiteralPath $signedTemp -Destination $finalApk -Force
$hash = Get-FileHash -LiteralPath $finalApk -Algorithm SHA256
Write-Host "APK: $finalApk"
Write-Host "SHA256: $($hash.Hash)"
