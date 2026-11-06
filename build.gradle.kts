plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tazkeer360.prayerdisplay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tazkeer360.prayerdisplay"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
