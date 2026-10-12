import os, sys

OPS = []

def new(path, content):
    OPS.append({"kind": "new", "path": path, "content": content})

def over(path, content, marker):
    OPS.append({"kind": "over", "path": path, "content": content, "marker": marker})

def patch(path, old, repl, marker):
    OPS.append({"kind": "patch", "path": path, "old": old, "repl": repl, "marker": marker})

FS = "features/feature-settings/"
FSK = FS + "src/main/kotlin/com/molina/suite/feature/settings/"
FSL = FSK + "library/"
FSD = FSK + "debian/"
FSP = FSK + "packages/"
FSR = FS + "src/main/res/"

# ---------- Refreshable ----------
new(FSK + "Refreshable.kt", r'''package com.molina.suite.feature.settings

/** Fragment submenu yang bisa memuat ulang statusnya, dipanggil saat tab Settings tampil kembali. */
interface Refreshable {
    fun refresh()
}
''')

# ---------- DebianStatus: ekspos rootfsDir ----------
over(FSD + "DebianStatus.kt", r'''package com.molina.suite.feature.settings.debian

import android.content.Context
import java.io.File

/** Memeriksa apakah rootfs Debian proot-distro sudah terpasang di sandbox aplikasi. */
internal object DebianStatus {

    // $PREFIX = filesDir/usr; rootfs di $PREFIX/var/lib/proot-distro/installed-rootfs/debian.
    private const val ROOTFS_RELATIVE = "usr/var/lib/proot-distro/installed-rootfs/debian"

    fun rootfsDir(context: Context): File = File(context.filesDir, ROOTFS_RELATIVE)

    /** Memblokir I/O: panggil dari Dispatchers.IO. */
    fun isInstalled(context: Context): Boolean {
        val dir = rootfsDir(context)
        if (!dir.isDirectory) return false
        val children = dir.list() ?: return false
        return children.isNotEmpty()
    }
}
''', "private const val ROOTFS_RELATIVE")

# ---------- DebianFragment: Refreshable ----------
patch(FSD + "DebianFragment.kt",
      "class DebianFragment : Fragment(R.layout.fragment_debian) {",
      "class DebianFragment : Fragment(R.layout.fragment_debian), Refreshable {",
      "Fragment(R.layout.fragment_debian), Refreshable")
patch(FSD + "DebianFragment.kt",
      "import com.molina.suite.feature.settings.TerminalCommandRunner",
      "import com.molina.suite.feature.settings.Refreshable\nimport com.molina.suite.feature.settings.TerminalCommandRunner",
      "import com.molina.suite.feature.settings.Refreshable")
patch(FSD + "DebianFragment.kt",
      "    private fun refresh() {",
      "    override fun refresh() {",
      "override fun refresh()")

# ---------- Paket ----------
new(FSP + "PackageSpec.kt", r'''package com.molina.suite.feature.settings.packages

import androidx.annotation.StringRes
import com.molina.suite.feature.settings.R

private const val YTDLP_PIP_UPGRADE = "/opt/ytdlp/bin/pip install -U 'yt-dlp[default,curl-cffi]'"
private const val YTDLP_VENV_SETUP =
    "python3 -m venv /opt/ytdlp && $YTDLP_PIP_UPGRADE && ln -sf /opt/ytdlp/bin/yt-dlp /usr/local/bin/yt-dlp"

internal enum class PackageKind { APT, YTDLP_VENV }

/**
 * Satu baris pada menu Paket. Untuk [PackageKind.APT] semua [aptPackages] harus terpasang;
 * untuk [PackageKind.YTDLP_VENV] [aptPackages] hanyalah prasyarat pembuatan venv.
 */
internal data class PackageSpec(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val kind: PackageKind,
    val aptPackages: List<String>
) {
    private val aptList: String get() = aptPackages.joinToString(" ")

    /** Perintah yang dijalankan di dalam Debian (satu baris). */
    fun installCommand(): String = when (kind) {
        PackageKind.APT -> "apt update && apt install -y $aptList"
        PackageKind.YTDLP_VENV -> "apt update && apt install -y $aptList && $YTDLP_VENV_SETUP"
    }

    fun updateCommand(): String = when (kind) {
        PackageKind.APT -> "apt update && apt install -y --only-upgrade $aptList"
        PackageKind.YTDLP_VENV -> YTDLP_PIP_UPGRADE
    }
}

internal object PackageCatalog {

    val all: List<PackageSpec> = listOf(
        PackageSpec(
            "python", R.string.pkg_python_title, R.string.pkg_python_desc,
            PackageKind.APT, listOf("python3", "python3-pip", "python3-venv")
        ),
        PackageSpec(
            "ytdlp", R.string.pkg_ytdlp_title, R.string.pkg_ytdlp_desc,
            PackageKind.YTDLP_VENV, listOf("python3", "python3-venv")
        ),
        PackageSpec(
            "ffmpeg", R.string.pkg_ffmpeg_title, R.string.pkg_ffmpeg_desc,
            PackageKind.APT, listOf("ffmpeg")
        ),
        PackageSpec(
            "aria2", R.string.pkg_aria2_title, R.string.pkg_aria2_desc,
            PackageKind.APT, listOf("aria2")
        ),
        PackageSpec(
            "jdk17", R.string.pkg_jdk_title, R.string.pkg_jdk_desc,
            PackageKind.APT, listOf("openjdk-17-jdk-headless")
        ),
        PackageSpec(
            "tools", R.string.pkg_tools_title, R.string.pkg_tools_desc,
            PackageKind.APT,
            listOf("git", "curl", "wget", "unzip", "p7zip-full", "nano", "rsync", "jq", "mediainfo")
        )
    )

    /** Satu perintah untuk semua paket yang belum terpasang; null bila tidak ada. */
    fun installAllCommand(missing: List<PackageSpec>): String? {
        if (missing.isEmpty()) return null
        val apt = LinkedHashSet<String>()
        var needsVenv = false
        for (spec in missing) {
            apt.addAll(spec.aptPackages)
            if (spec.kind == PackageKind.YTDLP_VENV) needsVenv = true
        }
        val base = "apt update && apt install -y ${apt.joinToString(" ")}"
        return if (needsVenv) "$base && $YTDLP_VENV_SETUP" else base
    }
}
''')

new(FSP + "PackageDetector.kt", r'''package com.molina.suite.feature.settings.packages

import android.content.Context
import com.molina.suite.feature.settings.debian.DebianStatus
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

internal sealed interface PackageDetection {
    object NoDebian : PackageDetection
    data class Failed(val reason: String) : PackageDetection

    /** [installed] memetakan id [PackageSpec] ke status terpasang. */
    data class Ok(val installed: Map<String, Boolean>) : PackageDetection
}

/**
 * Mendeteksi paket langsung dari rootfs Debian tanpa menjalankan proot: paket apt dari
 * var/lib/dpkg/status, yt-dlp dari berkas venv. Symlink absolut di dalam rootfs tidak
 * ter-resolve di luar proot, jadi berkas venv diperiksa dengan NOFOLLOW_LINKS.
 */
internal object PackageDetector {

    private const val DPKG_STATUS = "var/lib/dpkg/status"
    private const val YTDLP_VENV_BIN = "opt/ytdlp/bin/yt-dlp"
    private const val PACKAGE_PREFIX = "Package: "
    private const val STATUS_INSTALLED = "Status: install ok installed"

    /** Memblokir I/O: panggil dari Dispatchers.IO. */
    fun detect(context: Context): PackageDetection {
        if (!DebianStatus.isInstalled(context)) return PackageDetection.NoDebian
        val root = DebianStatus.rootfsDir(context)
        return try {
            val statusFile = File(root, DPKG_STATUS)
            if (!statusFile.isFile) {
                return PackageDetection.Failed("Berkas status paket tidak ditemukan: ${statusFile.path}")
            }
            val aptInstalled = readInstalled(statusFile)
            val venvPresent = Files.exists(File(root, YTDLP_VENV_BIN).toPath(), LinkOption.NOFOLLOW_LINKS)
            val result = LinkedHashMap<String, Boolean>()
            for (spec in PackageCatalog.all) {
                result[spec.id] = when (spec.kind) {
                    PackageKind.APT -> aptInstalled.containsAll(spec.aptPackages)
                    PackageKind.YTDLP_VENV -> venvPresent
                }
            }
            PackageDetection.Ok(result)
        } catch (e: IOException) {
            PackageDetection.Failed("Gagal membaca status paket: ${e.message}")
        } catch (e: SecurityException) {
            PackageDetection.Failed("Izin membaca status paket ditolak: ${e.message}")
        }
    }

    private fun readInstalled(file: File): Set<String> {
        val installed = HashSet<String>()
        var current: String? = null
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith(PACKAGE_PREFIX) -> current = line.substring(PACKAGE_PREFIX.length).trim()
                    line == STATUS_INSTALLED -> current?.let { installed.add(it) }
                    line.isEmpty() -> current = null
                }
            }
        }
        return installed
    }
}
''')

new(FSP + "PackagesFragment.kt", r'''package com.molina.suite.feature.settings.packages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.Refreshable
import com.molina.suite.feature.settings.ShellLine
import com.molina.suite.feature.settings.TerminalCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Layar Paket: status paket Debian, install, update, dan install semua yang belum terpasang. */
class PackagesFragment : Fragment(R.layout.fragment_packages), Refreshable {

    private class Row(
        val spec: PackageSpec,
        val status: TextView,
        val install: ImageButton,
        val update: ImageButton
    )

    private var statusView: TextView? = null
    private var installAllButton: MaterialButton? = null
    private var rows: List<Row> = emptyList()
    private var detection: PackageDetection? = null
    private var runJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusView = view.findViewById(R.id.packages_status)
        val installAll = view.findViewById<MaterialButton>(R.id.packages_install_all)
        installAll.setOnClickListener { installAllMissing() }
        installAllButton = installAll
        view.findViewById<View>(R.id.packages_refresh).setOnClickListener { refresh() }

        val container = view.findViewById<LinearLayout>(R.id.packages_list)
        val inflater = LayoutInflater.from(requireContext())
        rows = PackageCatalog.all.map { spec ->
            val root = inflater.inflate(R.layout.item_package, container, false)
            root.findViewById<TextView>(R.id.package_item_title).setText(spec.title)
            root.findViewById<TextView>(R.id.package_item_desc).setText(spec.description)
            val install = root.findViewById<ImageButton>(R.id.package_item_install)
            val update = root.findViewById<ImageButton>(R.id.package_item_update)
            install.setOnClickListener { runInTerminal(spec.installCommand()) }
            update.setOnClickListener { runInTerminal(spec.updateCommand()) }
            container.addView(root)
            Row(spec, root.findViewById(R.id.package_item_status), install, update)
        }
        refresh()
    }

    override fun onDestroyView() {
        statusView = null
        installAllButton = null
        rows = emptyList()
        super.onDestroyView()
    }

    override fun refresh() {
        if (view == null) return
        val appContext = context?.applicationContext ?: return
        detection = null
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { PackageDetector.detect(appContext) }
            detection = result
            render()
        }
    }

    private fun render() {
        val current = detection
        val ok = current as? PackageDetection.Ok
        statusView?.text = when (current) {
            null -> getString(R.string.debian_status_checking)
            PackageDetection.NoDebian -> getString(R.string.packages_no_debian)
            is PackageDetection.Failed -> getString(R.string.packages_failed, current.reason)
            is PackageDetection.Ok ->
                getString(R.string.packages_summary, current.installed.count { it.value }, current.installed.size)
        }
        for (row in rows) {
            val installed = ok?.installed?.get(row.spec.id)
            row.status.setText(
                when (installed) {
                    true -> R.string.packages_installed
                    false -> R.string.packages_missing
                    null -> R.string.packages_unknown
                }
            )
            setEnabled(row.install, installed == false)
            setEnabled(row.update, installed == true)
        }
        val hasMissing = ok?.installed?.any { !it.value } == true
        installAllButton?.isEnabled = hasMissing
    }

    private fun setEnabled(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.35f
    }

    private fun installAllMissing() {
        val ok = detection as? PackageDetection.Ok ?: return
        val missing = PackageCatalog.all.filter { ok.installed[it.id] == false }
        val command = PackageCatalog.installAllCommand(missing) ?: return
        runInTerminal(command)
    }

    private fun runInTerminal(command: String) {
        if (runJob?.isActive == true) return
        val host = activity ?: return
        val line = ShellLine.wrapForDebian(command)
        runJob = viewLifecycleOwner.lifecycleScope.launch {
            val reason = TerminalCommandRunner.run(host, line)
            if (reason != null) toast(getString(R.string.library_run_failed, reason))
        }
    }

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
''')

# ---------- Resource paket ----------
new(FSR + "drawable/ic_package_install.xml", r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="?attr/colorControlNormal"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z" />
</vector>
''')

new(FSR + "drawable/ic_package_update.xml", r'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="?attr/colorControlNormal"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z" />
</vector>
''')

new(FSR + "layout/fragment_packages.xml", r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="16dp"
        android:paddingTop="8dp"
        android:paddingEnd="16dp"
        android:paddingBottom="8dp">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/settings_packages_title"
                android:textSize="20sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/packages_status"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:alpha="0.8"
                android:text="@string/debian_status_checking"
                android:textSize="14sp" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/packages_refresh"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/debian_refresh" />
    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/packages_install_all"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginEnd="16dp"
        android:enabled="false"
        android:text="@string/packages_install_all" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:alpha="0.7"
        android:paddingStart="16dp"
        android:paddingTop="8dp"
        android:paddingEnd="16dp"
        android:text="@string/packages_hint"
        android:textSize="12sp" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:id="@+id/packages_list"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical" />
    </ScrollView>
</LinearLayout>
''')

new(FSR + "layout/item_package.xml", r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="16dp"
    android:paddingTop="8dp"
    android:paddingEnd="4dp"
    android:paddingBottom="8dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/package_item_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/package_item_desc"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:alpha="0.8"
            android:textSize="13sp" />

        <TextView
            android:id="@+id/package_item_status"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textSize="12sp"
            android:textStyle="italic" />
    </LinearLayout>

    <ImageButton
        android:id="@+id/package_item_install"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/packages_install"
        android:src="@drawable/ic_package_install" />

    <ImageButton
        android:id="@+id/package_item_update"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/packages_update"
        android:src="@drawable/ic_package_update" />
</LinearLayout>
''')

patch(FSR + "values/strings.xml", "</resources>", r'''
    <string name="settings_packages_title">Paket</string>
    <string name="settings_packages_subtitle">Python, yt-dlp, FFmpeg, aria2, JDK, dan alat lain di Debian</string>
    <string name="packages_install_all">Install semua yang belum terpasang</string>
    <string name="packages_hint">Perintah berjalan di Terminal. Setelah selesai, ketuk Segarkan.</string>
    <string name="packages_no_debian">Debian belum terpasang. Pasang lewat menu Debian.</string>
    <string name="packages_failed">Gagal memeriksa paket: %1$s</string>
    <string name="packages_summary">%1$d dari %2$d paket terpasang</string>
    <string name="packages_installed">Terpasang</string>
    <string name="packages_missing">Belum terpasang</string>
    <string name="packages_unknown">Status belum diketahui</string>
    <string name="packages_install">Install</string>
    <string name="packages_update">Update</string>
    <string name="pkg_python_title">Python</string>
    <string name="pkg_python_desc">python3, pip, dan venv.</string>
    <string name="pkg_ytdlp_title">yt-dlp + curl_cffi</string>
    <string name="pkg_ytdlp_desc">Pengunduh video lewat venv /opt/ytdlp; wajib untuk TikTok.</string>
    <string name="pkg_ffmpeg_title">FFmpeg</string>
    <string name="pkg_ffmpeg_desc">ffmpeg dan ffprobe untuk konversi dan info media.</string>
    <string name="pkg_aria2_title">aria2</string>
    <string name="pkg_aria2_desc">Pengunduh paralel aria2c.</string>
    <string name="pkg_jdk_title">JDK 17</string>
    <string name="pkg_jdk_desc">OpenJDK 17 headless dari repositori Debian 12.</string>
    <string name="pkg_tools_title">Alat bantu</string>
    <string name="pkg_tools_desc">git, curl, wget, unzip, p7zip, nano, rsync, jq, dan mediainfo.</string>
</resources>''', "settings_packages_title")

# ---------- Menu Settings: baris Paket ----------
patch(FSR + "layout/fragment_settings.xml",
      '                android:text="@string/settings_debian_subtitle"\n'
      '                android:textSize="13sp" />\n'
      '        </LinearLayout>',
      '                android:text="@string/settings_debian_subtitle"\n'
      '                android:textSize="13sp" />\n'
      '        </LinearLayout>\n\n'
      '        <LinearLayout\n'
      '            android:id="@+id/settings_row_packages"\n'
      '            android:layout_width="match_parent"\n'
      '            android:layout_height="wrap_content"\n'
      '            android:background="?attr/selectableItemBackground"\n'
      '            android:clickable="true"\n'
      '            android:focusable="true"\n'
      '            android:minHeight="56dp"\n'
      '            android:orientation="vertical"\n'
      '            android:paddingTop="8dp"\n'
      '            android:paddingBottom="8dp">\n\n'
      '            <TextView\n'
      '                android:layout_width="match_parent"\n'
      '                android:layout_height="wrap_content"\n'
      '                android:text="@string/settings_packages_title"\n'
      '                android:textSize="16sp" />\n\n'
      '            <TextView\n'
      '                android:layout_width="match_parent"\n'
      '                android:layout_height="wrap_content"\n'
      '                android:alpha="0.7"\n'
      '                android:text="@string/settings_packages_subtitle"\n'
      '                android:textSize="13sp" />\n'
      '        </LinearLayout>',
      "settings_row_packages")

SF = FSK + "SettingsFragment.kt"
patch(SF, "import com.molina.suite.feature.settings.library.LibraryFragment",
      "import com.molina.suite.feature.settings.library.LibraryFragment\n"
      "import com.molina.suite.feature.settings.packages.PackagesFragment",
      "import com.molina.suite.feature.settings.packages.PackagesFragment")
patch(SF, "        view.findViewById<View>(R.id.settings_row_debian).setOnClickListener { openDebian() }",
      "        view.findViewById<View>(R.id.settings_row_debian).setOnClickListener { openDebian() }\n"
      "        view.findViewById<View>(R.id.settings_row_packages).setOnClickListener { openPackages() }",
      "{ openPackages() }")
patch(SF, "    private fun openDebian() {",
      "    private fun openPackages() {\n"
      "        if (childFragmentManager.backStackEntryCount > 0) return\n"
      "        childFragmentManager.beginTransaction()\n"
      "            .setReorderingAllowed(true)\n"
      "            .replace(R.id.settings_child_container, PackagesFragment())\n"
      "            .addToBackStack(BACKSTACK_PACKAGES)\n"
      "            .commit()\n"
      "    }\n\n"
      "    private fun openDebian() {",
      "private fun openPackages()")
patch(SF, '        const val BACKSTACK_DEBIAN = "debian"',
      '        const val BACKSTACK_DEBIAN = "debian"\n        const val BACKSTACK_PACKAGES = "packages"',
      "BACKSTACK_PACKAGES =")
patch(SF, "    override fun onDestroyView() {",
      "    override fun onHiddenChanged(hidden: Boolean) {\n"
      "        super.onHiddenChanged(hidden)\n"
      "        if (!hidden) {\n"
      "            (childFragmentManager.findFragmentById(R.id.settings_child_container) as? Refreshable)?.refresh()\n"
      "        }\n"
      "    }\n\n"
      "    override fun onDestroyView() {",
      "override fun onHiddenChanged")

# ---------- Mode ANDROID ----------
patch(FSL + "LibraryEntry.kt", '    DEBIAN("debian");',
      '    DEBIAN("debian"),\n\n'
      '    /** Perintah Android (am, pm): hanya ada di shell host, bukan di dalam Debian. */\n'
      '    ANDROID("android");',
      'ANDROID("android")')

patch(FSK + "ShellLine.kt",
      "        return if (entry.runIn == RunMode.DEBIAN) wrapForDebian(inner) else inner",
      "        return when (entry.runIn) {\n"
      "            RunMode.DEBIAN -> wrapForDebian(inner)\n"
      "            RunMode.ANDROID -> guardAndroid(inner)\n"
      "            RunMode.HOST -> inner\n"
      "        }",
      "RunMode.ANDROID -> guardAndroid(inner)")
patch(FSK + "ShellLine.kt",
      "    /** [values] diisikan ke placeholder dengan quote shell otomatis. */",
      "    /** Perintah Android (am, pm) hanya ada di shell host; di dalam Debian tampilkan petunjuk. */\n"
      "    fun guardAndroid(inner: String): String =\n"
      "        \"if command -v proot-distro >/dev/null 2>&1; then $inner; \" +\n"
      "            \"else echo 'Perintah ini hanya jalan di shell biasa. Ketik exit untuk keluar dari Debian.'; fi\"\n\n"
      "    /** [values] diisikan ke placeholder dengan quote shell otomatis. */",
      "fun guardAndroid(")

patch(FSL + "LibraryFragment.kt",
      "val runIn = if (debianCheck.isChecked) RunMode.DEBIAN else RunMode.HOST",
      "val runIn = when {\n"
      "                    debianCheck.isChecked -> RunMode.DEBIAN\n"
      "                    entry?.runIn == RunMode.ANDROID -> RunMode.ANDROID\n"
      "                    else -> RunMode.HOST\n"
      "                }",
      "entry?.runIn == RunMode.ANDROID")

BL = FSL + "BuiltinLibrary.kt"
patch(BL,
      '"Membuka tautan video atau audio langsung di tab mpv.", RunMode.HOST',
      '"Membuka tautan video atau audio langsung di tab mpv.", RunMode.ANDROID',
      '"Membuka tautan video atau audio langsung di tab mpv.", RunMode.ANDROID')
patch(BL,
      '"Isi path file di penyimpanan, mis. $downloads/video.mp4.", RunMode.HOST\n        ),',
      r'''"Isi path file di penyimpanan, mis. $downloads/video.mp4.", RunMode.ANDROID
        ),
        entry(
            "builtin-mpv-stream", "Putar stream (IPTV, rtsp, rtmp, udp)",
            "U={URL}; case \"\$U\" in http://*|https://*) " +
                "am start -a android.intent.action.VIEW -d \"\$U\" -t 'video/*' -p $MPV_PACKAGE;; " +
                "*) am start -a android.intent.action.VIEW -d \"\$U\" -p $MPV_PACKAGE;; esac",
            "Tautan http(s) memakai tipe video; rtsp, rtmp, udp, dan sejenisnya tanpa tipe.", RunMode.ANDROID
        ),''',
      "builtin-mpv-stream")

# ================= Fase 1: validasi semua dulu =================
errors = []
state = {}

def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()

for op in OPS:
    kind, path = op["kind"], op["path"]
    if kind == "new":
        if path in state:
            errors.append(path + ": dibuat dua kali dalam skrip")
        elif os.path.exists(path):
            if read(path) != op["content"]:
                errors.append(path + ": sudah ada dengan isi berbeda")
        else:
            state[path] = op["content"]
        continue
    current = state.get(path)
    if current is None:
        if not os.path.isfile(path):
            errors.append(path + ": berkas tidak ada")
            continue
        current = read(path)
    if kind == "over":
        if current == op["content"]:
            continue
        if op["marker"] not in current:
            errors.append(path + ": isi tidak seperti yang diharapkan (tidak ditimpa)")
            continue
        state[path] = op["content"]
    else:
        if op["marker"] in current:
            continue
        count = current.count(op["old"])
        if count != 1:
            errors.append("%s: anchor muncul %dx: %r" % (path, count, op["old"][:60]))
            continue
        state[path] = current.replace(op["old"], op["repl"], 1)

if errors:
    print("GAGAL, tidak ada berkas yang diubah:")
    for e in errors:
        print("  - " + e)
    sys.exit(1)

# ================= Fase 2: tulis =================
if not state:
    print("Tidak ada perubahan: semua sudah terpasang.")
    sys.exit(0)
for path, text in state.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print("ditulis: " + path)
print("Selesai.")
