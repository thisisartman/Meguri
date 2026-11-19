# Contributing to Meguri

Thanks for wanting to help! Meguri is a one-person project built in spare time, so replies can take a few days, but every report and idea gets read.

## Found a bug or a mistake?

- **Something isn't working:** open a [bug report](https://github.com/thisisartman/Meguri/issues/new?template=bug_report.yml). It helps a lot to include your Meguri version, your phone, the steps to make it happen, and the app's log (Settings › Help and about › Logs › Export logs).
- **A card, stamp or place is wrong or missing:** open a [catalogue correction](https://github.com/thisisartman/Meguri/issues/new?template=data_correction.yml). A link to an official page, or the date you saw it in person, makes it easy to fix.
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

## The catalogue and maps

The catalogue (cards, stamps, stations, dams and roadside stations) and the offline maps are built by the scripts in `tools/` and published by prefecture in [meguri-data](https://github.com/thisisartman/meguri-data). [docs/CSV_FORMAT.md](docs/CSV_FORMAT.md) describes the format. Changes to the data start as a catalogue correction issue.

## Translations

Meguri is in English and Japanese (`app/src/main/res/values` and `values-ja`). If something in the Japanese reads oddly, a pull request or an issue is very welcome.

## Licence

By contributing, you agree that your work is released under the [MIT licence](LICENSE).
