# CrummyStock

An offline-first stock and sales tracker for a small shop, built with Jetpack Compose.

Everything lives on the device. There is no account, no backend, no analytics and
no crash reporting — the only network traffic the app can produce is an
explicitly-started backup transfer between two devices on the same Wi-Fi.

## Features

- **Inventory** — products with barcode or QR codes, photos, categories, tags,
  stock counts and low/out-of-stock filtering.
- **Sales** — a cart that applies promotions automatically, with receipt rendering,
  undo, and cancellable sales.
- **Promotions** — category- or product-scoped discounts, either a fixed amount or
  a percentage, with optional start/end scheduling.
- **Supplies** — materials, providers and purchase entries with cost tracking.
- **Reports** — daily and ranged reports, charts, and CSV/JSON export.
- **Backup** — a full export/import archive, plus device-to-device transfer over
  the local network.
- **Personalisation** — background, panel, button, highlight and text colours; a
  set of bundled fonts; text size; motion speed; and a custom sale animation.

## Building

Requires Android Studio with support for AGP 9 and JDK 25. `compileSdk` is 37,
`minSdk` is 24.

```bash
./gradlew app:assembleDebug
./gradlew app:testDebugUnitTest
```

For release builds see [`CONTRIBUTING.md`](CONTRIBUTING.md) — they are unsigned
unless you provide your own key via `keystore.properties`.

## Architecture

The app is a single-module Compose application with a deliberately small
dependency set:

```
app/src/main/java/com/example/stockapplication/
├── data/            domain models, Room database, repository, import/export
│   └── transfer/    local-network backup server and uploader
└── ui/
    ├── components/  shared composables (dialogs, pickers, charts, scanner)
    ├── screens/     one package per feature area
    ├── theme/       colour-scheme resolution and typography
    └── viewmodel/   screen state holders
```

Two things worth knowing before changing anything:

- **Theming is computed, not hard-coded.** `resolveColorScheme` layers the user's
  five colour channels onto a base palette, and `highlightedScheme` derives the
  Material slots from them. Text colour deliberately wins over every accent so
  labels never follow a button colour. If you add a composable that reads
  `colorScheme.error` for something other than an inline warning, check it
  against a dark panel — `error` is the one slot the theme never derives.
- **Promotions are evaluated in `PromoEngine`**, which is pure and heavily tested.
  Discounts are stored as basis points and cents, never floats.

## Licence

Released under the **GNU Affero General Public License v3.0**. See [`LICENSE`](LICENSE).

AGPL means that anyone who distributes this app — or runs a modified version of
it as a network service — must release their source under the same terms.

Third-party components keep their own licences; see [`NOTICE`](NOTICE) and
`app/src/main/assets/font-licences/` for the bundled fonts.
