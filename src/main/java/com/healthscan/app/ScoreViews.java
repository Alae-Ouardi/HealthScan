package com.healthscan.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Hand-drawn score widgets: the colored score disc and the bad -> excellent scale. */
final class ScoreViews {
    private ScoreViews() {}

    /** Round colored disc showing "NN /100". */
    static final class Disc extends View {
        private final int score;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Disc(Context c, int score) {
            super(c);
            this.score = score;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - 4f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Scoring.bandColor(score));
            canvas.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.07f);
            paint.setColor(Ui.WHITE);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(r * 0.72f);
            canvas.drawText(String.valueOf(score), cx, cy + r * 0.12f, paint);
            paint.setTextSize(r * 0.26f);
            paint.setColor(0xD8FFFFFF);
            canvas.drawText("/100", cx, cy + r * 0.52f, paint);
        }
    }

    /** Four colored segments with a pointer above the score position. */
    static final class Scale extends View {
        private static final int[] BANDS = {
            Scoring.COLOR_BAD, Scoring.COLOR_POOR, Scoring.COLOR_GOOD, Scoring.COLOR_EXCELLENT};
        private final int score;
        private final int pointerColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Scale(Context c, int score, int pointerColor) {
            super(c);
            this.score = score;
            this.pointerColor = pointerColor;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float top = h * 0.45f, gap = h * 0.08f, seg = w / BANDS.length;
            for (int i = 0; i < BANDS.length; i++) {
                paint.setColor(BANDS[i]);
                canvas.drawRoundRect(i * seg, top, (i + 1) * seg - gap, h, 6f, 6f, paint);
            }
            float x = Math.max(8f, Math.min(w - 8f, w * score / 100f));
            Path tri = new Path();
            tri.moveTo(x, top - 3f);
            tri.lineTo(x - top * 0.45f, 0f);
            tri.lineTo(x + top * 0.45f, 0f);
            tri.close();
            paint.setColor(pointerColor);
            canvas.drawPath(tri, paint);
        }
    }
}
