package com.example.isoburner

import me.jahnen.libaums.core.UsbMassStorageDevice

/**
 * Mode Windows (UEFI), dikerjakan bertahap:
 *  Tahap 1 (SEKARANG): MBR + format FAT32 kosong.            -> format()
 *  Tahap 2: pembaca UDF 1.02 (baca ISO lewat FileChannel agar bisa seek), tampilkan daftar file.
 *  Tahap 3: salin semua file ISO ke FAT32 (via libaums UsbFile) dengan progress.
 *  Tahap 4 (opsional): boot BIOS/Legacy (boot code PBR + bootmgr), split WIM > 4 GiB.
 */
object WindowsInstaller {
    fun format(dev: UsbMassStorageDevice, onStatus: (String) -> Unit) {
        val io = BlockIo(dev)
        try {
            Fat32Formatter.format(io, "WINUSB", onStatus)
        } finally {
            io.close()
        }
    }
}
