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
