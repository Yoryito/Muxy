package com.muxy.app.download

import android.util.Log
import ealvatag.audio.AudioFileIO
import ealvatag.tag.FieldKey
import ealvatag.tag.TagOptionSingleton
import ealvatag.tag.images.ArtworkFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "Muxy"

/**
 * Escribe título, artista y carátula dentro del propio M4A.
 *
 * Room ya guarda esos datos, así que esto no es para Muxy: es para que el
 * archivo se vea bien si alguna vez sale de la app (se copia al ordenador, se
 * abre con otro reproductor). Por eso un fallo aquí no tumba la descarga.
 */
object TrackTagger {

    private val configured = AtomicBoolean(false)

    /** Devuelve false si no se pudo etiquetar; el archivo sigue siendo válido. */
    suspend fun write(
        file: File,
        title: String,
        artist: String?,
        album: String?,
        cover: File?,
    ): Boolean = withContext(Dispatchers.IO) {
        configure()
        if (!Mp4Headers.prepareForTagging(file)) {
            Log.w(TAG, "El MP4 de ${file.name} no es tratable, se queda sin etiquetas")
            return@withContext false
        }

        try {
            runCatching {
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tagOrSetNewDefault

                tag.setField(FieldKey.TITLE, title)
                artist?.let { tag.setField(FieldKey.ARTIST, it) }
                album?.let { tag.setField(FieldKey.ALBUM, it) }

                if (cover != null && cover.exists()) {
                    tag.setArtwork(ArtworkFactory.createArtworkFromFile(cover))
                }

                audioFile.save()
            }.onFailure {
                Log.w(TAG, "No se pudieron escribir las etiquetas de ${file.name}", it)
            }.isSuccess
        } finally {
            // Salga bien o mal, el archivo tiene que quedar como un MP4 normal.
            Mp4Headers.restore(file)
        }
    }

    /**
     * eAlvaTag viene del mundo del escritorio y por defecto decodifica imágenes
     * con `javax.imageio`, que en Android no existe. Este interruptor lo manda a
     * usar `BitmapFactory` en su lugar.
     */
    private fun configure() {
        if (configured.compareAndSet(false, true)) {
            TagOptionSingleton.getInstance().isAndroid = true
        }
    }
}
