package com.healthscan.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.junit.Test;

/**
 * Checks the barcode reader against generated barcodes placed in fake camera frames.
 * Runs on the JVM with {@code ./gradlew test} (no phone needed).
 */
public final class BarcodeDecoderTest {
    private static final int W = 1280, H = 720; // landscape camera frame

    private final BarcodeDecoder decoder = new BarcodeDecoder();

    @Test
    public void ean13HeldSidewaysBarsAcrossTheFrame() throws Exception {
        assertEquals("4006381333931",
                decoder.decode(frame("4006381333931", BarcodeFormat.EAN_13, false), W, H));
    }

    @Test
    public void ean13HeldUprightNeedsTheQuarterTurn() throws Exception {
        assertEquals("4006381333931",
                decoder.decode(frame("4006381333931", BarcodeFormat.EAN_13, true), W, H));
    }

    @Test
    public void upcA() throws Exception {
        assertEquals("036000291452",
                decoder.decode(frame("036000291452", BarcodeFormat.UPC_A, false), W, H));
    }

    @Test
    public void ean8Upright() throws Exception {
        assertEquals("96385074",
                decoder.decode(frame("96385074", BarcodeFormat.EAN_8, true), W, H));
    }

    @Test
    public void emptyFrameGivesNothing() {
        assertNull(decoder.decode(blank(), W, H));
    }

    @Test
    public void missingFrameGivesNothing() {
        assertNull(decoder.decode(null, W, H));
    }

    @Test
    public void tooShortFrameGivesNothing() {
        assertNull(decoder.decode(new byte[10], W, H));
    }

    /** NV21-sized frame: light background, dark bars, optionally drawn turned by 90°. */
    private static byte[] frame(String digits, BarcodeFormat format, boolean turned) throws Exception {
        byte[] f = blank();
        int bw = turned ? 560 : 900, bh = turned ? 300 : 360;
        BitMatrix m = new MultiFormatWriter().encode(digits, format, bw, bh);
        int ox = (W - (turned ? m.getHeight() : m.getWidth())) / 2;
        int oy = (H - (turned ? m.getWidth() : m.getHeight())) / 2;
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                if (!m.get(x, y)) continue;
                int fx = turned ? ox + y : ox + x;
                int fy = turned ? oy + x : oy + y;
                if (fx >= 0 && fx < W && fy >= 0 && fy < H) f[fy * W + fx] = (byte) 25;
            }
        }
        return f;
    }

    private static byte[] blank() {
        byte[] f = new byte[W * H * 3 / 2];
        java.util.Arrays.fill(f, 0, W * H, (byte) 210);
        java.util.Arrays.fill(f, W * H, f.length, (byte) 128);
        return f;
    }
}
