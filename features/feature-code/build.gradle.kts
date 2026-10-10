plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.molina.suite.feature.code"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
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
    implementation(project(":engines:sora-core"))
    implementation(project(":engines:sora-textmate"))
    implementation(project(":core:core-common"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment:1.5.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
