package com.muxy.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Song>>

    /** Instantánea sin observar, para comprobaciones puntuales como [MusicLibrary.sync]. */
    @Query("SELECT * FROM songs")
    suspend fun all(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun byId(id: Long): Song?

    @Query("SELECT * FROM songs WHERE sourceId = :sourceId LIMIT 1")
    suspend fun bySourceId(sourceId: String): Song?

    /** Solo los ids de origen: la pantalla de búsqueda no necesita más. */
    @Query("SELECT sourceId FROM songs WHERE sourceId IS NOT NULL")
    fun observeSourceIds(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE filePath = :filePath LIMIT 1")
    suspend fun byFilePath(filePath: String): Song?

    /** Para la sección de "recientes" del inicio: lo último escuchado, no lo último descargado. */
    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<Song>>

    @Query("UPDATE songs SET lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun markPlayed(id: Long, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song): Long

    @Delete
    suspend fun delete(song: Song)
}
