package com.eirahoutmoss.nextools.ui.tools

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.Fft
import com.eirahoutmoss.nextools.core.measure.Notes
import com.eirahoutmoss.nextools.core.measure.Spectra
import com.eirahoutmoss.nextools.records.Column as DataColumn
import com.eirahoutmoss.nextools.sensors.MicReader
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.components.BadgeLevel
import com.eirahoutmoss.nextools.ui.components.BigReadout
import com.eirahoutmoss.nextools.ui.components.NoticeBanner
import com.eirahoutmoss.nextools.ui.components.RecordingSpec
import com.eirahoutmoss.nextools.ui.components.SectionCard
import com.eirahoutmoss.nextools.ui.components.SpectrumChart
import com.eirahoutmoss.nextools.ui.components.ToolScaffold
import com.eirahoutmoss.nextools.ui.components.rememberRecording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

private const val N = 4096
private const val SG_W = 240
private const val SG_H = 128

private class SpecFrame(val db: FloatArray, val fs: Int, val peakHz: Double?, val peakDb: Double, val totalDbfs: Double, val sg: ImageBitmap?)

/** Turuncu-sarı ısı haritası (koyu → turkuaz → sarı → kırmızı). */
private fun heat(t: Float): Int {
    val v = t.coerceIn(0f, 1f)
    val stops = arrayOf(floatArrayOf(10f, 15f, 20f), floatArrayOf(8f, 80f, 120f), floatArrayOf(34f, 211f, 238f), floatArrayOf(250f, 220f, 60f), floatArrayOf(239f, 68f, 68f))
    val p = v * (stops.size - 1); val i = p.toInt().coerceAtMost(stops.size - 2); val f = p - i
    val a = stops[i]; val b = stops[i + 1]
    return (0xFF shl 24) or ((a[0] + (b[0] - a[0]) * f).toInt() shl 16) or ((a[1] + (b[1] - a[1]) * f).toInt() shl 8) or (a[2] + (b[2] - a[2]) * f).toInt()
}

/** Frekans Analizörü: canlı spektrum, şelale (spektrogram), baskın frekans ve nota. */
@Composable
fun SpectrumTool(onBack: () -> Unit, onOpenRecord: (String) -> Unit) {
    val context = LocalContext.current
    var ok by remember { mutableStateOf(context.hasPermission(Manifest.permission.RECORD_AUDIO)) }
    if (!ok) {
        ToolScaffold(ToolId.SPECTRUM, onBack, onOpenRecord, null, null, false, null, null) {
            PermissionGate(Manifest.permission.RECORD_AUDIO, stringResource(R.string.mic_rationale_generic)) { LaunchedEffect(Unit) { ok = true } }
        }
        return
    }
    val mic = remember { MicReader(context) }
    val frames = remember { MutableStateFlow<SpecFrame?>(null) }
    val frame by frames.collectAsStateWithLifecycle()
    val micError by mic.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val started = rememberIsStarted()
    val rec = rememberRecording(ToolId.SPECTRUM)
    var frozen by remember { mutableStateOf(false) }
    var logX by rememberSaveable { mutableStateOf(true) }
    var holdOn by rememberSaveable { mutableStateOf(false) }
    val hold = remember { arrayOfNulls<FloatArray>(1) }
    val noteNames = stringArrayResource(R.array.note_names)
    val window = remember { Fft.hann(N) }
    val pixels = remember { IntArray(SG_W * SG_H) { heat(0f) } }
    val lastPost = remember { longArrayOf(0L) }
    val frozenRef = remember { booleanArrayOf(false) }
    frozenRef[0] = frozen

    LaunchedEffect(started) {
        if (started) mic.start(scope, N, N / 2) { win, fs ->
            val x = DoubleArray(N) { win[it].toDouble() }
            val amp = Fft.amplitudeSpectrum(x, window)
            val db = FloatArray(amp.size) { Spectra.toDb(amp[it], 1e-7).toFloat() }
            val peak = Spectra.dominant(amp, fs.toDouble(), N, minHz = 30.0, maxHz = 16_000.0)
            var ss = 0.0; for (v in x) ss += v * v
            val total = 10 * log10(max(ss / N, 1e-14))
            if (rec.isRecording) rec.add(doubleArrayOf(peak?.hz ?: Double.NaN, peak?.let { Spectra.toDb(it.amplitude) } ?: Double.NaN, total))
            if (frozenRef[0]) return@start
            // spektrogram: sola kaydır, sağ sütuna yeni spektrum (log frekans, 30 Hz–fs/2)
            for (row in 0 until SG_H) System.arraycopy(pixels, row * SG_W + 1, pixels, row * SG_W, SG_W - 1)
            val fMin = 30.0; val fMax = fs / 2.0
            for (row in 0 until SG_H) {
                val f = fMin * (fMax / fMin).pow((SG_H - 1 - row).toDouble() / (SG_H - 1))
                val k = (f / (fs.toDouble() / N)).toInt().coerceIn(1, db.size - 1)
                pixels[row * SG_W + SG_W - 1] = heat(((db[k] + 110f) / 90f))
            }
            hold[0] = hold[0]?.let { h -> FloatArray(db.size) { max(h[it], db[it]) } } ?: db.copyOf()
            val now = System.nanoTime()
            if (now - lastPost[0] > 80_000_000L) {
                lastPost[0] = now
                val bmp = Bitmap.createBitmap(pixels, SG_W, SG_H, Bitmap.Config.ARGB_8888).asImageBitmap()
                frames.value = SpecFrame(db, fs, peak?.hz, peak?.let { Spectra.toDb(it.amplitude) } ?: -120.0, total, bmp)
            }
        } else mic.stop()
    }
    DisposableEffect(Unit) { onDispose { mic.stop() } }

    ToolScaffold(
        tool = ToolId.SPECTRUM, onBack = onBack, onOpenRecord = onOpenRecord,
        recording = rec,
        onStartRecording = {
            val note = context.getString(R.string.spectrum_note, N, mic.sampleRate)
            rec.start(
                RecordingSpec(
                    columns = listOf(DataColumn("dom_hz", "Hz", 1), DataColumn("peak_dbfs", "dBFS", 1), DataColumn("total_dbfs", "dBFS", 1)),
                    headerComments = csvHeader(context, ToolId.SPECTRUM, "microphone ${mic.sourceName}", note, listOf("FFT $N, Hann window", "audio is not stored")),
                    calibrated = false, calibrationNote = note, sensorName = context.getString(R.string.hw_microphone),
                ),
            )
        },
        frozen = frozen, onFreezeToggle = { frozen = !frozen }, onCalibrate = null,
    ) {
        if (micError != 0) { NoticeBanner(stringResource(R.string.sound_mic_failed, micError), BadgeLevel.ALARM); return@ToolScaffold }
        val f = frame
        if (f == null) { Text(stringResource(R.string.waiting_sensor)); return@ToolScaffold }
        val note = f.peakHz?.takeIf { f.peakDb > -80 }?.let { Notes.fromHz(it) }
        BigReadout(
            value = f.peakHz?.takeIf { f.peakDb > -80 }?.let { Fmt.num(it, if (it < 1000) 1 else 0) } ?: "—", unit = "Hz",
            caption = note?.let { stringResource(R.string.spectrum_note_line, noteNames[it.pitchClass], it.octave, Fmt.signed(it.cents, 0)) } ?: stringResource(R.string.spectrum_no_peak),
            badge = stringResource(R.string.spectrum_badge), badgeLevel = BadgeLevel.NEUTRAL,
        )
        SectionCard(null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(logX, { logX = !logX }, label = { Text(stringResource(R.string.spectrum_log)) })
                FilterChip(holdOn, { holdOn = !holdOn; hold[0] = null }, label = { Text(stringResource(R.string.spectrum_hold)) })
            }
            SpectrumChart(
                f.db, binHz = f.fs.toDouble() / N, minHz = if (logX) 20.0 else 0.0, maxHz = f.fs / 2.0, minDb = -120f, maxDb = 0f,
                logX = logX, hold = if (holdOn) hold[0] else null, markHz = f.peakHz,
            )
            Text(stringResource(R.string.spectrum_total, Fmt.num(f.totalDbfs, 1)), style = MaterialTheme.typography.bodySmall)
        }
        f.sg?.let { bmp ->
            SectionCard(stringResource(R.string.spectrum_waterfall)) {
                Image(bmp, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxWidth().height(160.dp))
                Text(stringResource(R.string.spectrum_waterfall_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.sound_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
