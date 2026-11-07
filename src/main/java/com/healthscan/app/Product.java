package com.healthscan.app;

import java.util.ArrayList;
import java.util.List;

/** Product model (see MECHANICS.md). */
final class Product {
    static final class Nutrition {
        final float sugar, satFat, salt, fiber, protein; // grams per 100 g

        Nutrition(float sugar, float satFat, float salt, float fiber, float protein) {
            this.sugar = sugar;
            this.satFat = satFat;
            this.salt = salt;
            this.fiber = fiber;
            this.protein = protein;
        }
    }

    static final class Additive {
        final String name;
        final boolean risky;

        Additive(String name, boolean risky) {
            this.name = name;
            this.risky = risky;
        }
    }

    /**
     * Cosmetic ingredient with a hazard level: {@link #LOW}, {@link #MODERATE}, {@link #HIGH},
     * or {@link #UNRATED} when HealthScan has no grade for it. Ordered: a larger value is worse.
     */
    static final class Ingredient {
        static final int UNRATED = -1, LOW = 0, MODERATE = 1, HIGH = 2;

        final String name;
        final int hazard;

        Ingredient(String name, int hazard) {
            this.name = name;
            this.hazard = hazard;
        }
    }

    final String code, name, brand;
    final String imageUrl; // product photo from the open databases; empty when unknown
    final boolean food, organic;
    final Nutrition nutrition; // null for cosmetics or when unknown
    final List<Additive> additives = new ArrayList<>();
    final List<Ingredient> ingredients = new ArrayList<>();

    Product(String code, String name, String brand, boolean food, boolean organic,
            Nutrition nutrition) {
        this(code, name, brand, food, organic, nutrition, "");
    }

    Product(String code, String name, String brand, boolean food, boolean organic,
            Nutrition nutrition, String imageUrl) {
        this.code = code;
        this.name = name;
        this.brand = brand;
        this.food = food;
        this.organic = organic;
        this.nutrition = nutrition;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }

    Product add(String additive, boolean risky) {
        additives.add(new Additive(additive, risky));
        return this;
    }

    Product ing(String ingredient, int hazard) {
        ingredients.add(new Ingredient(ingredient, hazard));
        return this;
    }
}
