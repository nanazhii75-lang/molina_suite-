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

## Kompatibilitas compileSdk 34
- HelpActivity: hapus WebSettings.setAppCacheEnabled(false) dari WebView settings. API ini dihapus di SDK 33+ dan tidak berefek (nilai bawaan false, App Cache deprecated sejak API 33). Berkas terdampak: app/src/main/java/com/molina/suite/terminal/app/activities/HelpActivity.java

## Fase 1b: selaraskan engine dengan bootstrap berprefix com.molina.suite
- `app/.../TermuxInstaller.java`: path second stage diganti ke `etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh` (chmod saat ekstraksi dan eksekusi), sesuai bootstrap hasil build termux-packages terbaru.
- `termux-shared/.../shell/TermuxShellUtils.java`: environment shell mengekspor `TERMUX__USER_ID` (`Process.myUid() / 100000`), karena skrip termux-tools terbaru memakai `am --user "$TERMUX__USER_ID"`.

## Fase 1b: sumber bootstrap
- `app/build.gradle`: bootstrap diunduh dari release repo molina_suite- (prefix com.molina.suite), bukan dari termux-packages. Baru aarch64; arsitektur lain di-comment dan `abiFilters` hanya `arm64-v8a` sampai bootstrap arm, i686, dan x86_64 tersedia.

## Notifikasi service
- `app/.../TermuxService.java` dan `app/src/main/res/values/strings.xml`: judul notifikasi foreground service memakai string `notification_title` ("Molina Terminal"), bukan `TermuxConstants.TERMUX_APP_NAME`.

## Kontrak host terminal (tahap 2a)
- `app/.../TermuxHost.java` (baru): interface yang memuat metode TermuxActivity yang dipanggil klien terminal. `TermuxActivity` mengimplementasikannya dan menambah `getHostActivity()`. Perilaku tidak berubah; klien dipindahkan ke interface ini pada tahap 2b.

## Tahap 2b: klien terminal memakai TermuxHost, bukan TermuxActivity
- TermuxTerminalViewClient, TermuxTerminalSessionClient, TermuxSessionsListViewController: konstruktor menerima TermuxHost. Metode host lewat mHost, kebutuhan Context/Activity lewat mActivity (= host.getHostActivity()).
- TermuxTerminalViewClient.getActivity() diganti getHost() (pemakai: TermuxTerminalExtraKeys).
- TermuxActivityRootView.setActivity(TermuxActivity) diganti setHost(TermuxHost); TermuxActivity memanggil setHost(this).
- FullScreenWorkAround.apply dan TerminalToolbarViewPager (PageAdapter, OnPageChangeListener) menerima TermuxHost.
- TermuxHost: tambah getNavBarHeight() (metode ke-22).
- Perilaku TermuxActivity tidak berubah; TermuxActivity tetap implementasi TermuxHost.

## Tahap 3a: TermuxFragment (belum disambungkan ke shell)
- Ditambahkan: TermuxFragment.java dan res/layout/fragment_termux.xml (salinan activity_termux.xml tanpa fitsSystemWindows). TermuxActivity tidak diubah dan tetap berfungsi.
- Fragment tidak mengimplementasikan TermuxHost langsung (Fragment.isVisible() final); TermuxHost diberikan lewat kelas dalam FragmentHost.
- TermuxHost: tambah findViewById(int) (metode ke-23). TermuxTerminalSessionClient dan TermuxTerminalViewClient memakai mHost.findViewById untuk view milik fragment.
- Visibilitas dikendalikan lewat onResume, onStop, dan onHiddenChanged (shell memakai add/hide). Saat disembunyikan: flag disable-keyboard dihapus, softInputMode window dikembalikan, keyboard disembunyikan.
- Sesi terakhir selesai: tidak menutup apa pun; sesi baru dibuat otomatis kecuali service sedang berhenti.
- Belum dibawa: mode fullscreen, shortcut ACTION_RUN, tombol menu hardware, penerusan onContextMenuClosed (butuh kontrak di core-common).

## Penyesuaian MOTD dan Extra Keys
- `TermuxInstaller.applyMolinaMotd()`: etc/motd ditimpa dengan "Welcome Molina Terminal" setiap aplikasi dimulai (bin/login mencetaknya tepat di atas prompt).
- `TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS`: `/` -> DRAWER (tampil ☰), END -> CLEAR, PGUP -> COPY, PGDN -> PASTE.
- `ExtraKeysConstants`: simbol ⎘ untuk PASTE dihapus; COPY, PASTE, CLEAR tampil sebagai teks.
- `TermuxTerminalExtraKeys`: handler COPY (salin seluruh isi terminal: riwayat scroll dan layar, termasuk prompt; dipotong ke 100KB terakhir bila lebih panjang) dan CLEAR (CSI 3 J ke emulator untuk menghapus riwayat scroll, lalu Ctrl+L untuk menggambar ulang prompt). `TerminalView.getFullTranscriptText()` ditambahkan.
- `strings.xml`: `molina_extra_key_screen_copied`, `molina_extra_key_screen_empty`.

## Tombol tutup sesi
- `fragment_termux.xml`, `TermuxFragment`: tombol ☰ kanan atas (`sessions_drawer_button`) dihapus karena duplikat dengan tombol DRAWER di Extra Keys; drawable `ic_sessions_drawer` dan string `action_open_sessions` ikut dihapus.
- `item_terminal_sessions_list.xml`: baris sesi dibungkus FrameLayout dengan tombol x kecil (`session_close_button`, ikon `ic_molina_close_session`) di pojok kanan atas.
- `TermuxSessionsListViewController`: tombol x tampil untuk sesi ke-2 dan seterusnya; sesi yang masih berjalan dikonfirmasi dulu (`title_confirm_kill_process`).
- `TermuxTerminalSessionClient.closeSession()`: sesi berjalan dihentikan lalu dihapus dari daftar saat `onSessionFinished`; sesi selesai langsung dihapus. Sesi yang sedang tampil tidak berpindah kecuali sesi itu sendiri yang ditutup.

## Banner permanen
- `TermuxInstaller.applyMolinaBanner()`: menulis `etc/profile.d/02-molina-banner.sh` setiap aplikasi dimulai (hanya bila isi berbeda). Untuk bash interaktif, fungsi `clear` dan tombol Ctrl+L mencetak ulang motd (atau `~/.termux/motd.sh`) setelah layar dibersihkan; `clear -x` tetap bawaan.
- Tombol CLEAR di Extra Keys (CSI 3 J lalu Ctrl+L) ikut memunculkan banner. Dimatikan dengan `MOLINA_NO_BANNER_REDRAW=1` atau `~/.hushlogin`.
- Warna brand: motd "Welcome Molina Terminal" tebal hijau muda (256-color 120, #87ff87), tetap sama di semua tema warna terminal.
- Ukuran teks default terminal: 12 dp menjadi 8 dp (`TermuxAppSharedPreferences.getDefaultFontSizes`; mode float ikut). Ukuran yang sudah disimpan pengguna lewat zoom tidak berubah.

## Pilihan login dan prompt Debian
- `app/.../MolinaShellProfile.java` (baru): dipanggil dari `TermuxInstaller` bersama `applyMolinaBanner()`.
- `etc/profile.d/03-molina-login.sh`: pada sesi bash interaktif pertama menanyakan login Debian sebagai `user@localhost` atau `root@localhost`, menyimpan pilihan di `~/.molina/login-mode`, lalu `proot-distro login debian` (user: `--user user`, dibuat otomatis dengan `useradd -m`). Rootfs belum terpasang: shell host tetap dipakai tanpa pertanyaan. Ganti pilihan dengan `molina-login [user|root]`; lewati dengan `MOLINA_NO_LOGIN_PROMPT=1`.
- Prompt Debian: blok bertanda `molina-prompt` ditambahkan ke `~/.bashrc` root, user, dan `/etc/skel`. Format `root@localhost:path $` dan `user@localhost:path $`: nama hijau (SGR 32), path hijau muda (SGR 92), tanda akhir `$` untuk root maupun user. `clear` dan Ctrl+L di Debian mencetak ulang banner yang sama dengan host.

## Skrip login: prompt dan penanda sesi
- `MolinaShellProfile` kini hanya menulis dua berkas di prefix host: `etc/profile.d/03-molina-login.sh` dan `etc/molina/prompt-block.bashrc`. Penulisan blok prompt ke `~/.bashrc` rootfs pindah ke skrip login dan dijalankan sebelum setiap login (tidak lagi menunggu aplikasi dimulai ulang).
- Selama sesi Debian berjalan, skrip menulis PID shell host ke `~/.molina/in-debian` dan menghapusnya saat keluar. Menu Debian di Setting membaca penanda ini (PID harus masih hidup) untuk mengaktifkan atau menonaktifkan kartu Keluar dan kartu Masuk.

## Persetujuan login root
- Login sebagai root meminta persetujuan: di Terminal lewat pertanyaan `Setuju? [y/N]` (pilihan 2 di menu awal dan `molina-login root`), di Setting lewat dialog Lanjutkan/Batal pada kartu "Masuk sebagai root". Kartu Setting menjalankan `molina-login root --yes` agar tidak bertanya dua kali. Login otomatis dengan pilihan root yang sudah tersimpan tidak bertanya lagi. Login sebagai user tidak meminta persetujuan.
