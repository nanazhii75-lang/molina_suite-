plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.molina.suite"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        applicationId = "com.molina.suite"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = 1
        versionName = "0.1.0"

        // Placeholder manifest yang dibutuhkan engine terminal (Termux).
        // TERMUX_PACKAGE_NAME WAJIB sama dengan applicationId.
        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = "com.molina.suite"
        manifestPlaceholders["TERMUX_APP_NAME"] = "Molina Terminal"
        manifestPlaceholders["TERMUX_API_APP_NAME"] = "Termux:API"
        manifestPlaceholders["TERMUX_BOOT_APP_NAME"] = "Termux:Boot"
        manifestPlaceholders["TERMUX_FLOAT_APP_NAME"] = "Termux:Float"
        manifestPlaceholders["TERMUX_STYLING_APP_NAME"] = "Termux:Styling"
        manifestPlaceholders["TERMUX_TASKER_APP_NAME"] = "Termux:Tasker"
        manifestPlaceholders["TERMUX_WIDGET_APP_NAME"] = "Termux:Widget"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

configurations.configureEach {
    // Guava 24.1-jre (dipakai engine terminal) sudah memuat ListenableFuture.
    exclude(group = "com.google.guava", module = "listenablefuture")
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-storage"))
    implementation(project(":features:feature-terminal"))
    implementation(project(":features:feature-code"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
