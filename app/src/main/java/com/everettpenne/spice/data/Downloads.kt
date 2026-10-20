package com.everettpenne.spice.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.everettpenne.spice.data.model.JellyfinJson
import com.everettpenne.spice.data.model.JfItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

enum class DownloadStatus { ACTIVE, PAUSED, DONE, FAILED }

/** Where one download is: how far, and how it ended if it did. */
data class DownloadInfo(val status: DownloadStatus, val bytes: Long = 0, val total: Long = 0, val problem: String? = null) {
    val fraction: Float? get() = if (status == DownloadStatus.ACTIVE && total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
}

/**
 * A movie, episode or song saved to the phone, with its own copy of the item (so it can be shown and played with no server) and of its poster.
 * [posterFile] is null until the poster has been fetched, or when the item has none.
 */
@Serializable data class SavedItem(val item: JfItem, val downloadId: Long, val fileName: String, val posterFile: String? = null, val requestedAt: Long = 0)

/**
 * Media saved for watching and listening without the server: on a plane, or away from home when the server is not reachable from outside.
 *
 * The file is the original, as stored on the server (Jellyfin's Items/{id}/Download), so it plays exactly as it would direct-played. The transfer is
 * Android's DownloadManager's: it carries on with the app closed, resumes after a lost connection, and can wait for Wi-Fi. It needs the session's
 * Authorization header, which it is given per request; the system keeps that with the download's entry until the download is deleted here (removing
 * the entry any earlier would delete the file with it). Files live in the app's own storage and go when the app is uninstalled or the download is
 * deleted.
 */
class Downloads(
    private val context: Context,
    private val dir: File,
    private val http: OkHttpClient,
    private val auth: () -> String?,
    private val posterUrl: (JfItem) -> String?,
    private val wifiOnly: () -> Boolean,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val filesDir = File(dir, "files").apply { mkdirs() }
    private val recordsFile = File(dir, "downloads.json")
    private val _saved = MutableStateFlow(readRecords())
    private val _state = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    private var poller: Job? = null

    /** Everything asked for, newest first. */
    val saved: StateFlow<List<SavedItem>> = _saved.asStateFlow()
    val state: StateFlow<Map<String, DownloadInfo>> = _state.asStateFlow()

    init {
        val done = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { scope.launch { refresh() } } }
        // Exported: the completion broadcast comes from the system's download provider, another app.
        androidx.core.content.ContextCompat.registerReceiver(context, done, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        scope.launch { refresh() }
    }

    // ---- asking ------------------------------------------------------------------------------------------------------------

    /** Starts saving [item] (a movie, episode, video or song), unless it is saved already or on its way. Returns false when it cannot be asked for. */
    fun download(item: JfItem, serverDownloadUrl: String): Boolean {
        val existing = _saved.value.firstOrNull { it.item.id == item.id }
        if (existing != null && _state.value[item.id]?.status != DownloadStatus.FAILED) return true
        existing?.let { remove(it.item.id) }                                            // a failed one is replaced
        val token = auth() ?: return false
        val name = fileNameFor(item)
        val wifi = wifiOnly()
        val request = DownloadManager.Request(Uri.parse(serverDownloadUrl))
            .setTitle(title(item)).setDescription("Spice")
            .addRequestHeader("Authorization", token)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(File(filesDir, name)))
            .setAllowedOverMetered(!wifi).setAllowedOverRoaming(!wifi)
        val id = runCatching { manager.enqueue(request) }.getOrElse { return false }
        // the saved copy keeps what the screens draw and the player needs, not the cast list
        val record = SavedItem(item.copy(people = emptyList()), id, name, requestedAt = clock())
        _saved.update { listOf(record) + it }
        saveRecords()
        scope.launch { fetchPoster(record); refresh() }
        return true
    }

    /** Cancels a download in progress, or deletes a finished one (its file and poster go too). */
    fun remove(itemId: String) {
        val record = _saved.value.firstOrNull { it.item.id == itemId } ?: return
        runCatching { manager.remove(record.downloadId) }
        File(filesDir, record.fileName).delete()
        record.posterFile?.let { File(filesDir, it).delete() }
        _saved.update { list -> list.filterNot { it.item.id == itemId } }
        _state.update { it - itemId }
        saveRecords()
    }

    fun removeAll() { _saved.value.map { it.item.id }.forEach(::remove) }

    /** The saved copy of an item, finished or not: what the player shows when the server cannot be reached. */
    fun savedItem(itemId: String): SavedItem? = _saved.value.firstOrNull { it.item.id == itemId }

    /** The finished file for the player, or null while it is still downloading, or if it never finished. */
    fun fileFor(itemId: String): File? {
        val record = savedItem(itemId) ?: return null
        if (_state.value[itemId]?.status != DownloadStatus.DONE) return null
        return File(filesDir, record.fileName).takeIf { it.isFile && it.length() > 0 }
    }

    /** The saved poster, for showing a download with no server. */
    fun posterFor(itemId: String): File? = savedItem(itemId)?.posterFile?.let { File(filesDir, it) }?.takeIf { it.isFile }

    /** Bytes taken by finished downloads. */
    fun totalBytes(state: Map<String, DownloadInfo>): Long = state.values.filter { it.status == DownloadStatus.DONE }.sumOf { it.bytes }

    // ---- learning how they are doing ---------------------------------------------------------------------------------------

    /** Asks the DownloadManager about every download we started, and keeps asking, quickly, while any is still going. */
    suspend fun refresh() {
        val records = _saved.value
        val next = HashMap<String, DownloadInfo>()
        if (records.isNotEmpty()) withContext(Dispatchers.IO) {
            val byId = records.associateBy { it.downloadId }
            runCatching {
                manager.query(DownloadManager.Query().setFilterById(*records.map { it.downloadId }.toLongArray()))?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID); val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    val soFar = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR); val total = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val reason = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    while (c.moveToNext()) {
                        val record = byId[c.getLong(idCol)] ?: continue
                        val file = File(filesDir, record.fileName)
                        next[record.item.id] = when (c.getInt(statusCol)) {
                            DownloadManager.STATUS_SUCCESSFUL -> if (file.isFile && file.length() > 0) DownloadInfo(DownloadStatus.DONE, file.length(), file.length()) else DownloadInfo(DownloadStatus.FAILED, problem = "The file is missing.")
                            DownloadManager.STATUS_FAILED -> DownloadInfo(DownloadStatus.FAILED, problem = failureText(c.getInt(reason)))
                            DownloadManager.STATUS_PAUSED -> DownloadInfo(DownloadStatus.PAUSED, c.getLong(soFar), c.getLong(total).coerceAtLeast(0), if (wifiOnly()) "Waiting for Wi-Fi" else "Waiting for a connection")
                            else -> DownloadInfo(DownloadStatus.ACTIVE, c.getLong(soFar), c.getLong(total).coerceAtLeast(0))
                        }
                    }
                }
            }
            // a record the DownloadManager no longer knows (finished and cleaned up, or cleared): finished if the file is there, otherwise gone
            for (r in records) if (r.item.id !in next) {
                val f = File(filesDir, r.fileName)
                next[r.item.id] = if (f.isFile && f.length() > 0) DownloadInfo(DownloadStatus.DONE, f.length(), f.length()) else DownloadInfo(DownloadStatus.FAILED, problem = "No longer available.")
            }
        }
        _state.value = next
        records.filter { it.posterFile == null && next[it.item.id]?.status == DownloadStatus.DONE }.forEach { scope.launch { fetchPoster(it) } }
        if (next.values.any { it.status == DownloadStatus.ACTIVE || it.status == DownloadStatus.PAUSED }) keepPolling()
    }

    private fun keepPolling() {
        if (poller?.isActive == true) return
        poller = scope.launch { while (_state.value.values.any { it.status == DownloadStatus.ACTIVE || it.status == DownloadStatus.PAUSED }) { delay(800); refresh() } }
    }

    private suspend fun fetchPoster(record: SavedItem) {
        val url = posterUrl(record.item) ?: return
        val name = "${record.item.id}.poster.jpg"
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@use false
                    File(filesDir, name).outputStream().use { out -> r.body!!.byteStream().copyTo(out) }; true
                }
            }.getOrDefault(false)
        }
        if (ok) { _saved.update { list -> list.map { if (it.item.id == record.item.id) it.copy(posterFile = name) else it } }; saveRecords() }
    }

    // ---- files -------------------------------------------------------------------------------------------------------------

    private fun readRecords(): List<SavedItem> = runCatching { JellyfinJson.decodeFromString(ListSerializer(SavedItem.serializer()), recordsFile.readText()) }.getOrDefault(emptyList())
    private fun saveRecords() {
        val snapshot = _saved.value
        scope.launch(Dispatchers.IO) {
            synchronized(this@Downloads) {
                val tmp = File(dir, "downloads.json.tmp"); tmp.writeText(JellyfinJson.encodeToString(ListSerializer(SavedItem.serializer()), snapshot))
                if (!tmp.renameTo(recordsFile)) { recordsFile.delete(); tmp.renameTo(recordsFile) }
            }
        }
    }

    companion object {
        /** "Movie (2020)", "Show · S1E2 · Title", "Artist · Song": the line the system's download notification shows. */
        fun title(item: JfItem): String = when {
            item.isEpisode -> listOfNotNull(item.seriesName, item.episodeLabel, item.name).joinToString(" · ")
            item.isAudio -> listOfNotNull(item.musicArtist, item.name).joinToString(" · ")
            else -> item.name + (item.year?.let { " ($it)" } ?: "")
        }

        /** A file name for an item: its id (unique and safe) and the container its media source says it is in. */
        fun fileNameFor(item: JfItem): String {
            val container = item.mediaSources.firstOrNull()?.container?.split(',')?.firstOrNull()?.trim()?.lowercase()
                ?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
            return "${item.id}.${container ?: if (item.isAudio) "mp3" else "mkv"}"
        }

        private fun failureText(reason: Int): String = when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough space on the phone."
            DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_CANNOT_RESUME -> "The connection dropped. Try again."
            401, 403 -> "The server does not allow this account to download."
            404 -> "The server no longer has this file."
            else -> "Couldn't download ($reason)."
        }
    }
}
