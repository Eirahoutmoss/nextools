package com.eirahoutmoss.nextools.barolog

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * İsteğe bağlı barometre günlüğü: ~30 dakikada bir tek basınç okuması dosyaya yazılır
 * (son 48 saat tutulur). Barometre ekranındaki 24 saatlik eğilim bu veriyle çizilir.
 * Kullanıcı açmadıkça çalışmaz. Okuma birkaç saniye sürer; pil etkisi önemsizdir.
 */
class PressureLog(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("pressure_log", Context.MODE_PRIVATE)
    private val file = File(app.filesDir, "pressure_log.csv")

    private val _enabled = MutableStateFlow(prefs.getBoolean(K_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _version = MutableStateFlow(0)
    /** Yeni okuma eklendikçe artar (ekran yenilensin diye). */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(K_ENABLED, on).apply()
        _enabled.value = on
        val js = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (on) {
            val job = JobInfo.Builder(JOB_ID, ComponentName(app, PressureLogJob::class.java))
                .setPeriodic(30 * 60_000L)
                .setPersisted(true)
                .build()
            js.schedule(job)
        } else {
            js.cancel(JOB_ID)
        }
    }

    @Synchronized
    fun append(timeMs: Long, hpa: Double) {
        val cutoff = timeMs - 48 * 3_600_000L
        val kept = read().filter { it.first >= cutoff } + (timeMs to hpa)
        file.writeText(kept.joinToString("\n") { "${it.first},${"%.3f".format(java.util.Locale.ROOT, it.second)}" } + "\n")
        _version.value++
    }

    @Synchronized
    fun read(): List<Pair<Long, Double>> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val p = line.split(',')
            if (p.size != 2) null else p[0].toLongOrNull()?.let { t -> p[1].toDoubleOrNull()?.let { t to it } }
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        _version.value++
    }

    companion object {
        const val JOB_ID = 4201
        private const val K_ENABLED = "enabled"
    }
}

/** JobScheduler işi: barometreden birkaç örnek alıp ortalamasını günlüğe yazar. */
class PressureLogJob : JobService() {
    private var listener: SensorEventListener? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return false
        val values = ArrayList<Float>()
        val handler = Handler(Looper.getMainLooper())
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                values += e.values[0]
                if (values.size >= 5) finish(sm, params, values)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        listener = l
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
        // Bazı cihazlarda sensör olay göndermeyebilir: 10 sn sonra elimizdekiyle bitir.
        handler.postDelayed({ finish(sm, params, values) }, 10_000)
        return true
    }

    private fun finish(sm: SensorManager, params: JobParameters, values: List<Float>) {
        val l = listener ?: return
        listener = null
        sm.unregisterListener(l)
        if (values.isNotEmpty()) {
            val app = application as? com.eirahoutmoss.nextools.NexToolsApp
            val log = app?.container?.pressureLog ?: PressureLog(this)
            log.append(System.currentTimeMillis(), values.average())
        }
        jobFinished(params, false)
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        listener?.let { sm?.unregisterListener(it) }
        listener = null
        return true
    }
}
