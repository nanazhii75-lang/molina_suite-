# Molina Terminal (engine)

Emulator terminal untuk molina-suite (`com.molina.suite.terminal`). Source ini adalah turunan
dari Termux (termux-app v0.118.3): https://github.com/termux/termux-app

- Lisensi: [LICENSE.md](LICENSE.md) dan [termux-shared/LICENSE.md](termux-shared/LICENSE.md),
  tidak diubah. Header lisensi dan nama author asli di berkas sumber dipertahankan.
- Daftar perubahan terhadap upstream: [MOLINA-CHANGES.md](MOLINA-CHANGES.md)
- Atribusi seluruh proyek: [../../docs/ATTRIBUTION.md](../../docs/ATTRIBUTION.md)

## Catatan

- Modul ini adalah library; aplikasi dibangun oleh monorepo molina-suite (lihat root repositori),
  bukan dari folder ini.
- Prefix: `/data/data/com.molina.suite/files/usr`, home: `/data/data/com.molina.suite/files/home`.
  Bootstrap diambil dari release repositori molina-suite.
- `pkg` sengaja diblokir. Alat tambahan (python3, ffmpeg, yt-dlp, git, dan lainnya) dipasang di
  Debian lewat `proot-distro` dari menu Setting.
- Aplikasi plugin upstream (API, Float, Tasker, Widget, Styling, Boot) tidak disertakan.

Repositori: https://github.com/nanazhii75-lang/molina_suite-
