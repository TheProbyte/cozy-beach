plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.theprobyte.cozybeach"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.theprobyte.cozybeach"
        minSdk = 30
        targetSdk = 37
        // Each CI build counts up, so a new APK installs as an update.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    signingConfigs {
        // Committed on purpose: every CI build is signed with the same key, so a new APK
        // installs as an update over the old one instead of failing with a signature mismatch.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
