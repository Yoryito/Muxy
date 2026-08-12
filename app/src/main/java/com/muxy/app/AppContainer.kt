package com.muxy.app

import android.content.Context
import com.muxy.app.data.MuxyDatabase
import com.muxy.app.data.MusicLibrary
import com.muxy.app.playback.PlayerConnection

/**
 * Contenedor de dependencias hecho a mano. La app es pequeña y con esto basta;
 * meter Hilt aquí sería procesamiento de anotaciones a cambio de nada.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val library: MusicLibrary by lazy {
        MusicLibrary(appContext, MuxyDatabase.get(appContext).songDao())
    }

    val player: PlayerConnection by lazy { PlayerConnection(appContext) }
}
