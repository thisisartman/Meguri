# Meguri

An offline Android app for logging Japanese collectibles: manhole cards, goshuin, eki stamps and Ekitags. It shows what you have, what you still need, and where the nearest ones are.

The name is 巡り, going round a circuit, as in a pilgrimage or a stamp rally.

## Why this exists

These collectibles are picked up on day trips, standing at a counter, a shrine or a station, often with weak signal and one hand free. Meguri is built for that moment.

**One tap to log.** "Got it" records the time and position straight away. A photo is always optional, and a find never waits on GPS.

**Everything stays on the phone.** There is no account or sync service, and the app does not have the internet permission. Photos stay in your gallery.

## Features

- "Nearby": the closest things you still need, and your collections with progress
- "My book": manhole cards by prefecture, eki stamps as a stamp book per railway line, and goshuin
- Log a find with or without a photo, with Undo for a few seconds afterwards
- Mark many items at once as "had before the app"
- Search by name or card code, and add an item that isn't in the catalogue along with its find
- Item pages with the hand-out place, address, hours, phone and a link to Maps
- Load a catalogue from a CSV file ([format](docs/CSV_FORMAT.md)); loading again updates items and never removes a find
- Back up all finds to a zip file
- English or Japanese, chosen in the app
- Material 3 with one colour per type, light and dark

## Status

Version 0.0.1, pre-release, with 12 unit tests passing. The trip build has been tested on a POCO M2 Pro running Android 14, and the dev build also on a Galaxy S23.

Not built yet: restoring a backup, Ekitags as their own type, alerts near an item, the map and structured opening hours. See [`docs/ROADMAP.md`](docs/ROADMAP.md).

Android only, sideloaded only. The catalogue in `tools/trip-catalog/` covers the Niigata area and uses station positions from OpenStreetMap (ODbL).

- Spec: [`docs/PRD.md`](docs/PRD.md)
- Build plan and milestones: [`docs/BUILD_PLAN.md`](docs/BUILD_PLAN.md)
- Design: [`docs/DESIGN.md`](docs/DESIGN.md)
- Current state: [`PROGRESS.md`](PROGRESS.md) · history: [`CHANGELOG.md`](CHANGELOG.md)

## Branches

- `main`: tagged versions only (`v1`, `v2`, ...). Each version is a checklist walked on real phones.
- `testing`: features that passed their phone check, verified together.
- `experimental/<feature>/<mini>`: work in progress. Each lands in `testing` by pull request.

Every change starts as an issue and lands through a pull request.

## Build

Open this folder in Android Studio, or from a terminal:

```
./gradlew assembleDebug testDebugUnitTest
```

The debug build installs as a separate app (`.dev` suffix), so testing never touches real data.

## License

MIT. See [`LICENSE`](LICENSE).
