plugins {
    id("com.android.application")
}

android {
    // Must match the phone app's applicationId so the Wear Data Layer delivers its data here
    namespace = "au.com.up.lockwidget.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "au.com.up.lockwidget"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
