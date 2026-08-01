#!/usr/bin/env python3
"""Build Ghamidi (Al-Bayan) and Islahi (Tadabbur-i-Quran) tafsir assets.

These two works are NOT available in any structured per-ayah API, so they are
curated offline from their published text and aligned to ruku (section)
boundaries, matching the works' own section-based organisation.

Input format (one file per surah):  tafsir_sources/<slug>/surah_<n>.txt

    [1:5]
    Tafsir text for ayahs 1 to 5.
    Multiple paragraphs allowed.

    [6:11]
    Tafsir text for ayahs 6 to 11.

    [12]
    Tafsir text for a single ayah 12.

Ranges must be inside the surah's ayah count (printed as a reference template
by the --template flag). Blocks that only cover part of a surah are fine;
ayahs without a block simply have no tafsir in the app.

Output: app/src/main/assets/tafsir/<slug>/surah_<n>.json  (same block format as
fetch_tafsir.py: {"surah": n, "blocks": [{"start": s, "end": e, "text": t}]})

Usage:
    python3 build_ghamidi_islahi.py                 # build everything found
    python3 build_ghamidi_islahi.py --template islahi  # print ruku ranges to fill in
"""
import json
import os
import re
import sys

import requests

BASE = "https://api.quran.com/api/v4"
SRC_ROOT = "tafsir_sources"
OUT_ROOT = "app/src/main/assets/tafsir"

SOURCES = ["ghamidi", "islahi"]

session = requests.Session()
session.headers.update({"Accept": "application/json"})


def ayah_counts():
    chapters = session.get(f"{BASE}/chapters", timeout=30).json()["chapters"]
    return {c["id"]: c["verses_count"] for c in chapters}


def ruku_ranges(chapter, total):
    ranges = []
    current = None
    start = None
    page = 1
    while True:
        result = session.get(
            f"{BASE}/verses/by_chapter/{chapter}",
            params={"fields": "ruku_number", "per_page": 300, "page": page},
            timeout=60,
        )
        if result.status_code != 200:
            return ranges
        data = result.json()
        for v in data.get("verses", []):
            ruku = v.get("ruku_number")
            if ruku is None:
                continue
            verse_no = int(v["verse_key"].split(":")[1])
            if current is None:
                current, start = ruku, verse_no
            elif ruku != current:
                ranges.append((start, verse_no - 1))
                current, start = ruku, verse_no
        pagination = data.get("pagination", {})
        if pagination.get("current_page") >= pagination.get("total_pages", 1):
            break
        page = pagination["current_page"] + 1
    if current is not None:
        ranges.append((start, total))
    return ranges


def print_template(slug, counts):
    os.makedirs(SRC_ROOT, exist_ok=True)
    for n in range(1, 115):
        ranges = ruku_ranges(n, counts[n])
        lines = [f"# سورہ {n} — {counts[n]} آیات"]
        for s, e in ranges:
            lines.append(f"[{s}:{e}]")
            lines.append("")
        path = os.path.join(SRC_ROOT, slug, f"surah_{n}.txt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if not os.path.exists(path):
            with open(path, "w", encoding="utf-8") as f:
                f.write("\n".join(lines) + "\n")
        print(f"template written: {path}")


def parse_blocks(text, total):
    blocks = []
    cur_start = None
    cur_lines = []
    for raw in text.splitlines():
        line = raw.strip()
        m = re.match(r"^\[(\d+)(?::(\d+))?\]$", line)
        if m:
            if cur_start is not None:
                blocks.append((cur_start, cur_end, "\n".join(cur_lines).strip()))
            cur_start = int(m.group(1))
            cur_end = int(m.group(2)) if m.group(2) else cur_start
            cur_lines = []
        elif cur_start is not None:
            cur_lines.append(line)
    if cur_start is not None:
        blocks.append((cur_start, cur_end, "\n".join(cur_lines).strip()))
    validated = []
    for s, e, t in blocks:
        if s < 1 or e > total or s > e:
            print(f"  WARN: bad range [{s}:{e}] (surah has {total} ayahs) - skipped")
            continue
        if t:
            validated.append({"start": s, "end": e, "text": t})
    return validated


def build_one(slug, counts):
    srcdir = os.path.join(SRC_ROOT, slug)
    if not os.path.isdir(srcdir):
        print(f"  no source dir {srcdir}, skipping")
        return 0
    made = 0
    for n in range(1, 115):
        src = os.path.join(srcdir, f"surah_{n}.txt")
        if not os.path.exists(src):
            continue
        with open(src, encoding="utf-8") as f:
            blocks = parse_blocks(f.read(), counts[n])
        outdir = os.path.join(OUT_ROOT, slug)
        os.makedirs(outdir, exist_ok=True)
        with open(os.path.join(outdir, f"surah_{n}.json"), "w", encoding="utf-8") as f:
            json.dump({"surah": n, "blocks": blocks}, f, ensure_ascii=False, indent=1)
        made += 1
        print(f"  built surah_{n}.json ({len(blocks)} blocks)")
    return made


def main():
    counts = ayah_counts()
    if "--template" in sys.argv:
        idx = sys.argv.index("--template")
        slug = sys.argv[idx + 1]
        if slug not in SOURCES:
            print(f"unknown source '{slug}', expected one of {SOURCES}")
            return 1
        print_template(slug, counts)
        return 0
    for slug in SOURCES:
        print(f"### {slug}")
        build_one(slug, counts)
    print("Done!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
