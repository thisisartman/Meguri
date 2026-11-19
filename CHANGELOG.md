# Changelog

All notable changes to Meguri are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Added
- Project pages for contributors: a README with download link and the signing certificate to check an APK, a Japanese summary, contributing, privacy and security guides, issue forms (bug, feature, catalogue correction) and store text with screenshots in English and Japanese.

### Changed
- Privacy page lists region downloads with the other online services, and names every permission the app uses.
- Send feedback and Edit: Send, Cancel and Save stay on screen above the keyboard; the form scrolls under them.

### Fixed
- Something you added (a Place, a stamp) now goes under its prefecture in My book, found from its position, instead of "No prefecture". Items added earlier are sorted the next time the app opens.
- A map already on screen when a map download finished (such as My book's small map) now redraws from the new map files.

## [0.5.0] - 2026-11-15

### Added
- Region downloads: each prefecture is its own download in Settings › Catalogue › Regions, with what you can collect there and, if you want it, its offline map (36 to 43 MB). "Update all" when something changed; "Remove" keeps your finds, photos and edits. The first-open region step downloads too. Prefectures on your phone come first.
- Opening hours: an item says whether it is open ("Open until 17:00", "Closed now · opens at 9:30", "Closed today"), in Japan time, with national holidays and the "closed Monday, or the next day when Monday is a holiday" rule. Trip stops you haven't collected warn when they are closed today. Hours Meguri can't read for certain show no status rather than a wrong one.
- Places: a new type for somewhere with more than one thing to collect (for example a stamp and a goshuin), with its own "Places" tile in My book. Add one from Add › Category › "Place".
- Change the type of something you added yourself from the pencil on its page; its photos stay.
- Send feedback (Settings › Help and about): write a note, add screenshots and send it with your mail app, with the app version, phone model and, if ticked, the app log.
- Scan: a code that isn't in Meguri (often one misread digit) offers the closest cards ("Or is it ...?"), "Type code" and "Pick by hand". Pick by hand's "Near you" looks 50 km around you when the photo has no place.
- Motion: pages slide in and out; onboarding and Collection check steps fade into place; choice cards show their press.

### Changed
- The app is 28 MB (0.4.0 was 134 MB): the offline map no longer comes inside the app. Updating from 0.4.0 keeps everything; the old built-in map keeps working until each prefecture's map is downloaded, then it is removed.
- One station page: a station's stamp and its Ekitag open the same page, with an Ekitag section that has its own Collect and finds. Collecting it says "Collected Urasa Station · Ekitag".
- Map: zoomed out, every marker shows its category icon; tapping a group glides in with its pins already drawn; groups are a little larger and easier to tap. Water is plain grey and light-mode land a neutral light grey.
- Nearby: the category filter is the same pill and menu as on Alerts ("All categories" or one category). Search results use it too.
- Manhole cards, dam cards and the other categories list prefectures you have started first; station stamps list lines you have started first, then the nearest.
- What's new has a new layout: icon, title, the version in a pill and the notes in a scrolling box.
- The photo slot "Where you got it" is now "Location".
- A card's page shows its code once.

### Fixed
- First-open picker: Nearest starts at the top; tapping a place puts the keyboard away; OK clears the search; Done stays above the keyboard.
- Nearby: Back clears a typed search before it leaves the app.
- Collection check offers the card picture download while pictures are missing.
- Card pictures: no "Downloading 0 of 0"; a failed download says to tap Card pictures to try again; the source site's "image coming soon" picture is no longer kept.
- Road number signs show their number again on the offline map.
- Ekitag pictures: a saved stamp image now fills its tile correctly.
- The Nagaoka mascot card is spelled "Kawagutchi" (かわぐっち).
- The app log names each page with its real arguments.

## [0.4.0] - 2026-11-09

### Added
- Notebook stamps: scan a notebook page and Meguri boxes each stamp (move, resize, remove or add boxes), cleans it, reads the station name and picks the station when sure, suggests it when fairly sure, or lets you search. Saved as had before Meguri; the date is optional.
- Collection check is a card deck: choose a category and a prefecture, then swipe right ("Have it") or left ("Not yet") through what you don't have yet, with Undo.
- Scan a card: trims to the top card, turns it so the code reads the right way up, offers "Or is it ...?" for look-alike codes, and offers Retake photo or Type code when the code can't be read. The first time you type a code, a short animation shows where it is on the card.
- Item page: Remove item (for something you added) or Remove from my collection (for a catalogue item), both asking first.
- Settings: Theme (Phone, Light or Dark; the map follows it), Colours (Meguri or Wallpaper) and Language as one-tap choices.
- Categories: rename one, change its colour or icon, or remove it when empty. A type you write on the Add form that Meguri doesn't have becomes a category of its own.
- Backup and data: Back up now (asks Without photos or With photos), a Weekly backup page (folder, Include photos, keep the last 2, 4 or 8), Restore, Suggest new photos, and Photos kept in Meguri (how many and how much space).
- About page: version (tap to copy), credits for map and data sources, fonts and icons with their licences, third-party licences and privacy.
- Logs record activity in Developer mode (Help and about › About); errors and crashes are always recorded.

### Changed
- + opens a small card with Scan, Search and Add; the bottom bar stays usable.
- Map: each pin is the item's picture in a ring of its category colour; places with many items show a gold circle with a count; items on the same spot sit side by side. The land is a shade apart from the app so the search bar and sheet stand out.
- Nearby: a station's stamp and its Ekitag are one tile, with a badge that fills in once you have the Ekitag. Shorter tile names; empty tiles show their category icon in grey. Distances update as you move.
- Alerts, simpler: When to alert is Off, Travelling or Always; one slider for How close (everything starts at 500 m, station stamps at 200 m), and any category can keep its own distance. Always no longer stops Proximity mode.
- Search is forgiving everywhere: any part of a card code, full-width typing, hiragana or katakana, either language, the town; best match first, then nearest. Search results name their category.
- What you collect is called a category everywhere. Wording: Collected and Not collected, Roadside station, Travelling alerts, "added to My book".
- Settings list: an icon and a summary of what is set for each section.
- Manhole cards show the design as the title, with place and code below.
- Photos: Meguri's own copy is 3000 px, so full screen and zoom stay sharp; an Other photo can be named; Remove photo asks first.
- Dates follow the phone's date style everywhere.
- Backups, restores, card picture downloads and CSV imports keep running after you leave Settings. Files Meguri saves are named meguri-.

### Fixed
- Scan: a collected card keeps where you were, with Undo; the full screen button works.
- Nearby: Back closes a tapped pin's card first; location is asked for once per visit.
- Search: letters in order count as an abbreviation only from the start of a name.
- Taps vibrate on more phones.

## [0.3.0] - 2026-11-08

### Added
- Nearby is a map with a sheet of round tiles (Stamps and Cards tabs, filters, nearest or A to Z, three tile sizes). Tapping a pin opens a compact card with Directions and Collect.
- First open: Meguri explains the permissions it needs, lets you pick the prefectures you collect in, and, if you already have some, a picker to tick what you have.
- Trip: pick where you start and end, or paste a Google Maps directions link; Meguri finds the train, every station on the way and what is within 2 km. Station stamps count only where you tap "Get off". Start trip turns on Proximity mode, which says where you get off next.
- Trip report: share a day you collected on as a story image or a CSV.
- Timeline in My book lists everything you collected, by day.
- Scan a card: the on-device scanner finds the card, reads its printed code and offers the matching card to collect with the photo. A photo filed as a manhole cover is cut out as a circle.
- Card pictures: Settings › Catalogue › Card pictures downloads each manhole card's picture onto your phone, for your own use only.
- Share photos to Meguri from Android's share sheet: photos are grouped by where they were taken and matched to the nearest item.
- Photo suggestions (off by default): a camera photo taken near something you haven't collected brings a quiet notification to attach it.
- Your own collections, with duplicate counts and, for goshuin, what you paid.
- Collection check, to tick off what you already have.
- "No stamp here" marks a place where there was nothing to get.
- Edit an item's details (names, where to get it, hours, notes, position); your edits stay when the catalogue is updated.
- Several photos per find, kept as Meguri's own copies so clearing the gallery loses nothing. Choose which photo is the display picture, or let the first matching photo become it.
- New categories: dam cards, museum stamps and roadside stations. Goshuin show the official map symbol (torii or 卍).
- Catalogue: every Niigata, Saitama and Tokyo manhole card with where to get it; JR East station stamps for Saitama and Tokyo; dam cards for Saitama and Tokyo; 20 Niigata Ekitag stations; Kamitokoro Station.
- Back up with photos, for moving to a new phone.
- What's new shows once after an update.

### Changed
- My book is one page with your totals, a map of where you collected and one tile per category with a progress ring.
- The bottom bar is Nearby, a gold + for logging a find, and My book. The menu (Trip, Collection check, Settings, Logs) is on My book.
- Buttons say "Collect" and "Collect again".
- Settings is a short list of sections, each opening its own page; Alerts have a distance for each category.
- The offline map covers all of Niigata (with Sado), Saitama and Tokyo; it is flat and the same online and offline, light and dark.
- Search is forgiving (spaces, hyphens, long vowels, small typos, Japanese) and searches every name and code.
- The item page puts "Where to get it" right under the name; Directions open the place in Google Maps.
- Faster start.
- The database is upgraded when you update; back up first.

### Fixed
- The release build no longer crashes when starting the card scanner.
- Restoring onto a new phone no longer links a find to the wrong picture.
- The welcome screen no longer flashes on start after an update.
- What's new lists the current version's changes.

## [0.2.0] - 2026-11-06

### Added
- Alerts near things still to get: Trip mode (checks your position until midnight, from More or a quick-settings tile) and alerts while the app is closed. Got it, Photo and Dismiss on the notification. Distance 200 m to 1 km, a switch per type, quiet at night, once a visit.
- More says when background alerts can't work and opens the right settings page; "Keep alerts working" opens battery settings.

### Changed
- Nearby says why there is no position, with the right button, and says when nothing is within 30 km.
- My book: sections fold, headers stay at the top, complete collections show a check.
- One button and chip style, with visible borders and 48 dp touch targets.
- Distances under 25 m read "here".
- Japanese mode reads more naturally.
- Backups name the file they saved; the weekly backup dialog says its folder and how many it keeps.

### Fixed
- "Approximate" location now gives a position.
- A double tap on Got it logs one find, not two.
- After the camera closes, "Logged ... Undo" shows again.
- Reloading the same catalogue says it is up to date.
- Screen readers hear which item a button is for.
- No white flash before the first frame.

## [0.1.0] - 2026-11-03

### Added
- Log a find with "Got it" (time and position saved, Undo for a few seconds), with or without a photo.
- Nearby: the nearest things you still need, and your collections with progress.
- My book: manhole cards by prefecture, station stamps as a stamp book per railway line, goshuin; mark the ones you already have in one go.
- Add a goshuin, stamp or card that isn't in the list.
- Back up all finds to a file; restore from a backup (your current data is saved first); optional weekly backup to a folder you pick.
- Load a catalogue from a CSV file. Catalogue for Niigata (with Nagaoka, Tsubame, Niigata City, Sado and Kashiwazaki cards and Yahiko, Echigo and Shin'etsu Line stamp books) and Tokyo.
- English or Japanese, chosen in the app.
- An item's page with Directions and Call; a station says which lines it is on.
- Logs screen with Export logs; errors and crashes are saved on the phone.

### Changed
- The app is called Meguri (巡り, going round a circuit), under the MIT license.
- No internet permission; Android backup and device-to-device transfer are off.

### Fixed
- Photos taken in the app are no longer deleted after capture.
- Nearby no longer crashes for items under 1 km.
- A find logged with a photo keeps its position.
- Large text: distances, titles and labels stay visible.
- Removing a "had before the app" mark asks first.

[0.5.0]: https://github.com/thisisartman/Meguri/releases/tag/v0.5.0
