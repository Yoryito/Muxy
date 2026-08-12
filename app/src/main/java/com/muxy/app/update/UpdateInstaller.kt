package com.muxy.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.muxy.app.BuildConfig
import com.muxy.app.download.HttpFetcher
import kotlinx.coroutines.delay
import java.io.File

/**
 * Baja el APK de una release y se lo pasa al instalador de Android.
 *
 * Android no permite instalar en silencio sin ser device-owner o tener root, así
 * que el último paso es siempre la pantalla del sistema con su "¿Instalar?": la
 * app puede traer el archivo, pero quien acepta es el usuario.
 */
class UpdateInstaller(
    private val context: Context,
    private val fetcher: HttpFetcher,
) {

    /**
     * Instalar desde fuera de una tienda exige que el usuario haya dado permiso a
     * *esta* app en concreto ("Instalar apps desconocidas"). Se pregunta antes de
     * descargar: bajar 20 MB para chocar al final con un permiso es tiempo tirado.
     */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Intent que abre el ajuste del sistema donde se concede ese permiso. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    suspend fun download(
        update: AvailableUpdate,
        onProgress: suspend (Float?) -> Unit,
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Los intentos anteriores no valen para nada y ocupan lo que ocupa un
        // APK: cada descarga empieza dejando la carpeta con un solo archivo.
        dir.listFiles()?.forEach { it.delete() }

        val target = File(dir, "muxy-${update.versionName}.apk")

        // Un corte de red a mitad de descarga es lo normal en un móvil, y volver
        // a empezar cuesta unos megas: mejor eso que dar la actualización por
        // imposible y que el usuario tenga que buscarla en ajustes.
        repeat(ATTEMPTS) { attempt ->
            val outcome = runCatching { fetcher.toFile(update.apkUrl, target, onProgress) }
            if (outcome.isSuccess) return target

            target.delete()
            if (attempt == ATTEMPTS - 1) throw outcome.exceptionOrNull()!!
            delay(RETRY_DELAY_MS)
        }
        error("inalcanzable")
    }

    fun install(apk: File) {
        // El instalador corre en otro proceso y no puede leer la caché de la app,
        // así que el archivo viaja como content:// con permiso de lectura.
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.updates", apk)

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    private companion object {
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_500L
    }
}
