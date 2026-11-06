package com.healthscan.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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
import android.widget.TextView;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen: search bar, camera scan and one central message area (hint, lookup in
 * progress, nothing to search, not found, lookup failed, offline). A match opens
 * {@link ProductActivity}. Also hosts the history list.
 */
public final class MainActivity extends Activity {
    private static final int REQ_SCAN = 1;
    private static final int MESSAGE_ICON_DP = 56;
    private static final ExecutorService LOOKUPS = Executors.newFixedThreadPool(2);

    // Saved across screen re-creation.
    private static final String S_QUERY = "query", S_HISTORY_QUERY = "historyQuery",
            S_HISTORY_SCREEN = "historyScreen", S_MESSAGE = "message", S_MESSAGE_QUERY = "messageQuery";

    /** What the central message area shows. */
    private enum Message { HINT, LOADING, EMPTY_QUERY, NOT_FOUND, LOOKUP_FAILED, OFFLINE }

    private Ui ui;
    private History history;
    private Network network;
    private String query = "";
    private String historyQuery = "";
    private boolean historyScreen;
    private Message message = Message.HINT;
    private String messageQuery = "";
    private int lookupId; // answers to searches the person has moved on from are ignored
    private String pendingQuery; // lookup interrupted by leaving the screen; re-run on return
    private LinearLayout messageArea;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        history = new History(this);
        network = new Network(this);
        if (state != null) restore(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(S_QUERY, query);
        out.putString(S_HISTORY_QUERY, historyQuery);
        out.putBoolean(S_HISTORY_SCREEN, historyScreen);
        out.putString(S_MESSAGE, message.name());
        out.putString(S_MESSAGE_QUERY, messageQuery);
    }

    private void restore(Bundle state) {
        query = state.getString(S_QUERY, "");
        historyQuery = state.getString(S_HISTORY_QUERY, "");
        historyScreen = state.getBoolean(S_HISTORY_SCREEN);
        messageQuery = state.getString(S_MESSAGE_QUERY, "");
        try {
            message = Message.valueOf(state.getString(S_MESSAGE, Message.HINT.name()));
        } catch (IllegalArgumentException e) {
            message = Message.HINT;
        }
        if (message == Message.LOADING) pendingQuery = messageQuery; // its answer went to the old screen
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        network.watch(this::onConnectivityChanged);
        if (pendingQuery != null) search(pendingQuery);
    }

    @Override
    protected void onPause() {
        network.stop();
        if (message == Message.LOADING) {
            lookupId++; // never open a product page while the person is elsewhere
            pendingQuery = messageQuery;
        }
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
        LinearLayout body = ui.body();
        View header;
        if (historyScreen) {
            header = historyTopBar(body);
            messageArea = null;
        } else {
            header = topBar();
            messageArea = ui.vertical();
            messageArea.setGravity(Gravity.CENTER_HORIZONTAL);
            body.addView(messageArea);
            showMessage();
        }
        ui.setScreen(header, body, true);
    }

    // ---- top bar -----------------------------------------------------------

    private View topBar() {
        LinearLayout bar = ui.topBarPanel();

        LinearLayout title = ui.horizontal();
        title.addView(ui.text("●", 20, Scoring.COLOR_EXCELLENT, false));
        title.addView(ui.text("  HealthScan", 21, ui.textColor, true), new LinearLayout.LayoutParams(0, -2, 1f));
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
            // A hardware Enter sends key-down and key-up: search once.
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (id == EditorInfo.IME_ACTION_SEARCH || enter) submit(field);
            return true;
        });
        Ui.onTextChanged(field, text -> query = text);
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
        camera.addView(ui.icon(R.drawable.ic_camera, Ui.WHITE),
                new LinearLayout.LayoutParams(ui.dp(Ui.ICON_DP), ui.dp(Ui.ICON_DP)));
        camera.addView(ui.gap(10));
        camera.addView(ui.text("Scan barcode with camera", 16, Ui.WHITE, true));
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
        if (messageArea != null) showMessage();
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
                addIcon(R.drawable.ic_cloud_off, Ui.WARNING);
                addMessage("You're offline",
                        "HealthScan can't search or look up barcodes without an internet connection. "
                                + "Connect to Wi-Fi or mobile data and try again. "
                                + "Your saved scans are still in History.",
                        ui.textColor);
                break;
            case LOADING:
                messageArea.addView(new ProgressBar(this));
                messageArea.addView(ui.gap(12));
                addMessage("Looking up " + messageQuery + "…",
                        "Checking the open food and beauty databases.", ui.textColor);
                break;
            case EMPTY_QUERY:
                addIcon(R.drawable.ic_search, ui.mutedColor);
                addMessage("Nothing to search",
                        "Type a barcode or a product name, or scan a barcode with the camera.", ui.textColor);
                break;
            case NOT_FOUND:
                addIcon(R.drawable.ic_search_off, Ui.BAD_DOT);
                addMessage("Product not found",
                        Finder.looksLikeBarcode(messageQuery)
                                ? "No product with barcode " + messageQuery
                                        + " in the open databases. Check the number or scan it again."
                                : "No product named \"" + messageQuery + "\" among your scans. "
                                        + "Scan its barcode or type the barcode number.",
                        Ui.BAD_DOT);
                break;
            case LOOKUP_FAILED:
                addIcon(R.drawable.ic_cloud_off, Ui.WARNING);
                addMessage("Couldn't reach the product database",
                        "The lookup for " + messageQuery + " didn't go through. "
                                + "Please try again in a moment.",
                        ui.textColor);
                break;
            default:
                addIcon(R.drawable.ic_search, ui.mutedColor);
                addMessage("Scan a product",
                        "Scan a barcode with the camera, or type a barcode or product name above.",
                        ui.textColor);
        }
    }

    /** Large icon shown above the message title. */
    private void addIcon(int icon, int color) {
        ImageView v = ui.icon(icon, color);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        messageArea.addView(v, new LinearLayout.LayoutParams(ui.dp(MESSAGE_ICON_DP), ui.dp(MESSAGE_ICON_DP)));
        messageArea.addView(ui.gap(12));
    }

    private void addMessage(String title, String detail, int titleColor) {
        TextView t = ui.text(title, 19, titleColor, true);
        t.setGravity(Gravity.CENTER);
        messageArea.addView(t, new LinearLayout.LayoutParams(-1, -2));
        messageArea.addView(ui.gap(6));
        TextView d = ui.text(detail, 15, ui.mutedColor, false);
        d.setGravity(Gravity.CENTER);
        messageArea.addView(d, new LinearLayout.LayoutParams(-1, -2));
    }

    private void resetSearch() {
        query = "";
        lookupId++;
        pendingQuery = null;
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
        search(field.getText().toString());
    }

    /** Barcodes are looked up online (saved scans as offline fallback); names in saved scans. */
    private void search(String text) {
        String q = text.trim();
        int id = ++lookupId;
        pendingQuery = null;
        if (q.isEmpty()) {
            setMessage(Message.EMPTY_QUERY, "");
        } else if (!Finder.looksLikeBarcode(q)) {
            openSavedOr(q, Message.NOT_FOUND);
        } else if (!network.isOnline()) {
            openSavedOr(q, Message.OFFLINE);
        } else {
            setMessage(Message.LOADING, q);
            LOOKUPS.execute(() -> lookUp(id, q));
        }
    }

    /** Runs on a lookup thread; hands the answer back to the screen if it is still wanted. */
    private void lookUp(int id, String code) {
        Product found = null;
        boolean failed = false;
        try {
            found = OpenFacts.lookup(code);
        } catch (IOException e) {
            failed = true;
        }
        Product result = found;
        boolean lookupFailed = failed;
        runOnUiThread(() -> {
            if (id != lookupId || isDestroyed()) return;
            if (result != null) {
                openProduct(result);
            } else if (lookupFailed) {
                // A failure while the connection dropped is shown as offline, not as an error.
                openSavedOr(code, network.isOnline() ? Message.LOOKUP_FAILED : Message.OFFLINE);
            } else {
                openSavedOr(code, Message.NOT_FOUND);
            }
        });
    }

    /** Opens the saved scan matching {@code q}, or shows {@code otherwise}. */
    private void openSavedOr(String q, Message otherwise) {
        Product saved = Finder.find(q, history.products());
        if (saved != null) {
            openProduct(saved);
        } else {
            setMessage(otherwise, q);
        }
    }

    private void openProduct(Product p) {
        history.record(p);
        setMessage(Message.HINT, "");
        ProductActivity.open(this, p);
    }

    // ---- history screen ------------------------------------------------------

    /** Top bar of the history screen: back, title, clear and a live search box. */
    private View historyTopBar(LinearLayout body) {
        LinearLayout bar = ui.topBarPanel();
        Button clear = ui.button("Clear");
        clear.setEnabled(!history.isEmpty());
        clear.setAlpha(history.isEmpty() ? 0.5f : 1f);
        clear.setOnClickListener(v -> {
            history.clear();
            historyQuery = "";
            render();
        });
        bar.addView(ui.backTitleRow("History", this::closeHistory, clear));
        bar.addView(ui.gap(10));

        LinearLayout list = ui.vertical();
        body.addView(list);
        EditText search = new EditText(this);
        bar.addView(ui.clearableField(search, "Search your scans", historyQuery, null));
        fillHistory(list);
        Ui.onTextChanged(search, text -> {
            historyQuery = text;
            fillHistory(list);
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
            list.addView(ui.text("No scans yet. Products you open will show up here.", 15, ui.mutedColor, false));
            return;
        }
        List<History.Entry> found = history.search(historyQuery);
        if (found.isEmpty()) {
            list.addView(ui.text("No scans match \"" + historyQuery.trim() + "\".", 14, ui.mutedColor, false));
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
