package com.healthscan.app;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reads product barcodes (EAN-13/8, UPC-A/E) from a camera frame's brightness plane.
 * Plain Java with no Android parts, so it can be tested on a computer.
 * Not thread-safe: use one instance per decoding thread.
 */
final class BarcodeDecoder {
    private final MultiFormatReader reader = new MultiFormatReader();

    BarcodeDecoder() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
    }

    /**
     * Tries the frame as-is, then turned 90°, so the code reads whichever way the phone is held.
     * {@code frame} starts with a w*h brightness plane (true for the camera's NV21 frames).
     * Returns the digits, or null when nothing readable is in the frame.
     */
    String decode(byte[] frame, int w, int h) {
        if (frame == null || w <= 0 || h <= 0 || frame.length < w * h) return null;
        String code = tryDecode(frame, w, h);
        if (code != null) return code;
        byte[] turned = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) turned[x * h + (h - 1 - y)] = frame[row + x];
        }
        return tryDecode(turned, h, w);
    }

    private String tryDecode(byte[] luminance, int w, int h) {
        try {
            PlanarYUVLuminanceSource src =
                    new PlanarYUVLuminanceSource(luminance, w, h, 0, 0, w, h, false);
            String text = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(src))).getText();
            return text != null && text.matches("\\d{6,14}") ? text : null;
        } catch (ReaderException | IllegalArgumentException e) {
            return null; // nothing readable in this frame
        } finally {
            reader.reset();
        }
    }
}
