package com.healthscan.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Scan history: products the person opened, newest first, saved on the phone so it
 * survives restarts. Whole products are stored, so online look-ups reopen offline.
 */
final class History {
    static final int MAX_ENTRIES = 100;
    private static final String PREFS = "healthscan_history", KEY = "items";

    static final class Entry {
        final Product product;
        final long time;

        Entry(Product product, long time) {
            this.product = product;
            this.time = time;
        }
    }

    private final SharedPreferences prefs;
    private final List<Entry> entries = new ArrayList<>();

    History(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    /** Moves (or adds) the product to the top of the history. */
    void record(Product p) {
        entries.removeIf(e -> e.product.code.equals(p.code));
        entries.add(0, new Entry(p, System.currentTimeMillis()));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
        save();
    }

    void clear() {
        entries.clear();
        save();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Entries whose name, brand or barcode contain the query (case-insensitive). */
    List<Entry> search(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            Product p = e.product;
            if (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q)
                    || p.brand.toLowerCase(Locale.ROOT).contains(q) || p.code.contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    // ---- storage -------------------------------------------------------------

    private void load() {
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                entries.add(new Entry(fromJson(o.getJSONObject("p")), o.optLong("t")));
            }
        } catch (Exception e) {
            entries.clear(); // unreadable history: start fresh rather than crash
        }
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) {
                arr.put(new JSONObject().put("t", e.time).put("p", toJson(e.product)));
            }
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
            // JSONObject.put only throws for NaN numbers, which scores never produce
        }
    }

    private static JSONObject toJson(Product p) throws Exception {
        JSONObject o = new JSONObject().put("code", p.code).put("name", p.name)
                .put("brand", p.brand).put("food", p.food).put("organic", p.organic);
        if (p.nutrition != null) {
            Product.Nutrition n = p.nutrition;
            o.put("n", new JSONArray().put(n.sugar).put(n.satFat).put(n.salt)
                    .put(n.fiber).put(n.protein));
        }
        JSONArray adds = new JSONArray();
        for (Product.Additive a : p.additives) {
            adds.put(new JSONObject().put("n", a.name).put("r", a.risky));
        }
        JSONArray ings = new JSONArray();
        for (Product.Ingredient i : p.ingredients) {
            ings.put(new JSONObject().put("n", i.name).put("h", i.hazard));
        }
        return o.put("a", adds).put("i", ings);
    }

    private static Product fromJson(JSONObject o) throws Exception {
        Product.Nutrition n = null;
        JSONArray na = o.optJSONArray("n");
        if (na != null) {
            n = new Product.Nutrition((float) na.getDouble(0), (float) na.getDouble(1),
                    (float) na.getDouble(2), (float) na.getDouble(3), (float) na.getDouble(4));
        }
        Product p = new Product(o.getString("code"), o.getString("name"), o.optString("brand"),
                o.getBoolean("food"), o.optBoolean("organic"), n);
        JSONArray adds = o.optJSONArray("a");
        for (int i = 0; adds != null && i < adds.length(); i++) {
            p.add(adds.getJSONObject(i).getString("n"), adds.getJSONObject(i).getBoolean("r"));
        }
        JSONArray ings = o.optJSONArray("i");
        for (int i = 0; ings != null && i < ings.length(); i++) {
            p.ing(ings.getJSONObject(i).getString("n"), ings.getJSONObject(i).getInt("h"));
        }
        return p;
    }
}
