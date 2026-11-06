# Offline map pack

The app ships an offline map for the Niigata trip area (#9) in `app/src/main/assets/`:

- `map/niigata.pmtiles`: OpenStreetMap data from the Protomaps daily build of 2026-11-06, zoom 0 to 14, for the area west 138.45, south 36.95, east 139.35, north 38.05 (IUJ and Urasa to Niigata City). 24 MB.
- `map/style-{light,dark}-{en,ja}.json`: styles from `@protomaps/basemaps` 5.7.2, with fonts renamed to `NotoSans-*` (no spaces, so asset paths work). The pack's location replaces `pmtiles://__PACK__` at runtime (`ui/OfflineMap.kt`).
- `glyphs/NotoSans-{Regular,Medium,Italic}/`: glyph files from `protomaps/basemaps-assets` (Noto Sans, SIL Open Font License). Japanese names are drawn with the phone's own fonts.
- `sprites/`: the light and dark icon sheets from `protomaps/basemaps-assets` (`sprites/v4`).

Map data © OpenStreetMap contributors (ODbL), credited in About and on the map.

## Rebuilding or covering another area

```
pmtiles extract https://build.protomaps.com/<YYYYMMDD>.pmtiles niigata.pmtiles --bbox=138.45,36.95,139.35,38.05 --maxzoom=14
npm i @protomaps/basemaps@5.7.2 && node make_styles.js
```

`pmtiles` is the go-pmtiles command (v1.31.2 was used). Try `--dry-run` first: it prints the size without downloading. If the area changes, update the bounds in `ui/OfflineMap.kt`; outside them the app uses online tiles.
