# Contributing to Meguri

Thanks for wanting to help! Meguri is a one-person project built in spare time, so replies can take a few days, but every report and idea gets read.

## Found a bug or a mistake?

- **Something isn't working:** open a [bug report](https://github.com/thisisartman/Meguri/issues/new?template=bug_report.yml). It helps a lot to include your Meguri version, your phone, the steps to make it happen, and the app's log (Settings › Help and about › Logs › Export logs).
- **Something new to collect:** use [Add a place or a collectible](https://github.com/thisisartman/Meguri/issues/new?template=new_place.yml), for a new spot or something new at a place Meguri already knows.
- **Sold out, back again, new hours, or gone:** use [Report availability](https://github.com/thisisartman/Meguri/issues/new?template=availability.yml).
- **Wrong details:** use [Correct something](https://github.com/thisisartman/Meguri/issues/new?template=data_correction.yml). A link to an official page, or the date you saw it in person, makes it easy to fix.
- **You have an idea:** open a [feature request](https://github.com/thisisartman/Meguri/issues/new?template=feature_request.yml).
- **Rather not use GitHub?** You can also send feedback from inside the app: Settings › Help and about › Send feedback.

## Want to change the code?

1. Start by opening an issue, or commenting on an existing one, so we can agree on the change before you put time into it.
2. Branch from `testing`. The `main` branch only gets releases.
3. Before opening a pull request, make sure the app builds and the tests pass:

   ```sh
   ./gradlew assembleDebug testDebugUnitTest
   ```

   The debug build installs as a separate app, "Meguri (dev)", so it never touches your own collection.
4. Keep each pull request to one change, and add before and after screenshots for anything you can see in the app.

You'll need JDK 17 and the Android SDK. Android Studio works fine, and so does the command line.

## How the catalogue grows

Meguri's catalogue is only as good as what collectors report, so every addition and change goes through the same simple steps:

1. **Someone reports it** with one of the three forms above. The issue starts with the label `needs confirmation`.
2. **Someone else confirms it.** If you've seen the same thing, give the issue a 👍 or leave a comment like "Still there on 3 December". One confirmation is usually enough; an official page or notice counts as one on its own.
3. **It's marked `confirmed`** and added to the catalogue files.
4. **It ships in the next data update.** The catalogue is published by prefecture in [meguri-data](https://github.com/thisisartman/meguri-data), and everyone gets it from Settings › Catalogue › Regions › "Update all", with no app update needed.

You can follow every report on the [project board](https://github.com/users/thisisartman/projects/2). Reporting straight from the app, with the same confirmation step, is on the [roadmap](ROADMAP.md).

The catalogue and the offline maps are built by the scripts in `tools/`, and [docs/CSV_FORMAT.md](docs/CSV_FORMAT.md) describes the format.

## Labels

Issues are sorted with a few labels, so you can find what you're interested in:

- **What it is:** `bug`, `enhancement` (a new feature or an improvement), `catalogue` (places, cards, stamps and their details), `documentation`
- **Catalogue reports:** `new place`, `availability`, `correction`, and their state, `needs confirmation` or `confirmed`
- **Where in the app:** `area: nearby`, `area: map`, `area: my book`, `area: trip`, `area: scan`, `area: alerts`, `area: photos`, `area: backup`, `area: settings`, `area: catalogue data`, `area: repo`
- **Planning:** `roadmap` for planned work, and a milestone for the version it's aimed at
- **Want to help?** Look for `help wanted` and `good first issue`

## Translations

Meguri is in English and Japanese (`app/src/main/res/values` and `values-ja`). If something in the Japanese reads oddly, a pull request or an issue is very welcome.

## Licence

By contributing, you agree that your work is released under the [MIT licence](LICENSE).
