# Trip catalogue

Builds the CSV that "More → Load catalogue" reads (format: `docs/CSV_FORMAT.md`).

```
python tools/trip-catalog/make_catalog.py tools/trip-catalog/joetsu.json tools/trip-catalog/trip-niigata.csv
```

- `joetsu.json`: JR Joetsu Line station positions from OpenStreetMap (ODbL), keyed by Japanese name.
- `make_catalog.py`: station order per line, and the manhole cards with their hand-out places.
- `trip-niigata.csv`: the generated file (35 Joetsu Line stations, 8 Niigata manhole cards).

## Sources for the cards (read 2026-11-03)
- Nagaoka A–G: https://www.city.nagaoka.niigata.jp/kurashi/cate05/gesui/mhcard.html and, for G (water supply 100 years), https://www.city.nagaoka.niigata.jp/suidou/suidou/manhole.html. Card C moved to Aore Nagaoka West 3F on 2026-11-02.
- Tsubame E (fifth card, merger 20th anniversary): https://www.city.tsubame.niigata.jp/material/files/group/1/20260707_PRESS.pdf
- Hand-out building positions: OpenStreetMap.

## Still to add for the Niigata trip
Positions come from OpenStreetMap `railway=station` nodes by name (route relations for these lines are not tagged under these names; prefecture-wide Overpass queries timed out, so query a bounding box such as `37.30,138.60,38.00,139.20`).

- Yahiko Line (弥彦線), Yahiko → Higashi-Sanjo: 弥彦, 矢作, 吉田, 西燕, 燕, 燕三条, 北三条, 東三条
- Echigo Line (越後線), Kashiwazaki → Niigata: 柏崎, 東柏崎, 西中通, 荒浜, 刈羽, 西山, 礼拝, 石地, 小木ノ城, 出雲崎, 妙法寺, 小島谷, 桐原, 寺泊, 分水, 粟生津, 南吉田, 吉田, 北吉田, 岩室, 巻, 越後曽根, 越後赤塚, 内野西が丘, 内野, 新潟大学前, 寺尾, 小針, 青山, 関屋, 白山, 新潟
- Shin'etsu Line (信越本線), Miyauchi → Niigata: 宮内, 長岡, 北長岡, 押切, 見附, 帯織, 東光寺, 三条, 東三条, 保内, 加茂, 羽生田, 田上, 矢代田, 古津, 新津, さつき野, 荻川, 亀田, 越後石山, 新潟

These orders are written from memory and must be checked against a timetable or OSM before use. A station on two lines (吉田, 東三条, 新潟) is one item; the schema allows it in several lines through `line_station`.
