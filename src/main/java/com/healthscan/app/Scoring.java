package com.healthscan.app;

/** Original scoring rules — the same numbers as MECHANICS.md. */
final class Scoring {
    private Scoring() {}

    static final float NUTRITION_BASE = 60f, ADDITIVES_BASE = 30f, ORGANIC_BONUS = 10f;
    static final float SUGAR = 1.2f, SATFAT = 1.5f, SALT = 6.0f, FIBER = 2.0f, PROTEIN = 1.0f;
    static final float RISKY_ADDITIVE = 6f, MINOR_ADDITIVE = 2f;
    static final float COSMETIC_HIGH = 25f, COSMETIC_MOD = 10f, COSMETIC_LOW = 3f;
    static final int BAND_EXCELLENT = 75, BAND_GOOD = 50, BAND_POOR = 25;
    static final int ALT_MIN_GAIN = 15, ALT_COUNT = 3;

    static int score(Product p) {
        return p.food ? food(p) : cosmetic(p);
    }

    private static int food(Product p) {
        float nutrition = NUTRITION_BASE * 0.5f;
        if (p.nutrition != null) {
            Product.Nutrition n = p.nutrition;
            nutrition = clamp(NUTRITION_BASE - n.sugar * SUGAR - n.satFat * SATFAT - n.salt * SALT
                    + n.fiber * FIBER + n.protein * PROTEIN, 0f, NUTRITION_BASE);
        }
        float penalty = 0f;
        for (Product.Additive a : p.additives) penalty += a.risky ? RISKY_ADDITIVE : MINOR_ADDITIVE;
        float additives = clamp(ADDITIVES_BASE - penalty, 0f, ADDITIVES_BASE);
        float organic = p.organic ? ORGANIC_BONUS : 0f;
        return Math.round(clamp(nutrition + additives + organic, 0f, 100f));
    }

    private static int cosmetic(Product p) {
        float penalty = 0f;
        for (Product.Ingredient i : p.ingredients) {
            penalty += i.hazard == 2 ? COSMETIC_HIGH : i.hazard == 1 ? COSMETIC_MOD : COSMETIC_LOW;
        }
        return Math.round(clamp(100f - penalty, 0f, 100f));
    }

    static String bandLabel(int s) {
        return s >= BAND_EXCELLENT ? "Excellent" : s >= BAND_GOOD ? "Good" : s >= BAND_POOR ? "Poor" : "Bad";
    }

    /** Red -> green band color (ARGB). */
    static int bandColor(int s) {
        if (s >= BAND_EXCELLENT) return 0xFF22A048;
        if (s >= BAND_GOOD) return 0xFF8CBE3C;
        if (s >= BAND_POOR) return 0xFFED9628;
        return 0xFFD63830;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
