// Modul tipis milik molina: membangun Sora Editor dari sumber upstream
// di engines/sora-editor (tag 0.23.4). Tidak ada file upstream yang diubah.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val soraModule = rootProject.file("engines/sora-editor/editor")

android {
    namespace = "io.github.rosemoe.sora"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        consumerProguardFiles(soraModule.resolve("consumer-rules.pro"))
    }

    sourceSets.getByName("main") {
        java.setSrcDirs(listOf(soraModule.resolve("src/main/java")))
        res.setSrcDirs(listOf(soraModule.resolve("src/main/res")))
        manifest.srcFile(soraModule.resolve("src/main/AndroidManifest.xml"))
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
    api("androidx.annotation:annotation:1.7.1")
    implementation("androidx.collection:collection:1.4.0")
}
