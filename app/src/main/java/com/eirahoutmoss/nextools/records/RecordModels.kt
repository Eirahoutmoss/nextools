package com.eirahoutmoss.nextools.records

import com.eirahoutmoss.nextools.core.catalog.ToolId
import org.json.JSONArray
import org.json.JSONObject

/** Kayıttaki bir veri sütunu. [name] CSV başlığıdır (ör. "x_deg"); [unit] gösterim birimi. */
data class Column(val name: String, val unit: String, val decimals: Int = 2)

/**
 * Özet satırı. [kind] dilden bağımsız anahtardır (min, max, avg, leq, lmax, lmin, last, peak);
 * ekrandaki adı uygulamada dil dosyasından seçilir.
 */
data class SummaryItem(val kind: String, val column: String, val value: Double, val unit: String, val decimals: Int)

data class GeoPoint(val lat: Double, val lon: Double, val accuracyM: Double?)

/**
 * Laboratuvar Defteri kaydı = araç + başlangıç/bitiş + ham veri (CSV) + özet + not + isteğe bağlı
 * fotoğraf + konum (izinle). Dosyalar: files/records/<id>/meta.json, data.csv, photo.jpg
 */
data class RecordMeta(
    val id: String,
    val tool: ToolId,
    val title: String,
    val note: String,
    val tags: List<String>,
    val startedAtMs: Long,
    val durationMs: Long,
    val sampleCount: Long,
    val columns: List<Column>,
    val summary: List<SummaryItem>,
    /** Kayıt anındaki kalibrasyon durumu ve açıklaması (kayıt dilinde). */
    val calibrated: Boolean,
    val calibrationNote: String,
    val deviceModel: String,
    val androidVersion: String,
    val sensorName: String,
    val appVersion: String,
    val location: GeoPoint?,
    val photo: String?,
    /** Defter listesindeki küçük grafik için ilk sütunun seyreltilmiş değerleri. */
    val preview: List<Double>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        put("id", id)
        put("tool", tool.key)
        put("title", title)
        put("note", note)
        put("tags", JSONArray(tags))
        put("startedAt", startedAtMs)
        put("durationMs", durationMs)
        put("samples", sampleCount)
        put("columns", JSONArray(columns.map { JSONObject().put("name", it.name).put("unit", it.unit).put("decimals", it.decimals) }))
        put(
            "summary",
            JSONArray(
                summary.map {
                    JSONObject().put("kind", it.kind).put("column", it.column).put("value", it.value.finiteOrNull())
                        .put("unit", it.unit).put("decimals", it.decimals)
                },
            ),
        )
        put("calibrated", calibrated)
        put("calibrationNote", calibrationNote)
        put("device", deviceModel)
        put("android", androidVersion)
        put("sensor", sensorName)
        put("appVersion", appVersion)
        location?.let { put("location", JSONObject().put("lat", it.lat).put("lon", it.lon).put("accuracy", it.accuracyM)) }
        photo?.let { put("photo", it) }
        put("preview", JSONArray(preview.map { it.finiteOrNull() }))
    }

    companion object {
        private fun Double.finiteOrNull(): Any = if (isNaN() || isInfinite()) JSONObject.NULL else this

        fun fromJson(o: JSONObject): RecordMeta? {
            val tool = ToolId.fromKey(o.optString("tool")) ?: return null
            fun arr(name: String) = o.optJSONArray(name) ?: JSONArray()
            val cols = arr("columns").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { Column(it.getString("name"), it.optString("unit"), it.optInt("decimals", 2)) } }
            val sum = arr("summary").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    SummaryItem(it.getString("kind"), it.optString("column"), it.optDouble("value", Double.NaN), it.optString("unit"), it.optInt("decimals", 2))
                }
            }
            val loc = o.optJSONObject("location")?.let {
                GeoPoint(it.getDouble("lat"), it.getDouble("lon"), if (it.isNull("accuracy")) null else it.optDouble("accuracy"))
            }
            val prev = arr("preview").let { a -> (0 until a.length()).map { a.optDouble(it, Double.NaN) } }
            val tags = arr("tags").let { a -> (0 until a.length()).map { a.getString(it) } }
            return RecordMeta(
                id = o.getString("id"),
                tool = tool,
                title = o.optString("title"),
                note = o.optString("note"),
                tags = tags,
                startedAtMs = o.optLong("startedAt"),
                durationMs = o.optLong("durationMs"),
                sampleCount = o.optLong("samples"),
                columns = cols,
                summary = sum,
                calibrated = o.optBoolean("calibrated"),
                calibrationNote = o.optString("calibrationNote"),
                deviceModel = o.optString("device"),
                androidVersion = o.optString("android"),
                sensorName = o.optString("sensor"),
                appVersion = o.optString("appVersion"),
                location = loc,
                photo = o.optString("photo").takeIf { it.isNotEmpty() },
                preview = prev,
            )
        }
    }
}
