package com.muxy.app.data

import androidx.room.ColumnInfo
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
    /**
     * Cuánto atenuar al reproducir para que no suene más alto que las demás.
     * Siempre `<= 0`: solo baja el volumen de lo que suena fuerte, nunca sube
     * lo flojo — [Player.volume][androidx.media3.common.Player] no amplifica
     * más allá de 1.0. `0` es "sin medir o sin ajuste", que es también lo que
     * traen las canciones de antes de que existiera este campo.
     */
    @ColumnInfo(defaultValue = "0.0")
    val gainDb: Float = 0f,
)

enum class SongSource {
    /** Descargada por la app. */
    YouTube,

    /** Copiada al dispositivo por otros medios. */
    Imported,
}
