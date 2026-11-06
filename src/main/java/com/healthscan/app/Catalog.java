package com.healthscan.app;

import java.util.ArrayList;
import java.util.List;

/** Built-in sample products with neutral placeholder names (offline fallback). */
final class Catalog {
    private Catalog() {}

    static List<Product> samples() {
        List<Product> list = new ArrayList<>();
        list.add(new Product("0000000000017", "Fizzy Cola Drink", "SampleBrand", true, false,
                new Product.Nutrition(39f, 0f, 0.1f, 0f, 0f))
                .add("Colorant E150d", true).add("Acidifier", false));
        list.add(new Product("0000000000024", "Oat & Nut Crunch Bar", "GreenSample", true, true,
                new Product.Nutrition(9f, 2f, 0.2f, 6f, 8f)));
        list.add(new Product("0000000000031", "Plain Rolled Oats", "PantryCo", true, true,
                new Product.Nutrition(1f, 1.2f, 0f, 10f, 13f)));
        list.add(new Product("0000000000048", "Instant Noodle Cup", "QuickSample", true, false,
                new Product.Nutrition(4f, 9f, 3.4f, 2f, 7f))
                .add("Flavour enhancer E621", true).add("Stabiliser", false).add("Antioxidant", false));
        list.add(new Product("0000000000055", "Rich Night Face Cream", "GlowSample", false, false, null)
                .ing("Fragrance mix", 2).ing("Paraben preservative", 2)
                .ing("Emulsifier", 1).ing("Glycerin", 0));
        list.add(new Product("0000000000062", "Gentle Aloe Moisturizer", "PureSample", false, true, null)
                .ing("Aloe extract", 0).ing("Glycerin", 0).ing("Plant oil", 0)
                .ing("Mild preservative", 1));
        return list;
    }
}
