"""Refines the Niigata rows of the bundled catalogue (stations, Ekitag, dam cards); other prefectures are left alone.

    python tools/trip-catalog/refine_niigata.py app/src/main/assets/catalogue/trip-niigata.csv
    python tools/trip-catalog/refine_niigata.py app/src/main/assets/catalogue/trip-niigata.csv --check

Curated data in data/niigata-*.json, the evidence behind it in sources/. Rerunnable: each step skips what is done.
- niigata-stations.json: new stations, inserted into their line after a named station (later stations on that line
  move up one line_seq; the key is name_ja, so finds are kept), and the "no stamp" note for stations listed there.
- niigata-ekitag.json: one EKITAG row per station, copied from the station row (name, romaji, position), with no
  line columns. Added after the last EKI_STAMP row.
- niigata-dams.json: nothing is written; --check compares the DAM_CARD rows with the MLIT snapshot.

--check writes nothing. It prints station rows more than 300 m from their OSM node, dam rows more than 300 m from
an OSM dam of the same name, and dam fields that differ from the MLIT list.
"""
import csv
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).parent
PREF = "新潟県"
OSM_NOTE = "Map data © OpenStreetMap contributors."


def load(name):
    return json.loads((HERE / name).read_text(encoding="utf-8"))


def metres(lat1, lng1, lat2, lng2):
    return 6371000 * math.hypot(math.radians(lat2 - lat1), math.radians(lng2 - lng1) * math.cos(math.radians(lat1)))


def read(path):
    with open(path, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        return reader.fieldnames, list(reader)


def key(r):
    return f"{r['type']}:{r['code'] or r['name_ja']}"


def add_stations(cols, rows, data):
    added = 0
    for s in data["new_stations"]:
        line = [r for r in rows if r["line_id"] == s["line_id"]]
        if any(r["name_ja"] == s["name_ja"] for r in line):
            continue
        after = next(r for r in line if r["name_ja"] == s["after"])
        seq = int(after["line_seq"]) + 1
        for r in line:
            if int(r["line_seq"]) >= seq:
                r["line_seq"] = str(int(r["line_seq"]) + 1)
        row = {c: "" for c in cols}
        row.update({k: after[k] for k in ("type", "prefecture", "line_id", "line_name_ja", "line_name_romaji")})
        row.update(name_ja=s["name_ja"], name_romaji=s["name_romaji"], place_name=s["name_ja"], lat=s["lat"],
                   lng=s["lng"], notes=s["notes"], source_url=s["source_url"], line_seq=str(seq))
        rows.insert(rows.index(after) + 1, row)
        added += 1
    noted = 0
    for r in rows:
        if r["type"] == "EKI_STAMP" and r["prefecture"] == PREF and r["name_ja"] in data["no_stamp"]:
            note = f"{data['no_stamp_note']} {OSM_NOTE}"
            if r["notes"] != note:
                r["notes"] = note
                noted += 1
    return added, noted


def add_ekitags(cols, rows, data):
    have = {r["name_ja"] for r in rows if r["type"] == "EKITAG"}
    stations = {}
    for r in rows:
        if r["type"] == "EKI_STAMP" and r["prefecture"] == PREF:
            stations.setdefault(r["name_ja"], r)
    new = []
    for batch in data["batches"]:
        for name in batch["stations"]:
            if name in have:
                continue
            st = stations[name]
            row = {c: "" for c in cols}
            row.update(type="EKITAG", name_ja=name, name_romaji=st["name_romaji"], prefecture=PREF,
                       place_name=st["place_name"], lat=st["lat"], lng=st["lng"], source_url=batch["source_url"],
                       notes=f"Ekitag since {batch['from']} (JR East Niigata branch). Where the tag is and when it "
                             f"can be read: the 'i' mark in the Ekitag app. {OSM_NOTE}")
            new.append(row)
            have.add(name)
    last = max(i for i, r in enumerate(rows) if r["type"] in ("EKI_STAMP", "EKITAG"))
    rows[last + 1:last + 1] = new
    return len(new)


def check(rows):
    osm = {}
    for n in load("sources/osm-niigata-stations-2026-11-08.json")["nodes"]:
        osm.setdefault(n["name"] + "駅", []).append((n["lat"], n["lon"]))
    seen = set()
    for r in rows:
        if r["type"] not in ("EKI_STAMP", "EKITAG") or r["prefecture"] != PREF or (r["type"], r["name_ja"]) in seen:
            continue
        seen.add((r["type"], r["name_ja"]))
        nodes = osm.get(r["name_ja"])
        if not nodes:
            print(f"  station {r['name_ja']}: no OSM node in the snapshot")
            continue
        d = min(metres(float(r["lat"]), float(r["lng"]), *n) for n in nodes)
        if d > 300:
            print(f"  station {r['name_ja']}: {d:.0f} m from its OSM node")
    dams = {}
    for d in load("sources/osm-niigata-dams-2026-11-08.json")["dams"]:
        dams.setdefault(d["name"], []).append((d["lat"], d["lon"]))
    mlit = {m["dam"].split("\n")[0]: m for m in load("sources/mlit-damcard-niigata-2026-01-16.json")["rows"]}
    known = load("data/niigata-dams.json")
    by_name = {r["name_ja"]: r for r in rows if r["type"] == "DAM_CARD"}
    squash = lambda s: "".join(str(s or "").replace("　", "").replace(";", "").split())
    for name, m in mlit.items():
        r = by_name.get(name)
        if r is None:
            if name not in known["not_added"]:
                print(f"  dam {name}: on the MLIT list, not in the CSV")
            continue
        if f"Ver.{float(m['ver']):.1f}" != r["code"]:
            print(f"  dam {name}: version {m['ver']} on the list, {r['code']} in the CSV")
        for field, col in (("place", "place_name"), ("address", "address"), ("hours", "hours_text")):
            if squash(m[field]).strip("－") != squash(r[col]):
                print(f"  dam {name}: {col} differs from the list")
        if not r["lat"]:
            if name not in known["no_position"]:
                print(f"  dam {name}: no position")
        elif name in dams:
            d = min(metres(float(r["lat"]), float(r["lng"]), *p) for p in dams[name])
            if d > 300:
                print(f"  dam {name}: {d:.0f} m from the OSM dam")
    for name in by_name:
        if name not in mlit:
            print(f"  dam {name}: in the CSV, not on the MLIT list")


def main(path, check_only):
    cols, rows = read(path)
    if check_only:
        check(rows)
        return
    others = [r.copy() for r in rows if r["prefecture"] != PREF]
    added, noted = add_stations(cols, rows, load("data/niigata-stations.json"))
    tags = add_ekitags(cols, rows, load("data/niigata-ekitag.json"))
    if [r for r in rows if r["prefecture"] != PREF] != others:
        sys.exit("a non-Niigata row changed: nothing written")
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    print(f"{PREF}: stations added {added}, no-stamp notes {noted}, Ekitag rows added {tags} -> {path}")


if __name__ == "__main__":
    if len(sys.argv) == 2 or (len(sys.argv) == 3 and sys.argv[2] == "--check"):
        main(sys.argv[1], len(sys.argv) == 3)
    else:
        sys.exit(__doc__)
