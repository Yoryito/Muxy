# Muxy

Reproductor y librería de música local para Android. El usuario busca una canción, la app la localiza en YouTube, extrae solo el audio, lo convierte y lo guarda en una librería local con reproductor propio.

## Contexto y restricciones

- **Uso personal.** Para el propietario y una amiga. Se distribuye como APK por sideload, **no** va a Google Play. Las políticas de la tienda sobre apps que descargan de YouTube no aplican; descargar de YouTube incumple sus ToS y es un compromiso aceptado conscientemente para uso personal.
- **Spotify no se puede descargar.** Su audio está protegido con DRM. Cualquier integración con Spotify es *solo metadatos*: buscar en su catálogo para identificar título/artista y luego resolver y descargar el audio equivalente desde YouTube. No intentar extraer audio de Spotify.
- **El diseño es un requisito de primera clase**, no decoración. Ver "Dirección visual".

## Stack y por qué

| Área | Elección | Razón (no deshacer sin motivo) |
|---|---|---|
| Extracción YouTube | **NewPipeExtractor** tras la interfaz `YoutubeAudioResolver` | Alternativa era yt-dlp embebido (vía Chaquopy), que parchea más rápido pero arrastra un runtime de Python entero. La interfaz existe precisamente para poder cambiar de backend si NewPipeExtractor se queda roto mucho tiempo. |
| Conversión de audio | **Media3 Transformer** → salida **M4A/AAC** | `ffmpeg-kit` fue archivado y retirado de Maven Central en 2025 y no hay sucesor consolidado. Transformer usa MediaCodec nativo, cero binarios de terceros. Por eso la salida es M4A y **no MP3**: MP3 obligaría a volver a FFmpeg. |
| Etiquetado | **eAlvaTag** | Escribe metadatos y carátula embebida en M4A. |
| Reproducción | **androidx.media3** (ExoPlayer + media3-session) con `MediaSessionService` | Estándar actual de Google para audio en segundo plano con controles de notificación. |
| Almacenamiento | **Room** + archivos en almacenamiento específico de la app | App de 1-2 usuarios, sin nube ni sincronización. No hace falta nada más. |
| Descargas | **WorkManager** con worker en primer plano | Sobreviven a que la app pase a segundo plano. |
| DI | Contenedor manual (`AppContainer`), **sin Hilt** | La app es pequeña; Hilt añadiría procesamiento de anotaciones sin beneficio real. |
| Actualizaciones | `UpdateChecker` contra **GitHub Releases** | Sin Play Store no hay auto-update. Compara la versión de la última release al arrancar y ofrece instalar el APK. Android no permite auto-instalación silenciosa sin root/device-owner, así que siempre requiere confirmación del usuario. |

## Dirección visual

Tema de **ranas y nenúfares**, estilo "estanque acogedor" — ilustración cálida tipo cuento, paleta botánica apagada, espacio en blanco real.

Lo que hay que **evitar** activamente (look de "AI slop"): degradados morados/azules, glassmorphism, neón, emojis como decoración, sombras en todo, mascota tipo clipart.

- La fuente de verdad de la paleta y tipografía es `app/src/main/java/.../ui/theme/`. No hardcodear colores en las pantallas.
- **Dynamic color de Android está desactivado a propósito** para que el tema sea idéntico en todos los dispositivos. No reactivarlo.
- Pochi aparece solo donde cumple una función (estados vacíos, error, éxito), nunca como adorno suelto. Ver la sección "Pochi" más abajo.
- La muesca del nenúfar (`LilyPadShape`) tiene que ser **estrecha**: una cuña ancha convierte el nenúfar en un Pac-Man. Y donde se recorta sobre un fondo, recortarla de todas las capas deja ver el fondo de la pantalla por el hueco.
- **La muesca se mide en grados, así que crece con el radio.** Los 15° por defecto son una hendidura fina en una fila de 52 dp, pero en la carátula de 300 dp del reproductor abren un pedazo de tarta descarado. Cualquier nenúfar grande tiene que estrechar `notchWidth` a mano (la carátula usa 5°, el botón de reproducir 10°). El defecto se queda en 15° porque es el que ya está aprobado en listas y mini-reproductor.
- Los contenedores de acento del modo oscuro tienen que ser tonos oscuros de verdad. Reutilizar un tono medio como fondo sobre el estanque nocturno queda chillón.

## Entorno de desarrollo

Esta máquina no tiene Android Studio, solo el SDK por línea de comandos:

- SDK: `F:\03_IA\Tools\Android\Sdk` (platform android-36, build-tools 36.0.0, platform-tools 37, cmdline-tools 22.0)
- JDK: `F:\Java Open JDK\Hotspot` (Temurin 21) — hay que exportar `JAVA_HOME` a mano, no está en el entorno
- Gradle: `F:\03_IA\Tools\Gradle\gradle-8.14.5` (el proyecto usa su propio wrapper; esto solo se usó para generarlo)

**Proxy que intercepta TLS.** Esta máquina tiene un proxy que intercepta TLS y la JVM no confía en su certificado, así que cualquier herramienta basada en JVM falla al descargar dependencias (`sdkmanager`: "IO exception while downloading manifest"; Gradle: "could not resolve plugin artifact"). curl sí funciona, lo que despista. La solución es leer las raíces de confianza del almacén de Windows:

- Gradle: ya resuelto en `~/.gradle/gradle.properties` con `systemProp.javax.net.ssl.trustStoreType=Windows-ROOT` (fuera del repo, es específico de esta máquina).
- sdkmanager: hay que pasarlo a mano cada vez.

```bash
JAVA_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT" sdkmanager "<paquete>"
```

## Versiones fijadas a propósito (no subir sin comprobar)

Las versiones del `libs.versions.toml` **no son las últimas**, y bajarlas fue deliberado:

- **AGP 8.13.2 / compileSdk 36.** Las últimas AndroidX (core-ktx 1.19, lifecycle 2.11, activity 1.13…) exigen AGP 9.1+ y compileSdk 37, pero **la API 37 todavía no existe como plataforma estable** en el repositorio del SDK. Por eso todas las AndroidX están una o dos versiones por debajo de la última.
- **Kotlin 2.2.21.** Es la última con la que KSP publica versión emparejada (`2.2.21-2.0.5`), y Room necesita KSP. KSP cambió a versionado independiente y aún no cubre Kotlin 2.3/2.4.
- **Coil 3.3.0.** La 3.5.0 arrastra `kotlin-stdlib` 2.4.0, que el compilador de Kotlin 2.2.21 no sabe leer.

Cuando salga la plataforma android-37 estable y KSP alcance a Kotlin 2.4, se puede subir todo el bloque de golpe.

## Comandos

Hay que exportar `JAVA_HOME` antes de cualquier comando de Gradle, y `adb` no está en el PATH por defecto:

```bash
export JAVA_HOME="F:/Java Open JDK/Hotspot"
export PATH="$PATH:/f/03_IA/Tools/Android/Sdk/platform-tools"
./gradlew installDebug           # compilar e instalar en el móvil conectado
adb logcat -s Muxy               # ver los logs de la app
```

El id de aplicación en debug lleva sufijo: `com.muxy.app.debug`.

```bash
adb shell am start -n com.muxy.app.debug/com.muxy.app.MainActivity
adb exec-out screencap -p > captura.png
```

Para publicar una versión (compila, commitea, etiqueta y sube la release con el APK):

```bash
scripts/release.sh 0.1.11 "Lo que cambia, contado para quien lo lee en el móvil"
```

Para capturas usar `adb exec-out`, no `adb shell screencap` con una ruta: Git Bash convierte `/sdcard/...` a una ruta de Windows y el comando falla de forma confusa. Lo mismo con cualquier ruta del dispositivo — hay que envolverla con `MSYS_NO_PATHCONV=1`, o usar PowerShell para los `adb push`.

## Puntos frágiles conocidos

- **La extracción de YouTube se romperá periódicamente.** Es esperado, no un bug puntual. La señal es `ResolveError.ExtractionFailed` apareciendo de golpe en todas las búsquedas (se registra con un aviso explícito en logcat). Cuando pase: subir `newpipeExtractor` en `gradle/libs.versions.toml`; si eso no basta, la interfaz `YoutubeAudioResolver` permite cambiar de backend (por ejemplo a yt-dlp) tocando un solo archivo.
- **El captcha de YouTube (HTTP 429) no es un fallo de red.** Se distingue a propósito como `ResolveError.RateLimited`, porque reintentar solo empeora el bloqueo. Por eso la búsqueda lleva debounce: sin él, cada pulsación sería una petición.
- Salida solo en M4A. Si algún día hiciera falta MP3, implicaría reintroducir FFmpeg y su carga de mantenimiento.
- **eAlvaTag no sabe etiquetar lo que sale de Media3 Transformer sin retocar antes las cabeceras.** Ver `download/Mp4Headers.kt`: son tres incompatibilidades reales, no una manía, y una de ellas dejaba las canciones mudas sin dar ningún error. Detalle abajo.

### La carátula de la notificación va por bytes, no por URI

Estaba el fallo de que SystemUI no podía leer `.../files/covers/<id>.jpg` (`EACCES`): la carpeta es privada de la app y el sistema la lee desde otro proceso. Resuelto, pero el arreglo tiene dos partes que hay que mantener juntas:

- **`Song.toMediaItem()` ya no pone `artworkUri`.** Es clave: mientras haya una URI, el sistema la prefiere sobre cualquier otra cosa y vuelve el EACCES. La ruta viaja en `mediaMetadata.extras` bajo `EXTRA_COVER_PATH`, que es de donde la saca `PlayerConnection.refresh()` para que Coil pinte la carátula dentro de la app (nuestro proceso sí puede leer el archivo).
- **`PlaybackService` le pega los bytes solo a la canción actual**, al vuelo, en `onMediaItemTransition` (ver `attachArtwork` y `CoverBytes`). No se hace al montar la cola porque la cola es la librería entera: serían decenas de megas de `ByteArray` cruzando Binder y un tirón en el hilo principal al dar a reproducir. Se reescala a 512 px, que es de sobra para una notificación.

Detalles que no conviene tocar: el `replaceMediaItem` **no corta el audio** porque el elemento nuevo conserva la misma URI y ExoPlayer solo actualiza metadatos; y la guarda de `artworkData != null` es lo que impide un bucle infinito, porque reemplazar el elemento vuelve a disparar eventos.

## Etiquetado: por qué existe `Mp4Headers`

Los MP4 que produce Transformer son válidos —cualquier reproductor los abre— pero eAlvaTag, pensada para archivos de iTunes, se atraganta con tres cosas. `Mp4Headers` las corrige tocando **solo cabeceras y tablas de offsets**, sin mover un byte de audio.

1. **`mdat` con tamaño de 64 bits.** Los muxers de Android escriben el campo de tamaño a 1 y el tamaño real en los 8 bytes siguientes. eAlvaTag guarda el tamaño de caja en un `int` y no contempla esa variante: falla con *"This file does not appear to be an Mp4 file"*. Como la cabecera de 64 bits ocupa 16 bytes y la de 32 ocupa 8, los 8 sobrantes se convierten en una caja de relleno vacía y el audio sigue empezando en el mismo desplazamiento — que es lo que importa, porque las tablas de chunks apuntan ahí.

2. **La caja `free` gigante que deja el muxer.** Media3 reserva ~400 KB para el `moov` y lo que sobra queda como `free` entre `moov` y `mdat`. Al verlo, eAlvaTag intenta encajar los metadatos ahí sin mover nada ("Option 5"), le sale mal la cuenta y aborta con *"Cannot make changes to file"*. El truco es **renombrar esa caja a `skip`** — el estándar las define como equivalentes, pero eAlvaTag no reconoce `skip` —, con lo que se va a su camino normal: reescribir el archivo desplazando el `mdat`.

3. **Y ahí está la trampa, que costó cara: al desplazar el `mdat` no corrige los offsets.** Las tablas que dicen dónde empieza cada trozo de audio existen en dos variantes, `stco` (32 bits) y `co64` (64 bits). **eAlvaTag solo conoce `stco`, y Media3 escribe `co64`.** Así que mueve el audio unos 50 KB y deja los offsets apuntando a donde estaba: al relleno. El resultado es una canción que **no suena** mientras el reloj del reproductor avanza con toda normalidad y no aparece ni un error en logcat. `repairChunkOffsets` los recoloca sumándoles el desplazamiento real del `mdat`.

Cambiar de muxer **no** arregla nada: se probó `FrameworkMuxer` (el del sistema) y es peor, porque además escribe un `moov/udta` que eAlvaTag rechaza al leer.

### Cómo comprobar que un M4A está sano

El fallo del punto 3 es invisible desde la app, así que **no vale con reproducir y ver que la barra avanza** — avanza igual con el archivo roto. La comprobación de verdad es sacar el archivo y mirar dónde apunta la primera entrada de la tabla de chunks:

- Sano: la primera entrada coincide con el principio del `mdat` y ahí hay tramas AAC (bytes variados, `ff f1` de sincronía).
- Roto: apunta antes del `mdat` y ahí solo hay ceros.

`TrackTagger` lleva esa comprobación incorporada (`Mp4Headers.chunkOffsetsPointIntoAudio`) y **si no la pasa recupera la copia previa al etiquetado**: la canción se queda sin etiquetas, que se nota y se arregla, en vez de muda, que no se nota. No quitar esa red.

Para depurar el etiquetado: eAlvaTag registra la excepción real con ealvalog y en Android no sale por ningún lado, así que el camino rápido es sacar el `.m4a` con `adb pull` y reproducir el fallo en la JVM de escritorio con `Loggers.INSTANCE.setFactory(StdoutLoggerFactory.INSTANCE)`, que sí imprime la causa y la rama que eligió.

## Cómo se distribuye y se actualiza

La app no pasa por ninguna tienda: se instala desde el APK de una **GitHub Release** y a partir de ahí se actualiza sola. Todo el proceso está en `scripts/release.sh`, que sube la versión, compila firmado, commitea, etiqueta y publica la release con el APK colgado. Publicar es lo que hace que el móvil de enfrente se entere: la app mira `releases/latest` al abrirse.

**La clave de firma es lo más frágil de todo esto.** Android se niega a actualizar una app si la firma cambia, así que todas las versiones tienen que ir firmadas con el mismo `muxy-release.keystore`. Si se pierde ese archivo (o su contraseña, en `keystore.properties`), la única salida es que cada usuario desinstale y vuelva a instalar, perdiendo su librería. Los dos archivos están en `.gitignore` —el repo es público— y **conviene tener una copia fuera de este disco**. Sin ellos el build sigue funcionando: sale un APK sin firmar, que compila pero no se instala.

**La versión vive en una sola línea**, `val muxyVersionName` en `app/build.gradle.kts`, y el `versionCode` se calcula de ella (`0.1.10` → `110`). Contar releases a mano acabaría repitiendo un número, y un `versionCode` repetido hace que Android rechace la actualización sin explicar por qué.

Detalles del camino de actualización que conviene no deshacer:

- **La comparación de versiones es numérica, tramo a tramo** (`isNewer`). Comparar las cadenas tal cual pondría `0.1.9` por delante de `0.1.10`, que es justo el salto que toca.
- Se usa `releases/latest`, que **deja fuera borradores y prereleases**: publicar un borrador no debe avisar a nadie. Y una release sin APK se ignora, para no ofrecer una actualización que al tocarla no tiene qué descargar.
- La API va **sin token** porque el repositorio es público (60 peticiones por hora y por IP, de sobra para una comprobación al abrir). Meter un token aquí sería publicarlo en un APK que se reparte.
- La instalación va por **`PackageInstaller`**, no lanzando un `ACTION_VIEW` con el APK. Aquello abre un "Abrir con" en cuanto hay otra app que diga entender los APK (un explorador de archivos, WPS Office), y elegir icono a mano no es lo que se espera al tocar "Actualizar". Como el archivo se copia dentro de la sesión, tampoco hace falta `FileProvider`.
- `PackageInstaller` **no enseña nada por su cuenta**: avisa por un `BroadcastReceiver` (`InstallResultReceiver`) de que hace falta confirmación y manda dentro el intent de esa pantalla. Sin abrirlo, la actualización se queda esperando en silencio para siempre.
- La descarga **reintenta tres veces**, y el APK se baja por **HTTP/1.1 con su propio pool de conexiones**. Lo segundo no es manía: OkHttp reaprovecha una conexión HTTP/2 para otro host cuando el certificado la cubre, y `api.github.com` y `objects.githubusercontent.com` caen en ese saco, así que la descarga se colaba por la conexión de la comprobación y el CDN la cortaba con `REFUSED_STREAM`. Fallaba siempre, no de vez en cuando.
- Instalar exige que el usuario haya concedido "instalar apps desconocidas" a Muxy. **Se comprueba antes de descargar** (`canInstall`), porque bajar 20 MB para chocar al final con un permiso es tiempo tirado. Al volver de esos ajustes solo se reintenta si el permiso está: reintentar a ciegas deja al usuario rebotando entre pantallas.
- La comprobación automática es **una por arranque**, no una por composición: el `LaunchedEffect` que la lanza vuelve a correr al girar la pantalla.
- **El aviso emergente vive en `MainActivity`, no en la pantalla de ajustes**, para que salga esté donde esté el usuario. La comprobación a mano no lo abre: quien ha ido a buscarla ya está mirando la respuesta en ajustes.

**Ojo con probar esto en debug:** el build de debug es otro `applicationId` (`com.muxy.app.debug`) y va sin firmar con la clave de release, así que puede comprobar y descargar, pero lo que instale será una app aparte. El camino de actualización de verdad solo se prueba sobre un APK de release instalado.

## Estado actual

Fases: 0 memoria ✅ · 1 andamiaje + diseño ✅ · 2 reproducción ✅ · 3 búsqueda YouTube ✅ · 4 pipeline de descarga ✅ · 5 pulido ✅ · **6 ajustes + auto-actualización ✅** · 7 (opcional) Spotify.

Cada fase termina con prueba manual en el móvil real por USB antes de pasar a la siguiente. Móvil de pruebas: Samsung Galaxy A55 (`SM_A556B`).

Lo que ya funciona, verificado en dispositivo: librería con Room, reproducción con Media3 en segundo plano con controles en notificación y pantalla de bloqueo, mini-reproductor, búsqueda real en YouTube, el pipeline completo de descarga (resolver → bajar → convertir a M4A → etiquetar con carátula → dar de alta), con avance por etapas en la fila de resultados, notificación de progreso y cancelación, el reproductor a pantalla completa, la carátula en la notificación del sistema, filtrar y ordenar la librería, borrar canciones, las playlists, y la pestaña de ajustes con la actualización desde GitHub Releases.

La pestaña de **Ajustes está a medias a propósito**: de momento solo lleva "Acerca de" y "Actualizaciones", que era lo que hacía falta para repartir la app. Es el sitio donde irán los ajustes que vayan saliendo.

La fase 5 se hizo en dos tandas, acotadas las dos por el propietario: primero solo el reproductor a pantalla completa, y después la carátula de la notificación, el borrado, el filtro/orden de la librería y las playlists — que las pidió él y no estaban en el plan original.

Sigue sin hacer, sin fecha: `MusicLibrary.pruneMissing` existe pero no lo llama nadie (`sync()` da de alta lo que aparece, pero no da de baja lo que desaparece), y las playlists no se pueden reordenar a mano.

### Cómo está montado el reproductor completo

`PlayerScreen` se abre tocando el mini-reproductor y es una **capa sobre el `Scaffold`**, no un destino de navegación: así tapa también la barra inferior, que es lo que se espera de un reproductor a pantalla completa. `MainActivity` guarda si está abierto y lo cierra con `BackHandler`, y también solo si la cola se queda vacía.

- La barra de posición es un `Slider` de Material con `track` y `thumb` propios, no un arrastre resuelto a mano: así se conserva el gesto y la accesibilidad y solo cambia cómo se pinta (nivel de agua + nenúfar por tirador). Eso obliga a `@OptIn(ExperimentalMaterial3Api::class)`, porque el `Slider` estable no deja sustituir esas piezas.
- **Mientras se arrastra manda el dedo.** La posición se refresca sola cada 500 ms, y sin guardar el valor del arrastre aparte el tirador se volvería solo a la posición real a mitad del gesto.
- Con duración desconocida el rango del `Slider` sería vacío y revienta, así que en ese caso va con rango de pega y deshabilitado.
- El vaivén de la carátula **se apaga casi del todo al pausar** en vez de cortarse: a ese tamaño, seguir flotando con la música parada chirría.

### Cómo están montadas las playlists y la librería

Las playlists son dos tablas nuevas (`playlists` y `playlist_songs`) y la base pasó a **versión 2 con migración de verdad**, no con `fallbackToDestructiveMigration`: a estas alturas ya hay canciones descargadas en el móvil y perder la librería por estrenar las listas sería un mal negocio. El SQL de `MIGRATION_1_2` tiene que coincidir **carácter a carácter** con lo que genera Room, que lo valida al abrir; la referencia está en `app/schemas/…/2.json` y se comprueba comparando con el `createSql` de ahí.

- Las **claves ajenas borran en cascada**, y de eso depende que borrar una canción la saque de todas las listas sin que nadie tenga que acordarse de limpiar a mano.
- La clave primaria compuesta de `playlist_songs` impide duplicados dentro de una lista, y el alta va con `IGNORE` para que añadir algo que ya está no sea un error.
- El puesto de una canción nueva sale de `MAX(position) + 1`, no del recuento: quitar una del medio y calcular por recuento haría que la siguiente chocara con un puesto ya usado.
- La pestaña **enseña la lista o el detalle según haya una abierta**, sin `NavHost`: con una sola pantalla de profundidad sería más andamiaje que navegación. El `BackHandler` del detalle está guardado con `!playerOpen` para que, con el reproductor abierto, atrás lo cierre a él primero.
- **Borrar una canción la saca antes de la cola** (`PlayerConnection.removeSong`). Si se quedara, el reproductor acabaría llegando a un archivo que ya no existe.

En la librería, el filtro y el orden se resuelven **en Kotlin sobre el flujo de la base**, no con consultas: la librería es pequeña y así no hay que rehacer el DAO por cada criterio. Dos detalles que sí importan:

- La búsqueda **ignora tildes** (normaliza a NFD y quita los diacríticos): en castellano, quien escribe "cancion" espera encontrar "Canción".
- El orden alfabético va con `Collator`, no con `compareBy`: comparar cadenas por code point deja la "Ñ" y todo lo acentuado detrás de la "Z".
- **La cola es lo que se está viendo**, no la librería entera: si hay un filtro puesto, se reproduce lo filtrado.

**Repositorio remoto:** https://github.com/Yoryito/Muxy — **público**, rama por defecto `master`. Se eligió público a propósito (el plan original asumía privado). El APK se reparte desde sus GitHub Releases, que al ser públicas se pueden descargar sin token.

Al ser público, todo lo que se commitee es visible: este mismo CLAUDE.md incluido. No meter nunca el keystore de release, `keystore.properties` ni `local.properties` (ya están en `.gitignore`).

### Cómo está montada la descarga

`DownloadQueue` encola un `DownloadWorker` (WorkManager, en primer plano) por vídeo, con nombre único `download-<videoId>` y política `KEEP`: eso impide duplicar una descarga en marcha pero permite reintentar una que ya terminó. El estado que ve la interfaz **no se guarda en ningún sitio propio** — sale de `WorkManager.getWorkInfosFlow`, que persiste solo y por tanto sobrevive a cerrar la app.

Decisiones que conviene no deshacer:

- **La pista se resuelve dentro del worker**, no al pulsar el botón. Las URLs de googlevideo van firmadas y caducan en unas horas; resolver en la pantalla y descargar más tarde daría un 403 incomprensible.
- **El archivo se cocina en `MusicLibrary.stagingDir` y solo al final se renombra** a la carpeta de música. `sync()` da de alta cualquier audio que encuentre ahí, así que un archivo a medio convertir se registraría como canción. El renombrado es atómico porque las dos carpetas están en el mismo sistema de archivos.
- **Solo se reintenta el fallo de red.** Un captcha o una extracción rota no mejoran reintentando, y reintentar el captcha empeora el bloqueo.
- `setForeground` va envuelto en `runCatching`: si Android no deja promocionar el worker (pasa al arrancar desde segundo plano en Android 12+), la descarga sigue, solo pierde la notificación. Y hay que declarar a mano `SystemForegroundService` con `foregroundServiceType="dataSync"` en el manifest, porque WorkManager lo declara sin tipo y desde Android 14 eso lanza excepción.
- Los avances a la notificación llevan freno de tiempo (`MIN_REPORT_INTERVAL_MS`): el sistema estrangula las notificaciones que se refrescan demasiado seguido.
- **`TrackNaming` limpia los títulos de YouTube** ("Artista - Canción (Official Video) [4K]") de forma deliberadamente conservadora: solo quita paréntesis que contengan una palabra de su lista de ruido, para no cargarse `(feat. X)`, `(Remix)` ni `(Acústico)`, que sí son parte del nombre. Los canales autogenerados se llaman `Artista - Topic` y son la mejor fuente de artista que hay.
- Las miniaturas son 16:9 y las carátulas se pintan cuadradas, así que `CoverArt` recorta al centro antes de guardar.

**Truco de pruebas:** `MusicLibrary.sync()` da de alta cualquier audio que aparezca en la carpeta de música de la app, así que se pueden meter canciones sin pasar por la descarga:

```bash
adb push "cancion.wav" /sdcard/Android/data/com.muxy.app.debug/files/music/
```

## Pochi

La mascota se llama **Pochi** y va acompañada de un caracol que se apoya en el suelo **a su izquierda** (antes lo llevaba en la cabeza; se movió al lado a petición del propietario). Está dibujada en Canvas (`ui/components/Pochi.kt`), no como vector estático, porque así se animan las partes por separado.

**El dibujo copia una referencia concreta** que dio el propietario: rana kawaii de contorno cerrado. Si algún día hay que retocarlo, el criterio es acercarse más a esa referencia, no reinterpretarla. Sus rasgos, y las proporciones medidas sobre ella (todas relativas al ancho del cuerpo `W`):

| Rasgo | Valor |
|---|---|
| alto del cuerpo | 0,87·W — claramente más ancho que alto |
| radio del bulto del ojo | 0,10·W, centros a ±0,35·W |
| centro del ojo bajo la coronilla | 0,02·W |
| centro de la boca bajo la coronilla | 0,067·W, ancho 0,18·W |
| tripa | ancho 0,50·W, de 0,28·W bajo la coronilla hasta casi el borde de abajo |
| grosor del contorno | 0,016·W |

Estilo: relleno **plano** (el degradado vertical es casi imperceptible a propósito) y **contorno oscuro y marcado** en un teal apagado emparentado con el `Pond` del tema. **La expresividad la lleva el movimiento, no el detalle del dibujo** — por eso no hace falta más definición en la cara.

Reglas que costó descubrir y conviene no romper:

- **Lo que la hace leer como rana y no como una bola** son tres cosas juntas: los ojos montados como bultos que sobresalen por encima de la cabeza, el cuerpo más ancho que alto, y la boca ondulada. Sin los bultos vuelve a parecer una pelota con ojos.
- El bulto tiene que **solaparse con la coronilla en torno a un tercio de su diámetro**. Con menos, la muesca se abre y los ojos parecen dos círculos posados encima en vez de montados.
- Cuerpo, bultos y patas se pintan como **una sola silueta** unida con `PathOperation.Union`. Dibujarlos por separado deja el contorno de cada bulto cruzando la cabeza y el de cada pata cruzando la tripa.
- El cuerpo son **curvas a mano, no un rectángulo redondeado**: hacen falta a la vez costados abombados y un fondo ancho y plano, y un radio único no da las dos cosas.
- La **tripa termina en base ancha**, casi rozando el contorno de abajo. Rematada en punta de huevo, la masa clara acaba demasiado arriba y sobra verde en la mitad inferior. Rematada plana del todo parece un babero.
- El **ojo es anillo amarillo + pupila grande y plana, sin brillo blanco**: el brillo rompe el estilo plano del resto.
- Las patas son **lóbulos que asoman por el borde de abajo**, cada uno con un pliegue que arranca del contorno y sube escorándose hacia fuera. Sin el pliegue son dos bultos sueltos; demasiado fino o largo, parece un arañazo. (Antes eran óvalos abiertos a los lados; la referencia las lleva recogidas.)
- **Sin coloretes**: la referencia no los tiene y añadirlos vuelve a alejar el dibujo de ella.
- El párpado del parpadeo se pinta con **el mismo `Brush` del cuerpo** (`bodyBrush()`), no con un color fijo, para que no se vea un escalón dentro del bulto.
- El caracol se dibuja **fuera de la escala del respiro** de la rana: compartiéndola parecerían la misma pieza en vez de dos bichos.
- Las animaciones (flotar, respirar, parpadear, y el vaivén propio del caracol) usan **periodos distintos a propósito** para que nunca caigan en fase y el bucle no se note.
- El encuadre no es `(0,0)-(DW,DH)` sino el rectángulo `VIEW_*`, que ciñe el dibujo: si no, el aire sobrante encoge la escena al centrarla. La escena es apaisada, así que `PochiEmptyState` la pinta a ancho completo con alto fijo en vez de en una caja cuadrada.
- Las dos poses comparten el mismo cuerpo, para que sea reconociblemente la misma rana. En `Resting` el nenúfar sostiene a los dos; en `Curious` el junco va a la derecha, lejos del caracol.
