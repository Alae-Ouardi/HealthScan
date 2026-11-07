package com.healthscan.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ScoringTest {
    private static Product cosmetic(int... hazards) {
        Product p = new Product("1", "Shampoo", "", false, false, null);
        for (int h : hazards) p.ing("x", h);
        return p;
    }

    @Test
    public void notRatedIngredientsDoNotLowerTheScore() {
        int[] unrated = new int[12];
        java.util.Arrays.fill(unrated, Product.Ingredient.UNRATED);
        assertEquals(100, Scoring.score(cosmetic(unrated)));
    }

    @Test
    public void gradedIngredientsLowerTheScore() {
        // 100 - low 3 - moderate 10 - high 25
        assertEquals(62, Scoring.score(cosmetic(
                Product.Ingredient.LOW, Product.Ingredient.MODERATE, Product.Ingredient.HIGH,
                Product.Ingredient.UNRATED)));
    }

    @Test
    public void gradedCountIgnoresNotRated() {
        Product p = cosmetic(Product.Ingredient.LOW, Product.Ingredient.UNRATED, Product.Ingredient.UNRATED);
        assertEquals(1, Scoring.gradedIngredients(p));
    }
}
