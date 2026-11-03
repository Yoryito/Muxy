#!/usr/bin/env python3
"""Muxy PC (CLI): busca una canción y descarga su audio equivalente de
YouTube, incluyendo variantes "extended mix" si las hay. La lógica vive en
muxy_core.py; esto es solo la interfaz de línea de comandos. Para una
interfaz con preescucha, usa muxy_gui.py.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from muxy_core import (
    build_filename,
    download,
    gather_candidates,
    lookup_spotify,
    tag_file,
)


def print_candidates(candidates) -> None:
    print()
    for i, c in enumerate(candidates, start=1):
        tag = "[EXTENDED]" if c.is_extended else "          "
        print(f"{i:>2}. {tag} {c.format_duration():>5}  {c.uploader[:25]:<25}  {c.title}")
    print()


def parse_selection(raw: str, total: int) -> list[int]:
    indices: list[int] = []
    for part in raw.split(","):
        part = part.strip()
        if not part:
            continue
        idx = int(part)
        if not (1 <= idx <= total):
            raise ValueError(f"{idx} fuera de rango (1-{total})")
        indices.append(idx - 1)
    return indices


def main() -> None:
    parser = argparse.ArgumentParser(description="Muxy PC: busca y descarga audio desde YouTube.")
    parser.add_argument("query", help="Qué buscar, ej. \"Daft Punk One More Time\"")
    parser.add_argument("--out", default="descargas", help="Carpeta de salida (por defecto ./descargas)")
    parser.add_argument("--format", choices=["mp3", "m4a"], default="mp3", help="Formato de salida (por defecto mp3)")
    parser.add_argument("--limit", type=int, default=6, help="Resultados por búsqueda (por defecto 6)")
    parser.add_argument("--no-extended", action="store_true", help="No buscar variantes extended mix")
    parser.add_argument("--no-spotify", action="store_true", help="No consultar Spotify aunque haya credenciales")
    parser.add_argument("--all", action="store_true", help="Descargar todos los resultados listados sin preguntar")
    args = parser.parse_args()

    meta = None if args.no_spotify else lookup_spotify(args.query)
    if meta and meta.query:
        print(f"Spotify: {meta.artist} - {meta.title}" + (f" ({meta.album})" if meta.album else ""))
        base_query = meta.query
    else:
        base_query = args.query

    print(f"Buscando en YouTube: \"{base_query}\"...")
    candidates = gather_candidates(base_query, args.limit, include_extended=not args.no_extended)
    if not candidates:
        print("Sin resultados.")
        return

    print_candidates(candidates)

    if args.all:
        selection = list(range(len(candidates)))
    else:
        raw = input("Elige qué descargar (ej. 1 o 1,3) o Enter para cancelar: ").strip()
        if not raw:
            print("Cancelado.")
            return
        try:
            selection = parse_selection(raw, len(candidates))
        except ValueError as exc:
            print(f"Selección inválida: {exc}")
            return

    out_dir = Path(args.out)
    for idx in selection:
        candidate = candidates[idx]
        filename = build_filename(meta, candidate)
        print(f"\nDescargando: {candidate.title}  ->  {filename}.{args.format}")
        try:
            path = download(candidate, out_dir, filename, args.format)
            tag_file(path, meta, candidate, args.format)
            print(f"Listo: {path}")
        except Exception as exc:
            print(f"Fallo con '{candidate.title}': {exc}", file=sys.stderr)


if __name__ == "__main__":
    main()
