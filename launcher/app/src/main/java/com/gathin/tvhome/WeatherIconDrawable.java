package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Crisp small weather symbols, drawn locally without downloading icon images. */
final class WeatherIconDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int code;

    WeatherIconDrawable(int code) { this.code = code; }

    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 64f, getBounds().height() / 64f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (code == 0) {
            sun(canvas, 32, 32, 12);
        } else if (code == 1 || code == 2) {
            sun(canvas, 22, 22, 10);
            cloud(canvas, 7);
        } else if (code == 45 || code == 48) {
            cloud(canvas, -2);
            stroke(0xFFBDCCD8, 3);
            canvas.drawLine(10, 48, 54, 48, paint);
            canvas.drawLine(16, 56, 48, 56, paint);
        } else if (code >= 95) {
            cloud(canvas, -5);
            fill(0xFFFFD35A);
            Path bolt = new Path();
            bolt.moveTo(34, 37); bolt.lineTo(23, 52); bolt.lineTo(32, 52);
            bolt.lineTo(27, 63); bolt.lineTo(44, 44); bolt.lineTo(34, 44); bolt.close();
            canvas.drawPath(bolt, paint);
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            cloud(canvas, -5);
            fill(0xFFCDEAFF);
            for (int x = 18; x <= 46; x += 14) canvas.drawCircle(x, x == 32 ? 56 : 50, 2.7f, paint);
        } else if (code >= 51 && code <= 82) {
            cloud(canvas, -5);
            stroke(0xFF75C7FF, 3.7f);
            for (int x = 20; x <= 48; x += 14) canvas.drawLine(x, 46, x - 5, 56, paint);
        } else {
            cloud(canvas, 4);
        }
        canvas.restoreToCount(save);
    }

    private void sun(Canvas c, float x, float y, float r) {
        stroke(0xFFFFD36B, 3);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4;
            c.drawLine(x + (r + 6) * (float) Math.cos(a), y + (r + 6) * (float) Math.sin(a),
                    x + (r + 11) * (float) Math.cos(a), y + (r + 11) * (float) Math.sin(a), paint);
        }
        fill(0xFFFFD36B);
        c.drawCircle(x, y, r, paint);
    }

    private void cloud(Canvas c, float offset) {
        fill(0xFFDCE4EE);
        c.drawRoundRect(8, 27 + offset, 58, 42 + offset, 8, 8, paint);
        c.drawCircle(19, 29 + offset, 10, paint);
        c.drawCircle(32, 23 + offset, 14, paint);
        c.drawCircle(47, 29 + offset, 10, paint);
        fill(0xFFF4F7FB);
        c.drawRoundRect(12, 30 + offset, 55, 41 + offset, 6, 6, paint);
    }

    private void fill(int color) { paint.setStyle(Paint.Style.FILL); paint.setColor(color); }
    private void stroke(int color, float width) { paint.setStyle(Paint.Style.STROKE); paint.setColor(color); paint.setStrokeWidth(width); }
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
