// Modul tipis milik molina: membangun language-textmate Sora Editor dari
// sumber upstream di engines/sora-editor (tag 0.23.4). File upstream tidak diubah.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val soraModule = rootProject.file("engines/sora-editor/language-textmate")

android {
    namespace = "io.github.rosemoe.sora.langs.textmate"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        consumerProguardFiles(soraModule.resolve("consumer-rules.pro"))
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets.getByName("main") {
        java.setSrcDirs(listOf(soraModule.resolve("src/main/java")))
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
    compileOnly(project(":engines:sora-core"))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jruby.jcodings:jcodings:1.0.58")
    implementation("org.jruby.joni:joni:2.2.1")
    implementation("org.snakeyaml:snakeyaml-engine:2.7")
    implementation("org.eclipse.jdt:org.eclipse.jdt.annotation:2.2.800")
}
