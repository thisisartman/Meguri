# Changelog

All notable changes to Meguri are listed here, newest first. Versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each big release opens with the story behind it, then its highlights, then everything in it.

## [Unreleased]

Merged into `testing` and heading toward 0.7.1.

### Highlights
- Nearby now splits into Places and Collectibles. Every station, city office or shrine gets a ring that fills as you collect there, a tiny progress bar of joy. A help bubble explains both.
- Every station and shrine gets one page with everything you can get there, each with its own card, photos and Collect. One stop, one page, maximum efficiency.
- Got a folder full of stamp photos from last summer? Bring the whole batch in at once. Nothing gets replaced, and stamps are cropped for you automatically.
- Every find now has the date it was added. The mysterious "had before Meguri" era is officially over.
- Station stamps wear red and Ekitag teal, so you can tell a station's two halves apart at a glance. Colour coordination is a love language.
- Add photo and Collect now stay pinned at the bottom of a place page. Your thumb knows exactly where to go, even mid-sprint to catch the train.
- Tile size now lives in the sort menu, and grouping is an on/off switch there too. Everything for tidying your view sits in one spot.
- Screenshot a station's Ekitag details page and Meguri keeps just the stamp now.
- The card has these weird numbers written on it. Meguri couldn't decode those ancient runes. After much RnD, Claude & I finally cracked the code. Turns out, they were just coordinates to the manhole cover. Now that we know, you can finally visit the actual cover. Go wild. Seek out your manhole covers! (What are we doing with our lives, guys?)

### Everything in this release
- Added `Places` and `Collectibles` to Nearby. Places gives each station, city office or shrine one tile with a ring that fills as you collect what's there, and a help bubble explains both views (#192)
- Added `Group by type` to the sort menu in Places, with headings for stations, cities and towns, shrines and temples, roadside stations and dams (#192)
- Added a page for each place, with a tile for each thing you can get there; tap one to open its card with its picture, `Directions` and `Collect`, and see the photos from the whole place below (#192)
- Added `Add photos from a file` in `Settings › Backup and data`, which brings in a batch of your own photos without replacing anything and crops stamp photos to the stamp (#188)
- Added a `Cover` button beside `Directions` on manhole cards (`Dam` on dam cards) that shows how far the cover is and opens directions to it; Meguri reads the position printed on the card when you scan it, and you can type it in `Edit details` (#203)
- Changed every way of marking something you already have (first open, Collection check, My book and notebook pages) to save a find with the date it was added, so "had before Meguri" is gone (#194)
- Changed station stamps to red, so they stand apart from teal Ekitags (#192)
- Changed `Add photo` and `Collect` to stay pinned at the bottom of a place page
- Changed `Tile size` to live in the sort menu (#192)
- Fixed an Ekitag screenshot of a station's details page saving the whole screen as the stamp (#190)

## [0.7.0] - 2026-11-25

Remember the first region showing up half built, like flat-pack furniture with the screws missing? And maps opening on the whole planet whenever you wandered far from your finds? After much RnD, 0.7.0 tidies all of that up, and it brings friends. (Your wallet has been notified.)

### Highlights
- Ibaraki, Kyoto, Osaka and Aichi are new regions to download. Your weekend plans just got a lot more expensive. They work in 0.6.0 too. So, you can waste money while using a dated release too.
- Ekitags can alert you now, at the stations you get off at on today's trip. Categories with nothing on your phone stay out of the alert list, so no ghost options.
- Switch it on and Meguri checks once a day whether a newer version is out, and tells you. It never downloads or installs anything by itself. Polite king behaviour.
- Setting up for the first time? The region step now offers the offline map too, already ticked, same as in Regions. One less trip to Settings.
- Backups now carry every detail in their readable spreadsheet files, plus a list of your categories. Your data, fully portable, fully yours.
- Times follow your phone's 12 or 24-hour setting on every screen. Team 5 PM and team 17:00 can finally live in peace.
- Back up now is a single dialog with an Include photos switch, so backing up takes about as long as saying "itadakimasu".
- Settings keeps its bar and Back button on screen while you scroll. Scrolling all the way back up is for peasants.
- The first region you loaded sometimes came in only partly, like a jigsaw with missing pieces. Now every pack loads, and Regions tells you when part of a prefecture is missing, with "Download the rest".
- Maps used to open on the whole planet when you were far from your finds. Now they frame your finds, because you're the main character here.

### Everything in this release
- Added Ibaraki, Kyoto, Osaka and Aichi to `Settings › Catalogue › Regions`; they download in 0.6.0 too
- Added Ekitags to alerts under the station rule, so they alert only at stations you get off at on today's trip; categories with nothing on your phone stay out of the alert list (#138)
- Added `Check for updates` in `Help and about`, off until you switch it on; once a day Meguri asks GitHub whether a newer release is out and tells you, and it never downloads or installs anything (#131)
- Added the offline map to the first-open region step, ticked as in `Regions` (#135)
- Added every detail to the readable spreadsheet files inside a backup, plus a list of your categories (#170)
- Changed the wording so each thing has one word, with `Collect` and `Collect again` everywhere and "find" for what you collected (#143, #144)
- Changed times to follow your phone's 12 or 24-hour setting on every screen (#145)
- Changed `Back up now` to one dialog with an `Include photos` switch (#163)
- Changed Settings pages to keep their bar and `Back` on screen while the page scrolls (#141)
- Changed the map credits to open from a bigger, easier-to-tap `Map data` button (#152)
- Changed explanations to be shorter where the controls already say it (#148, #149)
- Fixed the first region you load coming in only in part; every pack loads now, and `Regions` says when part of a prefecture is missing, with `Download the rest` (#134)
- Fixed maps opening on the whole world when you were far from your finds; they frame your finds now (#136)
- Fixed card pictures the card site hasn't put up yet counting as failures every time (#160)
- Fixed a display picture still pointing at a removed photo; it is cleared now, and the item can go with its region (#168)
- Fixed station totals and line pages counting differently from My book; totals also say how many have no stamp (#156)
- Fixed road numbers shared by two routes showing as "8;17"; they show one number now (#150)
- Fixed stutters at start, while scanning and in the log, where Meguri did heavy work while drawing the screen (#166)

## [0.6.0] - 2026-11-20

The weekly backup lived on the same phone it was meant to protect. One puddle, one banana peel, and it would all be gone. Meanwhile the keyboard kept eating the Save button. (It was getting fat.)

### Highlights
- Backups sitting on the same phone that falls in the puddle drove me nuts. Now the weekly backup asks where to go, whether that's a folder, Google Drive or another cloud app, or your own server.
- Backing up to my own server with no way to bring it back felt like a prank. Now you can restore from your server too.
- Every time a stamp moved or a shrine stopped doing goshuin, I had nowhere to say so. Now an item's ⋮ menu lets you report it, send a correction or suggest something new.
- Watching the keyboard eat the Save button was my villain origin story. Send, Cancel and Save now stay on screen above the keyboard.
- I wanted to know exactly what the app talks to and why. The Privacy page now lists every online service and permission.
- Places I added myself kept ending up in the wrong spot in My book. Now they sit under their prefecture, where they belong.
- Downloading a map and still staring at the old one was a special kind of sad. Maps now redraw right after a map download.

### Everything in this release
- Added a choice of place for the weekly backup, with a folder, one file in Google Drive or another cloud app (replaced each week), or your own server such as Nextcloud over HTTPS (#3)
- Added `Restore` from your own server (#3)
- Added reporting from an item's `⋮` menu, to say whether it is still there, send a correction or suggest something new
- Changed `Send`, `Cancel` and `Save` to stay on screen above the keyboard (#16)
- Changed the Privacy page to list every online service and permission (#15)
- Fixed places you add yourself landing outside their prefecture in My book (#17)
- Fixed maps not redrawing after a map download (#18)

## [0.5.0] - 2026-11-15

Meguri weighed 134 MB, which is a lot for an app about stamps. Everyone got every map, even people who had never left Niigata. Time for a diet.

### Highlights
- It bugged me that everyone got every map whether they wanted it or not. Now each prefecture is its own download, offline map optional, and "Remove" keeps your finds, photos and edits.
- Nothing ruins a day like reaching a shrine that closed ten minutes ago. Items now say whether they're open, in Japan time with holidays counted, and trip stops warn you when they're closed today.
- Some spots have a stamp and a goshuin, and cramming them into one type annoyed me. Places is a new type for exactly that, with its own tile in My book.
- I picked the wrong type once and had to add the whole thing again. Now the pencil on its page changes the type, and the photos stay put.
- Writing bug reports with screenshots scattered everywhere got old fast. Send feedback puts your note, screenshots and app details into one mail.
- One misread digit and the scanner just shrugged at me. Now it offers the closest cards ("Or is it ...?"), lets you type the code, or pick it by hand.
- The app felt like a slideshow from 2009. Pages slide in and out now, steps fade into place and choice cards show their press.
- A 134 MB app for collecting stamps was honestly embarrassing. Meguri is 28 MB now because the offline map is its own download, and updating keeps everything.
- A station's stamp and its Ekitag living on separate pages made zero sense to me. Now they share one page, and the Ekitag gets its own Collect.
- Zoomed out, the map was a blob of identical dots. Now every marker shows its category icon, and groups are bigger and easier to tap.

### Everything in this release
- Added region downloads in `Settings › Catalogue › Regions`; each prefecture is its own download with what you can collect there and, if you want it, its offline map (36 to 43 MB). `Update all` fetches what changed, `Remove` keeps your finds, photos and edits, the first-open region step downloads too, and prefectures on your phone come first (#19)
- Added opening hours to items, such as "Open until 17:00", "Closed now · opens at 9:30" or "Closed today", in Japan time with national holidays and the "closed Monday, or the next day when Monday is a holiday" rule; trip stops you haven't collected warn when they are closed today, and hours Meguri can't read for certain show no status (#20)
- Added Places, a new type for somewhere with more than one thing to collect (a stamp and a goshuin, say), with its own `Places` tile in My book; add one from `Add › Category › Place` (#21)
- Added changing the type of something you added yourself, from the pencil on its page; its photos stay (#22)
- Added `Send feedback` in `Settings › Help and about`, which puts your note, screenshots, the app version, phone model and, if ticked, the app log into one mail (#23)
- Added `Or is it ...?`, `Type code` and `Pick by hand` to Scan when a code isn't in Meguri (often one misread digit); `Near you` in `Pick by hand` looks 50 km around you when the photo has no place (#24)
- Added motion, with pages sliding in and out, onboarding and Collection check steps fading into place and choice cards showing their press (#25)
- Changed the app to 28 MB, down from 134 MB in 0.4.0, by moving the offline map into its own download; updating from 0.4.0 keeps everything, and the old built-in map keeps working until each prefecture's map is downloaded (#26)
- Changed a station's stamp and its Ekitag to open the same page, with an Ekitag section that has its own `Collect` and finds; collecting it says "Collected Urasa Station · Ekitag" (#27)
- Changed the map so that zoomed out, every marker shows its category icon, a tapped group glides in with its pins already drawn, and groups are larger and easier to tap; water is plain grey and light-mode land a neutral light grey (#28)
- Changed the Nearby category filter to the same pill and menu as Alerts (`All categories` or one category), in search results too (#29)
- Changed manhole cards, dam cards and the other categories to list prefectures you have started first; station stamps list lines you have started first, then the nearest (#30)
- Changed What's new to a new layout with an icon, a title, the version in a pill and the notes in a scrolling box (#31)
- Changed the photo slot `Where you got it` to `Location` (#32)
- Changed a card's page to show its code once (#33)
- Fixed the first-open picker; `Nearest` starts at the top, tapping a place puts the keyboard away, `OK` clears the search and `Done` stays above the keyboard (#34)
- Fixed `Back` in Nearby leaving the app with a typed search still there; it clears the search first (#35)
- Fixed Collection check not offering the card picture download while pictures are missing (#36)
- Fixed card pictures saying "Downloading 0 of 0"; a failed download says to tap `Card pictures` to try again, and the card site's "image coming soon" picture is no longer kept (#37)
- Fixed road number signs showing blank on the offline map (#38)
- Fixed a saved Ekitag stamp image not filling its tile (#39)
- Fixed the spelling of the Nagaoka mascot card, "Kawagutchi" (かわぐっち) (#40)
- Fixed the app log naming pages vaguely; each page is logged with what it opened on (#41)

## [0.4.0] - 2026-11-09

I had months of station stamps sitting in paper notebooks, and typing them in one by one was a hard no. So Meguri had to learn to read.

### Highlights
- Typing in every stamp from my old notebooks was killing me. Now you scan a page and Meguri boxes each stamp, cleans it up and finds the station, or asks when unsure.
- Ticking off a giant list of what I already had felt like doing taxes. Now it's a card deck you swipe, right for "Have it" and left for "Not yet", with Undo for butterfingers.
- Card scans kept failing on sideways or stacked cards. Now the scanner trims to the top card, turns it the right way up, and offers to retake or type the code.
- Something I added by mistake just sat there judging me, with no way to get rid of it. Now the item page can remove it, after asking first.
- Digging around for theme and language settings got on my nerves. Theme, Colours and Language are one-tap choices now, and the map follows your theme.
- I made a category with a typo in it and couldn't rename it. Now you can rename, recolour, change the icon or remove an empty one. Because ARTman learns from his mistakes.
- Backups felt like a mystery box, and I like knowing where my stuff is. Backup and data now has Back up now, a weekly backup page, Restore and how much space your photos take.
- The map and data people deserved credit. The About page has the version, credits, licences and privacy in one place.
- Logs record activity only in Developer mode, and errors and crashes are always kept. Nerds only, move along.
- The + button took over the whole screen when I just wanted to scan something. Now it opens a small card with Scan, Search and Add, and the bottom bar stays usable.

### Everything in this release
- Added scanning a notebook page of stamps. Meguri boxes each stamp (move, resize, remove or add boxes), cleans it, reads the station name and picks the station when sure, suggests one when fairly sure, or lets you search; they save as had before Meguri, with an optional date (#42)
- Added a card deck to Collection check; choose a category and a prefecture, then swipe right (`Have it`) or left (`Not yet`) through what you don't have yet, with `Undo` (#43)
- Added trimming to the top card when you scan a card, turning it so the code reads the right way up, `Or is it ...?` for look-alike codes, and `Retake photo` or `Type code` when the code can't be read; the first time you type a code, a short animation shows where it is on the card (#44)
- Added `Remove item` (for something you added) and `Remove from my collection` (for a catalogue item) to the item page, both asking first (#45)
- Added `Theme` (Phone, Light or Dark, and the map follows it), `Colours` (Meguri or Wallpaper) and `Language` to Settings as one-tap choices (#46)
- Added renaming a category, changing its colour or icon, and removing it when empty; a type you write on the Add form that Meguri doesn't have becomes a category of its own (#47)
- Added `Backup and data`, with `Back up now` (asks `Without photos` or `With photos`), a `Weekly backup` page (folder, `Include photos`, keep the last 2, 4 or 8), `Restore`, `Suggest new photos` and `Photos kept in Meguri` (how many and how much space) (#48)
- Added an About page with the version (tap to copy), credits for map and data sources, fonts and icons with their licences, third-party licences and privacy (#49)
- Added activity logs in Developer mode (`Help and about › About`); errors and crashes are always recorded (#50)
- Changed `+` to open a small card with `Scan`, `Search` and `Add`, so the bottom bar stays usable (#51)
- Changed map pins to the item's picture in a ring of its category colour; places with many items show a gold circle with a count, items on the same spot sit side by side, and the land is a shade apart from the app so the search bar and sheet stand out (#52)
- Changed a station's stamp and its Ekitag to one tile in Nearby, with a badge that fills in once you have the Ekitag; tile names are shorter, empty tiles show their category icon in grey, and distances update as you move (#53)
- Changed Alerts to be simpler; `When to alert` is `Off`, `Travelling` or `Always`, one `How close` slider sets the distance (500 m to start, 200 m for station stamps), any category can keep its own, and `Always` leaves Proximity mode running (#54)
- Changed search to forgive everywhere, matching any part of a card code, full-width typing, hiragana or katakana, either language and the town, best match first, then nearest; results name their category (#55)
- Changed the word for what you collect to "category" everywhere, with clearer wording such as `Collected` and `Not collected`, Roadside station, Travelling alerts and "added to My book" (#56)
- Changed the Settings list to show an icon and a summary of what is set in each section (#57)
- Changed manhole cards to show the design as the title, with place and code below (#58)
- Changed photos so Meguri's own copy is 3000 pixels across and stays sharp full screen and zoomed in; an `Other` photo can be named, and `Remove photo` asks first (#59)
- Changed dates to follow the phone's date style everywhere (#60)
- Changed backups, restores, card picture downloads and catalogue imports to keep running after you leave Settings; files Meguri saves start with "meguri-" (#61)
- Fixed Scan losing your place after you collect a card; it keeps where you were, with `Undo`, and the full screen button works (#62)
- Fixed `Back` in Nearby closing the page while a tapped pin's card was open; it closes the card first, and location is asked for once per visit (#63)
- Fixed search treating letters in order as an abbreviation from the middle of a name; it counts them only from the start (#64)
- Fixed taps not vibrating on some phones (#65)

## [0.3.0] - 2026-11-08

Planning one collecting day took three apps, a train timetable and an alarming amount of squinting. Then came typing every card code by hand. Something had to give.

### Highlights
- On the map, you can now tap on the items and go to their pages to add them easily.
- Getting thrown into the app with no idea why it wanted my location was weird. Now the first open explains the permissions, lets you pick your prefectures and tick what you already have.
- Working out which stamps were on my train route took three apps and forever. Now you pick a start and end, or paste a Google Maps link, and Meguri finds every station and what's within 2 km.
- I had a great collecting day and nothing to show off. Now you can share it as a story image or a spreadsheet file.
- Remembering what I collected on which day was hopeless. The Timeline in My book lists everything by day, like a diary that writes itself.
- Typing card codes by hand was the most boring part of the hobby. Now the scanner reads the printed code and offers the matching card, and manhole cover photos get cut into a circle.
- A manhole card list with no pictures is just a boring list. Now you can download each card's picture onto your phone, for your own use only.
- Adding photos one at a time after a long trip was soul-crushing. Now you can share a pile from your gallery, and Meguri groups them by place and matches each to the nearest item.
- I kept forgetting to attach the photo I'd just taken. Turn this on and a camera photo near something you haven't collected brings a quiet nudge to attach it.
- I collect things the app had never heard of, which felt lonely. Now you can make your own collections, count duplicates, and keep track of what you paid for goshuin.

### Everything in this release
- Added a map to Nearby with a sheet of round tiles (`Stamps` and `Cards` tabs, filters, nearest or A to Z, three tile sizes); tapping a pin opens a small card with `Directions` and `Collect` (#66)
- Added a first-open guide that explains the permissions Meguri needs, lets you pick the prefectures you collect in and, if you already have some, offers a picker to tick what you have (#67)
- Added Trip. Pick where you start and end, or paste a Google Maps directions link, and Meguri finds the train, every station on the way and what is within 2 km; station stamps count only where you tap `Get off`, and `Start trip` turns on Proximity mode, which says where you get off next (#68)
- Added the trip report, which shares a day you collected on as a story image or a spreadsheet file (#69)
- Added Timeline in My book, listing everything you collected by day (#70)
- Added card scanning; the scanner on your phone finds the card, reads its printed code and offers the matching card to collect with the photo, and a photo filed as a manhole cover is cut out as a circle (#71)
- Added `Card pictures` in `Settings › Catalogue`, which downloads each manhole card's picture onto your phone, for your own use only (#72)
- Added sharing photos to Meguri from Android's share menu; photos are grouped by where they were taken and matched to the nearest item (#73)
- Added photo suggestions, off at first; a camera photo taken near something you haven't collected brings a quiet notification to attach it (#74)
- Added your own collections, with duplicate counts and, for goshuin, what you paid (#75)
- Added Collection check, to tick off what you already have (#76)
- Added `No stamp here`, to mark a place where there was nothing to get (#77)
- Added editing an item's details (names, where to get it, hours, notes, position); your edits stay when the catalogue is updated (#78)
- Added several photos per find, kept as Meguri's own copies so clearing the gallery loses nothing; choose the display picture, or let the first matching photo become it (#79)
- Added dam cards, museum stamps and roadside stations as categories; goshuin show the official map symbol (torii or 卍) (#80)
- Added to the catalogue every Niigata, Saitama and Tokyo manhole card with where to get it, JR East station stamps and dam cards for Saitama and Tokyo, 20 Niigata Ekitag stations and Kamitokoro Station (#81)
- Added backing up with photos, for moving to a new phone (#82)
- Added What's new, shown once after an update (#83)
- Changed My book to one page with your totals, a map of where you collected and one tile per category with a progress ring (#84)
- Changed the bottom bar to Nearby, a gold `+` for logging a find, and My book; the menu (Trip, Collection check, Settings, Logs) moved to My book (#85)
- Changed the buttons to say `Collect` and `Collect again` (#86)
- Changed Settings to a short list of sections, each opening its own page; Alerts have a distance for each category (#87)
- Changed the offline map to cover all of Niigata (with Sado), Saitama and Tokyo, and to look the same online and offline, light and dark (#88)
- Changed search to forgive spaces, hyphens, long vowels, small typos and Japanese, across every name and code (#89)
- Changed the item page to put `Where to get it` right under the name; `Directions` opens the place in Google Maps (#90)
- Changed start-up to be faster (#91)
- Changed the database, which upgrades when you update; back up first (#92)
- Fixed the release build crashing when starting the card scanner (#93)
- Fixed a restore onto a new phone linking a find to the wrong picture (#94)
- Fixed the welcome screen flashing on start after an update (#95)
- Fixed What's new listing older changes; it lists the current version's now (#96)

## [0.2.0] - 2026-11-06

Picture strolling right past a manhole card office with your phone in your pocket, and only finding out from the map that evening. Devastating, right?

### Highlights
- Royalty got better by enabling Trip mode to receive alerts whenever there’s anything nearby. Because we cool like that.
- Background alerts quietly stopped working and I had no clue why. Now More tells you when they can't work and opens the right settings page, battery settings included.
- Nearby just showed nothing when it had no position, and I'd stare at it confused. Now it says why, gives you the right button, and tells you when nothing is within 30 km.
- Your book looks better now, because that’s what bros do for one another.
- Every button looked a bit different and my thumb kept missing the tiny ones. Now buttons and chips share one style, with visible borders and bigger spots to tap.
- Being told something was 7 m away while standing on it felt silly. Anything under 25 m now just says "here".
- Japanese mode read like a robot translated it, and it made me cringe. It reads more naturally now. Yokatta.
- Backups went somewhere and I never knew where. Now they name the file they saved, and the weekly backup says its folder and how many it keeps.
- Choosing "Approximate" location gave no position at all, which is approximately useless. Now it gives a position.
- A double tap on Got it logged the same find twice, and I am not that good. Now it logs one.

### Everything in this release
- Added alerts near things still to get, with Trip mode (checks your position until midnight, from More or a quick settings tile) and alerts while the app is closed; the notification has `Got it`, `Photo` and `Dismiss`, the distance runs from 200 m to 1 km, each type has a switch, and alerts stay quiet at night and come once a visit (#97)
- Added a note in More when background alerts can't work, with a button to the right settings page; `Keep alerts working` opens battery settings (#98)
- Changed Nearby to say why there is no position, with the right button, and to say when nothing is within 30 km (#99)
- Changed My book so sections fold, headers stay at the top and complete collections show a check (#100)
- Changed buttons and chips to one style, with visible borders and bigger spots to tap (#101)
- Changed distances under 25 m to read "here" (#102)
- Changed Japanese mode to read more naturally (#104)
- Changed backups to name the file they saved; the weekly backup dialog says its folder and how many it keeps (#105)
- Fixed `Approximate` location giving no position (#106)
- Fixed a double tap on `Got it` logging the same find twice (#107)
- Fixed "Logged ... Undo" going missing after the camera closes (#108)
- Fixed reloading the same catalogue giving no sign it was up to date (#109)
- Fixed screen readers not hearing which item a button is for (#110)
- Fixed a white flash before the first frame (#111)

## [0.1.0] - 2026-11-03

Finally got something to track my collections, hehehe. Nothing complicated, nothing full blown, just a little app for a little hobby.

Narrator: he did not, in fact, keep it little, and proceeded to spend ungodly hours on this hobby project for weeks on end.

### Highlights
- I collected a thing but the process of adding it was tedious and time-consuming. So, now, I can just mark it as collected to make sure it doesn’t cause me to lose aura points.
- Only peasants keep looking for nearby cards and stamps via kanji-heavy apps and a 100 different websites and forums. Royalty uses the nearby feature to see collectibles.
- Where can you see the stuff you collected? In your book, of course! Check the My Book page for the most clear idea of what you’ve collected, where you did and more information!
- Sometimes, you just find something at random that’s not available on the already awesome app. It’s okay. Nobody’s perfect. Now you can add it yourself.
- Oh no! Your phone fell into a puddle of water, because you slipped on a banana peel that you dropped. It’s okay. Meguri backs up your data.
- How does one find the list of stuff to collect? Import the catalogue and make your life easier.
- Oh! Nihongo ga dekimasu ka? Sasuga na! Nihongo ga jouzu desu ne!
- You can’t find the location or wanna call to check? Who you gonna call? Ask Meguri. It has an answer.
- This is just for nerds. Don’t worry about this.
- I wanted a name that felt like a trip around Japan. Meguri (巡り) means going round a circuit, and it's open for anyone under the MIT license.

### Everything in this release
- Added `Got it` to log a find, with or without a photo; the time and position are saved, with `Undo` for a few seconds (#112)
- Added Nearby, with the nearest things you still need and your collections with progress (#113)
- Added My book, with manhole cards by prefecture, station stamps as a stamp book per railway line, and goshuin; mark the ones you already have in one go (#114)
- Added a way to add a goshuin, stamp or card that isn't in the list (#115)
- Added backing up all finds to a file and restoring from a backup (your current data is saved first), with an optional weekly backup to a folder you pick (#116)
- Added loading a catalogue from a spreadsheet file, with a catalogue for Niigata (Nagaoka, Tsubame, Niigata City, Sado and Kashiwazaki cards, and Yahiko, Echigo and Shin'etsu Line stamp books) and Tokyo (#117)
- Added English or Japanese, chosen in the app (#118)
- Added an item page with `Directions` and `Call`; a station says which lines it is on (#119)
- Added a Logs screen with `Export logs`; errors and crashes are saved on the phone (#120)
- Changed the app's name to Meguri (巡り, going round a circuit), under the MIT license (#121)
- Changed Meguri to run with no internet permission, and turned off Android backup and device-to-device transfer (#122)
- Fixed photos taken in the app being deleted after capture (#123)
- Fixed Nearby crashing for items under 1 km (#124)
- Fixed a find logged with a photo losing its position (#125)
- Fixed large text hiding distances, titles and labels (#126)
- Fixed removing a "had before the app" mark without asking first (#127)

[Unreleased]: https://github.com/thisisartman/Meguri/compare/v0.7.0...testing
[0.7.0]: https://github.com/thisisartman/Meguri/releases/tag/v0.7.0
[0.6.0]: https://github.com/thisisartman/Meguri/releases/tag/v0.6.0
[0.5.0]: https://github.com/thisisartman/Meguri/releases/tag/v0.5.0
[0.4.0]: https://github.com/thisisartman/Meguri/releases/tag/v0.4.0
[0.3.0]: https://github.com/thisisartman/Meguri/releases/tag/v0.3.0
[0.2.0]: https://github.com/thisisartman/Meguri/releases/tag/v0.2.0
[0.1.0]: https://github.com/thisisartman/Meguri/releases/tag/v0.1.0
