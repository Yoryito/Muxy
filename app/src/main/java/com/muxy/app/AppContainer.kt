package com.muxy.app

import android.content.Context
import com.muxy.app.data.MuxyDatabase
import com.muxy.app.data.MusicLibrary
import com.muxy.app.data.PlaylistBackup
import com.muxy.app.data.PlaylistRepository
import com.muxy.app.download.AudioTranscoder
import com.muxy.app.download.DownloadQueue
import com.muxy.app.download.HttpFetcher
import com.muxy.app.playback.PlayerConnection
import com.muxy.app.update.UpdateChecker
import com.muxy.app.update.UpdateInstaller
import com.muxy.app.update.UpdatePreferences
import com.muxy.app.youtube.NewPipeAudioResolver
import com.muxy.app.youtube.YoutubeAudioResolver
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * Contenedor de dependencias hecho a mano. La app es pequeña y con esto basta;
 * meter Hilt aquí sería procesamiento de anotaciones a cambio de nada.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val library: MusicLibrary by lazy {
        MusicLibrary(appContext, MuxyDatabase.get(appContext).songDao())
    }

    val playlists: PlaylistRepository by lazy {
        PlaylistRepository(MuxyDatabase.get(appContext).playlistDao())
    }

    val playlistBackup: PlaylistBackup by lazy {
        PlaylistBackup(MuxyDatabase.get(appContext).playlistDao(), MuxyDatabase.get(appContext).songDao())
    }

    val player: PlayerConnection by lazy { PlayerConnection(appContext, library) }

    val youtube: YoutubeAudioResolver by lazy { NewPipeAudioResolver() }

    /**
     * Un solo cliente para toda la app: OkHttp comparte el pool de conexiones y
     * los hilos entre llamadas, y tener varios anula esa ventaja.
     *
     * El timeout de lectura es generoso porque descargar una canción entera es
     * una sola respuesta larga, no una petición de API.
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val httpFetcher: HttpFetcher by lazy { HttpFetcher(httpClient) }

    val transcoder: AudioTranscoder by lazy { AudioTranscoder(appContext) }

    val downloads: DownloadQueue by lazy { DownloadQueue(appContext) }

    val updateChecker: UpdateChecker by lazy { UpdateChecker(httpClient) }

    /**
     * El APK se baja por HTTP/1.1 y con su propio pool, a diferencia de todo lo
     * demás.
     *
     * OkHttp reaprovecha una conexión HTTP/2 para otro host cuando el certificado
     * la cubre, y `api.github.com` y `objects.githubusercontent.com` caen en ese
     * saco: la comprobación deja la conexión abierta, la descarga intenta colarse
     * por ella y el CDN la corta con REFUSED_STREAM. Falla siempre, no de vez en
     * cuando. Sin multiplexado no hay conexión que compartir.
     */
    private val updateHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool())
            .build()
    }

    val updateInstaller: UpdateInstaller by lazy {
        UpdateInstaller(appContext, HttpFetcher(updateHttpClient))
    }

    val updatePreferences: UpdatePreferences by lazy { UpdatePreferences(appContext) }
}
