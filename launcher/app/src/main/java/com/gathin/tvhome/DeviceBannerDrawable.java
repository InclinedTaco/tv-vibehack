package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.RadialGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Small vector-drawn banners for HDMI sources, with the same proportions as app artwork. */
final class DeviceBannerDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String title, port;
    private final String kind;

    DeviceBannerDrawable(String title, String port, boolean camera) {
        this(title, port, camera ? "camera" : "receiver");
    }

    DeviceBannerDrawable(String title, String port, String kind) {
        this.title = title;
        this.port = port;
        this.kind = kind;
    }

    @Override public int getIntrinsicWidth() { return 360; }
    @Override public int getIntrinsicHeight() { return 202; }

    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 360f, getBounds().height() / 202f);
        paint.setStyle(Paint.Style.FILL);
        int top = "camera".equals(kind) ? 0xFF154E79 : "market".equals(kind) ? 0xFF1C6854 : 0xFFD52839;
        int bottom = "camera".equals(kind) ? 0xFF071D39 : "market".equals(kind) ? 0xFF0A2B24 : 0xFF67102B;
        paint.setShader(new LinearGradient(0, 0, 360, 202, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 360, 202, paint);
        paint.setShader(new RadialGradient(180, 45, 170, 0x18FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 360, 202, paint);
        paint.setShader(null);
        paint.setColor(0xFFF3F5F8);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.8f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if ("camera".equals(kind)) {
            // A compact dome camera: solid housing, a distinct lens and no sketch lines.
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(139, 40, 221, 55, 6, 6, paint);
            Path dome = new Path();
            dome.moveTo(145, 61);
            dome.lineTo(215, 61);
            dome.cubicTo(214, 89, 201, 108, 180, 108);
            dome.cubicTo(159, 108, 146, 89, 145, 61);
            dome.close();
            canvas.drawPath(dome, paint);
            paint.setColor(0xFF123653);
            canvas.drawCircle(180, 81, 16, paint);
            paint.setColor(0xFF77C9F2);
            canvas.drawCircle(180, 81, 9, paint);
            paint.setColor(0xFF123653);
            canvas.drawCircle(180, 81, 5, paint);
            paint.setColor(0xFFF3F5F8);
            canvas.drawCircle(183, 78, 2, paint);
        } else if ("market".equals(kind)) {
            paint.setColor(0xFF78B8FF);
            canvas.drawLine(191, 42, 191, 101, paint);
            paint.setColor(0xFFF3F5F8);
            Path line = new Path();
            line.moveTo(139, 91); line.lineTo(155, 72); line.lineTo(168, 80);
            line.lineTo(182, 55); line.lineTo(198, 66); line.lineTo(222, 40);
            canvas.drawPath(line, paint);
        } else {
            canvas.drawRoundRect(143, 43, 217, 85, 7, 7, paint);
            canvas.drawRoundRect(145, 95, 215, 106, 4, 4, paint);
            paint.setStyle(Paint.Style.FILL);
            Path play = new Path();
            play.moveTo(175, 54); play.lineTo(175, 74); play.lineTo(190, 64); play.close();
            canvas.drawPath(play, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextSize(24);
        canvas.drawText(title, 180, port.isEmpty() ? 151 : 150, paint);
        paint.setColor(0xCFFFFFFF);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(13);
        if (!port.isEmpty()) canvas.drawText(port, 180, 175, paint);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
