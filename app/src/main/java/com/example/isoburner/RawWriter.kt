package com.example.isoburner

import me.jahnen.libaums.core.UsbMassStorageDevice
import java.io.InputStream

/** Mode Linux: tulis ISO hybrid mentah dari sektor 0 (seperti dd). */
object RawWriter {
    fun write(
        dev: UsbMassStorageDevice,
        input: InputStream,
        isoSize: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val io = BlockIo(dev)
        try {
            val bs = io.blockSize
            io.blocks?.let {
                val capacity = it * bs
                require(isoSize <= capacity) { "ISO ($isoSize B) lebih besar dari flashdisk ($capacity B)" }
            }
            val chunk = bs * maxOf(1, (1 shl 20) / bs)
            val buf = ByteArray(chunk)
            var sector = 0L
            var done = 0L
            while (true) {
                val n = readFully(input, buf)
                if (n <= 0) break
                val padded = ((n + bs - 1) / bs) * bs
                if (padded > n) java.util.Arrays.fill(buf, n, padded, 0.toByte())
                io.writeSectors(sector, buf, padded)
                sector += padded / bs
                done += n
                onProgress(done, isoSize)
                if (n < chunk) break
            }
        } finally {
            io.close()
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
