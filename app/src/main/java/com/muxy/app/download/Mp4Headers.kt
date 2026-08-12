package com.muxy.app.download

import java.io.File
import java.io.RandomAccessFile

/**
 * Retoques al contenedor MP4 alrededor del etiquetado con eAlvaTag.
 *
 * Los archivos que produce Media3 Transformer son MP4 válidos y cualquier
 * reproductor los abre, pero eAlvaTag —pensada para archivos de iTunes— se
 * atraganta con tres cosas distintas. Las tres se arreglan tocando **solo
 * cabeceras y tablas de offsets**: ni un byte de audio se mueve.
 *
 * **1. La caja `mdat` con tamaño de 64 bits.** El campo de tamaño de 4 bytes
 * vale 1 y el tamaño real viene en los 8 siguientes. eAlvaTag guarda el tamaño
 * en un `int` y no contempla esa variante: se pierde recorriendo el archivo y
 * falla con "This file does not appear to be an Mp4 file". Como la cabecera de
 * 64 bits ocupa 16 bytes y la de 32 ocupa 8, los 8 que sobran se convierten en
 * una caja de relleno vacía y el audio sigue empezando en el mismo sitio:
 *
 * ```
 * antes:   [ mdat, cabecera de 16 bytes ][ audio ]
 * después: [ skip, 8 ][ mdat, 8 ]        [ audio ]
 * ```
 *
 * **2. La caja `free` gigante que deja el muxer.** Media3 reserva ~400 KB para
 * el `moov` y lo que sobra queda como `free` entre `moov` y `mdat`. Al verlo,
 * eAlvaTag intenta encajar los metadatos ahí sin mover nada y le sale mal la
 * cuenta: escribe un archivo que ni él mismo puede releer y aborta con "Cannot
 * make changes to file". Se le esconde renombrando la caja a `skip`, que el
 * estándar define como equivalente (ISO 14496-12: `free` y `skip` son la misma
 * caja de relleno) pero eAlvaTag no reconoce. Sin hueco donde encajar se va a su
 * camino normal: reescribir el archivo desplazando el `mdat`.
 *
 * **3. Y ahí está la trampa: al desplazar el `mdat` no corrige los offsets.**
 * Las tablas que dicen dónde empieza cada trozo de audio existen en dos
 * variantes, `stco` (32 bits) y `co64` (64 bits). eAlvaTag solo conoce `stco`, y
 * Media3 escribe `co64`. Resultado: mueve el audio 50 KB y deja los offsets
 * apuntando a donde estaba, así que el reproductor lee relleno en vez de audio —
 * el reloj avanza con normalidad y **no suena nada**, que es el peor fallo
 * posible porque no se parece a un fallo. [repairChunkOffsets] los recoloca
 * sumándoles el desplazamiento real del `mdat`.
 *
 * Nada de esto se arregla cambiando de muxer: con el del sistema es peor, porque
 * además escribe un `moov/udta` que eAlvaTag rechaza al leer.
 */
object Mp4Headers {

    /** Devuelve false si el archivo no tiene una pinta que sepamos tratar. */
    fun prepareForTagging(file: File): Boolean = editTopLevelBoxes(file) { raf, pos, type, size, headerBytes ->
        when {
            // El recorrido ya ha resuelto el tamaño real; lo que delata a una
            // caja de 64 bits es que su cabecera mide 16 en vez de 8.
            headerBytes == LARGE_HEADER_BYTES -> {
                if (type != MDAT || size !in LARGE_HEADER_BYTES..Int.MAX_VALUE.toLong()) {
                    return@editTopLevelBoxes null
                }
                raf.seek(pos)
                raf.writeInt(HEADER_BYTES)
                raf.writeAscii(SKIP)
                raf.writeInt((size - HEADER_BYTES).toInt())
                raf.writeAscii(MDAT)
                size
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
        editTopLevelBoxes(file) { raf, pos, type, size, _ ->
            if (type == SKIP) {
                raf.seek(pos + 4)
                raf.writeAscii(FREE)
            }
            size
        }
    }

    /** Desplazamiento donde empieza el audio, o -1 si no se encuentra el `mdat`. */
    fun audioStart(file: File): Long = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            var found = -1L
            editTopLevelBoxes(raf) { pos, type, size, headerBytes ->
                if (type == MDAT) found = pos + headerBytes
                size
            }
            found
        }
    }.getOrDefault(-1L)

    /**
     * Suma a todas las tablas de offsets el desplazamiento que ha sufrido el
     * audio, para que sigan apuntando a donde está de verdad.
     */
    fun repairChunkOffsets(file: File, audioStartBefore: Long): Boolean = runCatching {
        val audioStartNow = audioStart(file)
        if (audioStartBefore <= 0 || audioStartNow <= 0) return@runCatching false

        val shift = audioStartNow - audioStartBefore
        if (shift == 0L) return@runCatching true

        RandomAccessFile(file, "rw").use { raf ->
            forEachChunkOffsetTable(raf, 0, raf.length()) { entriesAt, count, is64 ->
                for (index in 0 until count) {
                    val at = entriesAt + index.toLong() * if (is64) 8 else 4
                    raf.seek(at)
                    val moved = if (is64) raf.readLong() + shift else (raf.readInt().toLong() and 0xFFFFFFFFL) + shift
                    raf.seek(at)
                    if (is64) raf.writeLong(moved) else raf.writeInt(moved.toInt())
                }
            }
        }
        true
    }.getOrDefault(false)

    /**
     * Red de seguridad: comprueba que el audio al que apuntan las tablas está
     * realmente dentro del `mdat`.
     *
     * Existe porque el fallo que motivó todo esto era silencioso — el archivo
     * parecía perfecto y simplemente no sonaba. Si una versión futura de
     * eAlvaTag o de Media3 vuelve a mover algo por su cuenta, es mejor quedarse
     * sin etiquetas que publicar una canción muda.
     */
    fun chunkOffsetsPointIntoAudio(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            var audioFrom = -1L
            var audioTo = -1L
            editTopLevelBoxes(raf) { pos, type, size, headerBytes ->
                if (type == MDAT) {
                    audioFrom = pos + headerBytes
                    audioTo = pos + size
                }
                size
            }
            if (audioFrom < 0) return@runCatching false

            var sane = true
            forEachChunkOffsetTable(raf, 0, raf.length()) { entriesAt, count, is64 ->
                if (count <= 0) return@forEachChunkOffsetTable
                raf.seek(entriesAt)
                val first = if (is64) raf.readLong() else raf.readInt().toLong() and 0xFFFFFFFFL
                if (first < audioFrom || first >= audioTo) sane = false
            }
            sane
        }
    }.getOrDefault(false)

    /**
     * Recorre las tablas `stco`/`co64`, que viven enterradas en
     * `moov/trak/mdia/minf/stbl`. [action] recibe dónde empiezan las entradas,
     * cuántas hay y si son de 64 bits.
     */
    private fun forEachChunkOffsetTable(
        raf: RandomAccessFile,
        start: Long,
        end: Long,
        action: (entriesAt: Long, count: Int, is64: Boolean) -> Unit,
    ) {
        var pos = start
        while (pos + HEADER_BYTES <= end) {
            raf.seek(pos)
            var size = raf.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also(raf::readFully).toString(Charsets.US_ASCII)
            var headerBytes = HEADER_BYTES.toLong()

            when (size) {
                EXTENDED_SIZE_MARKER -> {
                    size = raf.readLong()
                    headerBytes = LARGE_HEADER_BYTES
                }
                0L -> size = end - pos
            }
            if (size < headerBytes) return

            if (type == STCO || type == CO64) {
                // Detrás de la cabecera van version+flags (4 bytes) y el número
                // de entradas (4 más); las entradas empiezan después.
                raf.seek(pos + headerBytes + 4)
                val count = raf.readInt()
                action(pos + headerBytes + 8, count, type == CO64)
            }

            if (type in CONTAINERS) {
                forEachChunkOffsetTable(raf, pos + headerBytes, pos + size, action)
            }
            pos += size
        }
    }

    /**
     * Recorre las cajas de primer nivel dejando que [edit] las modifique.
     * [edit] devuelve el tamaño con el que avanzar, o null para abandonar.
     */
    private fun editTopLevelBoxes(
        file: File,
        edit: (RandomAccessFile, Long, String, Long, Long) -> Long?,
    ): Boolean = runCatching {
        RandomAccessFile(file, "rw").use { raf ->
            editTopLevelBoxes(raf) { pos, type, size, headerBytes ->
                edit(raf, pos, type, size, headerBytes)
            }
        }
    }.getOrDefault(false)

    /** Devuelve false si abandonó a medias por encontrar algo que no entiende. */
    private fun editTopLevelBoxes(
        raf: RandomAccessFile,
        visit: (pos: Long, type: String, size: Long, headerBytes: Long) -> Long?,
    ): Boolean {
        val length = raf.length()
        var pos = 0L

        while (pos + HEADER_BYTES <= length) {
            raf.seek(pos)
            var size = raf.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also(raf::readFully).toString(Charsets.US_ASCII)
            var headerBytes = HEADER_BYTES.toLong()

            if (size == EXTENDED_SIZE_MARKER) {
                size = raf.readLong()
                headerBytes = LARGE_HEADER_BYTES
            }
            // Un tamaño de cero significa "hasta el final": ya no hay más cajas.
            if (size == 0L) return true

            val step = visit(pos, type, size, headerBytes) ?: return false
            if (step < HEADER_BYTES) return false
            pos += step
        }
        return true
    }

    private fun RandomAccessFile.writeAscii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

    private const val HEADER_BYTES = 8
    private const val LARGE_HEADER_BYTES = 16L
    private const val EXTENDED_SIZE_MARKER = 1L
    private const val MDAT = "mdat"
    private const val FREE = "free"
    private const val SKIP = "skip"
    private const val STCO = "stco"
    private const val CO64 = "co64"

    /** Cajas que hay que abrir para llegar al `stbl`, donde viven las tablas. */
    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl")
}
