package com.healthscan.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen camera that reads a product barcode and returns it to the main screen.
 * Barcode decoding uses the open-source ZXing library (Apache 2.0).
 */
@SuppressWarnings("deprecation") // the classic camera API is the simplest fit without extra libraries
public final class ScannerActivity extends Activity
        implements SurfaceHolder.Callback, Camera.PreviewCallback {
    static final String EXTRA_CODE = "code";
    private static final int REQ_CAMERA = 7;
    private static final int TARGET_W = 1280, TARGET_H = 720;

    private final BarcodeDecoder barcodes = new BarcodeDecoder(); // used only on the decoder thread
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private SurfaceView surface;
    private TextView hint;
    private Button torch;
    private Camera camera;
    private Camera.Size previewSize;
    private boolean surfaceReady, torchOn;
    private volatile boolean busy, done;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        buildLayout();
        if (!hasCameraPermission()) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(new ScanFrame(this), new FrameLayout.LayoutParams(-1, -1));

        hint = new TextView(this);
        hint.setText("Point the camera at the barcode");
        hint.setTextColor(0xFFFFFFFF);
        hint.setTextSize(17);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), 0, dp(24), dp(120));
        root.addView(hint, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        Button cancel = pillButton("✕  Cancel");
        cancel.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        cp.setMargins(dp(16), dp(40), 0, 0);
        root.addView(cancel, cp);

        torch = pillButton("🔦  Light");
        torch.setOnClickListener(v -> toggleTorch());
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tp.setMargins(0, dp(40), dp(16), 0);
        root.addView(torch, tp);
        setContentView(root);
    }

    // ---- lifecycle -----------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        if (surfaceReady && hasCameraPermission()) startCamera();
    }

    @Override
    protected void onPause() {
        stopCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_CAMERA) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (surfaceReady) startCamera();
        } else {
            hint.setText("Camera permission is needed to scan.\nYou can still type the barcode instead.");
        }
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        surfaceReady = true;
        if (hasCameraPermission()) startCamera();
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hgt) {}

    @Override public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        stopCamera();
    }

    // ---- camera --------------------------------------------------------------

    private void startCamera() {
        if (camera != null) return;
        try {
            int id = backCameraId();
            camera = Camera.open(id);
            Camera.Parameters p = camera.getParameters();
            previewSize = closest(p.getSupportedPreviewSizes());
            p.setPreviewSize(previewSize.width, previewSize.height);
            List<String> modes = p.getSupportedFocusModes();
            if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            camera.setParameters(p);
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(id, info);
            camera.setDisplayOrientation(info.orientation % 360); // screen is locked to portrait
            camera.setPreviewDisplay(surface.getHolder());
            camera.setPreviewCallback(this);
            camera.startPreview();
            fitPreview();
            torch.setVisibility(hasTorch(p) ? View.VISIBLE : View.GONE);
        } catch (Exception e) {
            stopCamera();
            hint.setText("The camera couldn't start.\nClose other camera apps and try again.");
        }
    }

    private void stopCamera() {
        if (camera == null) return;
        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;
        torchOn = false;
    }

    /** Scales the preview to fill the screen without stretching (center crop). */
    private void fitPreview() {
        root.post(() -> {
            if (previewSize == null) return;
            float pw = previewSize.height, ph = previewSize.width; // portrait
            float scale = Math.max(root.getWidth() / pw, root.getHeight() / ph);
            surface.setLayoutParams(new FrameLayout.LayoutParams(
                    Math.round(pw * scale), Math.round(ph * scale), Gravity.CENTER));
        });
    }

    private void toggleTorch() {
        if (camera == null) return;
        try {
            Camera.Parameters p = camera.getParameters();
            torchOn = !torchOn;
            p.setFlashMode(torchOn ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(p);
        } catch (RuntimeException e) {
            torchOn = false;
        }
    }

    // ---- decoding ------------------------------------------------------------

    @Override
    public void onPreviewFrame(byte[] data, Camera cam) {
        if (busy || done || previewSize == null) return;
        busy = true;
        final int w = previewSize.width, h = previewSize.height;
        decoder.execute(() -> {
            String code = barcodes.decode(data, w, h);
            runOnUiThread(() -> {
                busy = false;
                if (code != null && !done) deliver(code);
            });
        });
    }

    private void deliver(String code) {
        done = true;
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_CODE, code));
        finish();
    }

    // ---- helpers ---------------------------------------------------------------

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private static int backCameraId() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return 0;
    }

    private static Camera.Size closest(List<Camera.Size> sizes) {
        Camera.Size best = sizes.get(0);
        long bestDiff = Long.MAX_VALUE;
        for (Camera.Size s : sizes) {
            long diff = Math.abs((long) s.width * s.height - (long) TARGET_W * TARGET_H)
                    + Math.abs(s.width * 9L - s.height * 16L) * 50L;
            if (diff < bestDiff) {
                bestDiff = diff;
                best = s;
            }
        }
        return best;
    }

    private static boolean hasTorch(Camera.Parameters p) {
        List<String> modes = p.getSupportedFlashModes();
        return modes != null && modes.contains(Camera.Parameters.FLASH_MODE_TORCH);
    }

    private Button pillButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x99000000);
        bg.setCornerRadius(dp(22));
        b.setBackground(bg);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Dims the screen around a scan window and draws the white corner frame. */
    private static final class ScanFrame extends View {
        private final Paint dim = new Paint(), line = new Paint(Paint.ANTI_ALIAS_FLAG);

        ScanFrame(android.content.Context c) {
            super(c);
            dim.setColor(0x88000000);
            line.setColor(0xFFFFFFFF);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float fw = w * 0.82f, fh = fw * 0.55f;
            float l = (w - fw) / 2f, t = (h - fh) / 2f - h * 0.04f, r = l + fw, b = t + fh;
            c.drawRect(0, 0, w, t, dim);
            c.drawRect(0, b, w, h, dim);
            c.drawRect(0, t, l, b, dim);
            c.drawRect(r, t, w, b, dim);
            float k = fw * 0.12f;
            line.setStrokeWidth(w * 0.012f);
            line.setColor(0xFFFFFFFF);
            c.drawLine(l, t, l + k, t, line);
            c.drawLine(l, t, l, t + k, line);
            c.drawLine(r, t, r - k, t, line);
            c.drawLine(r, t, r, t + k, line);
            c.drawLine(l, b, l + k, b, line);
            c.drawLine(l, b, l, b - k, line);
            c.drawLine(r, b, r - k, b, line);
            c.drawLine(r, b, r, b - k, line);
            line.setStrokeWidth(w * 0.005f);
            line.setColor(0xCC22A048);
            float mid = (t + b) / 2f;
            c.drawLine(l + k * 0.6f, mid, r - k * 0.6f, mid, line);
        }
    }
}
