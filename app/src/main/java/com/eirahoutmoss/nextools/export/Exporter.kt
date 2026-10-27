package com.eirahoutmoss.nextools.export

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.content.FileProvider
import com.eirahoutmoss.nextools.BuildConfig
import com.eirahoutmoss.nextools.R
import com.eirahoutmoss.nextools.core.catalog.Hardware
import com.eirahoutmoss.nextools.core.catalog.Presence
import com.eirahoutmoss.nextools.hardware.HardwareProbe
import com.eirahoutmoss.nextools.records.RecordMeta
import com.eirahoutmoss.nextools.records.RecordStore
import com.eirahoutmoss.nextools.ui.Fmt
import com.eirahoutmoss.nextools.ui.ToolUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Dışa aktarma: CSV, JSON, PDF rapor (grafik + özet + cihaz bilgisi + kalibrasyon durumu). */
class Exporter(private val context: Context, private val records: RecordStore) {

    private val exportDir: File get() = File(context.cacheDir, "export").apply { mkdirs() }

    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)

    private fun baseName(meta: RecordMeta): String = "NexTools_${meta.tool.key}_${Fmt.fileStamp(meta.startedAtMs)}"

    /** Eski dışa aktarımlar birikmesin. */
    private fun cleanup() {
        val cutoff = System.currentTimeMillis() - 24 * 3_600_000L
        exportDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    suspend fun csv(meta: RecordMeta): File = withContext(Dispatchers.IO) {
        cleanup()
        val out = File(exportDir, baseName(meta) + ".csv")
        records.dataFile(meta.id).copyTo(out, overwrite = true)
        out
    }

    /** Konum içeren kayıtlar (İz, Alan, GPS) GPX olarak da aktarılabilir. */
    fun hasGeo(meta: RecordMeta): Boolean = meta.columns.any { it.name == "lat" } && meta.columns.any { it.name == "lon" }

    suspend fun gpx(meta: RecordMeta): File = withContext(Dispatchers.IO) {
        cleanup()
        val t = records.readTable(meta.id)
        val iLat = t.columns.indexOf("lat"); val iLon = t.columns.indexOf("lon")
        val iEle = t.columns.indexOf("altitude_m")
        val pts = t.rows.filter { !it[iLat].isNaN() && !it[iLon].isNaN() }.map { r ->
            com.eirahoutmoss.nextools.core.geo.Gpx.Point(r[iLat], r[iLon], if (iEle >= 0) r[iEle].takeIf { v -> !v.isNaN() } else null, meta.startedAtMs + r[0].toLong())
        }
        val out = File(exportDir, baseName(meta) + ".gpx")
        out.bufferedWriter().use { com.eirahoutmoss.nextools.core.geo.Gpx.write(meta.title.ifBlank { "NexTools" }, pts, it) }
        out
    }

    suspend fun json(meta: RecordMeta): File = withContext(Dispatchers.IO) {
        cleanup()
        val table = records.readTable(meta.id)
        val o = meta.toJson()
        o.put("data", JSONObject().apply {
            put("columns", JSONArray(table.columns))
            put("rows", JSONArray(table.rows.map { r -> JSONArray(r.map { v -> if (v.isNaN()) JSONObject.NULL else v }) }))
        })
        val out = File(exportDir, baseName(meta) + ".json")
        out.writeText(o.toString(1))
        out
    }

    suspend fun pdf(meta: RecordMeta): File = withContext(Dispatchers.IO) {
        cleanup()
        val table = records.readTable(meta.id)
        val toolName = s(ToolUi.text(meta.tool).name)
        val w = PdfWriter(s(R.string.pdf_footer, BuildConfig.VERSION_NAME))
        w.newPage()
        w.text(s(R.string.pdf_report_kicker), 9f, PdfWriter.ACCENT, bold = true)
        w.title(meta.title.ifBlank { toolName })
        w.text("$toolName · ${Fmt.dateTime(meta.startedAtMs)} · ${Fmt.duration(meta.durationMs)}", 10f, PdfWriter.MUTED)

        w.heading(s(R.string.pdf_summary))
        val sumRows = meta.summary.map { item ->
            val label = s(ToolUi.summaryKind(item.kind)) + " · " + ToolUi.columnText(item.column) { s(it) }
            label to (Fmt.num(item.value, item.decimals) + " " + item.unit).trim()
        }
        w.table(
            sumRows + listOf(
                s(R.string.pdf_samples) to meta.sampleCount.toString(),
                s(R.string.pdf_duration) to Fmt.duration(meta.durationMs),
            ),
        )

        if (table.rows.isNotEmpty() && table.columns.size > 1) {
            val oneShot = table.rows.size > 1 && table.rows.last()[0] - table.rows.first()[0] < 50.0
            val t = DoubleArray(table.rows.size) { if (oneShot) it.toDouble() else table.rows[it][0] / 1000.0 }
            // Aynı birimdeki sütunlar aynı grafikte; en fazla 3 grafik.
            val byUnit = meta.columns.withIndex().groupBy { it.value.unit }.entries.take(3)
            for ((unit, cols) in byUnit) {
                w.heading(cols.joinToString(" · ") { ToolUi.columnText(it.value.name) { s(it) } })
                val series = cols.take(4).mapIndexed { k, iv ->
                    val idx = table.columns.indexOf(iv.value.name)
                    val ys = if (idx < 0) DoubleArray(0) else DoubleArray(table.rows.size) { table.rows[it][idx] }
                    Triple(t, ys, SERIES_COLORS[k % SERIES_COLORS.size])
                }
                w.chart(series, unit, s(R.string.pdf_time_s))
                if (cols.size > 1) {
                    w.legend(cols.take(4).mapIndexed { k, iv -> ToolUi.columnText(iv.value.name) { s(it) } to SERIES_COLORS[k % SERIES_COLORS.size] })
                }
            }
        }

        if (meta.note.isNotBlank() || meta.tags.isNotEmpty()) {
            w.heading(s(R.string.pdf_note))
            if (meta.note.isNotBlank()) w.text(meta.note, 10f)
            if (meta.tags.isNotEmpty()) w.text(s(R.string.pdf_tags, meta.tags.joinToString(", ")), 9f, PdfWriter.MUTED)
        }

        records.photoFile(meta)?.let { f ->
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(f.absolutePath, opts)?.let { bmp ->
                w.heading(s(R.string.pdf_photo))
                w.image(bmp)
                bmp.recycle()
            }
        }

        w.heading(s(R.string.pdf_conditions))
        val rows = mutableListOf(
            s(R.string.pdf_device) to "${meta.deviceModel} · Android ${meta.androidVersion}",
            s(R.string.pdf_sensor) to meta.sensorName.ifBlank { "—" },
            s(R.string.pdf_calibration) to (if (meta.calibrated) s(R.string.pdf_calibrated) else s(R.string.pdf_not_calibrated)) +
                (meta.calibrationNote.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
            s(R.string.pdf_app) to "NexTools ${meta.appVersion}",
        )
        meta.location?.let { l ->
            rows += s(R.string.pdf_location) to "%.5f, %.5f".format(java.util.Locale.ROOT, l.lat, l.lon) +
                (l.accuracyM?.let { " (±${it.toInt()} m)" } ?: "")
        }
        w.table(rows)
        w.heading(s(R.string.pdf_limits))
        w.text(s(ToolUi.text(meta.tool).limits), 9.5f)
        w.space(6f)
        w.text(s(R.string.pdf_disclaimer), 8.5f, PdfWriter.MUTED)

        val out = File(exportDir, baseName(meta) + ".pdf")
        w.save(out)
        out
    }

    /** Cihaz Karnesi PDF'i (ikinci el telefon alırken de işe yarar). */
    suspend fun deviceReport(probe: HardwareProbe, noise: Map<Hardware, Double>): File = withContext(Dispatchers.IO) {
        cleanup()
        val w = PdfWriter(s(R.string.pdf_footer, BuildConfig.VERSION_NAME))
        w.newPage()
        w.text(s(R.string.pdf_device_kicker), 9f, PdfWriter.ACCENT, bold = true)
        w.title("${Build.MANUFACTURER} ${Build.MODEL}".trim())
        w.text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Fmt.dateTime(System.currentTimeMillis())}", 10f, PdfWriter.MUTED)
        val sum = probe.summary
        w.space(6f)
        w.text(s(R.string.report_summary_line, sum.hardwareOk, sum.total), 11f, bold = true)

        w.heading(s(R.string.pdf_hardware))
        val rows = probe.reportOrder.map { h ->
            val presence = probe.capabilities.presenceOf(h)
            val state = when (presence) {
                Presence.PRESENT -> s(R.string.report_present)
                Presence.ABSENT -> s(R.string.report_absent)
                Presence.UNKNOWN -> s(R.string.report_unknown)
            }
            val sensor = probe.sensorFor(h)?.let { probe.detail(it) }
            val detail = sensor?.let { d ->
                buildList {
                    add("${d.vendor} ${d.name}".trim())
                    d.maxRateHz?.let { add(s(R.string.report_rate, Fmt.num(it, 0))) }
                    add(s(R.string.report_resolution, Fmt.num(d.resolution.toDouble(), 4)))
                    noise[h]?.let { add(s(R.string.report_noise, Fmt.num(it, 4))) }
                }.joinToString(" · ")
            } ?: ""
            listOf((if (presence == Presence.PRESENT) "● " else "○ ") + s(ToolUi.hardware(h)), state, detail)
        }
        w.grid(listOf(s(R.string.pdf_col_hardware), s(R.string.pdf_col_state), s(R.string.pdf_col_detail)), rows, listOf(1.3f, 0.8f, 3f))

        w.heading(s(R.string.pdf_all_sensors))
        val sensors = probe.allSensors().map { sn ->
            val d = probe.detail(sn)
            listOf(d.name, d.vendor, d.maxRateHz?.let { Fmt.num(it, 0) + " Hz" } ?: "—", Fmt.num(d.resolution.toDouble(), 5), Fmt.num(d.maxRange.toDouble(), 2))
        }
        w.grid(
            listOf(s(R.string.pdf_col_name), s(R.string.pdf_col_vendor), s(R.string.pdf_col_rate), s(R.string.pdf_col_resolution), s(R.string.pdf_col_range)),
            sensors, listOf(2.4f, 1.4f, 0.8f, 0.9f, 0.9f),
        )
        w.space(8f)
        w.text(s(R.string.pdf_device_note), 8.5f, PdfWriter.MUTED)
        val out = File(exportDir, "NexTools_cihaz-karnesi_${Fmt.fileStamp(System.currentTimeMillis())}.pdf")
        w.save(out)
        out
    }

    fun shareIntent(file: File, mime: String, subject: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, s(R.string.share_via)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private companion object {
        val SERIES_COLORS = intArrayOf(Color.rgb(8, 145, 178), Color.rgb(234, 88, 12), Color.rgb(22, 163, 74), Color.rgb(124, 58, 237))
    }
}
