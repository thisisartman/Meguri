# Region packs

Since 0.5, the catalogue and the offline map come to the phone as downloads, one prefecture at a time, instead of inside the app. This folder builds those downloads.

## What a pack is

- **Catalogue pack:** one prefecture, one type, as a gzip CSV with the catalogue's own header (`JP-15-manhole.csv.gz`). The app imports it the same way as a catalogue file: it adds and updates by catalogue key and never deletes a find. A station on two lines keeps both rows.
- **Map pack:** one prefecture's OpenStreetMap data from Protomaps as PMTiles (`JP-15-map.pmtiles`), cut along the prefecture's outline widened by about 3 km, so tiles at the coast and border aren't lost.
- **`index.json`:** lists every pack with its prefecture, type, item count, size and SHA-256. The app reads it to show what can be downloaded, checks each file against its checksum, and offers an update when a checksum changes.

The main types (manhole cards, station stamps, Ekitag, dam cards, museum stamps) are marked `core` and come with a prefecture. Roadside stations and goshuin are optional extras.

## Building

```sh
# 1. Map files: join the old combined pack (or download a newer Protomaps build) and cut one file per prefecture
pmtiles extract region.pmtiles map-niigata.pmtiles --region=niigata.geojson --maxzoom=14
# 2. The packs and the index
python3 tools/packs/make_packs.py <catalogue.csv> app/src/main/assets/map/prefectures.json <out> \
    --map JP-15=map-niigata.pmtiles --map JP-13=map-tokyo.pmtiles --map JP-11=map-saitama.pmtiles
```

Running it twice on the same input gives the same files and checksums, so an update only shows for packs that really changed.

## Testing on a phone

Debug builds read packs from `http://127.0.0.1:8765/`. Serve the output folder from the laptop and forward the port over USB:

```sh
cd <out> && python3 -m http.server 8765
adb reverse tcp:8765 tcp:8765
```

Release builds read the latest release of the public data repository (see `PACKS_URL` in `app/build.gradle.kts`).
