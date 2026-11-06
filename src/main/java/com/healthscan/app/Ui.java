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
import android.widget.TextView;
import java.util.Locale;

/** Theme colors and the small view builders shared by every screen. */
final class Ui {
    static final String PREFS = "healthscan", KEY_DARK = "dark";

    static final int GREEN = 0xFF1F9A55, GOOD_DOT = 0xFF46B464, BAD_DOT = 0xFFE05648;
    static final int ICON_BUTTON_DP = 40, ICON_DP = 22;

    final Activity a;
    final boolean dark;
    final int bg, top, card, text, weak, field, pointer, buttonFill;

    Ui(Activity a) {
        this.a = a;
        this.dark = isDark(a);
        bg = dark ? 0xFF121316 : 0xFFF4F5F7;
        top = dark ? 0xFF181A1E : 0xFFFFFFFF;
        card = dark ? 0xFF202328 : 0xFFFFFFFF;
        text = dark ? 0xFFF0F0F0 : 0xFF1A1C20;
        weak = dark ? 0xFF9A9CA2 : 0xFF6E7076;
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
        a.getWindow().setStatusBarColor(top);
        a.getWindow().setNavigationBarColor(bg);
        int flags = 0;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // ---- building blocks -----------------------------------------------------

    TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(a);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    Button button(String label) {
        Button b = new Button(a);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(text);
        b.setBackground(rounded(buttonFill, 10));
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        b.setMinHeight(0);
        b.setMinimumHeight(dp(40));
        return b;
    }

    /** Small square button showing only an icon; {@code label} is read by screen readers. */
    ImageView iconButton(int icon, String label) {
        ImageView b = new ImageView(a);
        b.setImageResource(icon);
        b.setColorFilter(text, PorterDuff.Mode.SRC_IN);
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

    /** Single-line input with a clear (x) icon that shows as soon as there is text. */
    FrameLayout clearableField(EditText input, String hint, String value, Runnable onCleared) {
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(text);
        input.setHintTextColor(weak);
        input.setBackground(rounded(field, 10));
        // Room for the clear icon only while it shows, so the hint is never cut short.
        input.setPadding(dp(12), dp(10), dp(value.isEmpty() ? 12 : 44), dp(10));

        ImageView clear = new ImageView(a);
        clear.setImageResource(R.drawable.ic_close);
        clear.setColorFilter(weak, PorterDuff.Mode.SRC_IN);
        clear.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        clear.setContentDescription("Clear text");
        clear.setPadding(dp(10), dp(10), dp(10), dp(10));
        clear.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        clear.setOnClickListener(v -> {
            input.setText("");
            input.requestFocus();
            if (onCleared != null) onCleared.run();
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                boolean empty = s.length() == 0;
                clear.setVisibility(empty ? View.GONE : View.VISIBLE);
                input.setPadding(dp(12), dp(10), dp(empty ? 12 : 44), dp(10));
            }
        });

        FrameLayout box = new FrameLayout(a);
        box.addView(input, new FrameLayout.LayoutParams(-1, -2));
        box.addView(clear, new FrameLayout.LayoutParams(dp(44), dp(44),
                Gravity.END | Gravity.CENTER_VERTICAL));
        return box;
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
        r.addView(text(label, 15, text, false), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(text(value, 14, weak, false));
        return r;
    }

    TextView chip(int score) {
        TextView c = text(String.valueOf(score), 12, 0xFFFFFFFF, true);
        c.setGravity(Gravity.CENTER);
        c.setBackground(rounded(Scoring.bandColor(score), 6));
        c.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(24)));
        return c;
    }

    TextView pill(String label, int color) {
        TextView p = text(label, 12, 0xFFFFFFFF, false);
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
        col.addView(text(p.name, 15, text, true));
        if (!sub.isEmpty()) col.addView(text(sub, 12, weak, false));
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    LinearLayout vertical() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    View gap(int dpSize) {
        View v = new View(a);
        v.setLayoutParams(new ViewGroup.LayoutParams(dp(dpSize), dp(dpSize)));
        return v;
    }

    GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    int dp(int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
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
