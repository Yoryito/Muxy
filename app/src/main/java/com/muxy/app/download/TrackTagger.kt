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
 * abre con otro reproductor).
 *
 * Como es un extra y el audio no lo es, el etiquetado trabaja **sobre una copia
 * de seguridad**: si algo sale mal —o si el resultado no pasa la revisión de
 * [Mp4Headers.chunkOffsetsPointIntoAudio]— se recupera el archivo de antes y la
 * canción se queda sin etiquetas. Esto no es paranoia: eAlvaTag ya rompió una vez
 * el audio de forma silenciosa, dejando un archivo que parecía correcto y no
 * sonaba. Perder las etiquetas se nota y se arregla; perder el audio sin avisar,
 * no.
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

        val audioStartBefore = Mp4Headers.audioStart(file)
        val backup = File(file.parentFile, "${file.name}.pretag")

        try {
            runCatching {
                file.copyTo(backup, overwrite = true)

                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tagOrSetNewDefault

                tag.setField(FieldKey.TITLE, title)
                artist?.let { tag.setField(FieldKey.ARTIST, it) }
                album?.let { tag.setField(FieldKey.ALBUM, it) }

                if (cover != null && cover.exists()) {
                    tag.setArtwork(ArtworkFactory.createArtworkFromFile(cover))
                }

                audioFile.save()

                // eAlvaTag desplaza el audio para hacer sitio a los metadatos
                // pero no toca las tablas `co64`, así que hay que recolocarlas.
                Mp4Headers.repairChunkOffsets(file, audioStartBefore)

                check(Mp4Headers.chunkOffsetsPointIntoAudio(file)) {
                    "las tablas de offsets no apuntan al audio"
                }
            }.onFailure { error ->
                Log.w(TAG, "Etiquetado de ${file.name} descartado, se recupera el audio", error)
                if (backup.exists()) {
                    backup.copyTo(file, overwrite = true)
                }
            }.isSuccess
        } finally {
            backup.delete()
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
