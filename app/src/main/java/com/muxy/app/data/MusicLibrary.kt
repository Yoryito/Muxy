package com.muxy.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "Muxy"

/**
 * La librería local: la base de datos manda, pero los archivos pueden
 * desaparecer por debajo (el usuario borra la caché, restaura el móvil...),
 * así que al arrancar se reconcilian ambas.
 */
class MusicLibrary(
    private val context: Context,
    private val dao: SongDao,
) {

    /** Carpeta donde viven los audios. Es privada de la app: no requiere permisos. */
    val musicDir: File
        get() = File(context.getExternalFilesDir(null), "music").apply { mkdirs() }

    val coverDir: File
        get() = File(context.getExternalFilesDir(null), "covers").apply { mkdirs() }

    fun observeSongs(): Flow<List<Song>> = dao.observeAll()

    suspend fun songById(id: Long): Song? = dao.byId(id)

    suspend fun isAlreadyDownloaded(sourceId: String): Boolean =
        withContext(Dispatchers.IO) { dao.bySourceId(sourceId) != null }

    suspend fun add(song: Song): Long = withContext(Dispatchers.IO) { dao.insert(song) }

    suspend fun remove(song: Song) = withContext(Dispatchers.IO) {
        File(song.filePath).delete()
        song.coverArtPath?.let { File(it).delete() }
        dao.delete(song)
    }

    /**
     * Sincroniza la base con lo que hay realmente en disco:
     * da de baja las canciones cuyo archivo ya no existe y da de alta los
     * audios que aparezcan en la carpeta sin estar registrados.
     *
     * Lo segundo es lo que permite probar el reproductor copiando un archivo
     * a mano con `adb push`, antes de que exista la descarga.
     */
    suspend fun sync() = withContext(Dispatchers.IO) {
        val onDisk = musicDir.listFiles { f -> f.isFile && f.extension.lowercase() in AUDIO_EXTENSIONS }
            ?.toList()
            .orEmpty()

        for (file in onDisk) {
            if (dao.byFilePath(file.absolutePath) != null) continue
            runCatching { readMetadata(file) }
                .onSuccess { dao.insert(it) }
                .onFailure { Log.w(TAG, "No se pudo leer ${file.name}", it) }
        }
    }

    /** Da de baja las canciones cuyo archivo ya no está en disco. */
    suspend fun pruneMissing(songs: List<Song>) = withContext(Dispatchers.IO) {
        songs.filterNot { File(it.filePath).exists() }.forEach { dao.delete(it) }
    }

    private fun readMetadata(file: File): Song {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            Song(
                title = retriever.extract(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: file.nameWithoutExtension,
                artist = retriever.extract(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extract(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                filePath = file.absolutePath,
                durationMs = retriever.extract(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                addedAt = file.lastModified(),
                source = SongSource.Imported,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.extract(key: Int): String? =
        extractMetadata(key)?.takeIf { it.isNotBlank() }

    private companion object {
        val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "ogg", "opus", "flac", "wav")
    }
}
