package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline photographic dream with two image layers and bounded texture sizes. */
public class SceneryDream extends DreamService {
    private static final long SCENE_MS = 60_000L, DISSOLVE_MS = 2600L;
    private String[][] scenes = {
        {"lofoten.jpg", "LOFOTEN, NORWAY", "Frantisek Duris / Unsplash"},
        {"coast.jpg", "TROPICAL COAST", "Bernd Dittrich / Unsplash"},
        {"dunes.jpg", "DESERT AT DUSK", "Fabian Struwe / Unsplash"}
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final ImageView[] images = new ImageView[2];
    private final ObjectAnimator[] pans = new ObjectAnimator[2];
    private LinearLayout clockBlock, caption;
    private TextView place, credit;
    private ObjectAnimator dissolve;
    private boolean running, detached;
    private int generation, visible = -1, scene;
    private final Runnable next = () -> loadScene((scene + 1) % scenes.length);
    private void readSceneCatalog() {
        try (InputStream input = getAssets().open("scenery/scenes.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            JSONArray entries = new JSONArray(bytes.toString("UTF-8"));
            if (entries.length() == 0) throw new java.io.IOException("Empty scene catalog");
            String[][] loaded = new String[entries.length()][3];
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                String file = entry.getString("file");
                if (!file.matches("[a-z0-9-]+\\.jpg")) throw new java.io.IOException("Invalid scene filename");
                loaded[i] = new String[]{file, entry.getString("label"), entry.getString("credit")};
            }
            scenes = loaded; Log.i("SceneryDream", "Catalog: " + scenes.length + " scenes");
        } catch (Exception failure) { Log.w("SceneryDream", "Using default scenery catalog", failure); }
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void type(TextView v, float size, int color, int weight) {
        v.setTextSize(size); v.setTextColor(color); v.setIncludeFontPadding(false);
        v.setTypeface(getResources().getFont(R.font.outfit)); v.setFontVariationSettings("'wght' " + weight);
        v.setShadowLayer(dp(2), 0, dp(1), 0x60000000);
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow(); readSceneCatalog(); setInteractive(false); setFullscreen(true); setScreenBright(false);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(0xff080f19);
        FrameLayout photos = new FrameLayout(this); root.addView(photos, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < images.length; i++) {
            images[i] = new ImageView(this); images[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            images[i].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); images[i].setAlpha(0f);
            photos.addView(images[i], new FrameLayout.LayoutParams(-1, -1));
        }
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xb3071019, 0x35071019, 0x00071019}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, dp(190), Gravity.BOTTOM));
        caption = new LinearLayout(this); caption.setOrientation(LinearLayout.VERTICAL);
        place = new TextView(this); type(place, 12, 0xe6ffffff, 450); place.setLetterSpacing(0.13f);
        credit = new TextView(this); type(credit, 10, 0xaaffffff, 350); credit.setPadding(0, dp(6), 0, 0);
        caption.addView(place); caption.addView(credit);
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        captionParams.setMargins(dp(48), 0, dp(48), dp(43)); root.addView(caption, captionParams);
        clockBlock = new LinearLayout(this); clockBlock.setOrientation(LinearLayout.VERTICAL); clockBlock.setGravity(Gravity.END);
        LinearLayout timeRow = new LinearLayout(this); timeRow.setGravity(Gravity.BOTTOM);
        TextClock time = new TextClock(this); time.setFormat12Hour("h:mm"); time.setFormat24Hour("HH:mm");
        type(time, 48, 0xf5ffffff, 250); time.setLetterSpacing(-0.035f); timeRow.addView(time);
        TextClock period = new TextClock(this); period.setFormat12Hour("a"); period.setFormat24Hour("");
        type(period, 12, 0xc9ffffff, 400); period.setPadding(dp(6), 0, 0, dp(5)); timeRow.addView(period);
        clockBlock.addView(timeRow);
        TextClock date = new TextClock(this); date.setFormat12Hour("EEE, d MMM"); date.setFormat24Hour("EEE, d MMM");
        type(date, 14, 0xccffffff, 350); date.setPadding(dp(6), dp(5), 0, 0); clockBlock.addView(date);
        // Tighten the visible gap without changing the clock row's measured width or position.
        date.setTranslationY(-dp(10));
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        clockParams.setMargins(dp(48), 0, dp(48), dp(32)); root.addView(clockBlock, clockParams);
        setContentView(root);
    }
    @Override public void onDreamingStarted() {
        super.onDreamingStarted(); running = true;
        scene = Math.floorMod(getSharedPreferences("scenery", MODE_PRIVATE).getInt("next", 0), scenes.length); loadScene(scene);
    }
    private void loadScene(int index) {
        if (!running || detached) return;
        final int token = ++generation;
        final int targetWidth = Math.min(3840, Math.round(getResources().getDisplayMetrics().widthPixels * 1.15f));
        decoder.execute(() -> {
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888; options.inScaled = false;
                try (InputStream input = getAssets().open("scenery/" + scenes[index][0])) {
                    bitmap = BitmapFactory.decodeStream(input, null, options);
                }
                if (bitmap == null) throw new java.io.IOException("Image decode failed");
                // Retain 4K source files, but size runtime textures to the display plus pan headroom.
                if (bitmap.getWidth() > targetWidth) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth,
                            Math.round(bitmap.getHeight() * (targetWidth / (float) bitmap.getWidth())), true);
                    if (scaled != bitmap) bitmap.recycle(); bitmap = scaled;
                }
                Bitmap ready = bitmap;
                handler.post(() -> {
                    if (!running || detached || token != generation) { ready.recycle(); return; }
                    showScene(index, ready);
                });
            } catch (Exception | OutOfMemoryError failure) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                Log.w("SceneryDream", "Could not load scene " + index, failure);
                handler.post(() -> {
                    if (running && !detached && token == generation) { scene = index; handler.postDelayed(next, 5000); }
                });
            }
        });
    }
    private void showScene(int index, Bitmap bitmap) {
        final int old = visible, slot = visible == 0 ? 1 : 0;
        final ImageView incoming = images[slot]; visible = slot; scene = index;
        if (dissolve != null) dissolve.cancel(); if (pans[slot] != null) pans[slot].cancel();
        incoming.setImageBitmap(bitmap); incoming.setAlpha(0f); incoming.bringToFront();
        float sign = index % 2 == 0 ? 1f : -1f;
        float w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        pans[slot] = ObjectAnimator.ofPropertyValuesHolder(incoming,
                PropertyValuesHolder.ofFloat("scaleX", 1.04f, 1.10f), PropertyValuesHolder.ofFloat("scaleY", 1.04f, 1.10f),
                PropertyValuesHolder.ofFloat("translationX", -sign * w * 0.012f, sign * w * 0.012f),
                PropertyValuesHolder.ofFloat("translationY", h * 0.007f, -h * 0.007f));
        pans[slot].setDuration(SCENE_MS + DISSOLVE_MS + 3000);
        pans[slot].setInterpolator(new LinearInterpolator()); pans[slot].start();
        dissolve = ObjectAnimator.ofFloat(incoming, "alpha", 0f, 1f); dissolve.setDuration(old < 0 ? 900 : DISSOLVE_MS);
        dissolve.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (old >= 0 && visible != old) {
                    if (pans[old] != null) pans[old].cancel(); images[old].setAlpha(0f); images[old].setImageDrawable(null);
                }
            }
        });
        dissolve.start(); place.setText(scenes[index][1]); credit.setText(scenes[index][2]);
        caption.setAlpha(0f); caption.animate().alpha(1f).setDuration(1800).start();
        clockBlock.animate().translationX(sign * dp(6)).translationY(index == 1 ? -dp(7) : 0).setDuration(3000).start();
        getSharedPreferences("scenery", MODE_PRIVATE).edit().putInt("next", (index + 1) % scenes.length).apply();
        handler.removeCallbacks(next); handler.postDelayed(next, SCENE_MS);
        Log.i("SceneryDream", "Showing " + scenes[index][0] + " texture=" + bitmap.getWidth() + "x" + bitmap.getHeight());
    }
    private void stopScenes() {
        running = false; generation++; handler.removeCallbacksAndMessages(null);
        if (dissolve != null) dissolve.cancel();
        for (int i = 0; i < images.length; i++) {
            if (pans[i] != null) pans[i].cancel(); if (images[i] != null) images[i].setImageDrawable(null);
        }
        if (caption != null) caption.animate().cancel(); if (clockBlock != null) clockBlock.animate().cancel(); visible = -1;
    }
    @Override public void onDreamingStopped() { stopScenes(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() {
        detached = true; stopScenes(); decoder.shutdownNow(); super.onDetachedFromWindow();
    }
}
