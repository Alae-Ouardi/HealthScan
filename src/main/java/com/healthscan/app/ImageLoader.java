package com.healthscan.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import java.io.IOException;
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

    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 4 * 1024 * 1024; // refuse oversized downloads
    private static final int CACHE_KB = 8 * 1024;
    private static final int HTTP_OK = 200;

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
        try {
            Http.Response r = Http.get(url, TIMEOUT_MS, MAX_BYTES);
            if (r.code != HTTP_OK) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(r.body, 0, r.body.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null; // not an image
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(Math.max(bounds.outWidth, bounds.outHeight), maxPx);
            return BitmapFactory.decodeByteArray(r.body, 0, r.body.length, opts);
        } catch (IOException e) {
            Log.w(AppInfo.LOG_TAG, "product photo not loaded: " + e.getMessage());
            return null;
        }
    }

    private static int sampleSize(int longest, int maxPx) {
        int size = 1;
        while (longest / (size * 2) >= maxPx) size *= 2;
        return size;
    }
}
