"""Lógica compartida por la CLI (muxy_dl.py) y la interfaz gráfica
(muxy_gui.py): búsqueda en YouTube, identificación opcional vía Spotify
(solo metadatos), descarga y etiquetado.

Spotify está protegido con DRM: aquí solo se usa su API de búsqueda para
sacar artista/título/álbum/carátula correctos. El audio siempre sale de
YouTube.
"""

from __future__ import annotations

import os
import re
import shutil
import sys
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

try:
    # En máquinas con un proxy que intercepta TLS, el almacén de certificados
    # de Windows confía en él pero el de Python (certifi) no. truststore hace
    # que ssl use el de Windows en su lugar.
    import truststore

    truststore.inject_into_ssl()
except ImportError:
    pass

import yt_dlp

NOISE_WORDS = (
    "official", "video", "audio", "lyrics", "lyric", "hd", "hq", "4k",
    "mv", "visualizer", "clip", "oficial", "letra",
)

EXTENDED_HINTS = ("extended", "extendido")


def strip_noise_parens(title: str) -> str:
    """Quita paréntesis/corchetes que solo contienen ruido (official video,
    4K...), igual de conservador que TrackNaming.kt: nunca toca feat./remix/
    acoustic/extended porque esas sí son parte del nombre."""

    def should_strip(inner: str) -> bool:
        lowered = inner.lower()
        return any(word in lowered for word in NOISE_WORDS)

    def repl(match: re.Match) -> str:
        inner = match.group(1) or match.group(2)
        return "" if should_strip(inner) else match.group(0)

    cleaned = re.sub(r"\(([^()]*)\)|\[([^\[\]]*)\]", repl, title)
    return re.sub(r"\s{2,}", " ", cleaned).strip(" -")


def normalize(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn").lower()


@dataclass
class Candidate:
    video_id: str
    title: str
    uploader: str
    duration: Optional[int]
    is_extended: bool
    url: str = field(init=False)

    def __post_init__(self) -> None:
        self.url = f"https://www.youtube.com/watch?v={self.video_id}"

    def format_duration(self) -> str:
        if not self.duration:
            return "?:??"
        minutes, seconds = divmod(int(self.duration), 60)
        return f"{minutes}:{seconds:02d}"


@dataclass
class TrackMeta:
    artist: Optional[str] = None
    title: Optional[str] = None
    album: Optional[str] = None
    cover_url: Optional[str] = None

    @property
    def query(self) -> Optional[str]:
        if self.artist and self.title:
            return f"{self.artist} {self.title}"
        return None


def lookup_spotify(query: str) -> Optional[TrackMeta]:
    client_id = os.environ.get("SPOTIFY_CLIENT_ID")
    client_secret = os.environ.get("SPOTIFY_CLIENT_SECRET")
    if not client_id or not client_secret:
        return None

    try:
        import spotipy
        from spotipy.oauth2 import SpotifyClientCredentials
    except ImportError:
        print("spotipy no está instalado (pip install spotipy); se omite Spotify.", file=sys.stderr)
        return None

    try:
        client = spotipy.Spotify(
            auth_manager=SpotifyClientCredentials(
                client_id=client_id, client_secret=client_secret
            )
        )
        results = client.search(q=query, type="track", limit=1)
        items = results.get("tracks", {}).get("items", [])
        if not items:
            return None
        track = items[0]
        images = track.get("album", {}).get("images", [])
        return TrackMeta(
            artist=track["artists"][0]["name"] if track.get("artists") else None,
            title=track.get("name"),
            album=track.get("album", {}).get("name"),
            cover_url=images[0]["url"] if images else None,
        )
    except Exception as exc:  # credenciales inválidas, red caída, etc.
        print(f"Aviso: búsqueda en Spotify falló ({exc}); sigo solo con YouTube.", file=sys.stderr)
        return None


def search_youtube(query: str, limit: int) -> list[dict]:
    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": "in_playlist",
        "default_search": "ytsearch",
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(f"ytsearch{limit}:{query}", download=False)
    return info.get("entries") or []


def gather_candidates(base_query: str, limit: int, include_extended: bool) -> list[Candidate]:
    seen: dict[str, Candidate] = {}

    def add_entries(entries: list[dict], force_extended: bool) -> None:
        for entry in entries:
            if not entry or not entry.get("id"):
                continue
            vid = entry["id"]
            if vid in seen:
                if force_extended:
                    seen[vid].is_extended = True
                continue
            title = entry.get("title", "")
            is_extended = force_extended or any(
                hint in normalize(title) for hint in EXTENDED_HINTS
            )
            seen[vid] = Candidate(
                video_id=vid,
                title=title,
                uploader=entry.get("uploader") or entry.get("channel") or "?",
                duration=entry.get("duration"),
                is_extended=is_extended,
            )

    add_entries(search_youtube(base_query, limit), force_extended=False)

    if include_extended:
        for suffix in ("extended mix", "extended version"):
            add_entries(
                search_youtube(f"{base_query} {suffix}", max(3, limit // 2)),
                force_extended=True,
            )

    return list(seen.values())


def split_artist_title(raw_title: str) -> tuple[Optional[str], str]:
    """Sin Spotify, el único dato de artista que hay es el patrón
    "Artista - Canción" del propio título de YouTube (igual que
    TrackNaming.kt hace con los canales "Artista - Topic")."""
    cleaned = strip_noise_parens(raw_title)
    if " - " in cleaned:
        artist, _, title = cleaned.partition(" - ")
        artist, title = artist.strip(), title.strip()
        if artist and title:
            return artist, title
    return None, cleaned


def build_filename(meta: Optional[TrackMeta], candidate: Candidate) -> str:
    if meta and meta.artist and meta.title:
        name = f"{meta.artist} - {meta.title}"
        if candidate.is_extended and "extended" not in name.lower():
            name += " (Extended Mix)"
    else:
        name = strip_noise_parens(candidate.title)
    safe = re.sub(r'[\\/:*?"<>|]', "_", name).strip()
    return safe or candidate.video_id


def find_ffmpeg_bin(name: str) -> Optional[str]:
    """Busca ffmpeg/ffplay en el PATH y, si no está, en la ruta típica de
    winget — por si el proceso que lanza esto (p.ej. un .exe empaquetado
    abierto con doble clic) no hereda el PATH completo del usuario."""
    exe = shutil.which(name)
    if exe:
        return exe
    localdata = os.environ.get("LOCALAPPDATA")
    if localdata:
        base = Path(localdata) / "Microsoft" / "WinGet" / "Packages"
        if base.is_dir():
            for match in base.glob(f"Gyan.FFmpeg_*/**/{name}.exe"):
                return str(match)
    return None


def resolve_stream(candidate: Candidate) -> dict:
    """Saca la URL de audio directa (y sus cabeceras) sin descargar nada,
    para la preescucha."""
    opts = {
        "quiet": True,
        "no_warnings": True,
        "format": "bestaudio/best",
        "skip_download": True,
        "noplaylist": True,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        return ydl.extract_info(candidate.url, download=False)


def download(candidate: Candidate, out_dir: Path, filename: str, audio_format: str,
             progress_hook=None) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    target_base = out_dir / filename
    opts = {
        "quiet": progress_hook is not None,
        "no_warnings": True,
        "format": "bestaudio/best",
        "outtmpl": str(target_base) + ".%(ext)s",
        "postprocessors": [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": audio_format,
                "preferredquality": "192",
            }
        ],
        "noplaylist": True,
    }
    ffmpeg_bin = find_ffmpeg_bin("ffmpeg")
    if ffmpeg_bin:
        opts["ffmpeg_location"] = ffmpeg_bin
    if progress_hook:
        opts["progress_hooks"] = [progress_hook]
    with yt_dlp.YoutubeDL(opts) as ydl:
        ydl.download([candidate.url])
    final_path = target_base.with_suffix(f".{audio_format}")
    if not final_path.exists():
        raise FileNotFoundError(f"yt-dlp no dejó el archivo esperado en {final_path}")
    return final_path


def fetch_cover_bytes(url: Optional[str]) -> Optional[bytes]:
    if not url:
        return None
    try:
        import urllib.request

        with urllib.request.urlopen(url, timeout=10) as resp:
            return resp.read()
    except Exception as exc:
        print(f"Aviso: no se pudo bajar la carátula ({exc}).", file=sys.stderr)
        return None


def tag_file(path: Path, meta: Optional[TrackMeta], candidate: Candidate, audio_format: str) -> None:
    if meta:
        artist, title, album = meta.artist, meta.title, meta.album
    else:
        artist, title = split_artist_title(candidate.title)
        album = None
    cover = fetch_cover_bytes(meta.cover_url if meta else None)

    if audio_format == "mp3":
        from mutagen.id3 import ID3, ID3NoHeaderError, TPE1, TIT2, TALB, APIC
        from mutagen.mp3 import MP3

        audio = MP3(path)
        try:
            tags = audio.tags or ID3()
        except ID3NoHeaderError:
            tags = ID3()
        if artist:
            tags.add(TPE1(encoding=3, text=artist))
        if title:
            tags.add(TIT2(encoding=3, text=title))
        if album:
            tags.add(TALB(encoding=3, text=album))
        if cover:
            tags.add(APIC(encoding=3, mime="image/jpeg", type=3, desc="Cover", data=cover))
        tags.save(path)
    elif audio_format in ("m4a", "aac"):
        from mutagen.mp4 import MP4, MP4Cover

        audio = MP4(path)
        if artist:
            audio["\xa9ART"] = [artist]
        if title:
            audio["\xa9nam"] = [title]
        if album:
            audio["\xa9alb"] = [album]
        if cover:
            audio["covr"] = [MP4Cover(cover, imageformat=MP4Cover.FORMAT_JPEG)]
        audio.save()
    else:
        print(f"Aviso: no sé etiquetar .{audio_format}, se deja sin tags.", file=sys.stderr)
