package com.eirahoutmoss.nextools.records

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.eirahoutmoss.nextools.core.catalog.ToolId
import com.eirahoutmoss.nextools.core.measure.RunningStats
import com.eirahoutmoss.nextools.core.record.Csv
import com.eirahoutmoss.nextools.core.record.Downsample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.util.UUID

/**
 * Kayıt sırasında veriyi doğrudan diske yazar (uzun kayıtta bellek dolmaz).
 * Satır zamanı: kaydın başlangıcından geçen süre (ms), telefon saatinden bağımsız.
 */
class RecordingSession internal constructor(
    val id: String,
    val tool: ToolId,
    val columns: List<Column>,
    internal val dir: File,
    headerComments: List<String>,
) {
    val startedAtMs: Long = System.currentTimeMillis()
    private val startElapsed = SystemClock.elapsedRealtime()
    private val writer: BufferedWriter
    private val stats = columns.associate { it.name to RunningStats() }
    private val firstColumn = FloatArrayList()
    private val firstColumnT = FloatArrayList()
    private val lock = Any()
    @Volatile var sampleCount: Long = 0
        private set
    @Volatile private var closed = false

    init {
        dir.mkdirs()
        writer = File(dir, RecordStore.DATA).bufferedWriter()
        headerComments.forEach { writer.write(Csv.comment(it)); writer.newLine() }
        writer.write(Csv.header(listOf("t_ms") + columns.map { it.name }))
        writer.newLine()
    }

    val elapsedMs: Long get() = SystemClock.elapsedRealtime() - startElapsed

    /** [values] sütun sırasıyla. Kayıt bittikten sonra gelen değerler yok sayılır. */
    fun add(values: DoubleArray) {
        if (values.size != columns.size) return
        synchronized(lock) {
            if (closed) return
            val t = elapsedMs
            writer.write(Csv.row(t, values, decimals = 5))
            writer.newLine()
            columns.forEachIndexed { i, c -> stats.getValue(c.name).add(values[i]) }
            firstColumn.add(values[0].toFloat())
            firstColumnT.add(t.toFloat())
            sampleCount++
        }
    }

    internal fun close(): Triple<Map<String, RunningStats>, List<Double>, Long> = synchronized(lock) {
        closed = true
        runCatching { writer.flush(); writer.close() }
        val n = firstColumn.size
        val xs = DoubleArray(n) { firstColumnT[it].toDouble() }
        val ys = DoubleArray(n) { firstColumn[it].toDouble() }
        val (_, py) = Downsample.minMax(xs, ys, 60)
        Triple(stats, py.toList(), elapsedMs)
    }

    internal fun discard() {
        synchronized(lock) { closed = true; runCatching { writer.close() } }
        dir.deleteRecursively()
    }
}

/** Biten ama henüz adı/notu girilmemiş kayıt. */
class RecordDraft internal constructor(
    val session: RecordingSession,
    val stats: Map<String, RunningStats>,
    val preview: List<Double>,
    val durationMs: Long,
) {
    val sampleCount: Long get() = session.sampleCount
}

/** Laboratuvar Defteri deposu: her kayıt kendi klasöründe, JSON + CSV. */
class RecordStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "records").apply { mkdirs() }
    private val pendingRoot = File(root, ".pending")
    private val mutex = Mutex()

    private val _records = MutableStateFlow<List<RecordMeta>>(emptyList())
    val records: StateFlow<List<RecordMeta>> = _records.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun ensureLoaded() = mutex.withLock {
        if (_loaded.value) return@withLock
        val list = withContext(Dispatchers.IO) {
            // Yarıda kalan (uygulama kapanınca kaydedilmemiş) kayıtlar temizlenir.
            pendingRoot.deleteRecursively()
            root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }.orEmpty().mapNotNull { d ->
                runCatching { RecordMeta.fromJson(JSONObject(File(d, META).readText())) }.getOrNull()
            }.sortedByDescending { it.startedAtMs }
        }
        _records.value = list
        _loaded.value = true
    }

    fun newSession(tool: ToolId, columns: List<Column>, headerComments: List<String>): RecordingSession {
        val id = UUID.randomUUID().toString()
        return RecordingSession(id, tool, columns, File(pendingRoot, id), headerComments)
    }

    fun finish(session: RecordingSession): RecordDraft {
        val (stats, preview, duration) = session.close()
        return RecordDraft(session, stats, preview, duration)
    }

    fun discard(session: RecordingSession) = session.discard()

    fun discard(draft: RecordDraft) = draft.session.discard()

    /** Taslağı Defter'e kalıcı olarak alır. */
    suspend fun save(draft: RecordDraft, meta: RecordMeta): RecordMeta = mutex.withLock {
        withContext(Dispatchers.IO) {
            val target = File(root, meta.id)
            if (!draft.session.dir.renameTo(target)) {
                draft.session.dir.copyRecursively(target, overwrite = true)
                draft.session.dir.deleteRecursively()
            }
            writeMeta(meta)
        }
        _records.value = (listOf(meta) + _records.value.filter { it.id != meta.id }).sortedByDescending { it.startedAtMs }
        meta
    }

    suspend fun update(meta: RecordMeta) = mutex.withLock {
        withContext(Dispatchers.IO) { writeMeta(meta) }
        _records.value = _records.value.map { if (it.id == meta.id) meta else it }
    }

    suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) { File(root, id).deleteRecursively() }
        _records.value = _records.value.filter { it.id != id }
    }

    fun get(id: String): RecordMeta? = _records.value.firstOrNull { it.id == id }

    fun dir(id: String): File = File(root, id)
    fun dataFile(id: String): File = File(dir(id), DATA)
    fun photoFile(meta: RecordMeta): File? = meta.photo?.let { File(dir(meta.id), it) }?.takeIf { it.exists() }

    /** Fotoğrafı kayda kopyalar (galeriden ya da kameradan). */
    suspend fun attachPhoto(meta: RecordMeta, source: Uri): RecordMeta {
        val name = PHOTO
        withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(source)?.use { input ->
                File(dir(meta.id), name).outputStream().use { input.copyTo(it) }
            } ?: throw java.io.IOException("open failed")
        }
        val next = meta.copy(photo = name)
        update(next)
        return next
    }

    suspend fun removePhoto(meta: RecordMeta): RecordMeta {
        withContext(Dispatchers.IO) { meta.photo?.let { File(dir(meta.id), it).delete() } }
        val next = meta.copy(photo = null)
        update(next)
        return next
    }

    /** Grafik için CSV'yi okur. */
    suspend fun readTable(id: String, maxRows: Int = 200_000): Csv.Table = withContext(Dispatchers.IO) {
        val f = dataFile(id)
        if (!f.exists()) return@withContext Csv.Table(emptyList(), emptyList())
        f.bufferedReader().useLines { Csv.parse(it, maxRows) }
    }

    private fun writeMeta(meta: RecordMeta) {
        val d = dir(meta.id).apply { mkdirs() }
        val tmp = File(d, "$META.tmp")
        tmp.writeText(meta.toJson().toString(2))
        val target = File(d, META)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    companion object {
        const val META = "meta.json"
        const val DATA = "data.csv"
        const val PHOTO = "photo.jpg"
    }
}

/** Kutulama yapmadan büyüyen float listesi. */
internal class FloatArrayList {
    private var a = FloatArray(1024)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == a.size) a = a.copyOf(a.size * 2)
        a[size++] = v
    }

    operator fun get(i: Int): Float = a[i]
}
