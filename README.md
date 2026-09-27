# Schedule

Schedule 是一款完全离线的个人日程与备忘 Android 应用，主要适配红米 K70 和澎湃 OS。

## v1.0 功能

### 日程

- 月历和每日列表，支持滑动切换月份与日期
- 点击年月选择日期，双击年月回到今天
- 隐藏月历后的每日视图，以及七日时间轴视图
- 新建、编辑、搜索、完成和批量删除日程
- 支持开始与结束时间、纪念日、每日习惯和每周习惯
- 支持提前提醒和开始时提醒，可选择响铃、震动或静默通知
- 锁屏亮屏提醒，每次提醒最长一分钟并可提前停止
- 控制中心“新建日程”按钮：单击快速新建，长按打开 Schedule

### 备忘

- 全屏长文本编辑
- 搜索、置顶、完成和归档
- 关联现有日程，或将备忘转换为日程

### 隐私空间

- 仅从备忘页顶部进行超长下拉进入
- 首次进入设置密码；默认数字键盘，双指缩放切换字母键盘
- 按日期聚合的加密日记，支持长按批量删除
- 加密密码本，包含名称、账号和密码，支持搜索、置顶和批量操作
- 主密码不明文保存，隐私内容使用 PBKDF2 与 AES-256-GCM 加密

所有业务数据均使用 SQLite 保存在手机本地，不需要账号或网络。

## 版本管理

- 当前稳定版本：`v1.0`
- Android `versionName`：`1.0`
- Android `versionCode`：`11`
- Git 标签：`v1.0`

以后每次发布时同时更新 `app/build.gradle.kts` 和 `build-offline.ps1` 中的版本号，再创建对应的 Git 标签。`versionCode` 必须始终递增，不能因显示版本重新编号而降低，否则手机无法覆盖安装。

建议后续版本使用：

- 小修复：`1.0.1`、`1.0.2`
- 新功能：`1.1.0`、`1.2.0`
- 大规模不兼容改版：`2.0.0`

## APK

本机离线构建后，安装包位于：

`app/build/outputs/apk/Schedule-v1.0.apk`

包名：`com.sherry.schedule`

- 最低系统：Android 8（API 26）
- 目标系统：Android 16（API 36）
- 编译平台：Android 17（API 37）

APK 构建产物不提交到 Git，避免仓库不断膨胀。

## 红米/澎湃 OS 首次设置

1. 允许 Schedule 发送通知。
2. 点击右上角三点，选择“权限设置”。
3. 开启精确定时和锁屏亮屏提醒。
4. 在电池设置中将 Schedule 设为“无限制”。
5. 允许锁屏显示、后台弹出和自启动。
6. 新建一个几分钟后的日程，锁屏测试响铃、震动、亮屏和停止提醒。
7. 如需快捷新建，在控制中心编辑页面中添加“新建日程”。

## 构建

可以使用 Android Studio 打开项目，或在 PowerShell 中运行：

```powershell
.\build-offline.ps1
```

当前离线脚本默认使用：

- Android Studio：`D:\app\Android_Studio`
- Android SDK：`D:\app\Android\SDK`
- Build-Tools 36.0.0
- Android API 37

其他电脑可以通过参数传入自己的安装目录：

```powershell
.\build-offline.ps1 -StudioHome "Android Studio路径" -SdkHome "Android SDK路径"
```

## 重要备份

`signing/schedule-personal.keystore` 是覆盖安装后续版本所必需的签名密钥。它已被 `.gitignore` 排除，不会上传 GitHub，必须单独安全备份。密钥丢失后，后续 APK 无法覆盖手机中已安装的 Schedule。

以下内容同样不会提交：

- `local.properties`
- `.idea/`、`.gradle/`
- `app/build/` 等构建产物
- `signing/` 中的签名文件
