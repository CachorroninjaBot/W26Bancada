package com.luiz.w26;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Grafico simples em Canvas: barras (passos) ou linha (BPM), com rotulos embaixo. */
public final class Chart extends View {
    public static final int BARS = 0, LINE = 1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float[] values = new float[0];
    private String[] labels = new String[0];
    private int color = 0xFF3D7EFF;
    private int mode = BARS;
    private String unit = "";

    public Chart(Context c, float density) {
        super(c);
        this.density = density;
        text.setTextSize(10 * density);
        text.setColor(0xFF7E8CA0);
        grid.setColor(0xFF1E2A38);
        grid.setStrokeWidth(density);
    }

    public void setData(float[] values, String[] labels, int color, int mode, String unit) {
        this.values = values;
        this.labels = labels;
        this.color = color;
        this.mode = mode;
        this.unit = unit;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float padL = 8 * density, padR = 8 * density, padB = 18 * density, padT = 16 * density;
        float plotW = w - padL - padR, plotH = h - padT - padB;
        float max = 0;
        for (float v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;
        max *= 1.15f;
        canvas.drawLine(padL, padT + plotH, w - padR, padT + plotH, grid);
        int n = values.length;
        if (n == 0) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("sem dados ainda", w / 2f, h / 2f, text);
            return;
        }
        text.setTextAlign(Paint.Align.CENTER);
        if (mode == BARS) {
            float slot = plotW / n;
            float bw = Math.min(slot * 0.6f, 26 * density);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < n; i++) {
                float cx = padL + slot * i + slot / 2f;
                float bh = plotH * values[i] / max;
                paint.setColor(values[i] > 0 ? color : 0xFF1E2A38);
                canvas.drawRoundRect(new RectF(cx - bw / 2, padT + plotH - bh, cx + bw / 2, padT + plotH),
                        3 * density, 3 * density, paint);
                if (i < labels.length) canvas.drawText(labels[i], cx, h - 5 * density, text);
            }
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(color);
            float slot = n > 1 ? plotW / (n - 1) : plotW;
            android.graphics.Path path = new android.graphics.Path();
            boolean started = false;
            for (int i = 0; i < n; i++) {
                float cx = padL + slot * i;
                float cy = padT + plotH - plotH * values[i] / max;
                if (values[i] <= 0) {
                    started = false;
                    continue;
                }
                if (!started) {
                    path.moveTo(cx, cy);
                    started = true;
                } else path.lineTo(cx, cy);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, 2.6f * density, paint);
                paint.setStyle(Paint.Style.STROKE);
                if (i < labels.length) canvas.drawText(labels[i], cx, h - 5 * density, text);
            }
            canvas.drawPath(path, paint);
        }
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(unit + " max " + fmt(max / 1.15f), padL, padT - 4 * density, text);
    }

    private static String fmt(float v) {
        if (v >= 1000) return String.format(java.util.Locale.ROOT, "%.1fk", v / 1000f);
        return String.valueOf(Math.round(v));
    }
}