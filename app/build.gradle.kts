plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nxt.iptv"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.nxt.iptv"
        minSdk = 21
        // 29 keeps legacy (non-scoped) storage, so playlists can be read straight off USB drives on TVs.
        targetSdk = 29
        versionCode = 4
        versionName = "1.2.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug key so the APK can be sideloaded directly; replace with a real key for store releases.
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
        buildConfig = false
    }
    lint {
        // Sideloaded TV app: the old targetSdk is deliberate (see defaultConfig).
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}

dependencies {
    // Media3 1.8.x is the last line supporting minSdk 21 (1.9+ requires 23).
    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
