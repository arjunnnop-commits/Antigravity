plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fairy.agypc"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.fairy.agypc"
        minSdk = 26
        // targetSdk 28 on purpose: Android 10+ blocks exec from app data for targetSdk >= 29 (proot needs it)
        targetSdk = 28
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
        }
    }
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.26.2")
}
