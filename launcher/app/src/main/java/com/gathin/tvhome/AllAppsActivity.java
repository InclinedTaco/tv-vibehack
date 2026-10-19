package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** All launchable installed apps, kept separate from the curated home tiles. */
public class AllAppsActivity extends Activity implements AppAdapter.TileListener {
    private RecyclerView grid;
    private AppAdapter adapter;
    private int focusedPosition;
    private static final int COLUMNS = 4;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF08090C);
        root.setPadding(dp(56), dp(28), dp(56), 0);
        TextView title = new TextView(this);
        title.setText("All Apps");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setPadding(dp(12), 0, 0, dp(18));
        root.addView(title);
        grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(0, dp(16), 0, dp(24));
        grid.setItemAnimator(null);
        adapter = new AppAdapter(this, this, 0, true);
        grid.setAdapter(adapter);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override public void onResume() {
        super.onResume();
        String key = adapter.keyAt(focusedPosition);
        adapter.reload();
        int restored = adapter.positionOf(key);
        focusedPosition = Math.max(0, restored);
        focusAfterLayout(focusedPosition);
    }

    private void focusAfterLayout(int position) {
        grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
                if (holder != null) holder.itemView.findViewById(R.id.card).requestFocus();
            }
        });
        grid.scrollToPosition(position);
        grid.requestLayout();
    }

    @Override public void onTileFocused(int position, View card) {
        focusedPosition = position;
    }

    @Override public boolean onTileVerticalKey(int position, int key) {
        int count = adapter.getItemCount();
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) return position % COLUMNS == 0;
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT) return position % COLUMNS == COLUMNS - 1 || position == count - 1;
        int target;
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return true;
            target = position - COLUMNS;
        } else {
            if (position >= ((count - 1) / COLUMNS) * COLUMNS) return true;
            target = Math.min(position + COLUMNS, count - 1);
        }
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(target);
        if (holder != null) holder.itemView.findViewById(R.id.card).requestFocus();
        else focusAfterLayout(target);
        return true;
    }
}
