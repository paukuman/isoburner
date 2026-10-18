package com.example.isoburner

/**
 * Tulis MBR (1 partisi tipe 0x0C) + format FAT32 kosong. Sektor 512 byte saja.
 * Cukup untuk boot UEFI (tanpa boot code). Boot BIOS/Legacy belum didukung.
 */
object Fat32Formatter {
    private const val SECTOR = 512
    private const val PART_START = 2048L
    private const val RESERVED = 32

    private fun w16(a: ByteArray, o: Int, v: Int) {
        a[o] = (v and 0xFF).toByte()
        a[o + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun w32(a: ByteArray, o: Int, v: Long) {
        for (i in 0 until 4) a[o + i] = ((v shr (8 * i)) and 0xFF).toByte()
    }

    fun format(io: BlockIo, label: String, onStatus: (String) -> Unit) {
        require(io.blockSize == SECTOR) {
            "Hanya sektor 512 byte yang didukung (flashdisk ini ${io.blockSize})"
        }
        val total = io.blocks ?: error("Kapasitas flashdisk tidak diketahui")
        onStatus("Kapasitas terdeteksi: ${total * SECTOR / 1048576} MiB")

        val volSectors = minOf(total - PART_START, 0xFFFFFFFFL)
        val spc = when {
            volSectors < 532_480L -> error("Flashdisk terlalu kecil untuk FAT32 (min ~260 MiB)")
            volSectors <= 16_777_216L -> 8
            volSectors <= 33_554_432L -> 16
            volSectors <= 67_108_864L -> 32
            else -> 64
        }
        val tmp2 = (256L * spc + 2) / 2
        val fatSz = ((volSectors - RESERVED) + tmp2 - 1) / tmp2
        val dataStart = RESERVED + 2 * fatSz
        val clusters = (volSectors - dataStart) / spc
        check(clusters >= 65_525) { "Jumlah cluster terlalu sedikit untuk FAT32" }

        onStatus("Membersihkan sektor awal dan akhir...")
        io.zeroSectors(1, PART_START - 1)                 // sisa GPT lama
        io.zeroSectors(total - 34, 34)                    // GPT cadangan lama

        onStatus("Menulis tabel partisi (MBR)...")
        val rnd = java.util.Random()
        val mbr = ByteArray(SECTOR)
        w32(mbr, 0x1B8, rnd.nextInt().toLong() and 0xFFFFFFFFL)
        mbr[446] = 0x80.toByte()                          // flag aktif
        mbr[447] = 0xFE.toByte(); mbr[448] = 0xFF.toByte(); mbr[449] = 0xFF.toByte()
        mbr[450] = 0x0C                                   // FAT32 LBA
        mbr[451] = 0xFE.toByte(); mbr[452] = 0xFF.toByte(); mbr[453] = 0xFF.toByte()
        w32(mbr, 454, PART_START)
        w32(mbr, 458, volSectors)
        mbr[510] = 0x55; mbr[511] = 0xAA.toByte()
        io.writeSectors(0, mbr)

        onStatus("Memformat FAT32 (cluster ${spc * SECTOR / 1024} KiB)...")
        val zeroCount = RESERVED + 2 * fatSz + spc        // reserved + 2 FAT + cluster root
        io.zeroSectors(PART_START, zeroCount)

        val volId = rnd.nextInt().toLong() and 0xFFFFFFFFL
        val boot = ByteArray(SECTOR)
        boot[0] = 0xEB.toByte(); boot[1] = 0x58; boot[2] = 0x90.toByte()
        "MSWIN4.1".toByteArray(Charsets.US_ASCII).copyInto(boot, 3)
        w16(boot, 11, SECTOR)
        boot[13] = spc.toByte()
        w16(boot, 14, RESERVED)
        boot[16] = 2                                      // jumlah FAT
        w16(boot, 17, 0); w16(boot, 19, 0)
        boot[21] = 0xF8.toByte()
        w16(boot, 22, 0); w16(boot, 24, 63); w16(boot, 26, 255)
        w32(boot, 28, PART_START)
        w32(boot, 32, volSectors)
        w32(boot, 36, fatSz)
        w16(boot, 40, 0); w16(boot, 42, 0)
        w32(boot, 44, 2)                                  // cluster root
        w16(boot, 48, 1)                                  // sektor FSInfo
        w16(boot, 50, 6)                                  // boot sector cadangan
        boot[64] = 0x80.toByte()
        boot[66] = 0x29
        w32(boot, 67, volId)
        label.uppercase().padEnd(11).take(11).toByteArray(Charsets.US_ASCII).copyInto(boot, 71)
        "FAT32   ".toByteArray(Charsets.US_ASCII).copyInto(boot, 82)
        boot[510] = 0x55; boot[511] = 0xAA.toByte()

        val fsInfo = ByteArray(SECTOR)
        w32(fsInfo, 0, 0x41615252L)
        w32(fsInfo, 484, 0x61417272L)
        w32(fsInfo, 488, clusters - 1)                    // cluster bebas (root memakai 1)
        w32(fsInfo, 492, 3)
        w32(fsInfo, 508, 0xAA550000L)

        io.writeSectors(PART_START, boot)
        io.writeSectors(PART_START + 1, fsInfo)
        io.writeSectors(PART_START + 6, boot)
        io.writeSectors(PART_START + 7, fsInfo)

        val fat0 = ByteArray(SECTOR)
        w32(fat0, 0, 0x0FFFFFF8L)
        w32(fat0, 4, 0x0FFFFFFFL)
        w32(fat0, 8, 0x0FFFFFFFL)                         // root = akhir rantai
        io.writeSectors(PART_START + RESERVED, fat0)
        io.writeSectors(PART_START + RESERVED + fatSz, fat0)
        onStatus("Format selesai")
    }
}
