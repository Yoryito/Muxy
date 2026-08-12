package com.muxy.app.download

import java.io.File
import java.io.RandomAccessFile

/**
 * Retoques al contenedor MP4 para que eAlvaTag sepa escribir en él.
 *
 * Los archivos que produce Media3 Transformer son MP4 perfectamente válidos y
 * cualquier reproductor los abre sin pestañear, pero traen dos detalles que a
 * eAlvaTag —una biblioteca pensada para archivos de iTunes— le sientan fatal.
 * Los dos se arreglan tocando **solo cabeceras**: ni un byte de audio se mueve.
 *
 * **1. La caja `mdat` con tamaño de 64 bits.** El campo de tamaño de 4 bytes
 * vale 1 y el tamaño real viene en los 8 siguientes. eAlvaTag guarda el tamaño
 * en un `int` y no contempla esa variante: se pierde recorriendo el archivo y
 * falla con "This file does not appear to be an Mp4 file". Como una cabecera de
 * 64 bits ocupa 16 bytes y una de 32 ocupa 8, los 8 que sobran se convierten en
 * una caja `free` vacía —que el estándar manda ignorar— y la `mdat` real empieza
 * 8 bytes más allá:
 *
 * ```
 * antes:   [ mdat, cabecera de 16 bytes ][ audio ]
 * después: [ free, 8 ][ mdat, 8 ]        [ audio ]
 * ```
 *
 * El audio sigue empezando en el mismo desplazamiento absoluto, que es lo que
 * importa: las tablas `stco` del `moov` apuntan ahí.
 *
 * **2. La caja `free` gigante que deja el muxer.** Media3 reserva ~400 KB para
 * el `moov` y lo que le sobra se queda como `free` entre el `moov` y el `mdat`.
 * Al verlo, eAlvaTag intenta meter los metadatos ahí sin mover nada ("Option 5")
 * y le sale mal la cuenta: escribe un archivo que ni él mismo puede releer, se
 * da cuenta y aborta con "Cannot make changes to file".
 *
 * El truco es esconderle esa caja renombrándola a `skip`, que el estándar define
 * como exactamente lo mismo (ISO 14496-12: `free` y `skip` son la misma caja de
 * relleno) pero eAlvaTag no reconoce. Sin hueco donde encajar, se va a su camino
 * normal —reescribir el archivo desplazando el `mdat` y corrigiendo los `stco`—,
 * que es el que usa con cualquier MP4 de iTunes y funciona. Después se deshace
 * el renombrado para dejar un archivo del todo convencional.
 */
object Mp4Headers {

    /** Devuelve false si el archivo no tiene una pinta que sepamos tratar. */
    fun prepareForTagging(file: File): Boolean = editTopLevelBoxes(file) { raf, pos, type, size ->
        when {
            size == EXTENDED_SIZE_MARKER -> {
                raf.seek(pos + HEADER_BYTES.toLong())
                val realSize = raf.readLong()
                if (type != MDAT || realSize !in LARGE_HEADER_BYTES..Int.MAX_VALUE.toLong()) {
                    return@editTopLevelBoxes null
                }
                raf.seek(pos)
                raf.writeInt(HEADER_BYTES)
                raf.writeAscii(SKIP)
                raf.writeInt((realSize - HEADER_BYTES).toInt())
                raf.writeAscii(MDAT)
                // La cabecera pasa a medir 8, así que la caja empieza 8 más allá.
                realSize
            }

            type == FREE -> {
                raf.seek(pos + 4)
                raf.writeAscii(SKIP)
                size
            }

            else -> size
        }
    }

    /** Deshace el renombrado de [prepareForTagging]. */
    fun restore(file: File) {
        editTopLevelBoxes(file) { raf, pos, type, size ->
            if (type == SKIP) {
                raf.seek(pos + 4)
                raf.writeAscii(FREE)
            }
            size
        }
    }

    /**
     * Recorre las cajas de primer nivel dejando que [edit] las modifique.
     * [edit] devuelve el tamaño con el que avanzar, o null para abandonar.
     */
    private fun editTopLevelBoxes(
        file: File,
        edit: (RandomAccessFile, Long, String, Long) -> Long?,
    ): Boolean = runCatching {
        RandomAccessFile(file, "rw").use { raf ->
            val length = raf.length()
            var pos = 0L

            while (pos + HEADER_BYTES <= length) {
                raf.seek(pos)
                val size = raf.readInt().toLong() and 0xFFFFFFFFL
                val type = ByteArray(4).also(raf::readFully).toString(Charsets.US_ASCII)

                // Un tamaño de cero significa "hasta el final": ya no hay más cajas.
                if (size == 0L) return@runCatching true

                val step = edit(raf, pos, type, size) ?: return@runCatching false
                if (step < HEADER_BYTES) return@runCatching false
                pos += step
            }
        }
        true
    }.getOrDefault(false)

    private fun RandomAccessFile.writeAscii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

    private const val HEADER_BYTES = 8
    private const val LARGE_HEADER_BYTES = 16L
    private const val EXTENDED_SIZE_MARKER = 1L
    private const val MDAT = "mdat"
    private const val FREE = "free"
    private const val SKIP = "skip"
}
