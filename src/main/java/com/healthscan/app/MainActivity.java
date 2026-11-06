package com.healthscan.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * Home screen: search bar, camera scan and one central message area (hint, lookup in
 * progress, not found). A match opens {@link ProductActivity}. Also hosts the history list.
 */
public final class MainActivity extends Activity {
    private static final int REQ_SCAN = 1;
    private static final int MESSAGE_ICON_DP = 56;

    /** What the central message area shows. */
    private enum Message { HINT, LOADING, EMPTY_QUERY, NOT_FOUND, OFFLINE }

    private Ui ui;
    private History history;
    private Network network;
    private String query = "";
    private String historyQuery = "";
    private boolean historyScreen;
    private Message message = Message.HINT;
    private String messageQuery = "";
    private int lookupId; // ignores answers to searches the person has moved on from
    private LinearLayout messageArea;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        history = new History(this);
        network = new Network(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        network.watch(this::onConnectivityChanged);
    }

    @Override
    protected void onPause() {
        network.stop();
        super.onPause();
    }

    /** Swaps the home hint for the offline notice (and back) as the connection changes. */
    private void onConnectivityChanged() {
        if (historyScreen || messageArea == null) return;
        if (message == Message.HINT || message == Message.OFFLINE) showMessage();
    }

    private void render() {
        ui = new Ui(this);
        ui.paintWindow();

        LinearLayout root = ui.vertical();
        root.setBackgroundColor(ui.bg);
        LinearLayout body = ui.vertical();
        body.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(24));

        if (historyScreen) {
            root.addView(historyTopBar(body));
        } else {
            root.addView(topBar());
            messageArea = ui.vertical();
            messageArea.setGravity(Gravity.CENTER_HORIZONTAL);
            body.addView(messageArea);
            showMessage();
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    // ---- top bar -----------------------------------------------------------

    private View topBar() {
        LinearLayout bar = ui.vertical();
        bar.setBackgroundColor(ui.top);
        bar.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(12));

        LinearLayout title = ui.horizontal();
        title.addView(ui.text("●", 20, 0xFF22A048, false));
        title.addView(ui.text("  HealthScan", 21, ui.text, true), new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView theme = ui.iconButton(ui.dark ? R.drawable.ic_sun : R.drawable.ic_moon,
                ui.dark ? "Switch to light theme" : "Switch to dark theme");
        theme.setOnClickListener(v -> toggleDark());
        title.addView(theme);
        title.addView(ui.gap(8));
        ImageView historyButton = ui.iconButton(R.drawable.ic_history, "Scan history");
        historyButton.setOnClickListener(v -> openHistory());
        title.addView(historyButton);
        bar.addView(title);
        bar.addView(ui.gap(12));

        LinearLayout search = ui.horizontal();
        EditText field = new EditText(this);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setOnEditorActionListener((v, id, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (id == EditorInfo.IME_ACTION_SEARCH || enter) submit(field);
            return true;
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
            }
        });
        search.addView(ui.clearableField(field, "Type a barcode or a name", query, this::resetSearch),
                new LinearLayout.LayoutParams(0, -2, 1f));
        Button go = ui.button("Search");
        go.setOnClickListener(v -> submit(field));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2);
        gp.leftMargin = ui.dp(8);
        search.addView(go, gp);
        bar.addView(search);
        bar.addView(ui.gap(10));

        bar.addView(cameraButton(), new LinearLayout.LayoutParams(-1, ui.dp(50)));
        return bar;
    }

    /** Green full-width button with the camera icon right next to its label. */
    private View cameraButton() {
        LinearLayout camera = ui.horizontal();
        camera.setGravity(Gravity.CENTER);
        camera.setBackground(ui.rounded(Ui.GREEN, 12));
        camera.setClickable(true);
        camera.setFocusable(true);
        camera.setContentDescription("Scan barcode with camera");
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_camera);
        icon.setColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
        camera.addView(icon, new LinearLayout.LayoutParams(ui.dp(Ui.ICON_DP), ui.dp(Ui.ICON_DP)));
        camera.addView(ui.gap(10));
        TextView label = ui.text("Scan barcode with camera", 16, 0xFFFFFFFF, true);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        camera.addView(label);
        camera.setOnClickListener(v -> openScanner());
        return camera;
    }

    private void toggleDark() {
        Ui.setDark(this, !ui.dark);
        render();
    }

    // ---- central message area --------------------------------------------------

    private void setMessage(Message m, String about) {
        message = m;
        messageQuery = about;
        if (!historyScreen && messageArea != null) showMessage();
    }

    private void showMessage() {
        messageArea.removeAllViews();
        messageArea.setPadding(0, ui.dp(40), 0, ui.dp(10));
        Message shown = message;
        if (shown == Message.HINT || shown == Message.OFFLINE) {
            shown = network.isOnline() ? Message.HINT : Message.OFFLINE;
        }
        switch (shown) {
            case OFFLINE:
                addIcon(R.drawable.ic_cloud_off, 0xFFED9628);
                addMessage("You're offline",
                        "HealthScan can't search or look up barcodes without an internet connection. "
                                + "Connect to Wi-Fi or mobile data and try again. "
                                + "Your saved scans are still in History.",
                        ui.text);
                break;
            case LOADING:
                messageArea.addView(new ProgressBar(this));
                messageArea.addView(ui.gap(12));
                addMessage("Looking up " + messageQuery + "…",
                        "Checking the open food and beauty databases.", ui.text);
                break;
            case EMPTY_QUERY:
                addIcon(R.drawable.ic_search, ui.weak);
                addMessage("Nothing to search",
                        "Type a barcode or a product name, or scan a barcode with the camera.", ui.text);
                break;
            case NOT_FOUND:
                addIcon(R.drawable.ic_search_off, Ui.BAD_DOT);
                addMessage("Product not found",
                        Finder.looksLikeBarcode(messageQuery)
                                ? "No product with barcode " + messageQuery
                                        + " in the open databases. Check the number or scan it again."
                                : "No product named \"" + messageQuery + "\" among your scans. "
                                        + "Scan its barcode or type the barcode number.",
                        0xFFE05648);
                break;
            default:
                addIcon(R.drawable.ic_search, ui.weak);
                addMessage("Scan a product",
                        "Scan a barcode with the camera, or type a barcode or product name above.",
                        ui.text);
        }
    }

    /** Large icon shown above the message title. */
    private void addIcon(int icon, int color) {
        ImageView v = new ImageView(this);
        v.setImageResource(icon);
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        messageArea.addView(v, new LinearLayout.LayoutParams(ui.dp(MESSAGE_ICON_DP), ui.dp(MESSAGE_ICON_DP)));
        messageArea.addView(ui.gap(12));
    }

    private void addMessage(String title, String detail, int titleColor) {
        TextView t = ui.text(title, 19, titleColor, true);
        t.setGravity(Gravity.CENTER);
        messageArea.addView(t, new LinearLayout.LayoutParams(-1, -2));
        messageArea.addView(ui.gap(6));
        TextView d = ui.text(detail, 15, ui.weak, false);
        d.setGravity(Gravity.CENTER);
        messageArea.addView(d, new LinearLayout.LayoutParams(-1, -2));
    }

    private void resetSearch() {
        query = "";
        lookupId++;
        setMessage(Message.HINT, "");
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

    private void submit(EditText field) {
        ui.hideKeyboard(field);
        search(field.getText().toString().trim());
    }

    /** Barcodes are looked up online (saved scans as offline fallback); names in saved scans. */
    private void search(String text) {
        String q = text.trim();
        int id = ++lookupId;
        if (q.isEmpty()) {
            setMessage(Message.EMPTY_QUERY, "");
            return;
        }
        if (!Finder.looksLikeBarcode(q)) {
            Product saved = Finder.find(q, history.products());
            if (saved != null) {
                openProduct(saved);
            } else {
                setMessage(Message.NOT_FOUND, q);
            }
            return;
        }
        if (!network.isOnline()) {
            Product saved = Finder.find(q, history.products());
            if (saved != null) {
                openProduct(saved);
            } else {
                setMessage(Message.OFFLINE, q);
            }
            return;
        }
        setMessage(Message.LOADING, q);
        new Thread(() -> {
            Product found = OpenFacts.lookup(q);
            runOnUiThread(() -> {
                if (id != lookupId || isDestroyed()) return;
                Product p = found != null ? found : Finder.find(q, history.products());
                if (p != null) {
                    openProduct(p);
                } else {
                    // A failed lookup while the connection dropped is not a missing product.
                    setMessage(network.isOnline() ? Message.NOT_FOUND : Message.OFFLINE, q);
                }
            });
        }).start();
    }

    private void openProduct(Product p) {
        history.record(p);
        setMessage(Message.HINT, "");
        ProductActivity.open(this, p);
    }

    // ---- history screen ------------------------------------------------------

    /** Top bar of the history screen: back, title, clear and a live search box. */
    private View historyTopBar(LinearLayout body) {
        LinearLayout bar = ui.vertical();
        bar.setBackgroundColor(ui.top);
        bar.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(12));

        LinearLayout head = ui.horizontal();
        ImageView back = ui.iconButton(R.drawable.ic_back, "Back");
        back.setOnClickListener(v -> closeHistory());
        head.addView(back);
        TextView title = ui.text("History", 21, ui.text, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button clear = ui.button("Clear");
        clear.setEnabled(!history.isEmpty());
        clear.setAlpha(history.isEmpty() ? 0.5f : 1f);
        clear.setOnClickListener(v -> {
            history.clear();
            historyQuery = "";
            render();
        });
        head.addView(clear);
        bar.addView(head);
        bar.addView(ui.gap(10));

        LinearLayout list = ui.vertical();
        body.addView(list);
        EditText search = new EditText(this);
        bar.addView(ui.clearableField(search, "Search your scans", historyQuery, null));
        fillHistory(list);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
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
        if (history.isEmpty()) {
            list.addView(ui.text("No scans yet. Products you open will show up here.", 15, ui.weak, false));
            return;
        }
        List<History.Entry> found = history.search(historyQuery);
        if (found.isEmpty()) {
            list.addView(ui.text("No scans match \"" + historyQuery.trim() + "\".", 14, ui.weak, false));
            return;
        }
        long now = System.currentTimeMillis();
        for (History.Entry e : found) {
            Product p = e.product;
            String when = DateUtils.getRelativeTimeSpanString(e.time, now,
                    DateUtils.MINUTE_IN_MILLIS).toString();
            LinearLayout line = ui.productLine(p, p.brand.isEmpty() ? when : p.brand + " · " + when);
            line.setOnClickListener(v -> openProduct(p));
            list.addView(line);
        }
    }
}
