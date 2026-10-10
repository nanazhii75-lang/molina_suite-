plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.molina.suite.feature.mpv"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":engines:mpv"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment:1.5.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
