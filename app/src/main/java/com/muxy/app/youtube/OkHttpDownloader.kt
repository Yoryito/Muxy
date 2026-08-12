package com.muxy.app.youtube

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * NewPipeExtractor no trae cliente HTTP propio: hay que darle uno.
 *
 * El User-Agent importa. YouTube devuelve respuestas distintas —o directamente
 * bloquea— según el cliente que cree estar sirviendo, así que se envía uno de
 * escritorio normal en vez de dejar el de OkHttp.
 */
class OkHttpDownloader(
    private val client: OkHttpClient = defaultClient(),
) : Downloader() {

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val builder = Request.Builder()
            .method(
                request.httpMethod(),
                request.dataToSend()?.toRequestBody(),
            )
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            // El 429 de YouTube significa "resuelve un captcha". No es un fallo
            // de red: reintentar sin más solo empeora el bloqueo.
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha", request.url())
            }

            val body = response.body?.string()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                body,
                response.request.url.toString(),
            )
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
