package com.example.isoburner

import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import java.nio.ByteBuffer

/**
 * Akses sektor mentah ke flashdisk lewat driver blok libaums.
 * Driver diambil lewat reflection (API publik tidak terverifikasi), dan satuan offset
 * (blok vs byte) dideteksi saat runtime lewat tes tulis-baca kecil di sektor 0-1.
 */
class BlockIo(private val dev: UsbMassStorageDevice) {
    private val driver: BlockDeviceDriver
    val blockSize: Int
    val blocks: Long?
    private var offsetInBlocks = true

    init {
        try {
            dev.init()
        } catch (e: Exception) {
            // init bisa gagal kalau tabel partisi tidak dikenali; driver mungkin tetap siap
        }
        driver = findDriver(dev)
        blockSize = (getter("getBlockSize") as? Number)?.toInt() ?: 512
        blocks = ((getter("getBlocks") ?: getter("getSize")) as? Number)?.toLong()
        probeUnit()
    }

    private fun getter(name: String): Any? = try {
        driver.javaClass.getMethod(name).apply { isAccessible = true }.invoke(driver)
    } catch (e: NoSuchMethodException) {
        null
    }

    private fun findDriver(d: UsbMassStorageDevice): BlockDeviceDriver {
        for (f in UsbMassStorageDevice::class.java.declaredFields) {
            if (BlockDeviceDriver::class.java.isAssignableFrom(f.type)) {
                f.isAccessible = true
                (f.get(d) as? BlockDeviceDriver)?.let { return it }
            }
        }
        error("Block device driver tidak ditemukan (init gagal atau struktur libaums berubah)")
    }

    /** Tulis 2 blok berpola, baca "offset 1": kalau isinya blok ke-2, satuan offset = blok. */
    private fun probeUnit() {
        val a = ByteArray(blockSize) { 0x11 }
        val b = ByteArray(blockSize) { 0x22 }
        driver.write(0, ByteBuffer.wrap(a + b))
        val r = ByteArray(blockSize)
        driver.read(1, ByteBuffer.wrap(r))
        if (r.contentEquals(b)) {
            offsetInBlocks = true
            return
        }
        val r2 = ByteArray(blockSize)
        driver.read(blockSize.toLong(), ByteBuffer.wrap(r2))
        if (r2.contentEquals(b)) {
            offsetInBlocks = false
            return
        }
        error("Satuan offset driver tidak bisa dideteksi (tes tulis-baca gagal)")
    }

    fun writeSectors(sector: Long, data: ByteArray, len: Int = data.size) {
        require(len % blockSize == 0) { "Panjang data harus kelipatan $blockSize" }
        val off = if (offsetInBlocks) sector else sector * blockSize
        driver.write(off, ByteBuffer.wrap(data, 0, len))
    }

    fun zeroSectors(start: Long, count: Long) {
        val chunkSectors = maxOf(1, (1 shl 20) / blockSize)
        val zeros = ByteArray(chunkSectors * blockSize)
        var done = 0L
        while (done < count) {
            val n = minOf(chunkSectors.toLong(), count - done).toInt()
            writeSectors(start + done, zeros, n * blockSize)
            done += n
        }
    }

    fun close() {
        try { dev.close() } catch (_: Exception) {}
    }
}
