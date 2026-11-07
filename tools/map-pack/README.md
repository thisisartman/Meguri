# Offline map pack

The app ships an offline map for the Niigata trip area (#9) in `app/src/main/assets/`:

- `map/region.pmtiles`: OpenStreetMap data from the Protomaps daily build of 2026-11-06 for Niigata, Saitama and Tokyo prefectures (owner's choice, 2026-11-07), cut along their outlines (`region.geojson`, from Nominatim, simplified), zoom 0 to 14, 105 MB. Over GitHub's 100 MB file limit, so the repo keeps it as `map-pack/region.pmtiles.part0` and `part1`, and the `joinMapPack` build step joins them into the app's assets. `map/coverage.json` holds the same outlines for the app's offline check.
- `map/style-{light,dark}-{en,ja}.json`: styles from `@protomaps/basemaps` 5.7.2, with fonts renamed to `NotoSans-*` (no spaces, so asset paths work). The pack's location replaces `pmtiles://__PACK__` at runtime (`ui/OfflineMap.kt`).
- `glyphs/NotoSans-{Regular,Medium,Italic}/`: glyph files from `protomaps/basemaps-assets` (Noto Sans, SIL Open Font License). Japanese names are drawn with the phone's own fonts.
- `sprites/`: the light and dark icon sheets from `protomaps/basemaps-assets` (`sprites/v4`).

Map data © OpenStreetMap contributors (ODbL), credited in About and on the map.

## Rebuilding or covering another area

```
pmtiles extract https://build.protomaps.com/<YYYYMMDD>.pmtiles region.pmtiles --region=region.geojson --maxzoom=14
split -b 60m -d -a 1 region.pmtiles region.pmtiles.part   # then move the parts into map-pack/
npm i @protomaps/basemaps@5.7.2 && node make_styles.js
```

`pmtiles` is the go-pmtiles command (v1.31.2 was used). Try `--dry-run` first: it prints the size without downloading. If the area changes, regenerate `region.geojson` and `coverage.json` (outer rings, longitude and latitude, 4 decimals); outside the outlines the app uses online tiles.
