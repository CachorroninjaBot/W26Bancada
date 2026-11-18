package com.luiz.w26;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Anel de progresso (bateria) estilo Da Fit: trilho + arco colorido. */
public final class Ring extends Drawable {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int level = 0;
    private int color;

    public Ring(int trackColor, int color, float density) {
        this.density = density;
        this.color = color;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(3.4f * density);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(trackColor);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(3.4f * density);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(color);
    }

    public void setPercent(int percent) {
        level = Math.max(0, Math.min(100, percent));
        invalidateSelf();
    }

    public void setColor(int c) { color = c; arc.setColor(c); invalidateSelf(); }

    @Override
    public void draw(Canvas canvas) {
        RectF b = new RectF(getBounds());
        float inset = 3.4f * density / 2f + 1;
        b.inset(inset, inset);
        canvas.drawArc(b, 0, 360, false, track);
        if (level > 0) canvas.drawArc(b, -90, 360f * level / 100f, false, arc);
    }

    @Override public void setAlpha(int alpha) { arc.setAlpha(alpha); track.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { arc.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}