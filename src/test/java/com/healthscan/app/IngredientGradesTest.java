package com.healthscan.app;

import static com.healthscan.app.Product.Ingredient.HIGH;
import static com.healthscan.app.Product.Ingredient.LOW;
import static com.healthscan.app.Product.Ingredient.MODERATE;
import static com.healthscan.app.Product.Ingredient.UNRATED;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class IngredientGradesTest {
    private static void grade(int expected, String name) {
        assertEquals(name, expected, IngredientGrades.grade(name));
    }

    @Test
    public void waterIsLowInAnySpelling() {
        grade(LOW, "Water");
        grade(LOW, "AQUA");
        grade(LOW, "Aqua (Water)");
        grade(LOW, "Aqua/Water/Eau");
        grade(LOW, "eau");
    }

    @Test
    public void accentsAndPunctuationDoNotMatter() {
        grade(LOW, "Glycérine");
        grade(LOW, "  glycerin. ");
    }

    @Test
    public void commonGentleIngredientsAreLow() {
        grade(LOW, "Sodium Chloride");
        grade(LOW, "Citric Acid");
        grade(LOW, "Butyrospermum Parkii (Shea) Butter");
        grade(LOW, "Polyquaternium-10");
        grade(LOW, "Sodium Xylenesulfonate");
        grade(LOW, "Manteca de Karité");
    }

    @Test
    public void fragranceAndHarshSurfactantsAreModerate() {
        grade(MODERATE, "Parfum");
        grade(MODERATE, "Parfum (Fragrance)");
        grade(MODERATE, "Sodium Laureth Sulfate");
        grade(MODERATE, "Sodium Lauryl Sulfate");
        grade(MODERATE, "Phenoxyethanol");
        grade(MODERATE, "Limonene");
    }

    @Test
    public void ingredientFamiliesAreRecognised() {
        grade(MODERATE, "PEG-40 Hydrogenated Castor Oil");
        grade(MODERATE, "Ceteareth-20");
        grade(MODERATE, "Methylparaben");
        grade(HIGH, "Propylparaben");
        grade(HIGH, "Isobutylparaben");
    }

    @Test
    public void ingredientsOfConcernAreHigh() {
        grade(HIGH, "Methylisothiazolinone");
        grade(HIGH, "Methylchloroisothiazolinone");
        grade(HIGH, "DMDM Hydantoin");
        grade(HIGH, "Triclosan");
        grade(HIGH, "Benzophenone-3");
    }

    @Test
    public void theWorstKnownNameWinsWhenSeveralAreGiven() {
        grade(HIGH, "Aqua / Methylisothiazolinone");
    }

    @Test
    public void unknownOrEmptyNamesAreNotRated() {
        grade(UNRATED, "Hydrolyzed Quinoa Extract XYZ");
        grade(UNRATED, "");
        grade(UNRATED, null);
    }
}
