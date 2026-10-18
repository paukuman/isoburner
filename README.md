# ISO Burner (kerangka)

Aplikasi Android untuk menulis ISO ke flashdisk via OTG tanpa root, pakai USB Host API + libaums.

## Status
- Mode Linux (raw/dd): sudah ada, perlu diuji.
- Mode Windows: kerangka + rencana di `WindowsInstaller.kt`.

## Build
Push ke GitHub, buka tab Actions, jalankan "Build APK", download artifact `app-debug`.

## Catatan uji
- Uji pertama pakai ISO Linux kecil (mis. Alpine/Tiny Core) dan flashdisk cadangan.
- Cek `blockDevices[0]` memang seluruh disk, bukan partisi pertama.
