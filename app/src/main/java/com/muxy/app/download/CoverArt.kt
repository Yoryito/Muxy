package com.muxy.app.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * Convierte una miniatura de YouTube en algo que sirva de carátula.
 *
 * Las miniaturas son 16:9 y las carátulas se pintan cuadradas, así que sin
 * recortar salen con bandas a los lados o estiradas. El recorte es centrado:
 * en una portada de canción lo que interesa casi siempre está en el medio.
 */
object CoverArt {

    /** Devuelve el archivo escrito, o null si la miniatura no se pudo usar. */
    suspend fun save(bytes: ByteArray, target: File): File? = withContext(Dispatchers.IO) {
        runCatching {
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            val side = min(source.width, source.height)
            val square = Bitmap.createBitmap(
                source,
                (source.width - side) / 2,
                (source.height - side) / 2,
                side,
                side,
            )

            target.outputStream().use { output ->
                square.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
            }

            square.recycle()
            if (square !== source) source.recycle()
            target
        }.getOrNull()
    }

    private const val QUALITY = 90
}
