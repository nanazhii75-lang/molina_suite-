plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

// Konfigurasi global: seluruh modul wajib mengikuti perilaku Android 8 (API 26).
extra["compileSdkVersion"] = 34
extra["minSdkVersion"] = 26
extra["targetSdkVersion"] = 26
