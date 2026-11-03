# Changelog

## 2026-11-03: Trip build (in progress)
- Log a find with "Got it" (time and position saved, Undo for a few seconds), with or without a photo from the phone's camera.
- "Nearby": the nearest things you still need, and your collections with progress.
- "My book": manhole cards by prefecture, eki stamps as a stamp book per railway line, goshuin; mark the ones you already have in one go.
- Add a goshuin, stamp or card that isn't in the list, together with its find.
- Back up all finds to a file; load a catalogue from a CSV file.
- English or Japanese, chosen in the app.
- Fixed before release: photos taken in the app were deleted right after capture; Nearby crashed for items under 1 km.
- Trip catalogue: Nagaoka's new water-supply card (G) and Tsubame's fifth card (E), with where to get them and opening hours.
- After an Undo, the nearest list scrolls back so the item is in view again; railway lines in "My book" no longer repeat "Eki stamps".
- Trip catalogue: Yahiko, Echigo and Shin'etsu Line stamp books added; Niigata City's two cards and the prefecture card now show where to get them, with hours and phone numbers.
- Long names on Nearby cards wrap onto a second line instead of being cut off.
- Catalogue: the other cards already in the collection (Sado, Kashiwazaki, Nagaoka F, Tokyo) and the Gotokuji goshuin.
- A find logged with a photo tries again for its position when the camera closes; before this, photo finds lost their position.

## 2026-11-02: Design complete
- Round 4 decided: one colour per type (indigo manhole cards, vermilion goshuin, teal eki stamps), the phone's normal typeface.
- Language: an app-level switch; in English mode names are in romaji everywhere, with the Japanese on the item page.
- Build spec written: `docs/DESIGN.md`, with colours checked against the backgrounds they really sit on, one rule for a missing GPS fix, place labels per type, and a list of what M1 must still check on the phones.

## 2026-11-01: Design round started
- Product ID card and the scope of the main-screens design round recorded in PROGRESS.md.
- M0 test build also checked on the Galaxy S23 (Android 16): installs, logs a crash, exports logs.
- Notes on four comparable apps (two manhole-card apps, Ekitag, Stamp Quest) in `docs/design/comparables.md`.
- Design brief: the four collection types in their own terms, the real tasks ranked, and a one-line thesis (`docs/design/brief.md`).
- v1 checklist: marking cards already owned in one pass.
- Design round 1 page drawn (what the app opens on, a stamp book per line, how collected items look); outside the repo in `Design/`.
- Round 1 decided: nearest items on top with the collection below, a stamp-book page per line, your photo or a filled seal for collected items.
- Design round 2 page drawn (getting around without a tab bar, language modes, the words).
- Round 2 decided: a bottom bar "Nearby · My book · More"; English or Japanese with a switch.
- Design round 3 page drawn (one item's page, logging with "+").
- Design round 4 page drawn (what colour means, how names are set), with colours checked for contrast and colour-blind reading in light and dark.
- Round 3 decided: the item page changes with whether you have it; "+" opens a search with the nearest items already listed. All layout decisions are now made; colours and fonts are next.

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
