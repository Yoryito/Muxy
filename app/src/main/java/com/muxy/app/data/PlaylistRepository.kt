package com.muxy.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Las listas de reproducción. Envuelve al DAO para que las pantallas no tengan
 * que saber que "añadir una canción" son en realidad dos consultas.
 */
class PlaylistRepository(private val dao: PlaylistDao) {

    fun observeSummaries(): Flow<List<PlaylistSummary>> = dao.observeSummaries()

    fun observePlaylist(id: Long): Flow<Playlist?> = dao.observeById(id)

    fun observeSongs(playlistId: Long): Flow<List<Song>> = dao.observeSongs(playlistId)

    fun observePlaylistsOf(songId: Long): Flow<Set<Long>> =
        dao.observePlaylistsOf(songId).map { it.toSet() }

    /**
     * Crea una lista. El nombre se recorta, y si llega en blanco se le pone uno
     * por defecto: una lista sin nombre no se puede ni distinguir ni tocar.
     */
    suspend fun create(name: String, fallbackName: String): Long = withContext(Dispatchers.IO) {
        dao.insert(
            Playlist(
                name = name.trim().ifBlank { fallbackName },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Renombra, salvo que el nombre nuevo esté en blanco: eso se ignora. */
    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isNotEmpty()) dao.rename(id, clean)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }

    /** Añade al final. Si la canción ya estaba, no pasa nada. */
    suspend fun addSong(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        dao.insertSong(
            PlaylistSong(
                playlistId = playlistId,
                songId = songId,
                position = dao.nextPosition(playlistId),
            ),
        )
    }

    suspend fun removeSong(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        dao.removeSong(playlistId, songId)
    }

    /** Guarda el orden en que ha quedado la lista tras arrastrar una canción. */
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>) = withContext(Dispatchers.IO) {
        dao.reorder(playlistId, orderedSongIds)
    }
}
