package com.muxy.app.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * Lee una carátula del disco y la deja en bytes para la notificación.
 *
 * Existe porque el sistema **no puede leer nuestros archivos**: la carpeta de
 * carátulas es privada de la app y SystemUI, que corre en otro proceso, se come
 * un EACCES al abrirla por `file://`. La única forma de que la miniatura salga
 * en la notificación y en la pantalla de bloqueo es darle los bytes ya leídos.
 *
 * Se reescala antes de entregarlos: la carátula guardada puede ser de 720 px y
 * pesar bastante, y esos bytes acaban viajando por Binder hasta el sistema, que
 * de todas formas la va a pintar del tamaño de un icono de notificación.
 */
object CoverBytes {

    /** De sobra para la notificación y la pantalla de bloqueo. */
    private const val TARGET_PX = 512
    private const val QUALITY = 85

    /** Devuelve los bytes, o null si el archivo no está o no se puede decodificar. */
    fun load(path: String): ByteArray? = runCatching {
        if (!File(path).exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val largestSide = max(bounds.outWidth, bounds.outHeight)
        if (largestSide <= 0) return null

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(largestSide) }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null

        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    /**
     * `inSampleSize` solo respeta potencias de dos, así que se busca la mayor
     * que deje la imagen en [TARGET_PX] o por encima — quedarse corto se ve,
     * pasarse solo gasta memoria.
     */
    private fun sampleSizeFor(largestSide: Int): Int {
        var sample = 1
        while (largestSide / (sample * 2) >= TARGET_PX) sample *= 2
        return sample
    }
}
