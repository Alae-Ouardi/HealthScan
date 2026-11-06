package com.healthscan.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Single screen: search bar, result card, breakdown, alternatives, sample browser. */
public final class MainActivity extends Activity {
    private static final String PREFS = "healthscan", KEY_DARK = "dark";
    private static final int REQ_SCAN = 1;

    private final List<Product> catalog = Catalog.samples();
    private Product selected;
    private boolean dark;
    private String query = "";
    private String status = "";
    private Theme t;
    private History history;
    private String historyQuery = "";
    private boolean historyScreen;

    /** Theme colors for dark and light mode. */
    private static final class Theme {
        final int bg, top, card, browse, text, weak, field, pointer;

        Theme(boolean dark) {
            bg = dark ? 0xFF121316 : 0xFFF4F5F7;
            top = dark ? 0xFF181A1E : 0xFFFFFFFF;
            card = dark ? 0xFF202328 : 0xFFFFFFFF;
            browse = dark ? 0xFF1C1E22 : 0xFFECEEF1;
            text = dark ? 0xFFF0F0F0 : 0xFF1A1C20;
            weak = dark ? 0xFF9A9CA2 : 0xFF6E7076;
            field = dark ? 0xFF0B0B0C : 0xFFF0F1F3;
            pointer = dark ? 0xFFFFFFFF : 0xFF1A1C20;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DARK, true);
        history = new History(this);
        render();
    }

    private void render() {
        t = new Theme(dark);
        getWindow().setStatusBarColor(t.top);
        getWindow().setNavigationBarColor(t.bg);

        LinearLayout root = vertical();
        root.setBackgroundColor(t.bg);
        LinearLayout body = vertical();
        body.setPadding(dp(16), dp(14), dp(16), dp(24));

        if (historyScreen) {
            root.addView(historyTopBar(body));
        } else {
            // Main screen: only the scan and the scanned product's info.
            root.addView(topBar());
            if (selected != null) {
                body.addView(resultCard(selected));
                body.addView(gap(14));
                body.addView(breakdownCard(selected));
                body.addView(gap(14));
                body.addView(alternativesCard(selected));
            } else {
                body.addView(emptyState());
                body.addView(gap(16));
                body.addView(browseCard());
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    // ---- top bar -----------------------------------------------------------

    private View topBar() {
        LinearLayout bar = vertical();
        bar.setBackgroundColor(t.top);
        bar.setPadding(dp(16), dp(14), dp(16), dp(12));

        LinearLayout title = horizontal();
        TextView dot = text("●", 20, 0xFF22A048, false);
        TextView name = text("  HealthScan", 21, t.text, true);
        title.addView(dot);
        title.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        // Theme toggle with the History button right below it, same width.
        LinearLayout actions = vertical();
        Button theme = button(dark ? "☀ Light" : "🌙 Dark");
        theme.setOnClickListener(v -> toggleDark());
        actions.addView(theme, new LinearLayout.LayoutParams(-1, -2));
        actions.addView(gap(6));
        Button historyButton = button("🕘 History");
        historyButton.setOnClickListener(v -> openHistory());
        actions.addView(historyButton, new LinearLayout.LayoutParams(-1, -2));
        title.addView(actions, new LinearLayout.LayoutParams(dp(132), -2));
        bar.addView(title);
        bar.addView(gap(10));

        LinearLayout search = horizontal();
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Type a barcode or a name");
        field.setText(query);
        field.setTextColor(t.text);
        field.setHintTextColor(t.weak);
        field.setBackground(rounded(t.field, 10));
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setOnEditorActionListener((v, id, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (id == EditorInfo.IME_ACTION_SEARCH || enter) scan(field);
            return true;
        });
        search.addView(field, new LinearLayout.LayoutParams(0, -2, 1f));
        Button go = button("Search");
        go.setOnClickListener(v -> scan(field));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2);
        gp.leftMargin = dp(8);
        search.addView(go, gp);
        bar.addView(search);
        bar.addView(gap(8));

        Button camera = button("📷  Scan barcode with camera");
        camera.setTextColor(0xFFFFFFFF);
        camera.setTypeface(Typeface.DEFAULT_BOLD);
        camera.setBackground(rounded(0xFF1F9A55, 12));
        camera.setMinimumHeight(dp(50));
        camera.setOnClickListener(v -> openScanner());
        bar.addView(camera, new LinearLayout.LayoutParams(-1, -2));

        if (!status.isEmpty()) {
            bar.addView(gap(6));
            bar.addView(text(status, 14, 0xFFD25A3C, false));
        }
        return bar;
    }

    private void toggleDark() {
        dark = !dark;
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putBoolean(KEY_DARK, dark).apply();
        render();
    }

    // ---- lookup ------------------------------------------------------------

    private void openScanner() {
        startActivityForResult(new Intent(this, ScannerActivity.class), REQ_SCAN);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_SCAN || result != RESULT_OK || data == null) return;
        String code = data.getStringExtra(ScannerActivity.EXTRA_CODE);
        if (code == null || code.isEmpty()) return;
        historyScreen = false;
        query = code;
        search(code);
    }

    private void scan(EditText field) {
        hideKeyboard(field);
        search(field.getText().toString().trim());
    }

    /** Finds a product by barcode or name: built-in samples first, then the open databases. */
    private void search(String text) {
        query = text;
        String q = query.toLowerCase();
        if (q.isEmpty()) {
            show(null, "Enter a barcode or a product name.");
            return;
        }
        for (Product p : catalog) {
            if (p.code.equals(q) || p.name.toLowerCase().contains(q)
                    || p.brand.toLowerCase().contains(q)) {
                show(p, "");
                return;
            }
        }
        if (q.length() < 6 || !q.matches("\\d+")) {
            show(null, "No product found for \"" + query + "\".");
            return;
        }
        show(selected, "Looking up " + query + "…");
        final String code = q;
        new Thread(() -> {
            Product found = OpenFacts.lookup(code);
            runOnUiThread(() -> {
                if (found == null) {
                    show(null, "No product found for \"" + code + "\".");
                    return;
                }
                catalog.removeIf(p -> p.code.equals(found.code));
                catalog.add(found);
                show(found, "");
            });
        }).start();
    }

    private void show(Product p, String message) {
        if (p != null && p != selected) history.record(p);
        selected = p;
        status = message;
        render();
    }

    // ---- cards -------------------------------------------------------------

    private View resultCard(Product p) {
        int s = Scoring.score(p);
        LinearLayout card = card(tint(Scoring.bandColor(s), 0.18f, t.card));
        LinearLayout row = horizontal();
        row.addView(new ScoreViews.Disc(this, s), new LinearLayout.LayoutParams(dp(92), dp(92)));

        LinearLayout info = vertical();
        info.setPadding(dp(14), 0, 0, 0);
        info.addView(text(p.name, 19, t.text, true));
        if (!p.brand.isEmpty()) info.addView(text(p.brand, 14, t.weak, false));
        LinearLayout pills = horizontal();
        pills.setPadding(0, dp(4), 0, dp(4));
        pills.addView(pill(p.food ? "Food" : "Cosmetic", 0xFF5A5A5A));
        if (p.organic) pills.addView(pill("Organic", 0xFF22A048));
        info.addView(pills);
        info.addView(text(Scoring.bandLabel(s), 17, Scoring.bandColor(s), true));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(row);

        card.addView(gap(12));
        card.addView(new ScoreViews.Scale(this, s, t.pointer), new LinearLayout.LayoutParams(-1, dp(26)));
        return card;
    }

    private View breakdownCard(Product p) {
        LinearLayout card = card(t.card);
        card.addView(text("What's inside", 16, t.text, true));
        card.addView(gap(6));
        if (p.food) {
            Product.Nutrition n = p.nutrition;
            if (n != null) {
                card.addView(row("Sugar", fmt(n.sugar, 1) + " g", n.sugar <= 5f));
                card.addView(row("Saturated fat", fmt(n.satFat, 1) + " g", n.satFat <= 2f));
                card.addView(row("Salt", fmt(n.salt, 2) + " g", n.salt <= 0.3f));
                card.addView(row("Fiber", fmt(n.fiber, 1) + " g", n.fiber >= 3f));
                card.addView(row("Protein", fmt(n.protein, 1) + " g", n.protein >= 5f));
            }
            if (p.additives.isEmpty()) card.addView(row("Additives", "none", true));
            for (Product.Additive a : p.additives) {
                card.addView(row(a.name, a.risky ? "risky" : "limited risk", !a.risky));
            }
        } else {
            if (p.ingredients.isEmpty()) card.addView(row("Ingredients", "not listed", true));
            for (Product.Ingredient i : p.ingredients) {
                String label = i.hazard == 2 ? "high hazard" : i.hazard == 1 ? "moderate" : "low risk";
                card.addView(row(i.name, label, i.hazard == 0));
            }
        }
        return card;
    }

    private View alternativesCard(Product base) {
        LinearLayout card = card(t.card);
        card.addView(text("Better alternatives", 16, t.text, true));
        card.addView(gap(6));
        List<Product> alts = alternatives(base);
        if (alts.isEmpty()) {
            card.addView(text("Nothing better in this category — nice pick.", 14, t.weak, false));
        }
        for (Product a : alts) {
            LinearLayout r = horizontal();
            r.setPadding(0, dp(4), 0, dp(4));
            r.addView(chip(Scoring.score(a)));
            LinearLayout col = vertical();
            col.setPadding(dp(10), 0, 0, 0);
            col.addView(text(a.name, 15, t.text, true));
            if (!a.brand.isEmpty()) col.addView(text(a.brand, 12, t.weak, false));
            r.addView(col);
            r.setOnClickListener(v -> show(a, ""));
            card.addView(r);
        }
        return card;
    }

    private List<Product> alternatives(Product base) {
        int min = Scoring.score(base) + Scoring.ALT_MIN_GAIN;
        List<Product> out = new ArrayList<>();
        for (Product p : catalog) {
            if (p != base && p.food == base.food && Scoring.score(p) >= min) out.add(p);
        }
        out.sort((a, b) -> Scoring.score(b) - Scoring.score(a));
        return out.size() > Scoring.ALT_COUNT ? out.subList(0, Scoring.ALT_COUNT) : out;
    }

    /**
     * Top bar of the dedicated history screen: back, title, clear and a live search box.
     * Fills {@code body} with the saved scans; only the list refreshes while typing.
     */
    private View historyTopBar(LinearLayout body) {
        LinearLayout bar = vertical();
        bar.setBackgroundColor(t.top);
        bar.setPadding(dp(16), dp(14), dp(16), dp(12));

        LinearLayout head = horizontal();
        Button back = button("← Back");
        back.setOnClickListener(v -> closeHistory());
        head.addView(back);
        TextView title = text("History", 21, t.text, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button clear = button("Clear");
        clear.setEnabled(!history.isEmpty());
        clear.setOnClickListener(v -> {
            history.clear();
            historyQuery = "";
            render();
        });
        head.addView(clear);
        bar.addView(head);
        bar.addView(gap(10));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search your scans");
        search.setText(historyQuery);
        search.setTextColor(t.text);
        search.setHintTextColor(t.weak);
        search.setBackground(rounded(t.field, 10));
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        bar.addView(search);

        LinearLayout list = vertical();
        body.addView(list);
        fillHistory(list);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                historyQuery = s.toString();
                fillHistory(list);
            }
        });
        return bar;
    }

    private void openHistory() {
        historyScreen = true;
        render();
    }

    private void closeHistory() {
        historyScreen = false;
        render();
    }

    @Override
    public void onBackPressed() {
        if (historyScreen) {
            closeHistory();
        } else {
            super.onBackPressed();
        }
    }

    private void fillHistory(LinearLayout list) {
        list.removeAllViews();
        List<History.Entry> found = history.search(historyQuery);
        if (history.isEmpty()) {
            list.addView(text("No scans yet. Products you open will show up here.", 15, t.weak, false));
            return;
        }
        if (found.isEmpty()) {
            list.addView(text("No scans match \"" + historyQuery.trim() + "\".", 14, t.weak, false));
            return;
        }
        long now = System.currentTimeMillis();
        for (History.Entry e : found) {
            Product p = e.product;
            LinearLayout r = horizontal();
            r.setPadding(0, dp(5), 0, dp(5));
            r.addView(chip(Scoring.score(p)));
            LinearLayout col = vertical();
            col.setPadding(dp(10), 0, 0, 0);
            col.addView(text(p.name, 15, t.text, p == selected));
            String when = DateUtils.getRelativeTimeSpanString(e.time, now,
                    DateUtils.MINUTE_IN_MILLIS).toString();
            String sub = p.brand.isEmpty() ? when : p.brand + " · " + when;
            col.addView(text(sub, 12, t.weak, false));
            r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            r.setOnClickListener(v -> open(p));
            list.addView(r);
        }
    }

    /** Opens a product, reusing the catalog copy when it is already there. */
    private void open(Product p) {
        historyScreen = false;
        for (Product c : catalog) {
            if (c.code.equals(p.code)) {
                show(c, "");
                return;
            }
        }
        catalog.add(p);
        show(p, "");
    }

    private View browseCard() {
        LinearLayout card = card(t.browse);
        card.addView(text("Browse samples", 14, t.weak, false));
        card.addView(gap(4));
        for (Product p : catalog) {
            LinearLayout r = horizontal();
            r.setPadding(0, dp(5), 0, dp(5));
            r.addView(chip(Scoring.score(p)));
            TextView name = text("  " + p.name, 15, t.text, p == selected);
            r.addView(name);
            r.setOnClickListener(v -> show(p, ""));
            card.addView(r);
        }
        return card;
    }

    private View emptyState() {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, dp(30), 0, dp(10));
        box.addView(text("🔍", 40, t.text, false));
        box.addView(text("Scan or pick a product", 18, t.text, true));
        box.addView(text("Scan a barcode with the camera, type it above, or pick a sample below.", 14, t.weak, false));
        return box;
    }

    // ---- small view helpers --------------------------------------------------

    private View row(String label, String value, boolean good) {
        LinearLayout r = horizontal();
        r.setPadding(0, dp(5), 0, dp(5));
        r.addView(text("● ", 14, good ? 0xFF46B464 : 0xFFE05648, false));
        r.addView(text(label, 15, t.text, false), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(text(value, 14, t.weak, false));
        return r;
    }

    private TextView chip(int score) {
        TextView c = text(String.valueOf(score), 12, 0xFFFFFFFF, true);
        c.setGravity(Gravity.CENTER);
        c.setBackground(rounded(Scoring.bandColor(score), 6));
        c.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(24)));
        return c;
    }

    private TextView pill(String label, int color) {
        TextView p = text(label, 12, 0xFFFFFFFF, false);
        p.setBackground(rounded(color, 10));
        p.setPadding(dp(8), dp(2), dp(8), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        p.setLayoutParams(lp);
        return p;
    }

    private LinearLayout card(int fill) {
        LinearLayout c = vertical();
        c.setBackground(rounded(fill, 14));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        return c;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(t.text);
        b.setBackground(rounded(dark ? 0xFF33363C : 0xFFE2E4E8, 10));
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        b.setMinHeight(0);
        b.setMinimumHeight(dp(40));
        return b;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private View gap(int dpH) {
        View v = new View(this);
        v.setLayoutParams(new ViewGroup.LayoutParams(1, dp(dpH)));
        return v;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String fmt(float v, int decimals) {
        return String.format(java.util.Locale.US, "%." + decimals + "f", v);
    }

    /** Blend a color toward a background by t (0 = background, 1 = color). */
    private static int tint(int c, float t, int bg) {
        int r = mix((bg >> 16) & 0xFF, (c >> 16) & 0xFF, t);
        int g = mix((bg >> 8) & 0xFF, (c >> 8) & 0xFF, t);
        int b = mix(bg & 0xFF, c & 0xFF, t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
