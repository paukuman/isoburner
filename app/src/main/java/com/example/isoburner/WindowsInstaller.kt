package com.example.isoburner

import me.jahnen.libaums.core.UsbMassStorageDevice
import java.io.InputStream

/**
 * Mode Windows: BELUM DIIMPLEMENTASI (kerangka saja).
 *
 * ISO Windows tidak bisa ditulis raw. Langkah yang harus dibuat:
 *  1. Tulis tabel partisi: MBR (BIOS/Legacy) atau GPT (UEFI).
 *     libaums TIDAK bisa membuat partisi -> tulis sendiri lewat BlockDeviceDriver.write().
 *  2. Format FAT32 (atau exFAT/NTFS). libaums hanya bisa BACA/TULIS FAT32 yang sudah ada,
 *     tidak bisa memformat -> tulis sendiri boot sector, FAT, root dir.
 *  3. Baca isi ISO. ISO Windows biasanya UDF (kadang ISO9660 + UDF bridge) -> butuh parser UDF
 *     (tidak ada library Android siap pakai; cari port Java dari udf reader / jlibudf / port libcdio).
 *  4. Salin semua file ke FAT32. Batas FAT32 = 4 GiB per file. Kalau sources/install.wim > 4 GiB,
 *     harus di-split jadi install.swm, install2.swm, ... (butuh implementasi split WIM,
 *     atau pakai NTFS/exFAT + UEFI:NTFS bootloader).
 *  5. Untuk BIOS: tulis boot code di MBR + PBR (bootmgr). Untuk UEFI: cukup file
 *     efi/boot/bootx64.efi dari ISO + partisi FAT32.
 *
 * Saran urutan kerja: selesaikan MBR + FAT32 + copy file dulu dengan ISO Windows kecil
 * (mis. Windows PE / Tiny11 yang install.wim < 4 GiB), baru tangani split WIM.
 */
object WindowsInstaller {
    fun install(
        dev: UsbMassStorageDevice,
        iso: InputStream,
        onProgress: (Long, Long) -> Unit
    ) {
        throw NotImplementedError("Mode Windows belum diimplementasi, lihat komentar di file ini")
    }
}
