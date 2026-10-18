package com.example.isoburner

import me.jahnen.libaums.core.UsbMassStorageDevice
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

/**
 * Mode Linux: tulis ISO hybrid mentah (raw) dari byte 0 flashdisk (seperti dd).
 * Driver blok diambil lewat reflection karena API publiknya tidak terverifikasi.
 */
object RawWriter {

    private fun findDriver(dev: UsbMassStorageDevice): Any {
        for (f in UsbMassStorageDevice::class.java.declaredFields) {
            if (f.type.name.endsWith("BlockDeviceDriver")) {
                f.isAccessible = true
                val v = f.get(dev)
                if (v != null) return v
            }
        }
        error("Block device driver tidak ditemukan (init gagal atau struktur libaums berubah)")
    }

    private fun Any.call(name: String): Any? = try {
        javaClass.getMethod(name).apply { isAccessible = true }.invoke(this)
    } catch (e: NoSuchMethodException) {
        null
    }

    fun write(
        dev: UsbMassStorageDevice,
        input: InputStream,
        isoSize: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        try {
            try {
                dev.init()
            } catch (e: Exception) {
                // init bisa gagal kalau tabel partisi tidak dikenali; driver mungkin tetap siap
            }
            val driver = findDriver(dev)
            val bs = (driver.call("getBlockSize") as? Number)?.toInt() ?: 512

            val blocks = ((driver.call("getBlocks") ?: driver.call("getSize")) as? Number)?.toLong()
            if (blocks != null) {
                val capacity = blocks * bs
                require(isoSize <= capacity) { "ISO ($isoSize B) lebih besar dari flashdisk ($capacity B)" }
            }

            val writeM = driver.javaClass.getMethod(
                "write", java.lang.Long.TYPE, ByteBuffer::class.java
            ).apply { isAccessible = true }

            val chunkSize = bs * maxOf(1, (1 shl 20) / bs) // ~1 MiB, kelipatan blockSize
            val buf = ByteArray(chunkSize)
            var offset = 0L

            while (true) {
                val n = readFully(input, buf)
                if (n <= 0) break
                val padded = ((n + bs - 1) / bs) * bs
                if (padded > n) java.util.Arrays.fill(buf, n, padded, 0.toByte())
                try {
                    writeM.invoke(driver, offset, ByteBuffer.wrap(buf, 0, padded))
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
                offset += n
                onProgress(offset, isoSize)
                if (n < chunkSize) break
            }
        } finally {
            try { dev.close() } catch (_: Exception) {}
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = input.read(buf, total, buf.size - total)
            if (r < 0) break
            total += r
        }
        return total
    }
}
