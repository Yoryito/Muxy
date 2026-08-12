package com.muxy.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    /**
     * Las listas con su recuento y su portada, en una sola consulta.
     *
     * Se resuelve con subconsultas en vez de con un JOIN + GROUP BY porque la
     * portada es "la carátula de la primera canción que tenga una", y eso con
     * agregados sale mucho más retorcido.
     */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
               (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount,
               (SELECT s.coverArtPath FROM playlist_songs ps
                  JOIN songs s ON s.id = ps.songId
                 WHERE ps.playlistId = p.id AND s.coverArtPath IS NOT NULL
                 ORDER BY ps.position LIMIT 1) AS coverArtPath
          FROM playlists p
         ORDER BY p.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<Playlist?>

    /** Las canciones de una lista, en el orden en que las puso el usuario. */
    @Query(
        """
        SELECT s.* FROM songs s
          JOIN playlist_songs ps ON ps.songId = s.id
         WHERE ps.playlistId = :playlistId
         ORDER BY ps.position
        """,
    )
    fun observeSongs(playlistId: Long): Flow<List<Song>>

    /** En qué listas está ya una canción, para marcarlas al ofrecer añadirla. */
    @Query("SELECT playlistId FROM playlist_songs WHERE songId = :songId")
    fun observePlaylistsOf(songId: Long): Flow<List<Long>>

    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** Borra la lista; sus filas de [PlaylistSong] se van en cascada. */
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * El siguiente puesto libre. Empieza en 0 con la lista vacía, y se calcula
     * sobre el máximo y no sobre el recuento para que quitar una canción del
     * medio no haga que la siguiente choque con un puesto ya usado.
     */
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    /** Añadir algo que ya está en la lista no es un fallo: simplemente no hace nada. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(ref: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)
}
