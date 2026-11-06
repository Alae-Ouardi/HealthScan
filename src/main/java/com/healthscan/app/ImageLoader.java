package com.healthscan.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads product photos off the main thread, scales them down to the size they are
 * shown at, and keeps recent ones in memory so reopening a product is instant.
 */
final class ImageLoader {
    private ImageLoader() {}

    /** Called on the main thread with the photo, or null when it could not be loaded. */
    interface Callback {
        void onResult(Bitmap bitmap);
    }

    private static final String TAG = "HealthScan";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 4 * 1024 * 1024; // refuse oversized downloads
    private static final int CACHE_KB = 8 * 1024;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(CACHE_KB) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount() / 1024;
        }
    };

    /** Loads {@code url}, scaled so its longest side is about {@code maxPx}. */
    static void load(String url, int maxPx, Callback callback) {
        if (url == null || !url.startsWith("https://")) {
            callback.onResult(null);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }
        POOL.execute(() -> {
            Bitmap b = download(url, maxPx);
            if (b != null) CACHE.put(url, b);
            MAIN.post(() -> callback.onResult(b));
        });
    }

    private static Bitmap download(String url, int maxPx) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "HealthScan/0.1 (personal app)");
            if (c.getResponseCode() != 200) return null;
            byte[] data = read(c.getInputStream());
            if (data == null) return null;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(Math.max(bounds.outWidth, bounds.outHeight), maxPx);
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception e) {
            Log.w(TAG, "product photo not loaded: " + e.getClass().getSimpleName());
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static int sampleSize(int longest, int maxPx) {
        int size = 1;
        while (longest / (size * 2) >= maxPx) size *= 2;
        return size;
    }

    private static byte[] read(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        for (int r; (r = in.read(chunk)) != -1; ) {
            buf.write(chunk, 0, r);
            if (buf.size() > MAX_BYTES) return null;
        }
        return buf.toByteArray();
    }
}
