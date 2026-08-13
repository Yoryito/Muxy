package com.muxy.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Cuánto atenuar un M4A para que no destaque frente al resto de la librería.
 *
 * Decodifica el archivo ya convertido —nunca toca la codificación, así que un
 * fallo aquí como mucho deja la canción sin normalizar, jamás la corrompe— y
 * mide el nivel medio (RMS) de sus muestras en dBFS. [TrackGain.dbFor] convierte
 * eso en la atenuación a aplicar, que solo puede bajar el volumen: subir el de
 * lo flojo pasaría de 1.0 en `Player.volume`, que no amplifica.
 */
object LoudnessAnalyzer {

    /** RMS en dBFS, o `null` si el archivo no se pudo decodificar. */
    fun measureRmsDb(file: File): Float? = runCatching { measure(file) }.getOrNull()

    private fun measure(file: File): Float {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Sin pista de audio en ${file.name}")

            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Sin MIME")
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return decodeAndMeasure(extractor, codec)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun decodeAndMeasure(extractor: MediaExtractor, codec: MediaCodec): Float {
        val bufferInfo = MediaCodec.BufferInfo()
        var sumSquares = 0.0
        var sampleCount = 0L
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: error("Sin buffer de entrada")
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: error("Sin buffer de salida")
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val samples = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (samples.hasRemaining()) {
                        val normalized = samples.get().toDouble() / Short.MAX_VALUE
                        sumSquares += normalized * normalized
                        sampleCount++
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }

        check(sampleCount > 0) { "Sin muestras decodificadas" }
        val rms = sqrt(sumSquares / sampleCount).coerceAtLeast(MIN_RMS)
        return (20 * log10(rms)).toFloat()
    }

    private const val TIMEOUT_US = 10_000L

    /** Suelo para que un silencio total no mande el logaritmo a -infinito. */
    private const val MIN_RMS = 1e-7
}

/** A cuánto normalizar: por debajo de esto se atenúa, por encima se deja igual. */
object TrackGain {
    private const val TARGET_RMS_DB = -18f

    /**
     * Nunca positivo: [androidx.media3.common.Player.volume] no pasa de 1.0,
     * así que lo único que se puede hacer con una canción floja es dejarla
     * como está, no subirla por encima de las demás.
     */
    fun dbFor(measuredRmsDb: Float): Float = (TARGET_RMS_DB - measuredRmsDb).coerceAtMost(0f)
}
