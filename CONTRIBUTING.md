# Contributing to Meguri

Thanks for helping! Meguri is a one-person project, so replies can take a few days, but everything gets read.

## Reporting

| | |
|---|---|
| [Add something](https://github.com/thisisartman/Meguri/issues/new?template=new_place.yml) | A new place or collectible |
| [Availability](https://github.com/thisisartman/Meguri/issues/new?template=availability.yml) | Sold out, back, new hours, moved, gone |
| [Correction](https://github.com/thisisartman/Meguri/issues/new?template=data_correction.yml) | Wrong location, name or details |
| [Bug](https://github.com/thisisartman/Meguri/issues/new?template=bug_report.yml) | Something doesn't work |
| [Idea](https://github.com/thisisartman/Meguri/issues/new?template=feature_request.yml) | Something Meguri could do |

No GitHub account? Use Settings › Help and about › Send feedback in the app.

## How the catalogue grows

1. **Report** it with one of the forms. It starts as `needs confirmation`.
2. **Confirm** it: if you've seen it too, give it a 👍 or a comment. An official page counts too.
3. It's marked **`confirmed`** and added to the catalogue.
4. It **ships** in the next data update. Everyone gets it with Settings › Catalogue › Regions › "Update all".

Follow everything on the [project board](https://github.com/users/thisisartman/projects/2). The plan for new prefectures is in [Growing the catalogue](docs/CATALOGUE_PLAN.md).

## Labels

- **What:** `bug`, `enhancement`, `catalogue`, `documentation`
- **Catalogue:** `add`, `availability`, `correction`, then `needs confirmation` or `confirmed`
- **Where:** `area: map`, `area: my book`, `area: trip`, `area: scan`, `area: backup`, `area: settings`
- **Plans:** `roadmap`, and a milestone for the version
- **Want to help?** `help wanted`, `good first issue`

## Code

1. Open or comment on an issue first, so we agree before you start.
2. Branch from `testing` (`main` only gets releases).
3. Check it builds and passes the tests: `./gradlew assembleDebug testDebugUnitTest`
4. One change per pull request, with before and after screenshots for anything visible.

You need JDK 17 and the Android SDK. The debug build installs as "Meguri (dev)", so it never touches your own collection. Catalogue and map tools are in `tools/`; the CSV format is in [docs/CSV_FORMAT.md](docs/CSV_FORMAT.md).

## Translations

English and Japanese live in `app/src/main/res/values` and `values-ja`. Fixes to the Japanese are very welcome.

## Licence

Contributions are released under the [MIT licence](LICENSE).
