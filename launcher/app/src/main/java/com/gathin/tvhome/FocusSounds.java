package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.provider.Settings;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.KeyEvent;

/** Preloaded system navigation sounds; one stream prevents a backlog on repeated input. */
final class FocusSounds implements AutoCloseable {
    private final Context context;
    private final SoundPool pool;
    private final SparseIntArray sounds = new SparseIntArray();
    private final SparseBooleanArray ready = new SparseBooleanArray();
    private boolean enabled;
    FocusSounds(Context context) {
        this.context = context;
        pool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p, id, status) -> { if (status == 0) ready.put(id, true); });
        load(KeyEvent.KEYCODE_DPAD_LEFT, "left"); load(KeyEvent.KEYCODE_DPAD_RIGHT, "right");
        load(KeyEvent.KEYCODE_DPAD_UP, "up"); load(KeyEvent.KEYCODE_DPAD_DOWN, "down"); refresh();
    }
    private void load(int key, String direction) {
        String path = "/system/media/audio/ui/android_TV_scroll_" + direction + ".ogg";
        if (new java.io.File(path).canRead()) sounds.put(key, pool.load(path, 1));
    }
    void refresh() { enabled = Settings.System.getInt(context.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0; }
    void play(int key) {
        if (!enabled) return;
        int id = sounds.get(key);
        if (id != 0 && ready.get(id)) {
            int stream = pool.play(id, 1f, 1f, 1, 0, 1f);
            android.util.Log.d("HomeFocusSound", "direction=" + key + " stream=" + stream);
        } else {
            int effect = key == KeyEvent.KEYCODE_DPAD_UP ? AudioManager.FX_FOCUS_NAVIGATION_UP
                    : key == KeyEvent.KEYCODE_DPAD_DOWN ? AudioManager.FX_FOCUS_NAVIGATION_DOWN
                    : key == KeyEvent.KEYCODE_DPAD_LEFT ? AudioManager.FX_FOCUS_NAVIGATION_LEFT : AudioManager.FX_FOCUS_NAVIGATION_RIGHT;
            AudioManager audio = context.getSystemService(AudioManager.class);
            if (audio != null) audio.playSoundEffect(effect);
        }
    }
    @Override public void close() { pool.release(); }
}
