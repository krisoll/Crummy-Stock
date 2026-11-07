# Contributing

Thanks for taking a look. This is a small, deliberately dependency-light Android
app, and contributions that keep it that way are the most welcome.

## Getting set up

1. Install Android Studio (the version shippping AGP 9 / JDK 25 is required —
   `compileSdk` is 37 and `minSdk` is 24).
2. Clone the repository and open the root folder in Android Studio.
3. Let Gradle sync. No API keys, accounts or configuration files are needed:
   the app talks to no backend, has no analytics and no crash reporting.
4. Run the `app` configuration on a device or emulator (API 24+).

`debug` builds keep every ABI, so an x86_64 emulator works out of the box.
Release builds are filtered to `arm64-v8a` and `armeabi-v7a` only.

## Building a release locally

Release builds are **unsigned** unless you supply your own key. Copy
[`keystore.properties.example`](keystore.properties.example) to
`keystore.properties` and fill it in. That file is git-ignored.

```bash
./gradlew app:assembleRelease   # universal APK
./gradlew app:bundleRelease     # .aab for Play
```

Never commit `keystore.properties`, a `.jks`, or any file containing a
password. If you ever do, treat the key as compromised and rotate it — git
history is effectively public once pushed.

## Tests

The test suite is the specification for most of the app's behaviour; please add
to it alongside a change rather than after.

```bash
./gradlew app:testDebugUnitTest
```

Unit tests run on Robolectric, so they execute on the JVM without a device.

## Style

- Kotlin, official code style (`kotlin.code.style=official`, enforced by
  `.editorconfig`-adjacent IDE settings in `.idea/codeStyles`).
- Comments in this codebase explain **why**, not what. If a decision looks
  arbitrary, it usually has a reason recorded next to it — match that.
- Prefer the platform and existing dependencies over adding a new one. Every
  library has to justify its weight in the APK.

## Pull requests

- Keep a change focused; a bug fix should not carry unrelated refactors.
- Describe the user-visible effect, and say what you tested on.
- If you touched theming, run `HighlightColourSchemeTest` and
  `ThemeColorTest` — the colour scheme has a lot of subtle invariants.

## Security

Please do not open a public issue for a security problem. Contact the
maintainer directly instead.
