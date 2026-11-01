# Collectibles Tracker (working name)

Offline Android app for logging Japanese collectibles: manhole cards, goshuin, eki stamps and Ekitags. Kotlin and Jetpack Compose, sideloaded only.

- Spec: [`docs/PRD.md`](docs/PRD.md)
- Build plan and milestones: [`docs/BUILD_PLAN.md`](docs/BUILD_PLAN.md)
- Status: [`PROGRESS.md`](PROGRESS.md) · history: [`CHANGELOG.md`](CHANGELOG.md)

## Branches

- `main`: tagged versions only (`v1`, `v2`, ...). Each version is a checklist walked on real phones.
- `testing`: features that passed their phone check, verified together.
- `experimental/<feature>/<mini>`: work in progress. Each lands in `testing` by pull request.

Every change starts as an issue and lands through a pull request.

## Build

Open this folder in Android Studio, or from a terminal:

```
./gradlew assembleDebug testDebugUnitTest
```

The debug build installs as a separate app (`.dev` suffix), so testing never touches real data.
