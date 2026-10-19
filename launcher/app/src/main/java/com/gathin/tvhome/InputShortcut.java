package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import java.util.ArrayList;
import java.util.List;

/** Device-specific names and routing live in config.xml. No network or polling. */
final class InputShortcut {
    final String id, label, port;
    final boolean camera;

    private InputShortcut(String id, String label, String port, boolean camera) {
        this.id = id;
        this.label = label;
        this.port = port;
        this.camera = camera;
    }

    static List<InputShortcut> load(Context context) {
        String[] ids = context.getResources().getStringArray(R.array.input_shortcut_ids);
        String[] labels = context.getResources().getStringArray(R.array.input_shortcut_labels);
        String[] ports = context.getResources().getStringArray(R.array.input_shortcut_ports);
        String[] artwork = context.getResources().getStringArray(R.array.input_shortcut_artwork);
        List<InputShortcut> result = new ArrayList<>();
        int count = Math.min(Math.min(ids.length, labels.length), Math.min(ports.length, artwork.length));
        for (int i = 0; i < count; i++) {
            if (!ids[i].trim().isEmpty()) {
                result.add(new InputShortcut(ids[i], labels[i], ports[i], "camera".equals(artwork[i])));
            }
        }
        return result;
    }

    Intent intent(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(TvContract.buildChannelUriForPassthroughInput(id),
                        "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String tvPackage = context.getString(R.string.live_tv_package).trim();
        if (!tvPackage.isEmpty()) intent.setPackage(tvPackage);
        return intent;
    }
}
