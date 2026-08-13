package com.muxy.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "Muxy"

/** Ver [MusicLibrary.storageUsage]. */
data class StorageUsage(
    val muxyUsedBytes: Long,
    val deviceUsedBytes: Long,
    val deviceTotalBytes: Long,
)

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

    /**
     * Donde se cocinan las descargas antes de entrar en la librería.
     *
     * Está fuera de [musicDir] porque [sync] da de alta cualquier audio que
     * encuentre ahí, y un archivo a medio convertir no es una canción todavía.
     * Comparte sistema de archivos con [musicDir], así que el paso final es un
     * renombrado atómico y no una copia.
     */
    val stagingDir: File
        get() = File(context.getExternalFilesDir(null), "staging").apply { mkdirs() }

    fun observeSongs(): Flow<List<Song>> = dao.observeAll()

    /**
     * Cuánto ocupa Muxy y cuánto queda libre en el volumen donde vive
     * [musicDir]. [deviceUsedBytes]/[deviceTotalBytes] son del dispositivo
     * entero (lo que reserva Android para todas las apps), no solo de Muxy:
     * es lo que responde a "cuánto me queda" mejor que solo el peso de la
     * música descargada.
     */
    suspend fun storageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        val muxyBytes = (dirFileSizes(musicDir) + dirFileSizes(coverDir))
        val totalBytes = musicDir.totalSpace
        val freeBytes = musicDir.usableSpace
        StorageUsage(
            muxyUsedBytes = muxyBytes,
            deviceUsedBytes = (totalBytes - freeBytes).coerceAtLeast(0),
            deviceTotalBytes = totalBytes,
        )
    }

    /** Las últimas [limit] canciones escuchadas, para la sección de recientes del inicio. */
    fun observeRecentlyPlayed(limit: Int = 15): Flow<List<Song>> = dao.observeRecentlyPlayed(limit)

    /** Las [limit] más escuchadas, para el "Mi Top" del inicio. */
    fun observeMostPlayed(limit: Int = 15): Flow<List<Song>> = dao.observeMostPlayed(limit)

    /** Se llama cada vez que una canción arranca a sonar, la haya elegido el usuario o le toque por la cola. */
    suspend fun markPlayed(songId: Long, timestamp: Long) =
        withContext(Dispatchers.IO) { dao.markPlayed(songId, timestamp) }

    suspend fun songById(id: Long): Song? = dao.byId(id)

    suspend fun isAlreadyDownloaded(sourceId: String): Boolean =
        withContext(Dispatchers.IO) { dao.bySourceId(sourceId) != null }

    /** Qué vídeos ya están en la librería, para no ofrecerlos otra vez al buscar. */
    fun observeDownloadedIds(): Flow<Set<String>> = dao.observeSourceIds().map { it.toSet() }

    /**
     * Da de alta una canción, reemplazando la fila que ya hubiera para ese
     * archivo. Lo segundo importa porque [sync] puede haber registrado el mismo
     * archivo por su cuenta, y sin esto quedarían dos filas idénticas.
     */
    suspend fun add(song: Song): Long = withContext(Dispatchers.IO) {
        val existing = dao.byFilePath(song.filePath)
        dao.insert(if (existing == null) song else song.copy(id = existing.id))
    }

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
        pruneMissing(dao.all())

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

    private fun dirFileSizes(dir: File): Long =
        dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

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
