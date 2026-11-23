#!/usr/bin/env python3
"""Generate Hirz test data on device/emulator storage via adb push.

Phase 0 creates this file; Phase 12/earlier phases will extend it as needed.
By default generates a small starter set; full set is generated with --full.
"""
from __future__ import annotations

import argparse
import os
import random
import shutil
import string
import subprocess
import tempfile
from pathlib import Path

MARKER_PREFIX = "HIRZ-TEST-"


def marker() -> str:
    return MARKER_PREFIX + "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(12))


def run(cmd: list[str]) -> None:
    subprocess.check_call(cmd)


def ensure_dir(p: Path) -> None:
    p.mkdir(parents=True, exist_ok=True)


def make_text_file(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")


def make_binary_marker(path: Path, size: int, header: bytes = b"") -> None:
    m = marker().encode()
    body = os.urandom(max(0, size - len(header) - len(m) - 2))
    path.write_bytes(header + m + b"\n" + body)


def gen_images(out: Path, count: int) -> None:
    ensure_dir(out)
    try:
        from PIL import Image, PngImagePlugin  # type: ignore
    except Exception:
        Image = None
    for i in range(count):
        p = out / f"img_{i:04d}.png"
        m = marker()
        if Image:
            img = Image.new("RGB", (64, 64), ((i * 17) % 255, (i * 31) % 255, (i * 47) % 255))
            meta = PngImagePlugin.PngInfo()
            meta.add_text("HirzMarker", m)
            if i % 20 == 0:
                meta.add_text("GPS", "31.2001,29.9187")
            img.save(p, pnginfo=meta)
        else:
            make_binary_marker(p, 2048, b"\x89PNG\r\n\x1a\n")


def gen_subtitles(out: Path) -> None:
    ensure_dir(out)
    (out / "sample_utf8.srt").write_text("1\n00:00:01,000 --> 00:00:03,000\nمرحبا " + marker() + "\n", encoding="utf-8")
    (out / "sample_ar.srt").write_bytes(("1\r\n00:00:01,000 --> 00:00:03,000\r\nمرحبا " + marker() + "\r\n").encode("cp1256", errors="replace"))
    (out / "sample.vtt").write_text("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nHello " + marker() + "\n", encoding="utf-8")
    (out / "sample.ass").write_text("""[Script Info]
Title: Hirz Test
ScriptType: v4.00+

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,مرحبا %s
""" % marker(), encoding="utf-8")


def gen_misc(out_docs: Path) -> None:
    ensure_dir(out_docs)
    make_binary_marker(out_docs / "sample.pdf", 4096, b"%PDF-1.4\n%")
    make_binary_marker(out_docs / "sample.docx", 4096, b"PK\x03\x04")
    make_binary_marker(out_docs / "sample.zip", 4096, b"PK\x03\x04")


def adb_push(local: Path, remote: str) -> None:
    run(["adb", "push", str(local), remote])


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--full", action="store_true", help="Generate full Phase 12-sized dataset")
    ap.add_argument("--out", default=None, help="Local output dir (default: temp dir)")
    ap.add_argument("--push", action="store_true", help="Push generated files to connected device")
    ap.add_argument("--serial", default=None)
    args = ap.parse_args()

    base = Path(args.out) if args.out else Path(tempfile.mkdtemp(prefix="hirz-testdata-"))
    img_count = 2000 if args.full else 20
    pictures = base / "Pictures" / "HirzTest"
    movies = base / "Movies" / "HirzTest"
    music = base / "Music" / "HirzTest"
    docs = base / "Documents" / "HirzTest"
    for p in [pictures, movies, music, docs]:
        ensure_dir(p)

    gen_images(pictures, img_count)
    gen_subtitles(docs / "subtitles")
    gen_misc(docs)
    # Placeholder media files; real media generation added in later phases.
    make_binary_marker(movies / "small.mp4", 1024 * 256, b"\x00\x00\x00\x18ftypmp42")
    make_binary_marker(music / "sample.mp3", 1024 * 128, b"ID3")
    make_binary_marker(music / "sample.m4a", 1024 * 128, b"\x00\x00\x00\x18ftypM4A ")
    make_binary_marker(music / "sample.wav", 1024 * 128, b"RIFF")
    (docs / "marker.txt").write_text(marker() + "\n", encoding="utf-8")

    if args.push:
        adb = ["adb"] + (["-s", args.serial] if args.serial else [])
        for local, remote in [(pictures, "/sdcard/Pictures/HirzTest"), (movies, "/sdcard/Movies/HirzTest"), (music, "/sdcard/Music/HirzTest"), (docs, "/sdcard/Documents/HirzTest")]:
            subprocess.check_call(adb + ["push", str(local), remote])
    print(base)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
