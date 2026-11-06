package com.healthscan.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Tells whether the phone has internet, and reports changes on the main thread. */
final class Network {
    private final ConnectivityManager cm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback callback;
    // While watching, the callbacks are the source of truth: right after a network is lost
    // the system can still report it as active for a moment.
    private volatile Boolean watched;

    Network(Context c) {
        cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    boolean isOnline() {
        Boolean w = watched;
        return w != null ? w : queryNow();
    }

    private boolean queryNow() {
        if (cm == null) return true; // cannot tell: let the lookup itself decide
        android.net.Network active = cm.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
        return hasInternet(caps);
    }

    private static boolean hasInternet(NetworkCapabilities caps) {
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** Calls {@code onChange} on the main thread whenever connectivity changes. */
    void watch(Runnable onChange) {
        stop();
        if (cm == null) return;
        watched = queryNow();
        callback = new ConnectivityManager.NetworkCallback() {
            private android.net.Network current = cm.getActiveNetwork();

            @Override public void onAvailable(android.net.Network n) {
                current = n;
                update(hasInternet(cm.getNetworkCapabilities(n)), onChange);
            }
            @Override public void onLost(android.net.Network n) {
                // A late "lost" for a network that was already replaced (Wi-Fi -> mobile) is ignored.
                if (current == null || n.equals(current)) update(false, onChange);
            }
            @Override public void onCapabilitiesChanged(android.net.Network n, NetworkCapabilities c) {
                current = n;
                update(hasInternet(c), onChange);
            }
        };
        try {
            cm.registerDefaultNetworkCallback(callback);
        } catch (RuntimeException e) {
            Log.w(AppInfo.LOG_TAG, "cannot watch connectivity", e);
            callback = null;
        }
    }

    private void update(boolean online, Runnable onChange) {
        watched = online;
        main.post(onChange);
    }

    void stop() {
        watched = null;
        if (cm == null || callback == null) return;
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (RuntimeException e) {
            Log.w(AppInfo.LOG_TAG, "connectivity watch already stopped", e);
        }
        callback = null;
    }
}
