package com.healthscan.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
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

    /** All saved products, newest first. */
    List<Product> products() {
        List<Product> out = new ArrayList<>(entries.size());
        for (Entry e : entries) out.add(e.product);
        return out;
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
        JSONArray arr;
        try {
            arr = new JSONArray(prefs.getString(KEY, "[]"));
        } catch (JSONException e) {
            Log.w(AppInfo.LOG_TAG, "saved history unreadable, starting fresh", e);
            return;
        }
        for (int i = 0; i < arr.length() && entries.size() < MAX_ENTRIES; i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                entries.add(new Entry(fromJson(o.getJSONObject("p")), o.optLong("t")));
            } catch (JSONException e) {
                Log.w(AppInfo.LOG_TAG, "skipping damaged history entry " + i, e); // keep the rest
            }
        }
    }

    private void save() {
        JSONArray arr = new JSONArray();
        for (Entry e : entries) {
            try {
                arr.put(new JSONObject().put("t", e.time).put("p", toJson(e.product)));
            } catch (JSONException ex) {
                Log.w(AppInfo.LOG_TAG, "not saving unstorable product " + e.product.code, ex);
            }
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    /** Product as JSON; also used to hand a product to the product page. */
    static JSONObject toJson(Product p) throws JSONException {
        JSONObject o = new JSONObject().put("code", p.code).put("name", p.name)
                .put("brand", p.brand).put("food", p.food).put("organic", p.organic)
                .put("img", p.imageUrl);
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

    static Product fromJson(JSONObject o) throws JSONException {
        Product.Nutrition n = null;
        JSONArray na = o.optJSONArray("n");
        if (na != null) {
            // cleaned again on the way in: the product page also reads products through here
            n = new Product.Nutrition(OpenFacts.amount(na.getDouble(0)), OpenFacts.amount(na.getDouble(1)),
                    OpenFacts.amount(na.getDouble(2)), OpenFacts.amount(na.getDouble(3)),
                    OpenFacts.amount(na.getDouble(4)));
        }
        Product p = new Product(o.getString("code"), o.getString("name"), o.optString("brand"),
                o.getBoolean("food"), o.optBoolean("organic"), n, o.optString("img"));
        JSONArray adds = o.optJSONArray("a");
        for (int i = 0; adds != null && i < adds.length(); i++) {
            p.add(adds.getJSONObject(i).getString("n"), adds.getJSONObject(i).getBoolean("r"));
        }
        JSONArray ings = o.optJSONArray("i");
        for (int i = 0; ings != null && i < ings.length(); i++) {
            // Re-graded from the name on every load, so saved scans follow the current list
            // (the stored grade "h" is kept for older app versions only).
            String name = ings.getJSONObject(i).getString("n");
            p.ing(name, IngredientGrades.grade(name));
        }
        return p;
    }
}
