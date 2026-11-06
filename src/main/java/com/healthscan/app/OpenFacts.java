package com.healthscan.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Barcode lookup in the open databases Open Food Facts (food) and Open Beauty Facts
 * (cosmetics). Blocking: call from a background thread. Everything coming back is treated
 * as untrusted: numbers are cleaned and texts and lists are capped.
 */
final class OpenFacts {
    private OpenFacts() {}

    static final float MAX_GRAMS = 100f; // values are per 100 g

    private static final String OFF = "https://world.openfoodfacts.org/api/v2/product/";
    private static final String OBF = "https://world.openbeautyfacts.org/api/v2/product/";
    private static final String FIELDS =
            "?fields=product_name,brands,nutriments,additives_tags,labels_tags,ingredients,"
                    + "image_front_url,image_url";
    private static final int TIMEOUT_MS = 6000;
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int HTTP_OK = 200, HTTP_NOT_FOUND = 404;

    private static final int MAX_NAME_CHARS = 120, MAX_ITEM_CHARS = 80, MAX_URL_CHARS = 500;
    private static final int MAX_ADDITIVES = 30, MAX_INGREDIENTS = 12;

    /**
     * The product for {@code code}, or null when neither database knows it.
     * Throws when a database could not be asked (network or server problem).
     */
    static Product lookup(String code) throws IOException {
        IOException failure = null;
        try {
            Product p = fetch(OFF, code, true);
            if (p != null) return p;
        } catch (IOException e) {
            failure = e;
        }
        try {
            Product p = fetch(OBF, code, false);
            if (p != null) return p;
        } catch (IOException e) {
            if (failure == null) failure = e;
        }
        if (failure != null) throw failure; // a miss is only certain when both answered
        return null;
    }

    private static Product fetch(String base, String code, boolean food) throws IOException {
        Http.Response r = Http.get(base + code + FIELDS, TIMEOUT_MS, MAX_BODY_BYTES);
        if (r.code == HTTP_NOT_FOUND) return null;
        if (r.code != HTTP_OK) throw new IOException("server answered " + r.code);
        try {
            JSONObject body = new JSONObject(new String(r.body, StandardCharsets.UTF_8));
            if (body.optInt("status") != 1) return null;
            return parse(body.getJSONObject("product"), code, food);
        } catch (JSONException e) {
            throw new IOException("unreadable answer", e);
        }
    }

    private static Product parse(JSONObject p, String code, boolean food) {
        String name = clip(p.optString("product_name", ""), MAX_NAME_CHARS);
        if (name.isEmpty()) return null;
        String brand = clip(p.optString("brands", ""), MAX_NAME_CHARS);
        Product out = new Product(code, name, brand, food, hasOrganicLabel(p), nutrition(p, food), image(p));
        if (food) {
            JSONArray tags = p.optJSONArray("additives_tags");
            for (int i = 0; tags != null && i < tags.length() && i < MAX_ADDITIVES; i++) {
                String t = tags.optString(i);
                String additive = clip(t.substring(t.indexOf(':') + 1).toUpperCase(Locale.ROOT), MAX_ITEM_CHARS);
                // open data has no risk grade; listed additives are treated conservatively
                if (!additive.isEmpty()) out.add(additive, true);
            }
        } else {
            JSONArray ings = p.optJSONArray("ingredients");
            for (int i = 0; ings != null && i < ings.length() && i < MAX_INGREDIENTS; i++) {
                JSONObject ing = ings.optJSONObject(i);
                String text = ing == null ? "" : clip(ing.optString("text"), MAX_ITEM_CHARS);
                // open data has no hazard grade: moderate
                if (!text.isEmpty()) out.ing(text, Product.Ingredient.MODERATE);
            }
        }
        return out;
    }

    private static Product.Nutrition nutrition(JSONObject p, boolean food) {
        JSONObject nu = p.optJSONObject("nutriments");
        if (!food || nu == null) return null;
        return new Product.Nutrition(amount(nu.optDouble("sugars_100g", 0)),
                amount(nu.optDouble("saturated-fat_100g", 0)), amount(nu.optDouble("salt_100g", 0)),
                amount(nu.optDouble("fiber_100g", 0)), amount(nu.optDouble("proteins_100g", 0)));
    }

    private static boolean hasOrganicLabel(JSONObject p) {
        JSONArray labels = p.optJSONArray("labels_tags");
        for (int i = 0; labels != null && i < labels.length(); i++) {
            if (isOrganicLabel(labels.optString(i))) return true;
        }
        return false;
    }

    /** Front photo (or any photo) URL; only HTTPS links of sane length are kept. */
    private static String image(JSONObject p) {
        String url = p.optString("image_front_url", "").trim();
        if (url.isEmpty()) url = p.optString("image_url", "").trim();
        return url.startsWith("https://") && url.length() <= MAX_URL_CHARS ? url : "";
    }

    /** Grams per 100 g, made safe: not-a-number becomes 0, negatives 0, huge values the maximum. */
    static float amount(double grams) {
        if (Double.isNaN(grams) || grams <= 0) return 0f;
        return (float) Math.min(grams, MAX_GRAMS);
    }

    /** True for organic labels such as "en:organic", "en:eu-organic" or "de:bio-siegel". */
    static boolean isOrganicLabel(String tag) {
        String label = tag.substring(tag.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        for (String word : label.split("-")) {
            switch (word) {
                case "organic": case "bio": case "biologique": case "biologico": case "biologisch":
                    return true;
                default:
            }
        }
        return false;
    }

    /** Trimmed text, cut to at most {@code max} characters. */
    static String clip(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() <= max ? t : t.substring(0, max).trim();
    }
}
