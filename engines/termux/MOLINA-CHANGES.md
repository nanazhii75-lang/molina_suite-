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
