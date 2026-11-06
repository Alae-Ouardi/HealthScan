package com.healthscan.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Minimal blocking HTTPS GET with timeouts, a size cap and an overall deadline. */
final class Http {
    private Http() {}

    /** Status code and body of a finished request. */
    static final class Response {
        final int code;
        final byte[] body;

        Response(int code, byte[] body) {
            this.code = code;
            this.body = body;
        }
    }

    /**
     * Fetches {@code url}. The body is read for any status, so callers can tell a "not found"
     * answer from a failure. Throws when the connection fails, the deadline passes or the
     * body is larger than {@code maxBytes}.
     */
    static Response get(String url, int timeoutMs, int maxBytes) throws IOException {
        if (url == null || !url.startsWith("https://")) throw new IOException("only HTTPS is allowed");
        long deadline = System.currentTimeMillis() + 2L * timeoutMs;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", AppInfo.USER_AGENT);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Response(code, in == null ? new byte[0] : read(in, maxBytes, deadline));
        } finally {
            c.disconnect();
        }
    }

    private static byte[] read(InputStream in, int maxBytes, long deadline) throws IOException {
        try (InputStream src = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int r; (r = src.read(chunk)) != -1; ) {
                buf.write(chunk, 0, r);
                if (buf.size() > maxBytes) throw new IOException("response too large");
                if (System.currentTimeMillis() > deadline) throw new IOException("response too slow");
            }
            return buf.toByteArray();
        }
    }
}
