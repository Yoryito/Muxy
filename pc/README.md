# Muxy PC

Script de línea de comandos para bajar música en el ordenador: busca una
canción, opcionalmente la identifica contra el catálogo de Spotify (solo
metadatos — Spotify tiene DRM, su audio nunca se toca) y descarga el
equivalente desde YouTube, incluyendo variantes "extended mix" si existen.

Uso personal, igual que la app de Android: bajar de YouTube incumple sus
ToS y es un compromiso aceptado a propósito.

## Instalación

Necesitas Python 3.10+ y `ffmpeg` en el PATH (o indicado a yt-dlp).

```bash
cd pc
py -m pip install -r requirements.txt
```

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

## Uso

```bash
py muxy_dl.py "Daft Punk One More Time"
```

Lista los resultados (marcando `[EXTENDED]` los que detecta como extended
mix) y pide qué número(s) descargar. Opciones útiles:

- `--out CARPETA` — dónde guardar (por defecto `./descargas`)
- `--format mp3|m4a` — formato de salida (por defecto mp3)
- `--no-extended` — no buscar variantes extended mix, solo el resultado normal
- `--no-spotify` — ignorar Spotify aunque haya credenciales
- `--all` — descargar todo lo listado sin preguntar
