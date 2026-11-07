"""Adds one prefecture's manhole cards to the bundled catalogue (#65), the way add_niigata_cards.py did Niigata.

    python tools/trip-catalog/add_cards_by_prefecture.py 11 app/src/main/assets/catalogue/trip-niigata.csv
    python tools/trip-catalog/add_cards_by_prefecture.py 13 --fetch /tmp/tokyo.json   # refresh a snapshot

The curated cards live in data/cards-<code>.json (one object per card: town, series, the feed entry chosen as
the main hand-out place and its position, condensed hours, notes for second or seasonal places). Merge rules match
add_niigata_cards.py: rows are keyed by card code; a code already in the file keeps its row if it has a position,
gets its place filled if it has none, and new codes are added after the last MANHOLE_CARD row.

--fetch reads Eki kara Manhole's list (search.cgi?m=<code>) and hand-out map feed (lmap/getJSON.cgi), writes the
feed entries in the snapshot format of sources/, and prints list codes the data file does not cover yet. It makes
two requests, two seconds apart. Hours and place picks are hand work: read the new entries and edit the data file.
"""
import csv
import html
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).parent
BASE = "https://ekikaramanhole.whitebeach.org/ext/manholecard/"


def get(url, referer=None):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", **({"Referer": referer} if referer else {})})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8")


def text(h):
    h = re.sub(r"<br\s*/?>", "\n", h).replace("<hr>", "\n\n")
    return html.unescape(re.sub(r"<[^>]+>", "", h)).strip()


def fetch(pref, out):
    page = get(f"{BASE}search.cgi?m={pref}")
    listed = [b[:b.index('"')] for b in page.split('<div class="list" id="div_')[1:]]
    time.sleep(2)
    # The whole of Japan: the m= filter does the work, and islands (Ogasawara) stay in. l=500 is the entry cap.
    feed = json.loads(get(f"{BASE}lmap/getJSON.cgi?dn=46&de=154&dw=122&ds=20&lat=35.8&lng=139.5&zoom=8&l=500"
                          f"&r=&s=&p=&i=&m={pref}&a=&c=", referer=f"{BASE}lmap/"))
    if len(feed) >= 500:
        sys.exit("feed hit the 500-entry cap: split the bounding box")
    entries = []
    for e in feed:
        i = e["info"]
        pos = re.search(r"daddr=([\d.]+),([\d.]+)", i)
        src = re.search(r'在庫確認先：<a href="([^"]+)"', i)
        entries.append({
            "id": e["id"],
            "code": re.search(r'file=card/([^".]+)\.jpg', i).group(1),
            "title": text(re.search(r'class="series-map"></a>(.*?)</div>', i, re.S).group(1)),
            "etc": text(re.search(r'<div class="card-etc-map">(.*?)</div>\s*</div>\s*$', i, re.S).group(1)),
            "lat": pos.group(1), "lng": pos.group(2), "src": src.group(1) if src else None,
        })
    Path(out).write_text(json.dumps(entries, ensure_ascii=False, indent=1), encoding="utf-8")
    data_path = HERE / "data" / f"cards-{pref}.json"
    known = set()
    if data_path.exists():
        data = json.loads(data_path.read_text(encoding="utf-8"))
        known = {c["code"] for c in data["cards"]} | set(data["skipped"]) | {c.get("listed_as") for c in data["cards"]}
    fed = {e["code"] for e in entries}
    print(f"{len(listed)} cards listed, {len(entries)} feed entries -> {out}")
    for code in listed:
        if code not in known:
            print(f"  not in data file: {code}{'' if code in fed else ' (no feed entry)'}")


def names(card):
    tail = card["code"][-4:]
    ja, ro = card.get("name_ja"), card.get("name_romaji")
    if ja is None:
        ja, ro = card["town"], card["romaji"]
    ja, ro = f"{ja} {tail}", f"{ro} {tail}"
    if "variant" in card:
        ja, ro = f"{ja} {card['variant'][0]}", f"{ro} ({card['variant'][1]})"
    return ja, ro


def merge(pref, path):
    data = json.loads((HERE / "data" / f"cards-{pref}.json").read_text(encoding="utf-8"))
    with open(path, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        cols, rows = reader.fieldnames, list(reader)
    by_code = {r["code"]: r for r in rows if r["type"] == "MANHOLE_CARD" and r["code"]}
    added = filled = kept = 0
    last_card = max(i for i, r in enumerate(rows) if r["type"] == "MANHOLE_CARD")
    new_rows = []
    for card in data["cards"]:
        fields = {
            "place_name": card["place"], "address": card["address"], "place_phone": card["phone"],
            "lat": card["lat"], "lng": card["lng"], "hours_text": card["hours"], "notes": card["notes"],
            "source_url": card["source_url"] or data["list_url"],
        }
        fields = {k: ("" if v is None else str(v)) for k, v in fields.items()}
        row = by_code.get(card["code"])
        if row is not None:
            if row["lat"]:
                kept += 1
                continue
            row.update(fields)
            filled += 1
            continue
        card["romaji"] = data["towns"].get(card["town"])
        ja, ro = names(card)
        row = {c: "" for c in cols}
        # Tokyo's ward-wide cards (東京23区) have no single municipality, like the prefecture-level ones.
        town = "" if card["town"] in ("", "東京23区") else card["town"]
        row.update(type="MANHOLE_CARD", code=card["code"], name_ja=ja, name_romaji=ro, series=card["series"],
                   prefecture=data["prefecture"], municipality=town, **fields)
        new_rows.append(row)
        added += 1
    rows[last_card + 1:last_card + 1] = new_rows
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    print(f"{data['prefecture']}: added {added}, filled {filled}, kept {kept} -> {path}")


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[2] == "--fetch":
        fetch(int(sys.argv[1]), sys.argv[3])
    elif len(sys.argv) == 3:
        merge(int(sys.argv[1]), sys.argv[2])
    else:
        sys.exit(__doc__)
