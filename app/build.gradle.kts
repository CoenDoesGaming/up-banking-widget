plugins {
    id("com.android.application")
}

android {
    namespace = "au.com.up.lockwidget"
    compileSdk = 37

    defaultConfig {
        applicationId = "au.com.up.lockwidget"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.2.0"
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
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
