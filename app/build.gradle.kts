plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sidbh.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sidbh.tvlauncher"
        minSdk = 28
        // 30, not 34: the TV runs Android 11, and connecting paired Bluetooth devices needs hidden APIs
        // that Android 11 only allows for apps targeting SDK 30 or lower.
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
    }

    lint {
        // Sideloaded only, never published to Google Play, so its minimum-target rule doesn't apply.
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed so the release APK can be sideloaded without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)

    testImplementation("junit:junit:4.13.2")
}
