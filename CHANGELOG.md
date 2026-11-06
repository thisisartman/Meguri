# Changelog

## 2026-11-06: Version 0.3.0, from the owner's trip feedback (built, not yet on a phone)
- Planned: a map on Nearby, in the layout the owner picked (the map fills the screen, with a sheet of Stamps and Cards on top), a colour setting, a welcome screen, several photos per find and plain English names. Plan in `docs/V0.3_PLAN.md`.
- Tried on an emulator, not yet on a phone: Nearby is now a map with a sheet of round tiles (Stamps and Cards tabs, filters by kind, nearest or A to Z, three sizes); tap a pin for Details and Directions; a full-screen map per tab.
- The map works offline around the trip area (IUJ and Urasa to Niigata City) and uses online map tiles elsewhere. The app goes online for map tiles only.
- Place names show on the map in your language (English mode adds the Japanese name). Dark mode works, with readable roads, and "still to get" pins are no longer the brightest thing on a dark map. Pins shrink when zoomed out, so they don't hide town names.
- Several photos per find, each marked as stamp, proof or other; a photo from the gallery is copied into the app's own folder.
- Merged from the Legion: the "seal gold" colours with a switch back to the phone's own colours, a welcome screen that loads the Niigata catalogue in one tap, and "Station stamps" in English.
- 0.3.0-beta.1, the first beta, merged into `testing` (2026-11-07).
- Planning: Meguri is now planned as an inventory manager (`docs/PLAN-inventory.md`, draft for the owner). The app has no changes from this yet.
- Item page: one layout for collected and uncollected. Photos are dotted squares, one per kind for the type (a station has Station stamp, Ekitag screenshot and Station photo), and the Photo button opens the same kinds in a menu. Each collection is one line with its date (or "Date not recorded"); "had before the app" is gone.
- Buttons say "Collect" and "Collect again"; the state is "Uncollected" everywhere.
- Tap a pin for its type icon, "Uncollected · 1.7 km from you", and Directions, Details and Collect, each with an icon. Pins are easier to hit.
- Directions open the actual place in Google Maps (by its name), not a loose pin.
- Search is forgiving (spaces, hyphens, long vowels, small typos, Japanese) and shows results under the search bar, with a chip per type.
- Collected stations show a station icon on their seal instead of three letters; manhole cards keep their card code.
- Type icons: manhole cover, torii, train, and a phone with NFC waves for Ekitags. About credits the icon sets.
- Pages change without the map lingering on screen.
- New layout (owner's redesign): the bottom bar is Nearby, a gold + for logging a find, and My book. Settings and Logs moved to a round menu at the top right. "Got it" is now "Collected" (Japanese 集めた), and its message says "Collected ...".
- Nearby's top card is a search bar: it searches everything by English or Japanese name or code, and the map and list show only the results. A button above the sheet brings the map back to you. Tap a pin to get Directions, Details and Collected.
- From a 5-round review on the Poco, in light and dark mode, English, Japanese and large text: Photo and Collected sit in the same order everywhere, the station note reads plainly, "Alerts when the app is closed" explains itself, and every control is at least 48 dp.
- On the Poco's dev copy (a real phone): place names show in English and Japanese, in dark and light mode. With all permissions given, alerts while the app is closed register with Google location (300 m around each item); none has fired on a walk yet. Trip mode is to be reworked later (#62).
- The Nearby sheet now rests high enough to show a whole row of tiles, with both lines of a name and the distance (names like Minami-Yoshida Station were cut off).
- Sort and tile size are now icons on the chips row: the sort icon opens Nearest or A to Z, and the grid icon opens a size slider. A new sort or filter starts from the top of the list, and names in neighbouring columns no longer run together.

## 2026-11-06: Version 0.2.0, ready for the S23, with alerts (#43, #10, #42)
- Alerts near things still to get: Trip mode (checks your position every 20 s to 2 min until 23:59, from More or a quick-settings tile) and alerts while the app is closed (Google geofences, needs location "Allow all the time"). Got it, Photo and Dismiss on the notification; Got it turns into "Logged ... Undo". Distance 200 m to 1 km, per-type switches, quiet at night 22:00 to 07:00, once a visit.
- More says when background alerts cannot work (Google Location Accuracy off) and opens the right settings page; "Keep alerts working" opens battery settings (and mentions Sleeping apps on Samsung).
- Nearby says why there is no position (not allowed, location switched off, no position yet) with the right button, notes an approximate position, and says when nothing is within 30 km. "Allow location" opens Settings once Android stops asking.
- "Approximate" location now gives a position (it gave none before).
- "Not in the list?" stays visible under the + sheet's list; Got it and Photo are 48 dp; names get two lines.
- Distances under 25 m read "here".
- My book: sections fold, headers stay at the top, complete collections show a check.
- One outlined button and one chip style: visible borders, a check on selected chips, 48 dp. No more purple from Material's defaults.
- A double tap on Got it logs one find, not two.
- After the camera closes, "Logged ... Undo" shows again, so a cancelled photo still shows the find was kept.
- Reloading the same catalogue says it is up to date instead of "119 updated".
- Backups name the file they saved; the weekly backup dialog says its folder and how many it keeps; counts use proper plurals.
- Japanese mode: Logs in Japanese, "・" separators, full-width colons, natural line breaks, plainer wording.
- Screen readers hear which item a Got it or Photo button is for, and which find a Remove button removes.
- No white flash before the first frame; landscape keeps a readable width.
- The dev build names its weekly backups `collectibles-dev-auto-...`, so it never prunes the real app's backups (#42).
- Tested in a 15-round overnight review on an Android 16 emulator with the S23's screen; records in `Resources/Verification/overnight-2026-11-06/` (outside the repo).

## 2026-11-03: Trip day (#36, round 5)
- "Nearby" lists the collections with something to get within 30 km, nearest first, each with what to get next; the rest fold into one line.
- An item's page leads with "Directions" (with the distance) and "Call"; a station says which lines it is on; hours come before the address.
- "My book" rows open the item instead of logging it; a find can be removed on the item page, after a confirm.
- Stamp books show how far each station still to get is, and mark the nearest.
- Manhole cards are named by town and design where the city publishes it (the five Nagaoka cards in the catalogue, Tsubame's new card).
- Large text: the next item's distance on Nearby stays visible, and stamp books use two columns so station names are not split.
- Notes on how this design round went are kept in the progress tracker, for improving the design process.
- Far from every collection (at home, say), "Nearby" lists all of them instead of an empty "Around here". With large text, "Call" moves under "Directions" instead of being squeezed.

## 2026-11-03: Run-through fixes (#36)
- With no catalogue loaded, "Nearby" says so and offers to load one, instead of "Nothing left to get near here".
- Right after allowing location, "Nearby" says it is finding your position instead of "Location off".
- Large text: the distance on "Nearby" and in the "+" list stays visible, "My book" keeps its title on one line, and seal labels fit.
- In English, an item's page shows the prefecture in English and dates as 2026-07-31; your record says "place saved" with a Map button instead of coordinates.
- "Mark ones I already have": the button asks you to choose first, and the + button is hidden meanwhile.
- Back arrows are named for screen readers. Station notes are written for you, with the map credit.
- Checked on the Poco in the test copy: empty start, loading the catalogue, Nearby from a real position.
- On the Poco's real app since 2026-11-03, with the owner's collection kept (21 finds).
- Removing a "had before the app" mark now asks first; it used to happen on one tap with no way back.

## 2026-11-03: Restore and weekly backup (on the real app since 2026-11-03)
- Restore from a backup file, with a copy of your current data saved to Downloads first; a damaged or newer backup is refused and nothing changes.
- Backups now confirm every write is included before copying the database.
- Optional weekly backup to a folder you pick, keeping the last 4.
- After a restore, photos are matched to their finds again; a photo with two equal matches is marked missing rather than guessed.
- Loading a catalogue counts a station on two lines once.
- Backups no longer wait for a quiet moment, so one can't fail because the app is busy.
- Removing "had before the app" from an item is now written to the log.
- About credits OpenStreetMap for the station positions.
- Weekly backup writes its first file once and shows the last backup date as soon as it finishes; the folder picker starts in Documents; Restore is shown in red.
- Testing: 21 unit tests pass. All 15 device tests pass on the Legion's Android 14 emulator, and restoring a real backup and pruning old ones were checked by hand there. On the Poco's dev app, a real backup restored correctly and a photo came back after an uninstall and reinstall. The release build is on the Poco; weekly backup is off there until a folder is picked, so it has only run on the emulator.

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
- The app is now called Meguri (巡り, going round a circuit); the GitHub repo is renamed to match, with a full README and an MIT license.
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
