#!/usr/bin/env python3
"""Tahap 1a: konversi engine Termux (engines/termux) menjadi library Gradle yang
dikonsumsi shell molina-suite lewat modul features/feature-terminal.

Jalankan dari root repo SETELAH `refactor_engine.py --dir engines/termux --apply`.
Setiap langkah memeriksa asumsi terhadap source asli; jika tidak cocok, berhenti
tanpa menebak. Aman dijalankan ulang.
"""
import os
import re
import shutil
import sys
import xml.dom.minidom as minidom

ROOT = os.getcwd()
ENG = "engines/termux"
NS = "com.molina.suite.terminal"


def P(*a):
    return os.path.join(ROOT, *a)


def die(msg):
    sys.exit("GAGAL: " + msg)


def read(rel):
    with open(P(rel), "r", encoding="utf-8", newline="") as f:
        return f.read()


def write(rel, text):
    full = P(rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        text = text.lstrip("\n")  # deklarasi XML harus di awal berkas
        f.write(text if text.endswith("\n") else text + "\n")
    print("  tulis   " + rel)


def sub1(pattern, repl, text, what, flags=0):
    new, n = re.subn(pattern, repl, text, flags=flags)
    if n != 1:
        die("%s: diharapkan 1 kecocokan, dapat %d" % (what, n))
    return new


# ------------------------------------------------------------------ prasyarat
if not os.path.isdir(P(".git")):
    die("jalankan dari root repo (folder berisi .git): " + ROOT)
if os.path.exists(P(ENG, "app/src/main/java/com/termux")):
    die("refactor namespace belum dijalankan. Jalankan dulu: "
        "python3 refactor_engine.py --dir engines/termux --apply")
if not os.path.isfile(P(ENG, "app/src/main/java/com/molina/suite/terminal/app/TermuxApplication.java")):
    die("TermuxApplication.java tidak ditemukan di paket baru; periksa engines/termux")

# ---------------------------------------------------- 1. buang file build upstream
print("[1] Hapus file build/CI upstream yang tidak dipakai di monorepo")
for name in [".github", "jitpack.yml", "gradle", "gradlew", "gradlew.bat",
             "build.gradle", "settings.gradle", "gradle.properties", "fastlane"]:
    path = P(ENG, name)
    if os.path.isdir(path):
        shutil.rmtree(path)
        print("  hapus   %s/%s/" % (ENG, name))
    elif os.path.isfile(path):
        os.remove(path)
        print("  hapus   %s/%s" % (ENG, name))

# ----------------------------------------------------- 2. build.gradle per modul
print("[2] Tulis ulang build.gradle modul engine (AGP 8.5, library)")

GRADLE_HEAD = """// molina-suite: dimodifikasi dari upstream termux-app v0.118.3.
// Versi asli ada di riwayat git (commit "vendor: termux-app v0.118.3").
"""

APP_GRADLE = GRADLE_HEAD + """plugins {
    id "com.android.library"
}

android {
    namespace "com.molina.suite.terminal"
    compileSdk rootProject.ext.compileSdkVersion
    ndkVersion project.property("ndkVersion")

    defaultConfig {
        minSdk rootProject.ext.minSdkVersion

        externalNativeBuild {
            ndkBuild {
                cFlags "-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections"
            }
        }

        ndk {
            abiFilters 'x86', 'x86_64', 'armeabi-v7a', 'arm64-v8a'
        }
    }

    externalNativeBuild {
        ndkBuild {
            path "src/main/cpp/Android.mk"
        }
    }

    buildFeatures {
        buildConfig true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    testOptions {
        unitTests {
            includeAndroidResources = true
        }
    }
}

dependencies {
    implementation "androidx.annotation:annotation:1.3.0"
    implementation "androidx.appcompat:appcompat:1.3.1"
    implementation "androidx.core:core:1.6.0"
    implementation "androidx.drawerlayout:drawerlayout:1.1.1"
    implementation "androidx.preference:preference:1.1.1"
    implementation "androidx.viewpager:viewpager:1.0.0"
    implementation "com.google.android.material:material:1.4.0"
    implementation "com.google.guava:guava:24.1-jre"
    implementation "io.noties.markwon:core:$markwonVersion"
    implementation "io.noties.markwon:ext-strikethrough:$markwonVersion"
    implementation "io.noties.markwon:linkify:$markwonVersion"
    implementation "io.noties.markwon:recycler:$markwonVersion"

    implementation project(":engines:termux:terminal-view")
    implementation project(":engines:termux:termux-shared")

    testImplementation "junit:junit:4.13.2"
    testImplementation "org.robolectric:robolectric:4.4"
}

def downloadBootstrap(String arch, String expectedChecksum, String version) {
    def digest = java.security.MessageDigest.getInstance("SHA-256")

    def localUrl = "src/main/cpp/bootstrap-" + arch + ".zip"
    def file = new File(projectDir, localUrl)
    if (file.exists()) {
        def buffer = new byte[8192]
        def input = new FileInputStream(file)
        while (true) {
            def readBytes = input.read(buffer)
            if (readBytes < 0) break
            digest.update(buffer, 0, readBytes)
        }
        input.close()
        def checksum = new BigInteger(1, digest.digest()).toString(16)
        while (checksum.length() < 64) { checksum = "0" + checksum }
        if (checksum == expectedChecksum) {
            return
        } else {
            logger.quiet("Deleting old local file with wrong hash: " + localUrl)
            file.delete()
        }
    }

    // Catatan: URL ini diarahkan ke release bootstrap molina-suite (prefix com.molina.suite) pada Fase 1b.
    def remoteUrl = "https://github.com/termux/termux-packages/releases/download/bootstrap-" + version + "/bootstrap-" + arch + ".zip"
    logger.quiet("Downloading " + remoteUrl + " ...")

    file.parentFile.mkdirs()
    def out = new BufferedOutputStream(new FileOutputStream(file))

    def connection = new URL(remoteUrl).openConnection()
    connection.setInstanceFollowRedirects(true)
    def digestStream = new java.security.DigestInputStream(connection.inputStream, digest)
    out << digestStream
    out.close()

    def checksum = new BigInteger(1, digest.digest()).toString(16)
    while (checksum.length() < 64) { checksum = "0" + checksum }
    if (checksum != expectedChecksum) {
        file.delete()
        throw new GradleException("Wrong checksum for " + remoteUrl + ": expected: " + expectedChecksum + ", actual: " + checksum)
    }
}

clean {
    doLast {
        def tree = fileTree(new File(projectDir, 'src/main/cpp'))
        tree.include 'bootstrap-*.zip'
        tree.each { it.delete() }
    }
}

task downloadBootstraps() {
    doLast {
        def version = "2025.03.28-r1+apt-android-7"
        downloadBootstrap("aarch64", "c8d702b6f742935001c37cda81b8ac69504a95d5cf28f2899532dd8cd4b057eb", version)
        downloadBootstrap("arm",     "f3bb9d1b32552b34fff41861dbf193ec5ba2848d67d779ac1c7256da6640f85d", version)
        downloadBootstrap("i686",    "36db3e1ac3547f9a174fd763bd9a484fa1a3449cdd81e1cf2408ff0454f839c6", version)
        downloadBootstrap("x86_64",  "1c124ec2396ee70a51b0b0a574f29aa659526aa2b9f558f993b2fb05d1e51855", version)
    }
}

// Zip bootstrap harus ada sebelum kompilasi native (termux-bootstrap-zip.S meng-include-nya).
tasks.configureEach { task ->
    if (task.name != "downloadBootstraps" &&
            (task.name == "preBuild" ||
             task.name.startsWith("generateJsonModel") ||
             task.name.startsWith("configureNdkBuild") ||
             task.name.startsWith("buildNdkBuild") ||
             task.name.startsWith("externalNativeBuild"))) {
        task.dependsOn("downloadBootstraps")
    }
}
"""

SHARED_GRADLE = GRADLE_HEAD + """plugins {
    id "com.android.library"
}

android {
    namespace "com.molina.suite.terminal.shared"
    compileSdk rootProject.ext.compileSdkVersion

    defaultConfig {
        minSdk rootProject.ext.minSdkVersion
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation "androidx.appcompat:appcompat:1.3.1"
    implementation "androidx.annotation:annotation:1.3.0"
    implementation "androidx.core:core:1.6.0"
    implementation "com.google.android.material:material:1.4.0"
    implementation "com.google.guava:guava:24.1-jre"
    implementation "io.noties.markwon:core:$markwonVersion"
    implementation "io.noties.markwon:ext-strikethrough:$markwonVersion"
    implementation "io.noties.markwon:linkify:$markwonVersion"
    implementation "io.noties.markwon:recycler:$markwonVersion"
    implementation "org.lsposed.hiddenapibypass:hiddenapibypass:6.1"

    // Jangan naikkan di atas 1.0.0-alpha09: merusak ViewUtils (catatan upstream).
    implementation "androidx.window:window:1.0.0-alpha09"

    // Jangan naikkan di atas 2.5: runtime exception di Android < 8 (java.nio.file.Path).
    implementation "commons-io:commons-io:2.5"

    implementation project(":engines:termux:terminal-view")

    testImplementation "junit:junit:4.13.2"
    androidTestImplementation "androidx.test.ext:junit:1.1.3"
    androidTestImplementation "androidx.test.espresso:espresso-core:3.4.0"
}
"""

VIEW_GRADLE = GRADLE_HEAD + """plugins {
    id "com.android.library"
}

android {
    namespace "com.molina.suite.terminal.view"
    compileSdk rootProject.ext.compileSdkVersion

    defaultConfig {
        minSdk rootProject.ext.minSdkVersion
    }

    buildFeatures {
        buildConfig true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation "androidx.annotation:annotation:1.3.0"
    api project(":engines:termux:terminal-emulator")

    testImplementation "junit:junit:4.13.2"
}
"""

EMULATOR_GRADLE = GRADLE_HEAD + """plugins {
    id "com.android.library"
}

android {
    namespace "com.molina.suite.terminal.terminal"
    compileSdk rootProject.ext.compileSdkVersion
    ndkVersion project.property("ndkVersion")

    defaultConfig {
        minSdk rootProject.ext.minSdkVersion

        externalNativeBuild {
            ndkBuild {
                cFlags "-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections"
            }
        }

        ndk {
            abiFilters 'x86', 'x86_64', 'armeabi-v7a', 'arm64-v8a'
        }
    }

    externalNativeBuild {
        ndkBuild {
            path "src/main/jni/Android.mk"
        }
    }

    buildFeatures {
        buildConfig true
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    testOptions {
        unitTests.returnDefaultValues = true
    }
}

tasks.withType(Test) {
    testLogging {
        events "started", "passed", "skipped", "failed"
    }
}

dependencies {
    testImplementation "junit:junit:4.13.2"
}
"""

MODULES = {
    "app": (APP_GRADLE, "com.android.application"),
    "termux-shared": (SHARED_GRADLE, "com.android.library"),
    "terminal-view": (VIEW_GRADLE, "com.android.library"),
    "terminal-emulator": (EMULATOR_GRADLE, "com.android.library"),
}
for mod, (content, marker) in MODULES.items():
    rel = "%s/%s/build.gradle" % (ENG, mod)
    if not os.path.isfile(P(rel)):
        die("tidak ada " + rel)
    current = read(rel)
    if "molina-suite" in current:
        print("  lewati  %s (sudah dikonversi)" % rel)
        continue
    if marker not in current:
        die("%s tidak berisi '%s' seperti upstream v0.118.3" % (rel, marker))
    write(rel, content)

# --------------------------------------------------------- 3. manifest modul engine
print("[3] Bersihkan AndroidManifest engine")


def validate_xml(rel, text):
    try:
        minidom.parseString(text.encode("utf-8"))
    except Exception as exc:  # noqa: BLE001
        die("XML tidak valid setelah patch %s: %s" % (rel, exc))


def strip_package(rel, expected):
    text = read(rel)
    m = re.search(r'\s+package="([^"]*)"', text)
    if m is None:
        print("  lewati  %s (tanpa atribut package)" % rel)
        return
    if m.group(1) != expected:
        die("%s: package=%s, diharapkan %s" % (rel, m.group(1), expected))
    text = text.replace(m.group(0), "", 1)
    validate_xml(rel, text)
    write(rel, text)


strip_package(ENG + "/termux-shared/src/main/AndroidManifest.xml", NS + ".shared")
strip_package(ENG + "/terminal-view/src/main/AndroidManifest.xml", NS + ".view")
strip_package(ENG + "/terminal-emulator/src/main/AndroidManifest.xml", NS + ".terminal")

APP_MANIFEST = ENG + "/app/src/main/AndroidManifest.xml"
t = read(APP_MANIFEST)
if "molina-suite" in t:
    print("  lewati  %s (sudah dibersihkan)" % APP_MANIFEST)
else:
    m = re.search(r'\s+package="([^"]*)"', t)
    if m is None or m.group(1) != NS:
        die("%s: package tidak sesuai (%s)" % (APP_MANIFEST, m.group(1) if m else "tidak ada"))
    t = t.replace(m.group(0), "", 1)
    for attr in ('android:installLocation="internalOnly"',
                 'android:sharedUserId="${TERMUX_PACKAGE_NAME}"',
                 'android:sharedUserLabel="@string/shared_user_label"'):
        t = sub1(r"\s+" + re.escape(attr), "", t, attr)

    def clean_application(match):
        tag = match.group(0)
        tag, n = re.subn(
            r'\s+android:(?:name|banner|extractNativeLibs|icon|label|roundIcon|supportsRtl|theme)="[^"]*"',
            "", tag)
        if n != 8:
            die("<application>: %d atribut dihapus, diharapkan 8" % n)
        return tag

    t = sub1(r"<application\b[^>]*>", clean_application, t, "<application>", flags=re.DOTALL)

    def clean_main_activity(match):
        blk = match.group(0)
        blk, n_filters = re.subn(r"\s*<intent-filter>.*?</intent-filter>", "", blk, flags=re.DOTALL)
        blk, n_meta = re.subn(
            r'\s*<meta-data\s+android:name="android\.app\.shortcuts"\s+android:resource="@xml/shortcuts"\s*/>',
            "", blk)
        if (n_filters, n_meta) != (2, 1):
            die("TermuxActivity: intent-filter=%d meta-data=%d (diharapkan 2 dan 1)" % (n_filters, n_meta))
        return blk.replace('android:name=".app.TermuxActivity"',
                           'android:name=".app.TermuxActivity"\n            android:theme="@style/Theme.Termux"', 1)

    t = sub1(r'<activity\s+android:name="\.app\.TermuxActivity".*?</activity>',
             clean_main_activity, t, "TermuxActivity", flags=re.DOTALL)
    t = sub1(r"\s*<activity-alias\b.*?</activity-alias>", "", t, "activity-alias", flags=re.DOTALL)
    t = sub1(r'(<activity\s+android:name="\.filepicker\.TermuxFileReceiverActivity")',
             lambda mm: mm.group(1) + '\n            android:theme="@style/Theme.Termux"',
             t, "TermuxFileReceiverActivity")
    t = sub1(r'android:authorities="\$\{TERMUX_PACKAGE_NAME\}\.files"',
             lambda mm: 'android:authorities="${TERMUX_PACKAGE_NAME}.terminal.files"',
             t, "authority .files")
    t = sub1(r"(<manifest\b[^>]*>)",
             lambda mm: mm.group(1) + "\n\n    <!-- molina-suite: dimodifikasi dari upstream; lihat MOLINA-CHANGES.md -->",
             t, "<manifest>")
    validate_xml(APP_MANIFEST, t)
    write(APP_MANIFEST, t)

# ------------------------------------------------------------ 4. TermuxConstants
print("[4] Patch TermuxConstants")
CONST = ENG + "/termux-shared/src/main/java/com/molina/suite/terminal/shared/termux/TermuxConstants.java"
c = read(CONST)
if "TERMUX_JAVA_PACKAGE_NAME" in c:
    print("  lewati  TermuxConstants (sudah dipatch)")
else:
    c = sub1(r'^([ \t]*)(public static final String TERMUX_PACKAGE_NAME\s*=\s*"com\.molina\.suite";[^\n]*\n)',
             lambda mm: (mm.group(1) + mm.group(2) + "\n" +
                         mm.group(1) + "/** Paket Java engine terminal (beda dari applicationId); untuk nama kelas komponen. */\n" +
                         mm.group(1) + 'public static final String TERMUX_JAVA_PACKAGE_NAME = "' + NS + '";\n'),
             c, "TERMUX_PACKAGE_NAME", flags=re.MULTILINE)
    c, n = re.subn(
        r'TERMUX_PACKAGE_NAME(\s*\+\s*"\.app\.(?:TermuxActivity|activities\.SettingsActivity|TermuxService|RunCommandService)")',
        lambda mm: "TERMUX_JAVA_PACKAGE_NAME" + mm.group(1), c)
    if n != 4:
        die("konstanta nama kelas: %d diganti, diharapkan 4" % n)
    c = sub1(r'(TERMUX_FILE_SHARE_URI_AUTHORITY\s*=\s*TERMUX_PACKAGE_NAME\s*\+\s*)"\.files"',
             lambda mm: mm.group(1) + '".terminal.files"', c, "TERMUX_FILE_SHARE_URI_AUTHORITY")
    write(CONST, c)

# ------------------------------------------------------------ 5. TermuxApplication
print("[5] TermuxApplication: sediakan initialize() untuk host application")
APPCLS = ENG + "/app/src/main/java/com/molina/suite/terminal/app/TermuxApplication.java"
a = read(APPCLS)
if "initialize(" in a:
    print("  lewati  TermuxApplication (sudah dipatch)")
else:
    if "TermuxCrashUtils.setCrashHandler(this)" not in a:
        die("TermuxApplication.java berbeda dari upstream v0.118.3")
    write(APPCLS, """package com.molina.suite.terminal.app;

import android.app.Application;
import android.content.Context;

import com.molina.suite.terminal.shared.crash.TermuxCrashUtils;
import com.molina.suite.terminal.shared.logger.Logger;
import com.molina.suite.terminal.shared.settings.preferences.TermuxAppSharedPreferences;

/**
 * molina-suite: engine ini dibangun sebagai library, sehingga {@link Application} milik
 * host memanggil {@link #initialize(Application)} (lihat TerminalEngineModule).
 * Kelas ini tetap dapat dipakai langsung sebagai Application jika engine dijalankan mandiri.
 */
public class TermuxApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        initialize(this);
    }

    /** Pasang crash handler dan terapkan log level dari preferensi. */
    public static void initialize(Application application) {
        // Set crash handler for the app
        TermuxCrashUtils.setCrashHandler(application);

        // Set log level for the app
        setLogLevel(application.getApplicationContext());
    }

    private static void setLogLevel(Context context) {
        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
        Logger.logDebug("Starting Application");
    }
}
""")

# ------------------------------------------------------------ 6. feature-terminal
print("[6] Buat modul features/feature-terminal")
FT = "features/feature-terminal"
FT_PKG = FT + "/src/main/kotlin/com/molina/suite/feature/terminal"

write(FT + "/build.gradle.kts", """
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.molina.suite.feature.terminal"
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
    implementation(project(":core:core-common"))
    implementation(project(":engines:termux:app"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
}
""")

write(FT + "/src/main/AndroidManifest.xml", """
<?xml version="1.0" encoding="utf-8"?>
<manifest />
""")

write(FT + "/src/main/res/values/strings.xml", """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="terminal_title">Molina Terminal</string>
    <string name="terminal_description">Terminal Linux untuk menjalankan daemon unduhan (aria2c, yt-dlp), WebDAV, dan SSH.</string>
    <string name="terminal_open">Buka Terminal</string>
    <string name="terminal_open_failed">Terminal tidak dapat dibuka.</string>
</resources>
""")

write(FT_PKG + "/TerminalFeature.kt", """
package com.molina.suite.feature.terminal

import androidx.fragment.app.Fragment
import com.molina.suite.core.common.EngineFeature
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Implementasi kontrak [EngineFeature] untuk tab Terminal (engine: Termux). */
class TerminalFeature : EngineFeature {

    override val id: EngineId = EngineId.TERMINAL

    // Pelacakan status service berjalan/berhenti ditambahkan pada tahap daemon (1d).
    private val mutableStatus = MutableStateFlow(EngineStatus.STOPPED)
    override val status: StateFlow<EngineStatus> = mutableStatus.asStateFlow()

    override fun createFragment(): Fragment = TerminalLauncherFragment()
}
""")

write(FT_PKG + "/TerminalEngineModule.kt", """
package com.molina.suite.feature.terminal

import android.app.Application
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.terminal.app.TermuxApplication

/**
 * Satu-satunya titik masuk shell ke engine terminal: menginisialisasi engine
 * lalu mendaftarkan fitur ke [EngineRegistry]. Shell tidak mengenal kelas Termux.
 */
object TerminalEngineModule {

    fun install(application: Application, engines: EngineRegistry) {
        TermuxApplication.initialize(application)
        engines.register(TerminalFeature())
    }
}
""")

write(FT_PKG + "/TerminalLauncherFragment.kt", """
package com.molina.suite.feature.terminal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

/**
 * Isi tab Terminal. UI terminal penuh (TermuxActivity) berjalan sebagai Activity
 * sendiri di proses yang sama; fragment ini hanya pintu masuknya. Activity dipanggil
 * lewat nama kelas (bukan referensi langsung) agar feature tidak terikat ke tipe internal engine.
 */
class TerminalLauncherFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val gap = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(context).apply {
            text = getString(R.string.terminal_title)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val description = TextView(context).apply {
            text = getString(R.string.terminal_description)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        val open = MaterialButton(context).apply {
            text = getString(R.string.terminal_open)
            setOnClickListener { openTerminal() }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(gap * 2, gap * 2, gap * 2, gap * 2)
            addView(title)
            addView(description, spaced(gap))
            addView(open, spaced(gap))
        }
    }

    private fun spaced(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    private fun openTerminal() {
        try {
            startActivity(Intent().setClassName(requireContext(), TERMINAL_ACTIVITY_CLASS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.terminal_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val TERMINAL_ACTIVITY_CLASS = "com.molina.suite.terminal.app.TermuxActivity"
    }
}
""")

# --------------------------------------------------------------- 7. wiring root/app
print("[7] Sambungkan ke root project dan app")
write("settings.gradle.kts", """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "molina-suite"

include(":app")
include(":core:core-common")
include(":features:feature-terminal")
include(":engines:termux:terminal-emulator")
include(":engines:termux:terminal-view")
include(":engines:termux:termux-shared")
include(":engines:termux:app")
""")

write("gradle.properties", """
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
# Engine Termux ditulis untuk R transitif.
android.nonTransitiveRClass=false
kotlin.code.style=official

# Dibaca oleh build.gradle modul engines/termux
ndkVersion=22.1.7171670
markwonVersion=4.6.2
""")

write("app/build.gradle.kts", """
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
    implementation(project(":features:feature-terminal"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
""")

write("app/src/main/kotlin/com/molina/suite/MolinaApplication.kt", """
package com.molina.suite

import android.app.Application
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.feature.terminal.TerminalEngineModule

class MolinaApplication : Application() {
    val engines = EngineRegistry()
    val daemons = DaemonStatusBoard()

    override fun onCreate() {
        super.onCreate()
        TerminalEngineModule.install(this, engines)
    }
}
""")

write(".github/workflows/build-debug.yml", """
name: Build Debug

on:
  push:
    branches: [main]
  pull_request:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 90
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Install NDK 22.1.7171670 (dibutuhkan engine terminal)
        run: |
          SDKMANAGER="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
          yes | "$SDKMANAGER" --licenses > /dev/null || true
          "$SDKMANAGER" --install "ndk;22.1.7171670"

      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: "8.9"

      - name: Build APK debug
        run: gradle :app:assembleDebug --stacktrace

      - uses: actions/upload-artifact@v4
        with:
          name: molina-suite-debug
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
""")

gi = read(".gitignore")
ignore_line = "engines/termux/app/src/main/cpp/bootstrap-*.zip"
if ignore_line not in gi:
    write(".gitignore", gi.rstrip("\n") + "\n\n# zip bootstrap diunduh saat build\n" + ignore_line + "\n")

# ----------------------------------------------------------- 8. catatan modifikasi
write(ENG + "/MOLINA-CHANGES.md", """
# Modifikasi terhadap upstream termux-app v0.118.3

Source ini adalah turunan dari https://github.com/termux/termux-app (lisensi: lihat
`LICENSE.md` di folder ini; file lisensi dan header hak cipta tidak diubah).
Commit "vendor: termux-app v0.118.3" berisi versi asli; semua perubahan di bawah
tercatat sebagai commit terpisah.

- Paket Java/JNI `com.termux` -> `com.molina.suite.terminal` (refactor otomatis,
  laporan di `docs/refactor-reports/termux.txt`).
- Path runtime/applicationId `com.termux` -> `com.molina.suite`.
- Modul `app` diubah dari application menjadi library; file build upstream
  (`build.gradle`, `settings.gradle`, `gradle.properties`, wrapper, `.github`,
  `jitpack.yml`, `fastlane`) dihapus karena build dikelola monorepo.
- Manifest: dihapus `sharedUserId`, intent LAUNCHER/LEANBACK/IOT, shortcut statis,
  dan atribut `<application>`; authority `.files` -> `.terminal.files`.
- `TermuxConstants`: ditambah `TERMUX_JAVA_PACKAGE_NAME` untuk nama kelas komponen.
- `TermuxApplication`: ditambah `initialize(Application)`.
- Label aplikasi pada `strings.xml`: "Termux" -> "Molina Terminal".
""")

print("\nSelesai. Berikutnya: git add -A, commit, push, lalu cek CI.")
