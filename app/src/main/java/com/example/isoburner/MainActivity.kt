package com.example.isoburner

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import me.jahnen.libaums.core.UsbMassStorageDevice

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var modeGroup: RadioGroup
    private var isoUri: Uri? = null
    private var isoSize = 0L

    private val ACTION_PERM = "com.example.isoburner.USB_PERMISSION"

    private val pickIso = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            isoUri = uri
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst() && i >= 0) isoSize = c.getLong(i)
            }
            log("ISO dipilih: $uri\nUkuran: ${isoSize / 1_048_576} MiB")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        val btnPick = Button(this).apply { text = "Pilih file ISO" }
        modeGroup = RadioGroup(this).apply {
            addView(RadioButton(context).apply { text = "Linux (raw/dd)"; id = 1; isChecked = true })
            addView(RadioButton(context).apply { text = "Windows (belum jadi)"; id = 2 })
        }
        val btnStart = Button(this).apply { text = "Tulis ke USB" }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        status = TextView(this).apply { text = "Colok flashdisk via OTG, pilih ISO, lalu Tulis." }

        listOf(btnPick, modeGroup, btnStart, progress, status).forEach {
            root.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        btnPick.setOnClickListener { pickIso.launch(arrayOf("*/*")) }
        btnStart.setOnClickListener { start() }

        ContextCompat.registerReceiver(
            this, permReceiver, IntentFilter(ACTION_PERM), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        unregisterReceiver(permReceiver)
        super.onDestroy()
    }

    private fun log(msg: String) = runOnUiThread { status.text = msg }

    private fun start() {
        if (isoUri == null) { log("Pilih ISO dulu"); return }
        val devices = UsbMassStorageDevice.getMassStorageDevices(this)
        if (devices.isEmpty()) { log("Flashdisk tidak terdeteksi (cek OTG)"); return }

        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val dev = devices[0]
        if (usbManager.hasPermission(dev.usbDevice)) {
            confirmAndWrite(dev)
        } else {
            val pi = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_PERM).setPackage(packageName),
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            )
            usbManager.requestPermission(dev.usbDevice, pi)
        }
    }

    private val permReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val granted = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted) start() else log("Izin USB ditolak")
        }
    }

    private fun confirmAndWrite(dev: UsbMassStorageDevice) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Hapus semua data flashdisk?")
            .setMessage("Semua data di flashdisk akan ditimpa. Lanjut?")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Lanjut") { _, _ -> runWrite(dev) }
            .show()
    }

    private fun runWrite(dev: UsbMassStorageDevice) {
        val uri = isoUri ?: return
        val windows = modeGroup.checkedRadioButtonId == 2
        Thread {
            try {
                log("Menulis...")
                contentResolver.openInputStream(uri)!!.use { input ->
                    val cb = { done: Long, total: Long ->
                        runOnUiThread {
                            progress.progress = if (total > 0) (done * 100 / total).toInt() else 0
                        }
                    }
                    if (windows) WindowsInstaller.install(dev, input, cb)
                    else RawWriter.write(dev, input, isoSize, cb)
                }
                log("Selesai. Aman dicabut.")
            } catch (e: Throwable) {
                log("Gagal: ${e.message}")
            }
        }.start()
    }
}
