# Third-party licences

## Fonts

LockApp bundles two typefaces as font resources (`app/src/main/res/font/`). Both are
licensed under the **SIL Open Font License, Version 1.1**, which permits bundling in an
application, including a commercial one, provided the licence travels with them and the
fonts are not sold on their own.

| Family | Files | Copyright |
|---|---|---|
| Instrument Sans | `instrument_sans_regular.ttf`, `instrument_sans_medium.ttf`, `instrument_sans_semibold.ttf` | Copyright © The Instrument Sans Project Authors |
| Instrument Serif | `instrument_serif_regular.ttf` | Copyright © The Instrument Serif Project Authors |

Both are available from Google Fonts, where the full licence text is published with each
family. The copies here are the Latin subsets served by the Google Fonts API, so the
system font still supplies glyphs for scripts they do not cover — which is what we want,
since the app shows the names of whatever apps are installed.

> **Still to do:** the OFL asks that the licence accompany the fonts. Shipping this file
> in the repository covers the source, but an **Open source licences** entry under
> Settings → About would cover the distributed app as well. Worth adding before release.

## Libraries

AndroidX, Material Components, Kotlin and the Jetpack libraries in `app/build.gradle.kts`
are Apache 2.0. CameraX, Security-Crypto and Biometric are likewise Apache 2.0.
