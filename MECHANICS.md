# Mechanics — HealthScan (original clean-room design)

This is an **original** reimplementation of a product-health-scanner concept
(scan/enter a product → 0–100 health score → breakdown → better alternatives).
No values below are taken from any decompiled binary. The commercial app this
workspace points at is protected by Google Play licensing/anti-tamper
(`com.pairip.LicenseClient`, `libpairipcore.so`); its internals were not read or
copied. Every number here is my own design choice and is marked `estimated`.

## Scoring model (my own)

Overall score is 0–100, higher = healthier, shown on a red→orange→yellow→green band.

### Food score (weighted sum, estimated)
- Nutrition quality: 0–60 points (60% weight) — from sugar, saturated fat, salt, fiber, protein.
  - Start 60, subtract: sugar_g * 1.2, satfat_g * 1.5, salt_g * 6.0.
  - Add: fiber_g * 2.0, protein_g * 1.0. Clamp 0–60.
- Additives: 0–30 points (30% weight) — start 30, subtract 6 per risky additive, 2 per minor. Clamp 0–30.
- Organic bonus: 0–10 points (10% weight) — 10 if organic flag, else 0.
- Total = clamp(nutrition + additives + organic, 0, 100).

### Cosmetic score (estimated)
- Start 100. Subtract 25 per high-hazard ingredient, 10 per moderate, 3 per low.
- Ingredients that are **not rated** subtract nothing.
- Clamp 0–100.
- When fewer than half of the ingredients are rated, the product page says
  "Based on X of Y ingredients".

### Cosmetic ingredient grades (estimated)
Open Beauty Facts lists ingredients but gives no risk grade, so HealthScan matches each
ingredient name against its own list (`IngredientGrades.java`). Names are compared without
case, accents or punctuation, and every alias counts ("Aqua (Water)", "Aqua/Water/Eau").
When several names are given, the worst grade wins. Anything not on the list is **not rated**.

| Grade | Examples | Why |
|---|---|---|
| Low | water/aqua, glycerin, sodium chloride, citric acid, panthenol, plant butters and oils, polyquaterniums | Common, well tolerated ingredients |
| Moderate | parfum/fragrance and the EU-declared fragrance allergens (limonene, linalool…), sodium lauryl/laureth sulfate, phenoxyethanol, PEG and "-eth" ingredients (e.g. ceteareth-20), methyl- and ethylparaben | Known irritants or sensitisers, or worth limiting |
| High | propyl-, butyl- and other long-chain parabens, formaldehyde and formaldehyde releasers (DMDM hydantoin, quaternium-15…), methylisothiazolinone, triclosan, benzophenone-3 | Restricted or banned in EU cosmetics, strong allergens, or suspected endocrine disruptors |

The grades are HealthScan's own simplification, informed by public sources: the EU
Cosmetics Regulation (EC) No 1223/2009 annexes (banned and restricted substances, labelled
fragrance allergens) and opinions of the EU Scientific Committee on Consumer Safety (SCCS).
They are not medical advice. Saved scans are re-graded from their ingredient names when
loaded, so changes to the list apply to past scans too.

### Rating bands (estimated)
- 75–100 → "Excellent" (green)
- 50–74  → "Good" (light green / yellow)
- 25–49  → "Poor" (orange)
- 0–24   → "Bad" (red)

### Alternatives (estimated)
- Same category, score at least 15 points higher than the scanned item, top 3 by score.
- Chosen from the products in the scan history (there is no built-in catalog).

## Product data source

Scanned products are looked up by barcode from the **open** collaborative databases:
- Open Food Facts — `https://world.openfoodfacts.org/api/v2/product/<barcode>` (food)
- Open Beauty Facts — `https://world.openbeautyfacts.org/api/v2/product/<barcode>` (cosmetics)

These are free, open-data APIs (ODbL). Their fields (nutriments, additives_tags,
labels_tags, ingredients, image_front_url) are mapped into our own model and scored with
the rules above — the scoring is ours, not theirs. Products already scanned are kept in the
on-device history, which is the offline fallback and the source for name search.
No data comes from Yuka. Network is used only on an explicit barcode scan or search, and to
load that product's photo.

## Values table
See `mechanics.json` for the machine-readable list. All marked `estimated` —
this is a fresh design, not a reproduction of the original's algorithm.

## Platform, build and test
HealthScan is an Android-only app written in Java (minSdk 24, targetSdk 34).
The build uses Gradle with the Android Gradle Plugin. Run the wrapper with JDK 17
or newer, and point `ANDROID_HOME` (or `sdk.dir` in `local.properties`) at the Android SDK.

- Unit tests (barcode reader, runs on the JVM): `./gradlew test`
- Debug APK: `./gradlew assembleDebug` → `build/outputs/apk/debug/HealthScan-debug.apk`
- Install on a connected phone: `./gradlew installDebug`
