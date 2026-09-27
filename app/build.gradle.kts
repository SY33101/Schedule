plugins {
    id("com.android.application")
}

android {
    namespace = "com.sherry.schedule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sherry.schedule"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
