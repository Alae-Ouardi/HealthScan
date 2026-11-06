package com.healthscan.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Cleaning rules for untrusted values coming from the open databases. */
public final class OpenFactsTest {
    private static final float EPS = 0.0001f;

    @Test
    public void amountKeepsNormalValues() {
        assertEquals(12.5f, OpenFacts.amount(12.5), EPS);
    }

    @Test
    public void amountTurnsNotANumberIntoZero() {
        assertEquals(0f, OpenFacts.amount(Double.NaN), EPS);
    }

    @Test
    public void amountTurnsInfinityAndHugeValuesIntoTheMaximum() {
        assertEquals(OpenFacts.MAX_GRAMS, OpenFacts.amount(Double.POSITIVE_INFINITY), EPS);
        assertEquals(OpenFacts.MAX_GRAMS, OpenFacts.amount(1e300), EPS);
    }

    @Test
    public void amountTurnsNegativeValuesIntoZero() {
        assertEquals(0f, OpenFacts.amount(-3), EPS);
        assertEquals(0f, OpenFacts.amount(Double.NEGATIVE_INFINITY), EPS);
    }

    @Test
    public void organicLabelsAreRecognised() {
        assertTrue(OpenFacts.isOrganicLabel("en:organic"));
        assertTrue(OpenFacts.isOrganicLabel("en:eu-organic"));
        assertTrue(OpenFacts.isOrganicLabel("en:usda-organic"));
        assertTrue(OpenFacts.isOrganicLabel("fr:ab-agriculture-biologique"));
        assertTrue(OpenFacts.isOrganicLabel("de:bio"));
        assertTrue(OpenFacts.isOrganicLabel("de:bio-siegel"));
    }

    @Test
    public void lookalikeLabelsAreNotOrganic() {
        assertFalse(OpenFacts.isOrganicLabel("en:no-antibiotics"));
        assertFalse(OpenFacts.isOrganicLabel("en:biodegradable-packaging"));
        assertFalse(OpenFacts.isOrganicLabel("en:inorganic-free"));
        assertFalse(OpenFacts.isOrganicLabel(""));
    }

    @Test
    public void clipShortensLongTextAndTrims() {
        assertEquals("abc", OpenFacts.clip("  abc  ", 10));
        assertEquals(10, OpenFacts.clip("x".repeat(500), 10).length());
    }
}
