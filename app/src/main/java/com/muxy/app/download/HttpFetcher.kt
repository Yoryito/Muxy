package com.muxy.app.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Descarga bytes a un archivo informando del avance.
 *
 * Va aparte del resolver porque son dos cosas distintas: aquel habla con la API
 * de YouTube, esto solo baja una URL. Manda el mismo User-Agent de escritorio
 * que [com.muxy.app.youtube.OkHttpDownloader]: los servidores de vídeo también
 * miran el cliente y con el de OkHttp responden de forma distinta.
 */
class HttpFetcher(private val client: OkHttpClient) {

    /** Baja [url] a [target]. [onProgress] recibe null mientras no se sepa el tamaño. */
    suspend fun toFile(
        url: String,
        target: File,
        onProgress: suspend (Float?) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            // Pedir el rango completo explícitamente: googlevideo estrangula el
            // ancho de banda de las peticiones que no lo llevan.
            .header("Range", "bytes=0-")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} al descargar el audio")
            }

            val total = response.body.contentLength().takeIf { it > 0 }
            var written = 0L
            var lastReported = -1f

            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        // Sin esto, cancelar la descarga solo marcaría el job:
                        // el bucle de copia seguiría hasta el final del archivo.
                        coroutineContext.ensureActive()

                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read

                        if (total == null) {
                            onProgress(null)
                        } else {
                            val fraction = (written.toFloat() / total).coerceIn(0f, 1f)
                            // Un progreso por byte saturaría WorkManager de escrituras.
                            if (fraction - lastReported >= PROGRESS_STEP) {
                                lastReported = fraction
                                onProgress(fraction)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Baja una URL pequeña (una miniatura) a memoria. Devuelve null si falla. */
    suspend fun bytesOrNull(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.bytes() else null
            }
        }.getOrNull()
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val PROGRESS_STEP = 0.02f

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}
