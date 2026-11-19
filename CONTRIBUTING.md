# Contributing to Meguri

Thank you for helping. Meguri is made by one person in their spare time, so replies can take a few days.

## Reporting a bug or wrong data

- **Bugs:** open a [bug report](https://github.com/thisisartman/Meguri/issues/new?template=bug_report.yml). The version, your phone, the steps and an exported log (Settings › Help and about › Logs) make it much faster to fix.
- **A card, stamp or place is wrong or missing:** open a [catalogue correction](https://github.com/thisisartman/Meguri/issues/new?template=data_correction.yml) with a source if you have one.
- **Ideas:** open a [feature request](https://github.com/thisisartman/Meguri/issues/new?template=feature_request.yml).
- You can also send feedback from inside the app: Settings › Help and about › Send feedback.

## Changing the code

1. Open an issue first, or comment on an existing one, so we agree on the change before you spend time on it.
2. Branch from `testing`, not `main`. `main` only receives releases.
3. Build and test before opening a pull request:

   ```sh
   ./gradlew assembleDebug testDebugUnitTest
   ```

   The debug build installs as a separate app, "Meguri (dev)", so it never touches your own collection.
4. Keep a pull request to one change, and add before and after screenshots for anything visible.

You need JDK 17 and the Android SDK. Android Studio works, as does the command line.

## The catalogue and map data

The catalogue (cards, stamps, stations, dams, roadside stations) and the offline maps are built by the scripts in `tools/` and published per prefecture in [meguri-data](https://github.com/thisisartman/meguri-data). The CSV format is described in [docs/CSV_FORMAT.md](docs/CSV_FORMAT.md). Data changes start as a catalogue correction issue.

## Translations

The app is in English and Japanese (`app/src/main/res/values` and `values-ja`). Corrections to the Japanese are very welcome as a pull request or an issue.

## Licence

By contributing you agree that your contribution is released under the [MIT licence](LICENSE).
