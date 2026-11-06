package com.healthscan.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Dedicated page for one product: photo, score, what's inside and better alternatives. */
public final class ProductActivity extends Activity {
    private static final String EXTRA_PRODUCT = "product";
    private static final int PHOTO_HEIGHT_DP = 170;
    private static final int PHOTO_PLACEHOLDER = 0xFFB8BBC2;

    // Per 100 g: at or under these is shown as good (green dot) ...
    private static final float GOOD_MAX_SUGAR = 5f, GOOD_MAX_SATFAT = 2f, GOOD_MAX_SALT = 0.3f;
    // ... and at or over these.
    private static final float GOOD_MIN_FIBER = 3f, GOOD_MIN_PROTEIN = 5f;

    private Ui ui;

    /** Opens the product page for {@code p}; tells the person if it cannot. */
    static void open(Activity from, Product p) {
        try {
            Intent i = new Intent(from, ProductActivity.class);
            i.putExtra(EXTRA_PRODUCT, History.toJson(p).toString());
            from.startActivity(i);
        } catch (JSONException | RuntimeException e) {
            Log.e(AppInfo.LOG_TAG, "could not open product page", e);
            Toast.makeText(from, "This product couldn't be opened.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Product p = readProduct();
        if (p == null) {
            Toast.makeText(this, "This product couldn't be opened.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ui = new Ui(this);
        ui.paintWindow();

        LinearLayout header = ui.topBarPanel();
        header.addView(ui.backTitleRow("Product details", this::finish, null));

        LinearLayout body = ui.body();
        if (!p.imageUrl.isEmpty()) {
            body.addView(photoCard(p));
            body.addView(ui.gap(14));
        }
        body.addView(scoreCard(p));
        body.addView(ui.gap(14));
        body.addView(breakdownCard(p));
        body.addView(ui.gap(14));
        body.addView(alternativesCard(p));

        ScrollView scroll = ui.setScreen(header, body, false);
        // Always start at the photo, even when keyboard navigation moved focus further down.
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private Product readProduct() {
        String json = getIntent().getStringExtra(EXTRA_PRODUCT);
        if (json == null) return null;
        try {
            return History.fromJson(new JSONObject(json));
        } catch (JSONException e) {
            Log.e(AppInfo.LOG_TAG, "unreadable product", e);
            return null;
        }
    }

    /** Product photo on a white card; a spinner while it loads, an icon if it cannot. */
    private View photoCard(Product p) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(ui.rounded(Ui.WHITE, 14));
        frame.setClipToOutline(true);
        int height = ui.dp(PHOTO_HEIGHT_DP);
        frame.setLayoutParams(new LinearLayout.LayoutParams(-1, height));

        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        photo.setContentDescription("Photo of " + p.name);
        frame.addView(photo, new FrameLayout.LayoutParams(-1, -1));

        ProgressBar spinner = new ProgressBar(this);
        frame.addView(spinner, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        ImageLoader.load(p.imageUrl, height, bitmap -> {
            if (isFinishing() || isDestroyed()) return;
            spinner.setVisibility(View.GONE);
            if (bitmap != null) {
                photo.setImageBitmap(bitmap);
            } else {
                photo.setImageResource(R.drawable.ic_photo);
                photo.setColorFilter(PHOTO_PLACEHOLDER, PorterDuff.Mode.SRC_IN);
                photo.setScaleType(ImageView.ScaleType.CENTER);
                photo.setContentDescription("Photo unavailable");
            }
        });
        return frame;
    }

    private View scoreCard(Product p) {
        int s = Scoring.score(p);
        LinearLayout card = ui.card(Ui.tint(Scoring.bandColor(s), 0.18f, ui.card));
        LinearLayout row = ui.horizontal();
        row.addView(new ScoreViews.Disc(this, s), new LinearLayout.LayoutParams(ui.dp(92), ui.dp(92)));

        LinearLayout info = ui.vertical();
        info.setPadding(ui.dp(14), 0, 0, 0);
        info.addView(ui.text(p.name, 19, ui.textColor, true));
        if (!p.brand.isEmpty()) info.addView(ui.text(p.brand, 14, ui.mutedColor, false));
        LinearLayout pills = ui.horizontal();
        pills.setPadding(0, ui.dp(4), 0, ui.dp(4));
        pills.addView(ui.pill(p.food ? "Food" : "Cosmetic", 0xFF5A5A5A));
        if (p.organic) pills.addView(ui.pill("Organic", Scoring.COLOR_EXCELLENT));
        info.addView(pills);
        info.addView(ui.text(Scoring.bandLabel(s), 17, Scoring.bandColor(s), true));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(row);

        card.addView(ui.gap(12));
        card.addView(new ScoreViews.Scale(this, s, ui.pointer),
                new LinearLayout.LayoutParams(-1, ui.dp(26)));
        return card;
    }

    private View breakdownCard(Product p) {
        LinearLayout card = ui.card(ui.card);
        card.addView(ui.text("What's inside", 16, ui.textColor, true));
        card.addView(ui.gap(6));
        if (p.food) {
            Product.Nutrition n = p.nutrition;
            if (n != null) {
                card.addView(ui.row("Sugar", Ui.fmt(n.sugar, 1) + " g", n.sugar <= GOOD_MAX_SUGAR));
                card.addView(ui.row("Saturated fat", Ui.fmt(n.satFat, 1) + " g", n.satFat <= GOOD_MAX_SATFAT));
                card.addView(ui.row("Salt", Ui.fmt(n.salt, 2) + " g", n.salt <= GOOD_MAX_SALT));
                card.addView(ui.row("Fiber", Ui.fmt(n.fiber, 1) + " g", n.fiber >= GOOD_MIN_FIBER));
                card.addView(ui.row("Protein", Ui.fmt(n.protein, 1) + " g", n.protein >= GOOD_MIN_PROTEIN));
            }
            if (p.additives.isEmpty()) card.addView(ui.row("Additives", "none", true));
            for (Product.Additive a : p.additives) {
                card.addView(ui.row(a.name, a.risky ? "risky" : "limited risk", !a.risky));
            }
        } else {
            if (p.ingredients.isEmpty()) card.addView(ui.row("Ingredients", "not listed", true));
            for (Product.Ingredient i : p.ingredients) {
                card.addView(ui.row(i.name, hazardLabel(i.hazard), i.hazard == Product.Ingredient.LOW));
            }
        }
        return card;
    }

    private static String hazardLabel(int hazard) {
        switch (hazard) {
            case Product.Ingredient.HIGH: return "high hazard";
            case Product.Ingredient.MODERATE: return "moderate";
            default: return "low risk";
        }
    }

    /** Better products of the same kind among the ones already scanned. */
    private View alternativesCard(Product base) {
        LinearLayout card = ui.card(ui.card);
        card.addView(ui.text("Better alternatives", 16, ui.textColor, true));
        card.addView(ui.gap(6));
        List<Product> alts = Finder.alternatives(base, new History(this).products());
        if (alts.isEmpty()) {
            card.addView(ui.text("No better product among your scans yet.", 14, ui.mutedColor, false));
        }
        for (Product a : alts) {
            LinearLayout line = ui.productLine(a, a.brand);
            line.setOnClickListener(v -> open(this, a));
            card.addView(line);
        }
        return card;
    }
}
