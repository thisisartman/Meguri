# Catalogue CSV format

Load with "More" → "Load catalogue (CSV)". UTF-8 (a BOM is fine), comma-separated, header row first, quotes around fields that contain commas, quotes or line breaks. Loading the same file again updates items in place; it never removes an item or a find.

| Column | Required | Meaning |
|---|---|---|
| `type` | yes | `MANHOLE_CARD`, `GOSHUIN` or `EKI_STAMP` |
| `catalog_key` | no | Stable key for updates. Default: `MANHOLE_CARD:<code>` for cards, `<TYPE>:<name_ja>` otherwise |
| `code` | cards | Card code, e.g. `15-202-A001` |
| `name_ja` | yes | Name as written, e.g. `長岡市 A001`, `浦佐駅` |
| `name_romaji` | no | Shown in English mode; falls back to `name_ja` |
| `series` | no | Wave (第N弾), e.g. `07` |
| `release_date` | no | e.g. `2018/04/28` |
| `prefecture`, `municipality` | no | Cards are grouped by prefecture |
| `place_name`, `address`, `place_phone` | no | Where to get it |
| `lat`, `lng` | no | Decimal degrees of **where you get it**; used for "Nearby" distances |
| `hours_text`, `closed_days` | no | Free text (structured hours come with #21) |
| `notes`, `source_url` | no | |
| `line_id` | stamps | Railway line id, e.g. `JR_JOETSU` (puts the station in that line's stamp book) |
| `line_name_ja`, `line_name_romaji` | stamps | Line name, e.g. `上越線`, `Joetsu Line` |
| `line_seq` | stamps | Position along the line, 1 = first station |

A station on two lines is two rows with the same `name_ja` (so the same item) and different `line_id` / `line_seq`.

Example:

```
type,code,name_ja,name_romaji,prefecture,lat,lng,line_id,line_name_ja,line_name_romaji,line_seq
EKI_STAMP,,浦佐駅,Urasa Station,新潟県,37.167217,138.922844,JR_JOETSU,上越線,Joetsu Line,27
MANHOLE_CARD,15-202-A001,長岡市 A001,Nagaoka-shi A001,新潟県,37.447250,138.851111,,,,
```
