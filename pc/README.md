# Muxy PC

Busca música en el ordenador, opcionalmente la identifica contra el
catálogo de Spotify (solo metadatos — Spotify tiene DRM, su audio nunca se
toca) y descarga el equivalente desde YouTube, incluyendo variantes
"extended mix" si existen. Hay dos formas de usarlo:

- **`muxy_gui.py`** (o el `.exe` empaquetado) — interfaz gráfica: buscas,
  preescuchas el resultado antes de decidir y descargas con un clic.
- **`muxy_dl.py`** — la misma lógica por línea de comandos.

Ambas comparten el código en `muxy_core.py`.

Uso personal, igual que la app de Android: bajar de YouTube incumple sus
ToS y es un compromiso aceptado a propósito.

## Instalación

Necesitas Python 3.10+ y `ffmpeg`/`ffplay` en el PATH (la preescucha de la
GUI usa `ffplay` para reproducir sin descargar).

Las dependencias van en un entorno virtual **dentro de esta carpeta**, para
no tocar el Python global de la máquina (puede haber otros scripts, como
automatizaciones, que dependan de versiones concretas de paquetes):

```bash
cd pc
py -m venv .venv
./.venv/Scripts/python.exe -m pip install -r requirements.txt
```

A partir de ahí, usa siempre `./.venv/Scripts/python.exe` en vez de `py` o
`python` para ejecutar el script (ver más abajo).

## Spotify (opcional)

Sin esto el script busca directamente en YouTube con lo que escribas. Con
credenciales de Spotify, primero resuelve artista/título/álbum/carátula
reales y busca ese nombre limpio en YouTube — útil cuando el título de
YouTube es un desastre.

1. Crea una app en https://developer.spotify.com/dashboard (gratis, no
   hace falta que la app esté "en producción" para la búsqueda).
2. Copia el Client ID y el Client Secret.
3. Expórtalos antes de ejecutar el script:

```bash
export SPOTIFY_CLIENT_ID="..."
export SPOTIFY_CLIENT_SECRET="..."
```

## Uso: interfaz gráfica

```bash
./.venv/Scripts/python.exe muxy_gui.py
```

Escribe la búsqueda y pulsa Enter (o "Buscar"). Los resultados salen en una
tabla, marcados con ★ los que parecen extended mix. Doble clic o "▶
Escuchar" reproduce ~30 s directamente desde YouTube sin descargar nada
(usa `ffplay`); puedes seleccionar varias filas (Ctrl/Shift+clic) y
"⬇ Descargar seleccionadas" las baja todas. La carpeta de salida y el
formato se recuerdan entre sesiones (`gui_config.json`, no se sube al repo).

### Ejecutable (.exe)

Para no tener que lanzarlo con Python cada vez, se puede empaquetar como un
`.exe` independiente con PyInstaller:

```bash
./.venv/Scripts/python.exe -m pip install pyinstaller
./.venv/Scripts/python.exe -m PyInstaller --noconfirm --onefile --windowed --name muxy-pc muxy_gui.py
```

El resultado queda en `dist/muxy-pc.exe` (no se commitea, son ~30 MB — se
regenera cuando haga falta). Sigue necesitando `ffmpeg`/`ffplay` instalados
en la máquina; no los empaqueta.

## Uso: línea de comandos

```bash
./.venv/Scripts/python.exe muxy_dl.py "Daft Punk One More Time"
```

Lista los resultados (marcando `[EXTENDED]` los que detecta como extended
mix) y pide qué número(s) descargar. Opciones útiles:

- `--out CARPETA` — dónde guardar (por defecto `./descargas`)
- `--format mp3|m4a` — formato de salida (por defecto mp3)
- `--no-extended` — no buscar variantes extended mix, solo el resultado normal
- `--no-spotify` — ignorar Spotify aunque haya credenciales
- `--all` — descargar todo lo listado sin preguntar
