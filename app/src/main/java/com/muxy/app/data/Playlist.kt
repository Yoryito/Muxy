package com.muxy.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Una lista de reproducción hecha a mano por el usuario.
 *
 * Es solo el nombre: las canciones que contiene viven en [PlaylistSong], porque
 * una canción puede estar en varias listas y en cada una en otro orden.
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * Qué canción está en qué lista y en qué puesto.
 *
 * Las dos claves ajenas borran en cascada a propósito: al borrar una canción de
 * la librería tiene que desaparecer de todas las listas, y al borrar una lista
 * no deben quedar filas huérfanas apuntando a ella. Sin la cascada haría falta
 * acordarse de limpiar a mano en cada sitio, y antes o después se olvida.
 *
 * La clave primaria compuesta impide que la misma canción entre dos veces en la
 * misma lista; el alta usa `IGNORE` para que reintentarlo no sea un error.
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // La clave primaria ya indexa por playlistId; songId necesita el suyo para
    // que borrar una canción no tenga que recorrer la tabla entera.
    indices = [Index("songId")],
)
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

/**
 * Lo que hace falta para pintar una lista sin cargar sus canciones: cuántas
 * tiene y la carátula de la primera, que es la que se usa de portada.
 */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val songCount: Int,
    val coverArtPath: String?,
)
