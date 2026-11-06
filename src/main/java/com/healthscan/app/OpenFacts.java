package com.healthscan.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Barcode lookup in the open databases Open Food Facts (food) and Open Beauty Facts
 * (cosmetics). Blocking: call from a background thread. Returns null on a miss or error.
 */
final class OpenFacts {
    private OpenFacts() {}

    private static final String OFF = "https://world.openfoodfacts.org/api/v2/product/";
    private static final String OBF = "https://world.openbeautyfacts.org/api/v2/product/";
    private static final String FIELDS =
            "?fields=product_name,brands,nutriments,additives_tags,labels_tags,ingredients,"
                    + "image_front_url,image_url";
    private static final int TIMEOUT_MS = 6000;

    static Product lookup(String code) {
        Product p = fetch(OFF, code, true);
        return p != null ? p : fetch(OBF, code, false);
    }

    private static Product fetch(String base, String code, boolean food) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + code + FIELDS).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "HealthScan/0.1 (personal app)");
            if (c.getResponseCode() != 200) return null;
            JSONObject body = new JSONObject(read(c.getInputStream()));
            if (body.optInt("status") != 1) return null;
            return parse(body.getJSONObject("product"), code, food);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static Product parse(JSONObject p, String code, boolean food) {
        String name = p.optString("product_name", "").trim();
        if (name.isEmpty()) return null;
        String brand = p.optString("brands", "").trim();
        boolean organic = false;
        JSONArray labels = p.optJSONArray("labels_tags");
        if (labels != null) {
            for (int i = 0; i < labels.length(); i++) {
                String t = labels.optString(i);
                if (t.contains("organic") || t.contains("bio")) organic = true;
            }
        }
        Product.Nutrition n = null;
        JSONObject nu = p.optJSONObject("nutriments");
        if (food && nu != null) {
            n = new Product.Nutrition((float) nu.optDouble("sugars_100g", 0),
                    (float) nu.optDouble("saturated-fat_100g", 0), (float) nu.optDouble("salt_100g", 0),
                    (float) nu.optDouble("fiber_100g", 0), (float) nu.optDouble("proteins_100g", 0));
        }
        String image = p.optString("image_front_url", "").trim();
        if (image.isEmpty()) image = p.optString("image_url", "").trim();
        if (!image.startsWith("https://")) image = ""; // only load photos over HTTPS
        Product out = new Product(code, name, brand, food, organic, n, image);
        if (food) {
            JSONArray tags = p.optJSONArray("additives_tags");
            for (int i = 0; tags != null && i < tags.length(); i++) {
                String t = tags.optString(i);
                // open data has no risk grade; listed additives are treated conservatively
                out.add(t.substring(t.indexOf(':') + 1).toUpperCase(), true);
            }
        } else {
            JSONArray ings = p.optJSONArray("ingredients");
            for (int i = 0; ings != null && i < ings.length() && i < 12; i++) {
                String t = ings.optJSONObject(i) == null ? "" : ings.optJSONObject(i).optString("text");
                if (!t.trim().isEmpty()) out.ing(t.trim(), 1); // no hazard grade: moderate
            }
        }
        return out;
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int r; (r = in.read(chunk)) != -1; ) buf.write(chunk, 0, r);
        return buf.toString("UTF-8");
    }
}
