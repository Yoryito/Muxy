#!/usr/bin/env python3
"""Muxy PC (interfaz gráfica): buscar, preescuchar y descargar sin tocar la
línea de comandos. La lógica de búsqueda/descarga vive en muxy_core.py."""

from __future__ import annotations

import json
import os
import subprocess
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, ttk

import muxy_core as core

CONFIG_PATH = Path(__file__).resolve().parent / "gui_config.json"
DEFAULT_OUT = Path(__file__).resolve().parent / "descargas"
PREVIEW_SECONDS = "30"


def load_config() -> dict:
    try:
        return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


def save_config(data: dict) -> None:
    try:
        CONFIG_PATH.write_text(json.dumps(data), encoding="utf-8")
    except Exception:
        pass


class MuxyGUI:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        root.title("Muxy PC")
        root.geometry("920x580")
        root.minsize(680, 420)

        config = load_config()
        self.candidates: list[core.Candidate] = []
        self.meta: core.TrackMeta | None = None
        self.preview_proc: subprocess.Popen | None = None

        self.out_dir = tk.StringVar(value=config.get("out_dir") or str(DEFAULT_OUT))
        self.audio_format = tk.StringVar(value=config.get("format", "mp3"))
        self.use_extended = tk.BooleanVar(value=True)

        spotify_available = bool(
            os.environ.get("SPOTIFY_CLIENT_ID") and os.environ.get("SPOTIFY_CLIENT_SECRET")
        )
        self.use_spotify = tk.BooleanVar(value=spotify_available)
        self._spotify_available = spotify_available

        self._build_ui()

    # --- construcción de la interfaz -----------------------------------

    def _build_ui(self) -> None:
        top = ttk.Frame(self.root, padding=8)
        top.pack(fill="x")
        ttk.Label(top, text="Buscar:").pack(side="left")
        self.search_entry = ttk.Entry(top)
        self.search_entry.pack(side="left", fill="x", expand=True, padx=6)
        self.search_entry.bind("<Return>", lambda _e: self.on_search())
        self.search_entry.focus_set()
        self.search_btn = ttk.Button(top, text="Buscar", command=self.on_search)
        self.search_btn.pack(side="left")

        opts = ttk.Frame(self.root, padding=(8, 0))
        opts.pack(fill="x")
        ttk.Checkbutton(opts, text="Extended mixes", variable=self.use_extended).pack(side="left")
        spotify_cb = ttk.Checkbutton(
            opts, text="Identificar con Spotify", variable=self.use_spotify
        )
        spotify_cb.pack(side="left", padx=(12, 0))
        if not self._spotify_available:
            spotify_cb.state(["disabled"])
            ttk.Label(
                opts, text="(sin SPOTIFY_CLIENT_ID/SECRET)", foreground="#888"
            ).pack(side="left", padx=(4, 0))

        ttk.Label(opts, text="Formato:").pack(side="left", padx=(16, 2))
        ttk.Combobox(
            opts, textvariable=self.audio_format, values=["mp3", "m4a"], width=6, state="readonly"
        ).pack(side="left")

        columns = ("ext", "dur", "canal", "titulo")
        self.tree = ttk.Treeview(self.root, columns=columns, show="headings", selectmode="extended")
        self.tree.heading("ext", text="")
        self.tree.heading("dur", text="Dur.")
        self.tree.heading("canal", text="Canal")
        self.tree.heading("titulo", text="Título")
        self.tree.column("ext", width=30, anchor="center")
        self.tree.column("dur", width=55, anchor="center")
        self.tree.column("canal", width=180)
        self.tree.column("titulo", width=520)
        self.tree.pack(fill="both", expand=True, padx=8, pady=6)
        self.tree.bind("<Double-1>", lambda _e: self.on_preview())

        actions = ttk.Frame(self.root, padding=8)
        actions.pack(fill="x")
        self.preview_btn = ttk.Button(actions, text="▶ Escuchar", command=self.on_preview)
        self.preview_btn.pack(side="left")
        self.stop_btn = ttk.Button(
            actions, text="⏹ Detener", command=self.on_stop_preview, state="disabled"
        )
        self.stop_btn.pack(side="left", padx=(6, 0))
        self.download_btn = ttk.Button(
            actions, text="⬇ Descargar seleccionadas", command=self.on_download
        )
        self.download_btn.pack(side="left", padx=(20, 0))

        outrow = ttk.Frame(self.root, padding=(8, 0, 8, 8))
        outrow.pack(fill="x")
        ttk.Label(outrow, text="Carpeta:").pack(side="left")
        ttk.Entry(outrow, textvariable=self.out_dir).pack(side="left", fill="x", expand=True, padx=6)
        ttk.Button(outrow, text="Elegir...", command=self.on_choose_dir).pack(side="left")

        self.status_var = tk.StringVar(value="Escribe una búsqueda y pulsa Enter.")
        ttk.Label(self.root, textvariable=self.status_var, anchor="w", padding=(8, 4)).pack(fill="x")

    # --- helpers de hilo -------------------------------------------------

    def set_status(self, text: str) -> None:
        self.root.after(0, lambda: self.status_var.set(text))

    def _selected_candidates(self) -> list[core.Candidate]:
        return [self.candidates[int(iid)] for iid in self.tree.selection()]

    def on_choose_dir(self) -> None:
        chosen = filedialog.askdirectory(initialdir=self.out_dir.get() or ".")
        if chosen:
            self.out_dir.set(chosen)
            save_config({"out_dir": self.out_dir.get(), "format": self.audio_format.get()})

    # --- búsqueda ---------------------------------------------------------

    def on_search(self) -> None:
        query = self.search_entry.get().strip()
        if not query:
            return
        use_spotify = self.use_spotify.get()
        use_extended = self.use_extended.get()
        self.search_btn.state(["disabled"])
        self.tree.delete(*self.tree.get_children())
        self.candidates = []
        self.meta = None
        self.set_status(f'Buscando "{query}"...')
        threading.Thread(
            target=self._search_worker, args=(query, use_spotify, use_extended), daemon=True
        ).start()

    def _search_worker(self, query: str, use_spotify: bool, use_extended: bool) -> None:
        try:
            meta = core.lookup_spotify(query) if use_spotify else None
            base_query = meta.query if (meta and meta.query) else query
            candidates = core.gather_candidates(base_query, 8, include_extended=use_extended)
        except Exception as exc:
            self.set_status(f"Error buscando: {exc}")
            self.root.after(0, lambda: self.search_btn.state(["!disabled"]))
            return
        self.root.after(0, lambda: self._populate_results(meta, candidates))

    def _populate_results(self, meta: core.TrackMeta | None, candidates: list[core.Candidate]) -> None:
        self.meta = meta
        self.candidates = candidates
        for i, c in enumerate(candidates):
            mark = "★" if c.is_extended else ""
            self.tree.insert("", "end", iid=str(i), values=(mark, c.format_duration(), c.uploader, c.title))
        note = f" — Spotify: {meta.artist} - {meta.title}" if (meta and meta.query) else ""
        self.set_status(f"{len(candidates)} resultados{note}." if candidates else "Sin resultados.")
        self.search_btn.state(["!disabled"])

    # --- preescucha ---------------------------------------------------------

    def on_preview(self) -> None:
        selected = self._selected_candidates()
        if not selected:
            self.set_status("Selecciona una canción para escucharla.")
            return
        self.on_stop_preview()
        candidate = selected[0]
        self.set_status(f"Cargando preescucha: {candidate.title}...")
        threading.Thread(target=self._preview_worker, args=(candidate,), daemon=True).start()

    def _preview_worker(self, candidate: core.Candidate) -> None:
        ffplay = core.find_ffmpeg_bin("ffplay")
        if not ffplay:
            self.set_status("No encuentro ffplay: instala ffmpeg y añádelo al PATH.")
            return
        try:
            info = core.resolve_stream(candidate)
        except Exception as exc:
            self.set_status(f"No se pudo cargar la preescucha: {exc}")
            return
        url = info.get("url")
        if not url:
            self.set_status("YouTube no dio una URL de audio reproducible.")
            return
        headers = info.get("http_headers") or {}
        cmd = [ffplay, "-nodisp", "-autoexit", "-loglevel", "quiet", "-t", PREVIEW_SECONDS]
        if headers:
            header_str = "".join(f"{k}: {v}\r\n" for k, v in headers.items())
            cmd += ["-headers", header_str]
        cmd.append(url)
        try:
            self.preview_proc = subprocess.Popen(cmd)
        except Exception as exc:
            self.set_status(f"No se pudo lanzar ffplay: {exc}")
            return
        self.root.after(0, lambda: self.stop_btn.state(["!disabled"]))
        self.set_status(f"Escuchando: {candidate.title}")
        self.preview_proc.wait()
        self.root.after(0, lambda: self.stop_btn.state(["disabled"]))
        if self.status_var.get().startswith("Escuchando"):
            self.set_status("Preescucha terminada.")

    def on_stop_preview(self) -> None:
        if self.preview_proc and self.preview_proc.poll() is None:
            self.preview_proc.terminate()
        self.preview_proc = None
        self.stop_btn.state(["disabled"])

    # --- descarga ---------------------------------------------------------

    def on_download(self) -> None:
        selected = self._selected_candidates()
        if not selected:
            self.set_status("Selecciona al menos una canción para descargar.")
            return
        self.on_stop_preview()
        out_dir = self.out_dir.get().strip() or str(DEFAULT_OUT)
        audio_format = self.audio_format.get()
        save_config({"out_dir": out_dir, "format": audio_format})
        self.download_btn.state(["disabled"])
        meta = self.meta
        threading.Thread(
            target=self._download_worker, args=(selected, meta, out_dir, audio_format), daemon=True
        ).start()

    def _download_worker(
        self,
        selected: list[core.Candidate],
        meta: core.TrackMeta | None,
        out_dir: str,
        audio_format: str,
    ) -> None:
        out_path = Path(out_dir)
        for candidate in selected:
            self.set_status(f"Descargando: {candidate.title}...")
            filename = core.build_filename(meta, candidate)
            try:
                path = core.download(candidate, out_path, filename, audio_format)
                core.tag_file(path, meta, candidate, audio_format)
                self.set_status(f"Listo: {path.name}")
            except Exception as exc:
                self.set_status(f"Fallo con '{candidate.title}': {exc}")
        self.root.after(0, lambda: self.download_btn.state(["!disabled"]))
        self.set_status(f"Descargas terminadas ({len(selected)}).")


def main() -> None:
    root = tk.Tk()
    MuxyGUI(root)
    root.mainloop()


if __name__ == "__main__":
    main()
