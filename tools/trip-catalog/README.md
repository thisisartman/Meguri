# Trip catalogue

Builds the CSV that "More → Load catalogue" reads (format: `docs/CSV_FORMAT.md`).

```
python tools/trip-catalog/make_catalog.py tools/trip-catalog/joetsu.json tools/trip-catalog/niigata-lines.json tools/trip-catalog/trip-niigata.csv
```

- `joetsu.json`: JR Joetsu Line station positions from OpenStreetMap (ODbL), keyed by Japanese name.
- `niigata-lines.json`: Yahiko, Echigo and Shin'etsu Line station positions from OpenStreetMap (ODbL), read 2026-11-03.
- `make_catalog.py`: station order per line, and the manhole cards with their hand-out places.
- `trip-niigata.csv`: the generated file (96 line rows: Joetsu 35, Yahiko 8, Echigo 32, Shin'etsu 21; 8 Niigata manhole cards; 18 more cards the owner already has; 1 goshuin the owner already has).

## Sources for the cards (read 2026-11-03)
- Nagaoka A–G: https://www.city.nagaoka.niigata.jp/kurashi/cate05/gesui/mhcard.html and, for G (water supply 100 years), https://www.city.nagaoka.niigata.jp/suidou/suidou/manhole.html. Card C moved to Aore Nagaoka West 3F on 2026-11-02.
- Tsubame E (fifth card, merger 20th anniversary): https://www.city.tsubame.niigata.jp/material/files/group/1/20260707_PRESS.pdf
- Niigata A, B: https://www.city.niigata.lg.jp/kurashi/jyogesuido/gesui/oshirase/mhcard.html (updated 2026-08-06).
- Niigata prefecture A: http://www.niigata-gesuikou.or.jp/manhole2017
- Hand-out building positions: OpenStreetMap.

## All Niigata cards (#65, 2026-11-07)
`add_niigata_cards.py` edits the asset CSV in place (it is the source since phase 5 added dam rows to it directly): it adds the 31 Niigata cards that were missing and fills hand-out places into the owner's cards that had none, keyed by code. Source: Eki kara Manhole's Niigata list and its hand-out map feed (`/ext/manholecard/lmap/getJSON.cgi`), snapshot in `sources/`. Shibata D (15-206-D001, series 28) has no place in the feed or on the city's pages yet. Saitama and Tokyo followed, see below.

## Saitama and Tokyo cards (#65, 2026-11-07)
`add_cards_by_prefecture.py <code> <csv>` is the rerunnable version of the Niigata script; the same merge rules apply. The curated cards are in `data/cards-11.json` (Saitama, 73) and `data/cards-13.json` (Tokyo, 101), and the feed snapshots are in `sources/ekikaramanhole-{saitama,tokyo}-2026-11-07.json`. To refresh a snapshot, run `add_cards_by_prefecture.py <code> --fetch <out.json>`. It also prints any listed codes that the data file does not cover yet.

- The position is the main hand-out place, taken from the feed's "ここに行く" link. Second places, places used on closed days and seasonal places go in notes. Hours were condensed by hand.
- Left out: the promotion cards (13-100-P001, 13-203-P001) and the gold special editions (`S-`), which are no longer handed out. Each one is listed under `skipped` in the data file.
- Setagaya G001 and Higashiyamato A001 appear on the site only as `S-13-100-G001` and `S-13-220-A001`. They are added under their plain codes, on the assumption that the regular card is the one handed out now.
- Tokorozawa's English cards (`E-11-208-C001`, `E-11-208-D001`) are rows of their own at the same place as the Japanese card.
- Hachioji A001 (13-201-A001) has no place in the feed.

## Cards the owner already has
Read from the owner's video of their cards (2026-11-03): the coordinates printed on each card, matched to https://ekikaramanhole.whitebeach.org/ext/manholecard/search.cgi?m=15 and ?m=13.

## Lines
Positions come from OpenStreetMap `railway=station` nodes by name (route relations for these lines are not tagged under these names; large Overpass queries time out, so query 0.25-degree tiles of `37.30,138.50,38.05,139.25`).

The Yahiko, Echigo and Shin'etsu orders were written from memory; on 2026-11-08 all five orders were checked against the 駅一覧 tables on ja.wikipedia and matched, except that Kamitokoro (上所, opened 2025-03-15) was missing from the Echigo Line. A station on two lines (吉田, 東三条, 新潟, 宮内) is one item in several line stamp books. The Shin'etsu list covers Miyauchi to Niigata only.

Goshuin and the light-up stamp rally are not in the catalogue; add ad-hoc in the app.

## Niigata refinement (2026-11-08)
`refine_niigata.py <csv>` edits the Niigata rows in place and leaves every other prefecture's rows alone (it refuses to write if one changed). `--check` writes nothing; it reports stations more than 300 m from their OSM node and dam rows that differ from the MLIT list. Data: `data/niigata-stations.json`, `data/niigata-ekitag.json`, `data/niigata-dams.json`. Snapshots in `sources/`: `osm-niigata-stations-2026-11-08.json`, `osm-niigata-dams-2026-11-08.json`, `mlit-damcard-niigata-2026-01-16.json`, `ekitag-niigata-2026-11-08.txt`.

- Stations: Kamitokoro (上所) added to the Echigo Line between Hakusan and Niigata, which moves Niigata to line_seq 33. All 76 Niigata stations are within 100 m of their OSM node. No source lists paper stamps for the Niigata branch, so no station is marked "スタンプなし". The `no_stamp` list in the data file is empty until someone checks a station in person.
- Ekitag: 20 EKITAG rows, one for each station on these lines named in JR East Niigata branch releases (2024-03-18, 2024-07-05, 2024-10-24). ekitag.jp only gives counts per prefecture (新潟県 50駅, 2026-07-31), so a station added after 2024-11 is missing. jreast.co.jp returns 403 to scripts; the PDFs were read from Wayback Machine captures.
- Dam cards: the 2026-01-16 MLIT list was still the latest on 2026-11-08, and the 32 rows already matched it. Okumiomote (奥三面) stays out because distribution is suspended. Shirokawa (城川) and Yamamoto Choseichi (山本調整池) have no position.
- Museum stamps: none added. The only Niigata lists found were summer rallies that have ended (Niigata University museum, 2025 and 2026).
