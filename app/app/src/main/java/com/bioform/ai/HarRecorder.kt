package com.bioform.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Graba el acelerometro y el giroscopio del telefono a 50 Hz.
 * Cada fila: t_ms, ax, ay, az (m/s2), gx, gy, gz (rad/s).
 */
class HarRecorder(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val acc = FloatArray(3)
    private val gyr = FloatArray(3)

    val hasSensors: Boolean get() = accelerometer != null && gyroscope != null

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, acc, 0, 3)
            Sensor.TYPE_GYROSCOPE -> System.arraycopy(event.values, 0, gyr, 0, 3)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun stop() = sensorManager.unregisterListener(this)

    /** Graba durante [seconds] segundos. Se remuestrea a 50 Hz exactos (una fila cada 20 ms). */
    suspend fun record(seconds: Int, onSecondsLeft: (Int) -> Unit): List<FloatArray> {
        sensorManager.registerListener(this, accelerometer, 10_000)  // ~100 Hz de entrada
        sensorManager.registerListener(this, gyroscope, 10_000)
        val periodMs = 20L
        val total = seconds * 1000L
        val rows = ArrayList<FloatArray>(seconds * 50)
        val t0 = SystemClock.elapsedRealtime()
        var n = 0L
        try {
            while (n * periodMs < total) {
                val wait = t0 + n * periodMs - SystemClock.elapsedRealtime()
                if (wait > 0) delay(wait)
                rows.add(floatArrayOf((n * periodMs).toFloat(), acc[0], acc[1], acc[2], gyr[0], gyr[1], gyr[2]))
                if (n % 50 == 0L) onSecondsLeft(seconds - (n * periodMs / 1000).toInt())
                n++
            }
        } finally {
            stop()
        }
        return rows
    }
}

/** Guarda y cuenta las grabaciones en Android/data/com.bioform.ai/files/har/. */
object HarFiles {

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "har").apply { mkdirs() }

    /** Modelo del telefono sin espacios ni guiones bajos (ej. SM-A536E). */
    fun deviceModel(): String =
        Build.MODEL.replace(Regex("[^A-Za-z0-9-]"), "-").ifEmpty { "desconocido" }

    fun save(context: Context, subject: String, activity: String, position: String, rows: List<FloatArray>): File {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(dir(context), "${subject}_${activity}_${position}_${stamp}_${deviceModel()}.csv")
        file.bufferedWriter().use { w ->
            w.write("t_ms,ax,ay,az,gx,gy,gz\n")
            rows.forEach { r ->
                // Locale.US: punto decimal siempre, aunque el telefono este en espanol
                w.write(String.format(Locale.US, "%.0f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f\n", r[0], r[1], r[2], r[3], r[4], r[5], r[6]))
            }
        }
        return file
    }

    fun allFiles(context: Context): List<File> =
        (dir(context).listFiles { f -> f.name.endsWith(".csv") } ?: emptyArray()).sortedBy { it.name }

    /**
     * Junta todas las grabaciones en un .zip y abre el menu de compartir de Android
     * (WhatsApp, Drive, correo). Devuelve cuantas grabaciones se compartieron.
     */
    fun shareAll(context: Context): Int {
        val files = allFiles(context)
        if (files.isEmpty()) return 0
        val exportDir = File(context.cacheDir, "har_export").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val zip = File(exportDir, "bioform_har_${deviceModel()}_$stamp.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            files.forEach { f ->
                zos.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Grabaciones BioForm AI (${files.size})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir grabaciones"))
        return files.size
    }

    fun countByActivity(context: Context): Map<String, Int> =
        allFiles(context)
            .groupingBy { it.name.split("_").getOrNull(1) ?: "?" }
            .eachCount()
}
