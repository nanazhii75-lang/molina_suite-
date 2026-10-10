# Atribusi

molina-suite adalah karya turunan dari proyek open-source berikut. File lisensi,
catatan hak cipta, dan header author asli pada source upstream tidak diubah.

| Modul molina-suite | Proyek upstream | Sumber |
|---|---|---|
| Terminal (`com.molina.suite.terminal`) | Termux | https://github.com/termux/termux-app |
| Files (`com.molina.suite.files`) | Material Files | https://github.com/haizhang/MaterialFiles |
| Media (`com.molina.suite.kodi`) | Kodi | https://github.com/xbmc/xbmc |
| WebDash (`com.molina.suite.webdash`) | Fulguris | https://github.com/Slava77/Fulguris |

Lisensi tiap engine tercantum pada berkas `LICENSE` di dalam direktori
`engines/<nama-engine>/` setelah source upstream di-vendor. File yang dimodifikasi
dari upstream ditandai pada riwayat commit repositori ini.

## Sora Editor (engines/sora-editor)
- Sumber: https://github.com/Rosemoe/sora-editor, tag 0.23.4, commit dadd162532ea00f26434c6d0e3e5705524dae9cf
- Lisensi: GNU LGPL 2.1; file LICENSE upstream dipertahankan utuh di engines/sora-editor/LICENSE
- Copyright (C) 2020-2024 Rosemoe
- Dipakai oleh modul feature-code sebagai library editor teks. Paket asli io.github.rosemoe.sora tidak diganti dan tidak ada file upstream yang diubah; build memakai modul tipis di engines/sora-modules/.
- Sumber di engines/sora-editor dapat diganti dengan versi modifikasi lalu aplikasi dibangun ulang.

## Grammar dan tema TextMate (feature-code/src/main/assets/textmate)
- Disalin apa adanya dari engines/sora-editor/app/src/main/assets/textmate (Sora Editor 0.23.4) untuk penyorotan sintaks.
- Tiap grammar dan tema berasal dari proyek aslinya dan tunduk pada lisensi masing-masing; berkas lisensi yang menyertainya di dalam folder tersebut dipertahankan.

## Modifikasi kompatibilitas Android 8 (Sora Editor dan tm4e)
- Pada sumber upstream di engines/sora-editor, pemanggilan `Collection.toArray(X[]::new)` (Java 11, tidak ada di Android 8) diganti `Collection.toArray(new X[0])`. Perilakunya sama.
- `Stream.toList()` (Java 16) diganti `collect(Collectors.toList())`; hasilnya kini bisa diubah.
- Header lisensi dan hak cipta asli pada tiap berkas tidak diubah.

## Grammar TextMate (microsoft/vscode)

Grammar penyorotan sintaks di features/feature-code/src/main/assets/textmate (json, yaml, shell, css, c, cpp, sql, typescript, ini, go, rust) diambil dari https://github.com/microsoft/vscode tag 1.90.0, lisensi MIT. Teks lisensi ada di assets/textmate/LICENSE-vscode-grammars.txt.
