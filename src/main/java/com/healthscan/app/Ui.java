package com.healthscan.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.Consumer;

/** Theme colors and the small view builders shared by every screen. */
final class Ui {
    private static final String PREFS = "healthscan", KEY_DARK = "dark";

    static final int WHITE = 0xFFFFFFFF;
    static final int GREEN = 0xFF1F9A55, GOOD_DOT = 0xFF46B464, BAD_DOT = 0xFFE05648;
    static final int WARNING = 0xFFED9628;
    static final int ICON_BUTTON_DP = 40, ICON_DP = 22;
    static final int PAGE_TITLE_SP = 20;

    private static final int FIELD_PAD_DP = 12, CLEAR_ICON_DP = 44;

    final Activity activity;
    final boolean dark;
    final int bg, top, card, textColor, mutedColor, field, pointer, buttonFill;

    Ui(Activity activity) {
        this.activity = activity;
        this.dark = isDark(activity);
        bg = dark ? 0xFF121316 : 0xFFF4F5F7;
        top = dark ? 0xFF181A1E : 0xFFFFFFFF;
        card = dark ? 0xFF202328 : 0xFFFFFFFF;
        textColor = dark ? 0xFFF0F0F0 : 0xFF1A1C20;
        mutedColor = dark ? 0xFF9A9CA2 : 0xFF6E7076;
        field = dark ? 0xFF0B0B0C : 0xFFF0F1F3;
        pointer = dark ? 0xFFFFFFFF : 0xFF1A1C20;
        buttonFill = dark ? 0xFF33363C : 0xFFE2E4E8;
    }

    static boolean isDark(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK, true);
    }

    static void setDark(Context c, boolean dark) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DARK, dark).apply();
    }

    /** Status and navigation bar colors matching the theme, with dark icons on light bars. */
    void paintWindow() {
        activity.getWindow().setStatusBarColor(top);
        activity.getWindow().setNavigationBarColor(bg);
        int flags = 0;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // ---- page structure ------------------------------------------------------

    /** Padded column that holds a page's content. */
    LinearLayout body() {
        LinearLayout body = vertical();
        body.setPadding(dp(16), dp(14), dp(16), dp(24));
        return body;
    }

    /** Fixed header above a scrolling body; returns the scroll view. */
    ScrollView setScreen(View header, LinearLayout body, boolean fillViewport) {
        LinearLayout root = vertical();
        root.setBackgroundColor(bg);
        root.addView(header);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(fillViewport);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        activity.setContentView(root);
        return scroll;
    }

    /** Background panel of a page's top bar. */
    LinearLayout topBarPanel() {
        LinearLayout bar = vertical();
        bar.setBackgroundColor(top);
        bar.setPadding(dp(16), dp(14), dp(16), dp(12));
        return bar;
    }

    /** Back button with a centered page title; {@code end} sits on the right (or a spacer). */
    LinearLayout backTitleRow(String title, Runnable onBack, View end) {
        LinearLayout row = horizontal();
        ImageView back = iconButton(R.drawable.ic_back, "Back");
        back.setOnClickListener(v -> onBack.run());
        row.addView(back);
        TextView t = text(title, PAGE_TITLE_SP, textColor, true);
        t.setGravity(Gravity.CENTER);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(end != null ? end : gap(ICON_BUTTON_DP)); // spacer keeps the title centered
        return row;
    }

    // ---- building blocks -----------------------------------------------------

    TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(activity);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    Button button(String label) {
        Button b = new Button(activity);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setBackground(rounded(buttonFill, 10));
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        b.setMinHeight(0);
        b.setMinimumHeight(dp(40));
        return b;
    }

    /** Small square button showing only an icon; {@code label} is read by screen readers. */
    ImageView iconButton(int icon, String label) {
        ImageView b = icon(icon, textColor);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp((ICON_BUTTON_DP - ICON_DP) / 2);
        b.setPadding(pad, pad, pad, pad);
        b.setBackground(rounded(buttonFill, 10));
        b.setContentDescription(label);
        b.setClickable(true);
        b.setFocusable(true);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(ICON_BUTTON_DP), dp(ICON_BUTTON_DP)));
        return b;
    }

    /** Vector icon tinted with {@code color}. */
    ImageView icon(int icon, int color) {
        ImageView v = new ImageView(activity);
        v.setImageResource(icon);
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        return v;
    }

    /** Single-line input with a clear (x) icon that shows as soon as there is text. */
    FrameLayout clearableField(EditText input, String hint, String value, Runnable onCleared) {
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(textColor);
        input.setHintTextColor(mutedColor);
        input.setBackground(rounded(field, 10));
        padForClear(input, value.isEmpty());

        ImageView clear = icon(R.drawable.ic_close, mutedColor);
        clear.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        clear.setContentDescription("Clear text");
        clear.setPadding(dp(10), dp(10), dp(10), dp(10));
        clear.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        clear.setOnClickListener(v -> {
            input.setText("");
            input.requestFocus();
            if (onCleared != null) onCleared.run();
        });
        onTextChanged(input, s -> {
            clear.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
            padForClear(input, s.isEmpty());
        });

        FrameLayout box = new FrameLayout(activity);
        box.addView(input, new FrameLayout.LayoutParams(-1, -2));
        box.addView(clear, new FrameLayout.LayoutParams(dp(CLEAR_ICON_DP), dp(CLEAR_ICON_DP),
                Gravity.END | Gravity.CENTER_VERTICAL));
        return box;
    }

    /** Room for the clear icon only while it shows, so the hint is never cut short. */
    private void padForClear(EditText input, boolean empty) {
        int pad = dp(FIELD_PAD_DP);
        input.setPadding(pad, dp(10), empty ? pad : dp(CLEAR_ICON_DP), dp(10));
    }

    /** Calls {@code action} with the new text after every change. */
    static void onTextChanged(EditText input, Consumer<String> action) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                action.accept(s.toString());
            }
        });
    }

    LinearLayout card(int fill) {
        LinearLayout c = vertical();
        c.setBackground(rounded(fill, 14));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        return c;
    }

    View row(String label, String value, boolean good) {
        LinearLayout r = horizontal();
        r.setPadding(0, dp(5), 0, dp(5));
        r.addView(text("● ", 14, good ? GOOD_DOT : BAD_DOT, false));
        r.addView(text(label, 15, textColor, false), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(text(value, 14, mutedColor, false));
        return r;
    }

    TextView chip(int score) {
        TextView c = text(String.valueOf(score), 12, WHITE, true);
        c.setGravity(Gravity.CENTER);
        c.setBackground(rounded(Scoring.bandColor(score), 6));
        c.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(24)));
        return c;
    }

    TextView pill(String label, int color) {
        TextView p = text(label, 12, WHITE, false);
        p.setBackground(rounded(color, 10));
        p.setPadding(dp(8), dp(2), dp(8), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        p.setLayoutParams(lp);
        return p;
    }

    /** One tappable product line: score chip, name and a grey second line. */
    LinearLayout productLine(Product p, String sub) {
        LinearLayout r = horizontal();
        r.setPadding(0, dp(5), 0, dp(5));
        r.addView(chip(Scoring.score(p)));
        LinearLayout col = vertical();
        col.setPadding(dp(10), 0, 0, 0);
        col.addView(text(p.name, 15, textColor, true));
        if (!sub.isEmpty()) col.addView(text(sub, 12, mutedColor, false));
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    LinearLayout vertical() {
        LinearLayout l = new LinearLayout(activity);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(activity);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    View gap(int dpSize) {
        View v = new View(activity);
        v.setLayoutParams(new ViewGroup.LayoutParams(dp(dpSize), dp(dpSize)));
        return v;
    }

    GradientDrawable rounded(int color, int radiusDp) {
        return rounded(activity, color, radiusDp);
    }

    void hideKeyboard(View v) {
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    int dp(int v) {
        return dp(activity, v);
    }

    // ---- theme-free helpers (also used by the camera screen) ------------------

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(Context c, int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static String fmt(float v, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    /** Blend a color toward a background by f (0 = background, 1 = color). */
    static int tint(int c, float f, int bg) {
        int r = mix((bg >> 16) & 0xFF, (c >> 16) & 0xFF, f);
        int g = mix((bg >> 8) & 0xFF, (c >> 8) & 0xFF, f);
        int b = mix(bg & 0xFF, c & 0xFF, f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int x, int y, float f) {
        return Math.round(x + (y - x) * f);
    }
}
