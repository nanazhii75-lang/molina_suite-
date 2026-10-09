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
