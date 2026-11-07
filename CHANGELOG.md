# Changelog

## 2026-11-07: next beta (not released)
- No more flash of the welcome screen on the first open after an update.
- A short vibration when you tap a pin, a tile, Collect or "No stamp here".
- Trip mode is now called Proximity mode.
- After an update, the app shows what's new, once.
- Alerts: a distance for each kind, and station stamps alert only at stations you get off at in today's trip.
- Settings is a short list of sections, each showing what is set and opening its own page.
- Photos: pick a photo's kind from a dropdown, "Use as display picture", "Remove photo"; the Photo button opens a list with an icon for each kind.
- Sorting shows "near me" for Nearest and A over Z for A to Z; the hint is two lines.
- Search: the sheet tucks away as soon as you tap search, and results show only in the dropdown.
- Every Niigata manhole card is in the catalogue: 48 cards (31 new), each with its hand-out place, address, phone and hours. Cards you already had now show where they're handed out too. Shibata D has no confirmed place yet.
- Alerts have two buttons, "Collect" and "Dismiss". "Collect" marks it collected and opens its page, where you can add photos or take it back.
- A "You" page (#72), from the top-right menu: how many finds and kinds you have and how many today, a small map of where you collected ("Open map" shows them full screen), progress for each collection, and your own collections (moved here from Settings).
- Saitama and Tokyo manhole cards are in the catalogue too: 73 in Saitama and 101 in Tokyo, each with where to get it and when. Your Tokyo cards now show their hand-out places. Gold special editions and promotion cards are left out, as they are no longer handed out; Hachioji A has no confirmed place yet.
- Photos are kept inside Meguri (#79): each photo gets Meguri's own copy, 1600 px and saved as WebP, so clearing the gallery loses nothing. Photos you already have are copied in on the first start. A picture picked from the gallery no longer shows up twice there; one you take with the camera keeps its full-size original in the gallery. Weekly backups and the safety copy made before a restore now include photos.
- Roadmap brought up to date (`docs/ROADMAP.md`): what's done, what's in this beta, and what's next.
- Research, not in the app yet: photo copies in WebP come out 14–30% smaller than JPEG on real card photos, with text just as readable. Card pictures are GKP's and the cities', so they can't ship inside the app (details in PROGRESS.md).

## 2026-11-07: 0.3.0-beta.5 (sent to the owner on Telegram, 15:10)
- "No stamp here": mark a station (or any place) where there was nothing to get. It stops showing as "still to get" and stops counting against its line, and "There is one" takes it back.
- Choose which photo stands for an entry: tap a photo and "Use as this entry's picture".
- Adding a photo: clearer full-width buttons, the camera first.
- New item: search OpenStreetMap straight away; picking a place fills in its Japanese and English names.
- Google Maps links: a shared place link works (it becomes where you're going), and station names like "Nagaoka Station Niigata" now match the right station.

## 2026-11-07: 0.3.0-beta.4
- Sent to the owner on Telegram, to install over the current app after a backup.
- The offline map now covers all of Niigata (with Sado), Saitama and Tokyo prefectures.
- Restoring onto a new phone no longer risks linking a find to another picture that happens to have the same gallery number; restored pictures appear in the gallery only once fully written.
- A test now checks that moving to the new database version keeps every find, including ones marked "had before the app".

## 2026-11-06: Version 0.3.0, from the owner's trip feedback (built, not yet on a phone)
- Planned: a map on Nearby, in the layout the owner picked (the map fills the screen, with a sheet of Stamps and Cards on top), a colour setting, a welcome screen, several photos per find and plain English names. Plan in `docs/V0.3_PLAN.md`.
- Tried on an emulator, not yet on a phone: Nearby is now a map with a sheet of round tiles (Stamps and Cards tabs, filters by kind, nearest or A to Z, three sizes); tap a pin for Details and Directions; a full-screen map per tab.
- The map works offline around the trip area (IUJ and Urasa to Niigata City) and uses online map tiles elsewhere. The app goes online for map tiles only.
- Place names show on the map in your language (English mode adds the Japanese name). Dark mode works, with readable roads, and "still to get" pins are no longer the brightest thing on a dark map. Pins shrink when zoomed out, so they don't hide town names.
- Several photos per find, each marked as stamp, proof or other; a photo from the gallery is copied into the app's own folder.
- Merged from the Legion: the "seal gold" colours with a switch back to the phone's own colours, a welcome screen that loads the Niigata catalogue in one tap, and "Station stamps" in English.
- Waiting on the owner: region packs, sample links, temple symbol, pin card choice. Work continues on the Predator.
- Draft PR #70 into `testing` for beta.3. Three versions of a compact pin card were drawn for the owner to choose from (not built yet).
- **0.3.0-beta.3** (versionCode 5) carries phases 2 to 5 below. The database moves to version 2, so it cannot go back to beta.2; back up first.
- **Phase 5 (in the next build): new kinds and official symbols.**
  - Dam cards: all 32 handed out in Niigata, with where and when to get them (from the MLIT list).
  - Museum stamps, for ones you add yourself.
  - Goshuin show the official map symbol: a torii for shrines, 卍 for temples.
- **Phase 4b (in the next build): Trip report.** From Trip, pick a day you collected on and share it as a story image (portrait) or a CSV for a spreadsheet.
- **Phase 4a (in the next build): Trip, in the top-right menu.**
  - Pick where you start and end, or paste a Google Maps directions link. The app finds the train (Shinkansen or local lines), every station it passes, and what is within 2 km of each.
  - Station stamps count only where you tap "Get off"; anything else near the line always counts.
  - "Start trip" turns on Trip mode for the day, and its notification says where you get off next.
  - "Already went" ticks what you got on a past trip, on the day you pick.
  - "Share" sends the plan as a message.
  - The Joetsu Shinkansen is added to the bundled lines.
- **Phase 3 (in the next build):** "Back up with photos" in Settings, for a new phone: the backup carries every photo, and restoring it puts them back in the gallery. On the same phone nothing is duplicated.
- **Phase 2 (in the next build):**
  - Your own collections: make one in Settings (name, colour, icon, whether you note what you paid); add items to it; they get a "Mine" tab on Nearby and a section in My book.
  - Each collection can count duplicates (×2) and, for goshuin, what you paid: tap the line on the item page.
  - "Collection check" ticks off what you already have, one group per page, swiping left and right, manhole cards first, with Undo.
  - Adding a new item can find its place on OpenStreetMap.
  - Database version 2 adds these without touching anything you already have.
- **0.3.0-beta.2** (phase 1 of the feedback build):
  - The item page puts "Where to get it" right under the name, as rows with icons: hours, place, phone (tap to call), notes, and a note when this place's map isn't saved on the phone.
  - Photos are a grid that shows every kind.
  - Tapping a pin shows the status apart from the distance, with an arrow pointing to the place (it turns with the phone).
  - Search results list the Japanese name, prefecture, the matched letters in bold, and the distance with an arrow; they stay open after the keyboard closes, and "Show on map" fits the map to them.
  - Directions open walking routes under 2 km and public transport beyond.
  - Opening a page no longer shows the map through it, and coming back to Nearby keeps the map where it was, with no light flash in dark mode.
  - Names wrap between words.
- Research: a UI analysis of Google Maps and Organic Maps place pages on the Poco (`docs/research/ui-analysis-map-apps-2026-11-07.md`).
- Map labels show place names in one language (no Japanese line under English names); the Japanese name is on the pin card and the item page instead.
- 0.3.0-beta.1, the first beta, merged into `testing` (2026-11-07). Installed as the Poco's real app the same night, over 0.0.1, after a backup; all 21 of the owner's finds came through.
- Planned from the owner's answers (2026-11-07, 03:20):
  - Collection check swipes left and right, starting with manhole cards;
  - cost is recorded on goshuin only;
  - region downloads go by prefecture and city;
  - the trip report is an image (and CSV);
  - Trip mode folds into route planning;
  - duplicates are to be planned;
  - no storage location.
- Planned from the owner's answers: route trips count station stamps only when you confirm you'll get off, within 2 km; the stock-take flow is called "Collection check" (#68); collections will record what you paid.
- Things you had before the app show "Added 3 Nov 2026" (the day they went in); lists show "—" where there is no date.
- Planning: Meguri follows the ethos of inventory management: everything you have counts, taking stock is easy, collecting is one tap, counts are right, nothing is lost (`docs/PLAN-inventory.md`). The app has no changes from this yet.
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
