package com.muxy.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class BackupFile(
    val version: Int = 1,
    val playlists: List<BackupPlaylist>,
)

@Serializable
private data class BackupPlaylist(
    val name: String,
    val songs: List<BackupSong>,
)

/**
 * Una canción tal como viaja en la copia de seguridad: por [sourceId] de
 * YouTube cuando lo hay, que es un identificador estable entre instalaciones —
 * el `id` de Room no lo es, cambia con cada base de datos nueva.
 */
@Serializable
private data class BackupSong(
    val sourceId: String? = null,
    val title: String,
    val artist: String? = null,
)

data class BackupImportResult(
    val playlistsCreated: Int,
    val songsAdded: Int,
    val songsSkipped: Int,
)

/**
 * Copia de seguridad de las playlists en JSON, pensada para guardarse fuera de
 * la app (Drive, el propio Downloads…) porque todo lo demás vive solo en Room:
 * si se pierde el móvil o se reinstala, las playlists se van con él aunque los
 * archivos de música sigan a salvo en el APK viejo.
 *
 * Las canciones no se referencian por su `id` de Room — no sobrevive a una base
 * de datos nueva — sino por el [BackupSong.sourceId] de YouTube, con título y
 * artista de apoyo para lo importado a mano que no tiene. Al importar, lo que
 * no encuentra pareja en la librería actual se cuenta y se salta: no tiene
 * sentido crear una fila de playlist que apunte a una canción que no está.
 */
class PlaylistBackup(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
) {

    suspend fun export(): String = withContext(Dispatchers.IO) {
        val summaries = playlistDao.observeSummaries().first()
        val playlists = summaries.map { summary ->
            val songs = playlistDao.observeSongs(summary.id).first()
            BackupPlaylist(
                name = summary.name,
                songs = songs.map { BackupSong(sourceId = it.sourceId, title = it.title, artist = it.artist) },
            )
        }
        json.encodeToString(BackupFile(playlists = playlists))
    }

    suspend fun import(content: String): BackupImportResult = withContext(Dispatchers.IO) {
        val backup = json.decodeFromString<BackupFile>(content)
        val library = songDao.all()

        var songsAdded = 0
        var songsSkipped = 0

        for (backupPlaylist in backup.playlists) {
            val playlistId = playlistDao.insert(
                Playlist(name = backupPlaylist.name, createdAt = System.currentTimeMillis()),
            )
            for (backupSong in backupPlaylist.songs) {
                val match = library.find(backupSong)
                if (match == null) {
                    songsSkipped++
                    continue
                }
                playlistDao.insertSong(
                    PlaylistSong(
                        playlistId = playlistId,
                        songId = match.id,
                        position = playlistDao.nextPosition(playlistId),
                    ),
                )
                songsAdded++
            }
        }

        BackupImportResult(
            playlistsCreated = backup.playlists.size,
            songsAdded = songsAdded,
            songsSkipped = songsSkipped,
        )
    }

    /** Por [BackupSong.sourceId] primero; sin eso (o sin coincidencia), por título y artista. */
    private fun List<Song>.find(backupSong: BackupSong): Song? {
        backupSong.sourceId?.let { id -> firstOrNull { it.sourceId == id }?.let { return it } }
        return firstOrNull {
            it.title.equals(backupSong.title, ignoreCase = true) &&
                it.artist.orEmpty().equals(backupSong.artist.orEmpty(), ignoreCase = true)
        }
    }

    private companion object {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }
}
