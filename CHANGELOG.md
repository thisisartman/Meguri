# Changelog

## 2026-11-01: Design round started
- Product ID card and the scope of the main-screens design round recorded in PROGRESS.md.
- M0 test build also checked on the Galaxy S23 (Android 16): installs, logs a crash, exports logs.
- Notes on four comparable apps (two manhole-card apps, Ekitag, Stamp Quest) in `docs/design/comparables.md`.
- Design brief: the four collection types in their own terms, the real tasks ranked, and a one-line thesis (`docs/design/brief.md`).
- v1 checklist: marking cards already owned in one pass.

## 2026-11-01: M0 project setup (0.0.1)
- App builds and installs on the Poco: a test copy ("Collectibles (dev)") and the real app side by side.
- No internet permission; Android backup and device-to-device transfer switched off.
- Errors and crashes are saved on the phone; "Logs" screen with "Export logs".
- Release builds signed with a dedicated key kept outside the repo.
- Built APK file names now carry the time of each build (they were stuck at the first build's time).

## 2026-11-01: Spec v4, geofencing first
- Alerts: Google geofencing in the background, Trip mode as fallback for trains and no-signal areas (the Poco has real Play Services, not microG).
- Alert spike now tests both, including data off and after a reboot.
- Versions: v1 = M0 + M1 + M2 + M5, checklist in `docs/ROADMAP.md`.

## 2026-11-01: Repo set up
- Spec, build plan and progress tracker moved into the repo.
- Branches: `main` for tagged versions, `testing` for verified features, `experimental/<feature>/<mini>` for work in progress.
- README, ignore rules and line-ending rules added.
