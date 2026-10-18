package com.example.isoburner

import me.jahnen.libaums.core.UsbMassStorageDevice
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Mode Linux: tulis ISO hybrid mentah (raw) dari byte 0 flashdisk.
 * Sama seperti `dd if=file.iso of=/dev/sdX`.
 */
object RawWriter {
    fun write(
        dev: UsbMassStorageDevice,
        input: InputStream,
        isoSize: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        dev.init()
        try {
            // blockDevices[0] = seluruh disk (bukan partisi). Verifikasi saat testing.
            val bd = dev.blockDevices[0] ?: error("Block device tidak ditemukan")
            val bs = bd.blockSize
            val capacity = bd.blocks * bs
            require(isoSize <= capacity) { "ISO ($isoSize B) lebih besar dari flashdisk ($capacity B)" }

            val chunkSize = bs * maxOf(1, (1 shl 20) / bs) // ~1 MiB, kelipatan blockSize
            val buf = ByteArray(chunkSize)
            var offset = 0L

            while (true) {
                val n = readFully(input, buf)
                if (n <= 0) break
                // Padding ke kelipatan blockSize untuk chunk terakhir
                val padded = ((n + bs - 1) / bs) * bs
                if (padded > n) java.util.Arrays.fill(buf, n, padded, 0)
                bd.write(offset, ByteBuffer.wrap(buf, 0, padded))
                offset += n
                onProgress(offset, isoSize)
                if (n < chunkSize) break
            }
        } finally {
            dev.close()
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
