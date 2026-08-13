package com.muxy.app.youtube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "Muxy"

/**
 * Implementación con NewPipeExtractor.
 *
 * Todas las llamadas al extractor son bloqueantes y hacen red, así que van a
 * [Dispatchers.IO]. Las excepciones del extractor se traducen a [ResolveError]
 * para que la interfaz no tenga que conocer sus tipos.
 */
class NewPipeAudioResolver : YoutubeAudioResolver {

    private val youtube get() = ServiceList.YouTube

    override suspend fun search(query: String): List<YoutubeResult> = withContext(Dispatchers.IO) {
        ensureInitialised()
        runCatching {
            val handler = youtube.searchQHFactory.fromQuery(
                query,
                // Solo vídeos: los canales y las listas no sirven para descargar.
                listOf("videos"),
                "",
            )
            SearchInfo.getInfo(youtube, handler)
                .relatedItems
                .filterIsInstance<StreamInfoItem>()
                // Los directos no tienen una pista descargable con final.
                .filterNot { it.streamType?.name?.contains("LIVE") == true }
                .mapNotNull { it.toResult() }
        }.getOrElse { throw it.asResolveError() }
    }

    override suspend fun resolve(videoId: String, quality: DownloadQuality): YoutubeTrack = withContext(Dispatchers.IO) {
        ensureInitialised()
        val url = "https://www.youtube.com/watch?v=$videoId"

        val info = runCatching { StreamInfo.getInfo(youtube, url) }
            .getOrElse { throw it.asResolveError() }

        val streams = info.audioStreams?.filter { !it.content.isNullOrBlank() }.orEmpty()
        // Dentro del techo de [quality] se coge la de mayor bitrate, igual que
        // antes; si ninguna baja de ese techo, mejor la más ligera disponible
        // que fallar la descarga.
        val underCap = streams.filter { it.averageBitrate <= quality.maxBitrateKbps }
        val best = if (underCap.isNotEmpty()) {
            // A igualdad de bitrate se prefiere m4a porque evita transcodificar.
            underCap.maxWithOrNull(compareBy({ it.averageBitrate }, { if (it.format?.suffix == "m4a") 1 else 0 }))
        } else {
            streams.minByOrNull { it.averageBitrate }
        } ?: throw ResolveError.NoAudio()

        YoutubeTrack(
            videoId = videoId,
            title = info.name.orEmpty(),
            uploader = info.uploaderName,
            durationSeconds = info.duration,
            thumbnailUrl = info.thumbnails?.maxByOrNull { it.height }?.url,
            audio = AudioStream(
                url = best.content,
                format = best.format?.suffix ?: "webm",
                bitrateKbps = best.averageBitrate,
                mimeType = best.format?.mimeType,
            ),
        )
    }

    /**
     * Va por páginas hasta [MAX_PLAYLIST_ITEMS] o [MAX_PLAYLIST_PAGES], lo que
     * llegue antes: para uso personal es de sobra, y sin un tope una lista
     * gigante se pondría a paginar sin parar.
     */
    override suspend fun resolvePlaylist(url: String): YoutubePlaylist = withContext(Dispatchers.IO) {
        ensureInitialised()
        runCatching {
            val info = PlaylistInfo.getInfo(youtube, url)
            val items = mutableListOf<StreamInfoItem>()
            items += info.relatedItems.filterIsInstance<StreamInfoItem>()

            var page = info.nextPage
            var pagesFetched = 0
            while (page != null && items.size < MAX_PLAYLIST_ITEMS && pagesFetched < MAX_PLAYLIST_PAGES) {
                val more = PlaylistInfo.getMoreItems(youtube, url, page)
                items += more.items.filterIsInstance<StreamInfoItem>()
                page = more.nextPage
                pagesFetched++
            }

            YoutubePlaylist(
                title = info.name.orEmpty(),
                items = items
                    .filterNot { it.streamType?.name?.contains("LIVE") == true }
                    .mapNotNull { it.toResult() }
                    .take(MAX_PLAYLIST_ITEMS),
            )
        }.getOrElse { throw it.asResolveError() }
    }

    private fun StreamInfoItem.toResult(): YoutubeResult? {
        val id = url?.substringAfter("v=", "")?.substringBefore("&")?.takeIf { it.isNotBlank() }
            ?: return null
        return YoutubeResult(
            videoId = id,
            url = url,
            title = name.orEmpty(),
            uploader = uploaderName,
            durationSeconds = duration,
            thumbnailUrl = thumbnails?.maxByOrNull { it.height }?.url,
        )
    }

    private companion object {
        const val MAX_PLAYLIST_ITEMS = 300
        const val MAX_PLAYLIST_PAGES = 5

        private val initialised = AtomicBoolean(false)

        fun ensureInitialised() {
            if (initialised.compareAndSet(false, true)) {
                NewPipe.init(OkHttpDownloader())
            }
        }
    }
}

/**
 * Traduce las excepciones del extractor.
 *
 * [ExtractionException] merece log explícito: cuando aparece de golpe en todas
 * las búsquedas, es que YouTube ha cambiado algo y toca subir la versión de
 * NewPipeExtractor.
 */
private fun Throwable.asResolveError(): ResolveError = when (this) {
    is ResolveError -> this
    is ReCaptchaException -> ResolveError.RateLimited(this)
    is ContentNotAvailableException -> ResolveError.Unavailable(message ?: "No disponible", this)
    is IOException -> ResolveError.Network(this)
    is ExtractionException -> {
        Log.e(TAG, "Extracción fallida — puede que toque actualizar NewPipeExtractor", this)
        ResolveError.ExtractionFailed(this)
    }
    else -> ResolveError.ExtractionFailed(this)
}
