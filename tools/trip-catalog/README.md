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

## Cards the owner already has
Read from the owner's video of their cards (2026-11-03): the coordinates printed on each card, matched to https://ekikaramanhole.whitebeach.org/ext/manholecard/search.cgi?m=15 and ?m=13.

## Lines
Positions come from OpenStreetMap `railway=station` nodes by name (route relations for these lines are not tagged under these names; large Overpass queries time out, so query 0.25-degree tiles of `37.30,138.50,38.05,139.25`).

The Yahiko, Echigo and Shin'etsu orders were written from memory and checked only geographically (each station lies between its neighbours), not against a timetable. A station on two lines (吉田, 東三条, 新潟, 宮内) is one item in several line stamp books. The Shin'etsu list covers Miyauchi to Niigata only.

Goshuin and the light-up stamp rally are not in the catalogue; add ad-hoc in the app.
