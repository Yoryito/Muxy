package com.muxy.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una canción de la librería local.
 *
 * [filePath] apunta a un archivo dentro del almacenamiento propio de la app.
 * El origen queda registrado en [sourceId] para poder detectar duplicados antes
 * de volver a descargar algo que ya está.
 */
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String?,
    val album: String? = null,
    val filePath: String,
    val coverArtPath: String? = null,
    val durationMs: Long = 0,
    val addedAt: Long,
    /** Identificador en el origen (por ahora, el id del vídeo de YouTube). */
    val sourceId: String? = null,
    val source: SongSource = SongSource.Imported,
)

enum class SongSource {
    /** Descargada por la app. */
    YouTube,

    /** Copiada al dispositivo por otros medios. */
    Imported,
}
