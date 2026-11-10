#!/usr/bin/env python3
"""
Region packs (0.5): the catalogue cut into one file per prefecture and type, plus one offline map per prefecture, and
an index.json the app reads to know what it can download.

    python3 tools/packs/make_packs.py <catalogue.csv> <prefectures.json> <out dir> [--map JP-15=map-niigata.pmtiles ...]

Catalogue packs are gzip CSV with the catalogue's own header, so the app imports them the same way as a catalogue
file. A station on two lines keeps both rows in its prefecture's pack. Map packs are PMTiles cut along a widened
prefecture outline (tools/packs/README.md). Re-running with the same input gives the same files and checksums.
"""
import csv
import gzip
import hashlib
import io
import json
import os
import shutil
import sys
from datetime import date

# The kinds that come first (owner, 2026-11-11); the rest are optional downloads
TYPES = {
    "MANHOLE_CARD": ("manhole", True),
    "EKI_STAMP": ("stations", True),
    "EKITAG": ("ekitag", True),
    "DAM_CARD": ("dams", True),
    "MUSEUM_STAMP": ("museums", True),
    "ROADSIDE_STATION": ("roadside", False),
    "GOSHUIN": ("goshuin", False),
}


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main(argv):
    if len(argv) < 4:
        print(__doc__)
        return 2
    catalogue, prefs_path, out = argv[1], argv[2], argv[3]
    maps = {}
    rest = argv[4:]
    while rest:
        if rest[0] == "--map" and len(rest) > 1:
            code, path = rest[1].split("=", 1)
            maps[code] = path
            rest = rest[2:]
        else:
            print("unknown argument:", rest[0])
            return 2
    os.makedirs(out, exist_ok=True)
    prefs = {p["ja"]: p for p in json.load(open(prefs_path, encoding="utf-8"))}
    by_code = {p["code"]: p for p in prefs.values()}

    with open(catalogue, encoding="utf-8", newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        rows = list(reader)
    col = {name: i for i, name in enumerate(header)}

    groups = {}
    unknown = set()
    for r in rows:
        pref = r[col["prefecture"]].strip()
        kind = r[col["type"]].strip()
        if pref not in prefs or kind not in TYPES:
            unknown.add((pref, kind))
            continue
        groups.setdefault((prefs[pref]["code"], kind), []).append(r)
    if unknown:
        print("left out (no prefecture or type):", sorted(unknown))

    version = date.today().isoformat()
    packs = []
    for (code, kind), group in sorted(groups.items()):
        slug, core = TYPES[kind]
        name = f"{code}-{slug}.csv.gz"
        buf = io.StringIO()
        w = csv.writer(buf, lineterminator="\n")
        w.writerow(header)
        w.writerows(group)
        # mtime=0 so the same rows always give the same file and checksum
        with open(os.path.join(out, name), "wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as gz:
            gz.write(buf.getvalue().encode("utf-8"))
        path = os.path.join(out, name)
        p = by_code[code]
        packs.append({
            "id": f"{code}/{slug}", "kind": "catalogue", "type": kind, "core": core,
            "code": code, "prefecture": p["ja"], "prefecture_en": p["en"],
            # A station on two lines is two rows but one thing to collect
            "items": len({row[col["catalog_key"]] or row[col["code"]] or row[col["name_ja"]] for row in group}),
            "file": name, "bytes": os.path.getsize(path), "sha256": sha256(path),
        })

    for code, src in sorted(maps.items()):
        name = f"{code}-map.pmtiles"
        dst = os.path.join(out, name)
        if os.path.abspath(src) != os.path.abspath(dst):
            shutil.copyfile(src, dst)
        p = by_code[code]
        packs.append({
            "id": f"{code}/map", "kind": "map", "core": True, "code": code,
            "prefecture": p["ja"], "prefecture_en": p["en"],
            "file": name, "bytes": os.path.getsize(dst), "sha256": sha256(dst),
            # The outline the app uses to decide where the offline map covers (same rings as prefectures.json)
            "rings": p["rings"],
        })

    index = {"format": 1, "version": version, "packs": packs}
    with open(os.path.join(out, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, separators=(",", ":"))
    total = sum(x["bytes"] for x in packs)
    print(f"{len(packs)} packs, {total / 1048576:.1f} MB, index.json written to {out}")
    for x in packs:
        print(f"  {x['id']:18} {x['bytes'] / 1024:9.1f} KB  {x.get('items', '')}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
