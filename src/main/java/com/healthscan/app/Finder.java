package com.healthscan.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure lookup rules over a pool of known products (the scan history). No Android code. */
final class Finder {
    private Finder() {}

    private static final int MIN_BARCODE_DIGITS = 6;

    /** First product whose barcode equals the query, or whose name or brand contains it. */
    static Product find(String query, List<Product> pool) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return null;
        for (Product p : pool) {
            if (p.code.equals(q)) return p;
        }
        for (Product p : pool) {
            if (p.name.toLowerCase(Locale.ROOT).contains(q)
                    || p.brand.toLowerCase(Locale.ROOT).contains(q)) {
                return p;
            }
        }
        return null;
    }

    /** True when the text can be sent to the open databases as a barcode. */
    static boolean looksLikeBarcode(String text) {
        return text != null && text.length() >= MIN_BARCODE_DIGITS && text.matches("\\d+");
    }

    /** Same-category products scoring clearly higher than {@code base}, best first. */
    static List<Product> alternatives(Product base, List<Product> pool) {
        int min = Scoring.score(base) + Scoring.ALT_MIN_GAIN;
        List<Product> out = new ArrayList<>();
        for (Product p : pool) {
            if (!p.code.equals(base.code) && p.food == base.food && Scoring.score(p) >= min) {
                out.add(p);
            }
        }
        out.sort((a, b) -> Scoring.score(b) - Scoring.score(a));
        return out.size() > Scoring.ALT_COUNT
                ? new ArrayList<>(out.subList(0, Scoring.ALT_COUNT)) : out;
    }
}
