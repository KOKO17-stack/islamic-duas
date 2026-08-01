#!/usr/bin/env python3
"""Fetch per-ayah tafsir from api.quran.com for the 4 available Urdu sources.

Writes range-based block files to app/src/main/assets/tafsir/<slug>/surah_<n>.json
so the app can lazy-load one surah at a time.

Each output file:
{
  "surah": <int>,
  "blocks": [ {"start": <int>, "end": <int>, "text": "<string>"}, ... ]
}
A block covers ayahs start..end inclusive. Block-based tafsirs (Ibn Kathir,
Tazkirul) cover a whole verse-range in one entry; the entry's verse_key is the
start of the range, and the range ends at (next entry verse - 1).

Usage: python3 fetch_tafsir.py
"""
import json
import os
import re
import sys
import time

import requests

BASE = "https://api.quran.com/api/v4"
OUT_ROOT = "app/src/main/assets/tafsir"

SOURCES = [
    (160, "ibn_kathir"),
    (157, "fi_zilal"),
    (159, "bayan_ul_quran"),
    (818, "tazkirul"),
]

session = requests.Session()
session.headers.update({"Accept": "application/json"})


def fetch_all_retry(url, params=None, max_retries=5):
    for attempt in range(max_retries):
        try:
            r = session.get(url, params=params, timeout=120)
            r.raise_for_status()
            return r.json()
        except Exception as e:
            print(f"  retry {attempt + 1}/{max_retries}: {e}")
            time.sleep(3)
    return None


def html_to_text(html):
    s = html
    s = re.sub(r"</?(p|div|br|li|h[1-6])[^>]*>", "\n", s, flags=re.I)
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("\u200b", "").replace("\u200e", "").replace("\u200f", "")
    s = re.sub(r"\n\s*\n+", "\n\n", s)
    s = re.sub(r"[ \t]+", " ", s)
    return s.strip()


def main():
    chapters = session.get(f"{BASE}/chapters", timeout=30).json()["chapters"]
    counts = {c["id"]: c["verses_count"] for c in chapters}

    for tid, slug in SOURCES:
        print(f"\n### {slug} (tafsir id {tid})")
        for n in range(1, 115):
            sys.stdout.write(f"\r  {slug}: {n}/114")
            sys.stdout.flush()
            total = counts[n]
            result = fetch_all_retry(f"{BASE}/tafsirs/{tid}/by_chapter/{n}")
            entries = []
            if result:
                for t in result.get("tafsirs", []):
                    vk = t.get("verse_key", "")
                    if ":" not in vk:
                        continue
                    verse = int(vk.split(":")[1])
                    text = html_to_text(t.get("text", ""))
                    if text:
                        entries.append((verse, text))
            entries.sort(key=lambda x: x[0])
            blocks = []
            for i, (verse, text) in enumerate(entries):
                end = (entries[i + 1][0] - 1) if i + 1 < len(entries) else total
                blocks.append({"start": verse, "end": max(end, verse), "text": text})
            outdir = os.path.join(OUT_ROOT, slug)
            os.makedirs(outdir, exist_ok=True)
            with open(os.path.join(outdir, f"surah_{n}.json"), "w", encoding="utf-8") as f:
                json.dump({"surah": n, "blocks": blocks}, f, ensure_ascii=False, indent=1)
            time.sleep(0.3)

    print("\nDone!")


if __name__ == "__main__":
    main()
