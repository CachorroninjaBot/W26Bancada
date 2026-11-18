package com.luiz.w26;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Icones de linha (estilo Da Fit) desenhados em Canvas, tingiveis por estado. */
public final class Icon extends Drawable {
    public static final int HOME = 0, DIAL = 1, BT = 2, SYNC = 3, SLIDERS = 4,
            TERMINAL = 5, PIN = 6, BELL = 7, MUSIC = 8, HEART = 9, MAP = 10,
            BATTERY = 11, WATCH = 12, PLUS = 13, TRASH = 14, LIST = 15, CHEVRON = 16;

    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public Icon(int kind, int color, float density) {
        this.kind = kind;
        this.density = density;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.9f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColor(int color) { paint.setColor(color); invalidateSelf(); }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        canvas.save();
        canvas.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        canvas.scale(s, s);
        Path p = new Path();
        switch (kind) {
            case HOME:
                p.moveTo(3.5f, 11); p.lineTo(12, 4); p.lineTo(20.5f, 11);
                canvas.drawPath(p, paint);
                p.reset();
                p.moveTo(6, 10); p.lineTo(6, 20); p.lineTo(18, 20); p.lineTo(18, 10);
                canvas.drawPath(p, paint);
                canvas.drawRect(10, 14, 14, 20, paint);
                break;
            case DIAL:
                canvas.drawCircle(12, 12, 8.2f, paint);
                p.moveTo(12, 12); p.lineTo(12, 7.2f);
                p.moveTo(12, 12); p.lineTo(15.4f, 13.4f);
                canvas.drawPath(p, paint);
                break;
            case WATCH:
                canvas.drawRoundRect(new RectF(6.5f, 6.5f, 17.5f, 17.5f), 3, 3, paint);
                p.moveTo(9, 6.5f); p.lineTo(9.4f, 3); p.lineTo(14.6f, 3); p.lineTo(15, 6.5f);
                p.moveTo(9, 17.5f); p.lineTo(9.4f, 21); p.lineTo(14.6f, 21); p.lineTo(15, 17.5f);
                canvas.drawPath(p, paint);
                p.reset();
                p.moveTo(12, 9.5f); p.lineTo(12, 12); p.lineTo(14, 13);
                canvas.drawPath(p, paint);
                break;
            case BT:
                p.moveTo(12, 3); p.lineTo(12, 21);
                p.moveTo(12, 3); p.lineTo(17, 8); p.lineTo(7, 16);
                p.moveTo(12, 21); p.lineTo(17, 16); p.lineTo(7, 8);
                canvas.drawPath(p, paint);
                break;
            case SYNC:
                canvas.drawArc(new RectF(4.5f, 4.5f, 19.5f, 19.5f), 200, 140, false, paint);
                canvas.drawArc(new RectF(4.5f, 4.5f, 19.5f, 19.5f), 20, 140, false, paint);
                p.moveTo(19.5f, 5); p.lineTo(19.5f, 9.5f); p.lineTo(15, 9.5f);
                p.moveTo(4.5f, 19); p.lineTo(4.5f, 14.5f); p.lineTo(9, 14.5f);
                canvas.drawPath(p, paint);
                break;
            case SLIDERS:
                canvas.drawLine(4, 7, 20, 7, paint);
                canvas.drawLine(4, 12, 20, 12, paint);
                canvas.drawLine(4, 17, 20, 17, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(9, 7, 2.1f, paint);
                canvas.drawCircle(15, 12, 2.1f, paint);
                canvas.drawCircle(8, 17, 2.1f, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case TERMINAL:
                canvas.drawRoundRect(new RectF(3, 5, 21, 19), 2.5f, 2.5f, paint);
                p.moveTo(7, 9.5f); p.lineTo(10, 12); p.lineTo(7, 14.5f);
                p.moveTo(12.5f, 14.5f); p.lineTo(16.5f, 14.5f);
                canvas.drawPath(p, paint);
                break;
            case PIN:
                p.moveTo(12, 21);
                p.cubicTo(12, 21, 5.5f, 14.5f, 5.5f, 9.8f);
                p.arcTo(new RectF(5.5f, 3.3f, 18.5f, 16.3f), 180, 180);
                p.cubicTo(18.5f, 14.5f, 12, 21, 12, 21);
                canvas.drawPath(p, paint);
                canvas.drawCircle(12, 9.6f, 2.4f, paint);
                break;
            case BELL:
                p.moveTo(6, 16); p.lineTo(6, 10.5f);
                p.arcTo(new RectF(6, 4.5f, 18, 16.5f), 180, 180);
                p.lineTo(18, 16); p.lineTo(20, 18); p.lineTo(4, 18); p.close();
                canvas.drawPath(p, paint);
                p.reset();
                p.moveTo(10, 20.5f); p.arcTo(new RectF(10, 18.5f, 14, 22.5f), 180, -180);
                canvas.drawPath(p, paint);
                break;
            case MUSIC:
                p.moveTo(9, 18); p.lineTo(9, 6); p.lineTo(19, 4); p.lineTo(19, 16);
                canvas.drawPath(p, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(7, 18, 2.2f, paint);
                canvas.drawCircle(17, 16, 2.2f, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case HEART:
                p.moveTo(12, 20);
                p.cubicTo(4, 14.5f, 4, 8, 8, 6.2f);
                p.cubicTo(10.2f, 5.2f, 12, 7, 12, 8.4f);
                p.cubicTo(12, 7, 13.8f, 5.2f, 16, 6.2f);
                p.cubicTo(20, 8, 20, 14.5f, 12, 20);
                canvas.drawPath(p, paint);
                break;
            case MAP:
                p.moveTo(3, 6); p.lineTo(9, 4); p.lineTo(15, 6); p.lineTo(21, 4);
                p.lineTo(21, 18); p.lineTo(15, 20); p.lineTo(9, 18); p.lineTo(3, 20); p.close();
                p.moveTo(9, 4); p.lineTo(9, 18);
                p.moveTo(15, 6); p.lineTo(15, 20);
                canvas.drawPath(p, paint);
                break;
            case BATTERY:
                canvas.drawRoundRect(new RectF(3, 8, 18, 16), 2, 2, paint);
                canvas.drawLine(20.5f, 10.5f, 20.5f, 13.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(5, 10, 12, 14), 1, 1, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case PLUS:
                canvas.drawLine(12, 5, 12, 19, paint);
                canvas.drawLine(5, 12, 19, 12, paint);
                break;
            case TRASH:
                p.moveTo(5, 7); p.lineTo(19, 7);
                p.moveTo(9, 7); p.lineTo(9, 4.5f); p.lineTo(15, 4.5f); p.lineTo(15, 7);
                p.moveTo(7, 7); p.lineTo(8, 20); p.lineTo(16, 20); p.lineTo(17, 7);
                p.moveTo(10.5f, 10.5f); p.lineTo(10.8f, 17);
                p.moveTo(13.5f, 10.5f); p.lineTo(13.2f, 17);
                canvas.drawPath(p, paint);
                break;
            case LIST:
                canvas.drawLine(8, 6.5f, 20, 6.5f, paint);
                canvas.drawLine(8, 12, 20, 12, paint);
                canvas.drawLine(8, 17.5f, 20, 17.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(4.3f, 6.5f, 1.3f, paint);
                canvas.drawCircle(4.3f, 12, 1.3f, paint);
                canvas.drawCircle(4.3f, 17.5f, 1.3f, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case CHEVRON:
                p.moveTo(9, 6); p.lineTo(15, 12); p.lineTo(9, 18);
                canvas.drawPath(p, paint);
                break;
            default:
                canvas.drawCircle(12, 12, 8, paint);
        }
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}