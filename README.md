<div align="center">

<img src="docs/images/icon.png" width="96" alt="Meguri icon">

# Meguri

**Collect all your favourite Japanese collectibles in one place.**<br>
Manhole cards, station stamps, Ekitag, goshuin, dam cards, roadside station stamps, and whatever else Japan hands out to people who go looking.

[![Latest release](https://img.shields.io/github/v/release/thisisartman/Meguri?label=release)](https://github.com/thisisartman/Meguri/releases/latest)
[![Build](https://github.com/thisisartman/Meguri/actions/workflows/build.yml/badge.svg)](https://github.com/thisisartman/Meguri/actions/workflows/build.yml)
[![Licence: MIT](https://img.shields.io/badge/licence-MIT-blue)](LICENSE)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84)

[<img src="docs/images/badge_obtainium.png" alt="Get it on Obtainium" height="48">](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522io.github.thisisartman.collectibles%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252Fthisisartman%252FMeguri%2522%252C%2522author%2522%253A%2522thisisartman%2522%252C%2522name%2522%253A%2522Meguri%2522%257D)

**[Download the latest APK](https://github.com/thisisartman/Meguri/releases/latest)** · [How to use it](docs/APP_GUIDE.md) · [Roadmap](ROADMAP.md) · [日本語](#日本語)

</div>

The name comes from *meguri* (巡り): going round a circuit, the way you would on a pilgrimage or a stamp rally.

<p>
  <img src="docs/screenshots/01-nearby.webp" width="200" alt="Nearby: a map with the closest cards and stamps, and a sheet of round tiles">
  <img src="docs/screenshots/03-my-book.webp" width="200" alt="My book: your totals, a map of your finds, and a progress ring for each category">
  <img src="docs/screenshots/05-card.webp" width="200" alt="A manhole card's page: its picture, opening hours, and where it's handed out">
  <img src="docs/screenshots/06-station.webp" width="200" alt="A station's page: its stamp and its Ekitag together">
</p>

## Why Meguri

You're out wandering somewhere and you wonder: is there a manhole card around here? Does this station have a stamp? Is the shrine down the road handing out goshuin today?

The answers are out there, but they're scattered across dozens of websites and apps, and the ones that exist are often hard to use or hard to read. What you really want is simple: to see where everything is, so you can plan a day around it, and to keep track of what you've already got. And when you head somewhere off the beaten track, you don't want to be stuck waiting for a signal.

Meguri is made for exactly that. Everything is on your phone, logging a find takes one tap, and it all works offline.

## What you can do

- **Nearby:** what's closest, on a map and in a list, with directions.
- **My book:** a progress ring for each category, by prefecture or railway line.
- **Item pages:** picture, where to get it, phone, and opening hours, including "Open now" and holidays.
- **Photos:** of the card, the cover, the stamp or an Ekitag, kept inside the app.
- **Alerts:** a nudge when you pass something you don't have yet.
- **Share to Meguri:** photos from your gallery, matched to what they show.
- **Trips:** plan a day by railway line or Google Maps link, then get a report.
- **Backups:** to a folder you choose, weekly if you like.
- English and Japanese, light and dark.

## Where it works

Meguri works one prefecture at a time, and each prefecture has two parts:

- **The catalogue:** every card, stamp and goshuin spot in that prefecture, with where to get it and when it's open. It's only a few KB. The first time you open Meguri, you pick the prefectures you collect in and their catalogues download.
- **The offline map (optional):** a full map of the prefecture, 36 to 43 MB, so the map works with no signal at all. You can download it from Settings › Catalogue › Regions; without it, Meguri uses online map tiles.

You can add or remove prefectures any time in Regions, and when the data changes, "Update all" fetches the new version. No app update is needed. The data lives in its own repository, [meguri-data](https://github.com/thisisartman/meguri-data).

Right now the catalogue covers **Niigata, Saitama and Tokyo** (manhole cards, station stamps, dam cards and roadside stations), plus Niigata's Ekitag stations and the Joetsu Line stations in Gunma. The rest of Japan comes as people help fill it in (see [Helping out](#helping-out)).

## Getting the app

Meguri isn't on Google Play.

**With Obtainium (recommended):** [Obtainium](https://obtainium.imranr.dev) installs Meguri from this page and tells you when there's an update.

[<img src="docs/images/badge_obtainium.png" alt="Get it on Obtainium" height="48">](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522io.github.thisisartman.collectibles%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252Fthisisartman%252FMeguri%2522%252C%2522author%2522%253A%2522thisisartman%2522%252C%2522name%2522%253A%2522Meguri%2522%257D)

**By hand:**

1. On your phone, open the [latest release](https://github.com/thisisartman/Meguri/releases/latest) and download `Meguri-<version>.apk`.
2. Open the downloaded file and follow Android's prompts to allow the install.

**Which phones:** Android 10 or newer, on a 64-bit ARM processor (`arm64-v8a`). That's almost every Android phone from the last several years; very old 32-bit phones and Intel-based devices can't run it.

**Updating:** new versions install over the old one and keep all your data. It's still a good idea to back up first (Settings › Backup and data › Back up now).

**Is it the real one?** Every release is signed with the same key. Its signing certificate's SHA-256 is:

```
06:7F:32:50:E8:46:AA:0B:E0:B6:A4:8B:89:3B:21:B7:56:8E:1C:5E:9C:FC:17:D9:30:A8:63:F9:40:38:79:13
```

You can check it on your phone with [AppVerifier](https://github.com/soupslurpr/AppVerifier), or on a computer with `apksigner verify --print-certs`.

## Learning the app

The [app guide](docs/APP_GUIDE.md) walks through every page and feature, with screenshots.

## Your privacy

Meguri has no accounts and no servers. Your finds, photos and backups stay on your phone and in the folders you choose, and nothing is collected about you: no analytics, no ads, no tracking. Meguri only goes online when you use something that needs it, like map tiles outside your offline maps or a region download.

[PRIVACY.md](PRIVACY.md) lists every online service, what it receives, and every permission the app asks for and why.

Card pictures belong to GKP and each city. They're only downloaded to your phone when you ask, and they're never put in backups.

## Building it yourself

You'll need [JDK 17](https://adoptium.net/) and the [Android SDK](https://developer.android.com/studio) (installing Android Studio gives you both). Then:

```sh
git clone https://github.com/thisisartman/Meguri.git
cd Meguri
./gradlew assembleDebug testDebugUnitTest
```

On Windows, use `gradlew.bat` instead of `./gradlew`. The first build downloads its tools and takes a while.

The app ends up in `app/build/outputs/apk/debug/app-debug.apk`. Install it with `adb install app/build/outputs/apk/debug/app-debug.apk`, or open the project in Android Studio and press Run. The debug build installs as a separate app, "Meguri (dev)", so it never touches your real collection.

Region packs are built with the scripts in `tools/packs/` (see its README). Every pull request is built and tested automatically.

## Why I made this

I made Meguri for myself. I collect these things, and I wanted one place on my phone where I could look back and say: I went to this railway station once, and this shrine, and I got a stamp here and a goshuin there, all laid out on a map and by date, a bit like Google Photos does for pictures (something I've always been fascinated by). Meguri has made my own trips easier, and that was the whole point.

I'm not a programmer. I'm a product tester and a UI/UX designer, and what I'm good at is working out how something should flow and feel. Meguri was built with a lot of help from Claude, an AI model, which wrote most of the code while I designed the app, decided how it should work, and tested every version on my own phone and trips.

I believe in open source. AI like the one that helped build this was trained on open source work, and that's one of the main reasons Meguri is open source too, and free. I don't expect to make any money from it. If someone else finds it fun or useful, that's more than enough.

Information like this can't come from one person, though. Opening hours change, new cards come out, stamps get retired. The catalogue only gets better if collectors help keep it right, so I'm looking forward to working with everyone out there who enjoys this as much as I do.

## Helping out

Help the catalogue grow and stay right:

- [Add something](https://github.com/thisisartman/Meguri/issues/new?template=new_place.yml): a new place or collectible
- [Availability](https://github.com/thisisartman/Meguri/issues/new?template=availability.yml): sold out, back, new hours, moved, gone
- [Correction](https://github.com/thisisartman/Meguri/issues/new?template=data_correction.yml): wrong location, name or details

Seen it too? A 👍 confirms it, and confirmed changes ship in the next data update. More in [CONTRIBUTING.md](CONTRIBUTING.md), plans in the [roadmap](ROADMAP.md). Security problems go privately through [SECURITY.md](SECURITY.md).

## Thanks

- Map data © OpenStreetMap contributors (ODbL), tiles by [Protomaps](https://protomaps.com), drawn with [MapLibre](https://maplibre.org)
- Station positions from OpenStreetMap, dam cards from the Ministry of Land, Infrastructure, Transport and Tourism, and Ekitag stations from JR East's announcements
- Manhole card pictures via [駅からマンホール](https://ekikaramanhole.whitebeach.org/ext/manholecard/)
- Icons from Mapbox Maki and Temaki (CC0), and the M PLUS Rounded 1c font (OFL)

## License

Meguri is free and open source under the MIT licence. See [LICENSE](LICENSE).

## 日本語

Meguri（巡り）は、マンホールカード、駅スタンプ、エキタグ、御朱印、ダムカード、道の駅スタンプなど、日本のさまざまな「集めもの」をひとつにまとめるアプリです。近くで集められるもの、もう集めたもの、まだ集めていないものがどこにあるかが、ひと目でわかります。アカウントもサーバーも使わず、記録や写真はすべてお使いの端末の中に保存されます。電波のない場所でも使えます。

[最新版のAPKはこちらからダウンロードできます](https://github.com/thisisartman/Meguri/releases/latest)（Android 10以降、64ビットARM）。アプリは日本語と英語に対応しています。新しいスポットや集めもの、配布状況の変化、データの誤りに気づいたら、[Issues](https://github.com/thisisartman/Meguri/issues/new/choose) か、アプリの「フィードバックを送る」から気軽にお知らせください。
