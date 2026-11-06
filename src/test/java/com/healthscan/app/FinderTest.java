package com.healthscan.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class FinderTest {
    private static Product food(String code, String name, String brand, float sugar) {
        return new Product(code, name, brand, true, false,
                new Product.Nutrition(sugar, 0f, 0f, 0f, 0f));
    }

    private static Product cosmetic(String code, String name, int hazard) {
        return new Product(code, name, "", false, false, null).ing("x", hazard);
    }

    private final Product cola = food("111", "Fizzy Cola", "SodaCo", 40f);   // low score
    private final Product oats = food("222", "Rolled Oats", "Pantry", 0f);    // high score
    private final Product bar = food("333", "Choco Bar", "Sweets", 10f);      // medium score
    private final Product cream = cosmetic("444", "Night Cream", 0);
    private final List<Product> pool = Arrays.asList(cola, oats, bar, cream);

    @Test
    public void findsByExactBarcode() {
        assertSame(oats, Finder.find("222", pool));
    }

    @Test
    public void findsByNameIgnoringCase() {
        assertSame(cola, Finder.find("fizzy", pool));
    }

    @Test
    public void findsByBrand() {
        assertSame(bar, Finder.find("SWEETS", pool));
    }

    @Test
    public void partialBarcodeDoesNotMatch() {
        assertNull(Finder.find("22", pool));
    }

    @Test
    public void blankQueryFindsNothing() {
        assertNull(Finder.find("   ", pool));
    }

    @Test
    public void looksLikeBarcodeNeedsSixDigits() {
        assertTrue(Finder.looksLikeBarcode("4006381333931"));
        assertTrue(Finder.looksLikeBarcode("123456"));
        assertEquals(false, Finder.looksLikeBarcode("12345"));
        assertEquals(false, Finder.looksLikeBarcode("cola"));
    }

    @Test
    public void alternativesAreSameCategoryAndClearlyBetterBestFirst() {
        List<Product> alts = Finder.alternatives(cola, pool);
        assertEquals(Arrays.asList(oats, bar), alts);
    }

    @Test
    public void alternativesSkipTheProductItselfByBarcode() {
        Product sameOats = food("222", "Rolled Oats", "Pantry", 0f);
        assertTrue(Finder.alternatives(sameOats, pool).isEmpty());
    }

    @Test
    public void alternativesAreCappedAtThree() {
        List<Product> many = Arrays.asList(cola,
                food("a", "A", "", 0f), food("b", "B", "", 0f),
                food("c", "C", "", 0f), food("d", "D", "", 0f));
        assertEquals(Scoring.ALT_COUNT, Finder.alternatives(cola, many).size());
    }
}
