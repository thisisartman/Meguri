# Changelog

## Next (after beta.9)
- Map: the land is a shade apart from the app (lighter in dark mode, slightly darker in light mode), so the search bar and the sheet have a clear edge. Roads in dark mode are lighter to match.
- + opens a small card just above it with three square buttons (Scan, Search, Add); the bottom bar stays usable. + again, a tap outside or Back closes it.
- (Earlier today: three large buttons in a sheet.) Scan, Search, Add. Add opens an empty form: a name, card or stamp, and its kind. A kind you type that Meguri doesn't have becomes a kind of its own, with its own place in My book.
- Ekitag screenshots: the stamp is cut out by its shape and set on a rounded square in its colour, with the station's name on two lines (your language first), in M PLUS Rounded 1c.
- Scan a card: trimmed to the top card when the scanner takes in the cards under it or the box (Tokyo's lighter frames too), and turned so its code sits top right, as on every card. Checked on 11 of the owner's 27 card photos, the rest running.
- Your own kinds show their own name at the top of an item's page, not "My collection".
- Not released yet: these go out as 0.4.0 after the owner's testing of beta.9.
- Scan a card: a card scanned upside down (or sideways) is turned the right way up, by finding which way its code reads.
- Photo dialog: what "Remove photo" does is now a tooltip on the button (long press), not a line of text.
- Photos: Meguri's own copy is now 3000 px (was 1600), so full screen and zoom stay sharp. Photos added before keep their size.

## 2026-11-09: Commit history cleaned
- Claude lines removed from every commit message on every branch. Code unchanged. Commit IDs changed: see the map in `Backups/`.
- Draft PR #70 is now #82.

## 2026-11-08: Poco updated to beta.9, test checklist
- The Poco real app went to beta.9 after another backup: 21 finds kept, no crash.
- `docs/TEST_CHECKLIST.md`: 31 things to test, for the owner and friends.

## 2026-11-08: Poco updated to beta.8
- The Poco real app went from beta.1 to beta.8 after a backup (21 finds). All 21 finds were kept, the catalogue grew to 754 items, no crash.

## 2026-11-08: 0.3.0-beta.9 (versionCode 11), from the owner's S23 feedback on beta.8
- **Card lists:** a grid like everywhere else, with short names ("Saitama-ken A001"); only two filters, Have and To get, both on by default (no All, no series).
- **Display picture by itself:** the first photo of an item's main kind becomes its display picture when it has none: a manhole card's cover, a station's Ekitag screenshot, a goshuin itself. Also when a photo's kind is changed to that.
- **Manhole covers cut to a circle:**
  - A photo filed as a manhole cover is cut out as a circle, using a small circle finder written for this, no library. It finds the lid's outer rim.
  - "Scan a card or cover": with no card code but a clear circle, it is a cover, filed on the card you pick from the nearest three. It becomes the display picture.
  - Seen on the Poco.
- **Crop accuracy** of card scans is still to tune with the owner's card photos.
- Sent on Telegram at 21:20, with a copy stored on the Predator.

## 2026-11-08: 0.3.0-beta.8 (versionCode 10)
- Carries Scan a card and Card pictures (below). Same database as beta.7, so not one-way from beta.7 (one-way from beta.6).
- Fixed before release: the release build crashed when starting the card scanner (the code shrinker removed parts of ML Kit). It now keeps ML Kit's classes.
  - Checked on the emulator (no crash) and on the Poco with a new `devRelease` build: the release code under the dev app's name and debug key. It read 15-202-A001.

## Next (after beta.7)
- Scan a card (M7 auto-crop, M8 recognition): + → "Scan a card".
  - Google's on-device document scanner finds the card's edges and straightens it (corners can be moved, or a gallery photo imported).
  - The Japanese text reader reads the printed code, and the matching card is offered: "Collect with this photo" (filed as the card's photo) or "Open". No code: "Pick by hand" opens the share screen.
  - Both run on the phone through Play services. The reader downloads once in the background; until then the app says so.
  - This replaces the planned OpenCV crop (+15 MB) with a scanner of well under 1 MB.
  - Seen on the Poco: 15-202-A001 read from a 240 px picture and matched to Nagaoka · Kaen doki.
- Card pictures (#53): Settings → Catalogue → Card pictures downloads each manhole card's picture onto this phone, one at a time, from Eki kara Manhole. They are for your own use only, never in the app or backups (© GKP and each city). Tiles show the card greyed until collected; your own photo still comes first; the item page's card opens full screen. Seen on the Poco: 222 of 222.
- Settings → Catalogue now says what is built in (Niigata, Saitama, Tokyo; all types).

## 2026-11-08: 0.3.0-beta.7 (versionCode 9), for the S23 and friends' testing
- Checked before release:
  - 111 unit and 28 device tests pass.
  - Fresh install walked on the emulator: onboarding, region, Collect, Undo.
  - Upgrade rehearsed on a copy of the real Poco backup: schema 1→4; all 21 finds and every item kept; no duplicate keys; 545 new items.
  - Release build over beta.6 on the emulator: What's new shows; Trip from a Tokyo→Urasa link; report shares; no crash.
  - Real phones: the Poco's and the S23's dev copies upgrade and import without errors.
- One-way: the database moves to version 4 (item editing). Back up first; beta.6 can't take it back.
- Catalogue (#65, #55, #22):
  - JR East station stamps for Saitama and Tokyo: 217 stations on 19 lines, in running order; the Joetsu Shinkansen now runs from Tokyo (12 stations).
  - Dam cards for Saitama (12) and Tokyo (2) from the MLIT list.
  - A new type, roadside stations (道の駅): 64 stamps in Niigata (42), Saitama (21) and Tokyo (1), from MLIT's list of 2026-09-04. Positions are from OpenStreetMap; two have none yet.
- Item page photos: the squares size to the screen, five to a line.
- Region list: "1 Roadside station stamp", not "1 Roadside stations".
- Photos larger: tapping a photo on an item page shows it big and whole, with a full-screen button; full screen zooms with two fingers or a double tap. The picture at the top of an item page opens full screen too.
- Photo suggestions (M7): with Settings → Alerts → "Suggest new photos" on (it asks for photo access), a photo taken with the camera within 300 m of something you have not collected brings a quiet notification: "Attach" collects it on the photo's day with the photo, "Pick another" opens the share screen. Off by default.
- Timeline (M6): My book → Timeline lists everything you collected, by day, with times; a day opens the trip report.
- List filters (M6): each prefecture's list has All / To get / Have, and the card series when there are several.
- Map on opening: frames you and the nearby pins above the sheet, even when the pins load a moment late (it had opened at street level with nothing in view, Poco).
- Photos: a picture the app can no longer read shows the seal instead of a grey box.
- What's new: rewritten for beta.7; its button is full height.
- Meguri opens on a map of Japan and flies in to where you are a moment after your position arrives; without a position it stays on Japan.
- First open (#81): instead of the welcome card, the Nearby sheet asks whether you're starting fresh or already have some, lists the permissions Meguri needs and why, then asks for them. Then you pick where you collect: your own prefecture first, then popular choices, shaded on the map as you tick them, or all of Japan. Only the chosen regions are loaded, and later catalogue updates keep to them. "Already have some" then opens a picker: search, a grid of places, tap one and tick what you have there (a station's stamp and Ekitag on one card). After each pick the search text is selected, so one backspace clears it for the next name. What you add stacks up above Done; Done marks them as had before the app, with a vibration, and the sheet says how many went to your profile, with "Go to profile".
- Onboarding:
  - On the very first open, "Meguri / 巡り" fades into the map of Japan.
  - In the picker, the keyboard stays up after OK, with the search text selected, so typing the next name replaces it.
  - The card opens beside a tile in the outer columns, and above or below one in the middle columns.
  - The last thing added shows by name and type.
  - Pulling the picker down shows the map with what the search found, framed above the sheet.
  - Back steps through the onboarding instead of leaving it.
  - The search bar is hidden while it runs.
- My book and the You page are one page, called My book: your finds, collections started and today's finds, the map of where you collected and what you collected last are at the top, with the collections below. Your own collections are back in Settings.
- The search bar is only search now; the menu (Trip, Collection check, Settings, Logs) is the ⋮ button on My book.
- The map is flat: land in the app's own colour, water in a soft tint of the accent, no relief texture, parks or buildings; the same online and offline, light and dark.
- The pin card's icons are all one size.
- Edit an item's details: the pencil on an item page opens its names, where to get it, address, hours, notes and position. The position can be where you are, a pasted Google Maps link (a map view, a place or a short link) or a place search. Your edits stay when the catalogue is updated. (Database version 4; existing items move over unchanged.)
- My book in sections (your pick B1): under the totals and the map, one tile per type (Manhole cards, Station stamps, Ekitag, Goshuin, Dam cards, Museum stamps, and each of your own collections), each with a ring for how far you are. A tile opens that type: its own map, its collections as rings (by line for stamps, by prefecture for the rest) with the next one to get, and what you collected last. A ring opens the full book. No long lists on My book itself.
- Share photos to Meguri (#80, your pick "Groups, then a sheet"):
  - Meguri is in Android's share sheet for one photo or several.
  - The photos are grouped by where they were taken, and each group shows its best guess: the nearest stamp, card or goshuin. "Change" opens a sheet with search, types and what's near where they were taken; "Leave these out" skips a group.
  - "Add all" puts each group's photos on that item, filed as Other photos to sort later. An item you already have keeps its find and gains the photos; one you don't have is collected on the day and at the place the photos were taken.
  - Photos with no location get no guess; you choose. A photo whose location was blanked on the way out (your S23 poster photo via Google Photos) counts as having none. Some galleries hide where a photo was taken; Meguri then says so and offers "Allow" (photo access and photo locations) instead of asking by itself.
- An Ekitag's tile says so ("Niitsu Station · Ekitag"), since it shares its station's name and spot.
- Niigata catalogue checked against the official sources:
  - Ekitag: 20 Niigata stations added, from JR East's Niigata branch announcements (2024-03, 2024-07 and 2024-11).
  - Kamitokoro Station (opened 2025-03) added to the Echigo Line, between Hakusan and Niigata.
  - Unchanged: the order of every line, every station's position, and the 32 dam cards (checked against the January 2026 list).
  - Still open: no official list of which stations have a paper stamp, and no permanent list of museum stamps.
- Roadmap brought up to date (`docs/ROADMAP.md`): beta.6 in Done, what beta.7 holds, and the order of what's next.
- Faster start: the catalogue file is no longer read on every start when nothing has changed, and the counts on the tabs stay blank (not 0 / 0) until your list has loaded. Loading a catalogue writes in one batch, and the region list's counts are kept after the first time.
- The onboarding sheet sits at the height of what it shows and snaps there, with no empty space under its buttons.
- Testing: after the onboarding, all 97 unit tests and 26 phone-level tests still pass; both onboarding paths were walked through on the emulator.
- Location is no longer asked for the moment the app opens on a new phone; the onboarding asks after saying why. "What's new" waits until there is a catalogue.
- Fixed: the welcome card no longer flashes on every start (it read "loaded" before the list had arrived), and "Nothing here yet" waits for the list too.
- Fixed: "What's new" now lists this version's changes; beta.6 showed beta.5's list.

## 2026-11-07: 0.3.0-beta.6 (versionCode 8, sent to the owner on Telegram, 23:58)
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
- Fixed before release: the map on the "You" page showed nothing and collection names showed as codes; a pin card for a pin under the search bar covered the bar.
- Testing: all 26 phone-level tests now run and pass on the main laptop's emulator, including the new photo tests and the database upgrade tests.
- Getting ready to share photos into Meguri (#80): photos taken at one place are grouped together and matched to the nearest item. Not in the app's screens yet; the design round on how several items are grouped is out.
- Tapping a pin opens a compact one-line card that grows out of the pin and stays on it as the map moves (#76): the kind, name and distance, then Directions and Collect as icons. Tap the name for the full page.
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
