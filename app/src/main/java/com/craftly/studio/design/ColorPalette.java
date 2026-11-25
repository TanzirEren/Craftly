package com.craftly.studio.design;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

public class ColorPalette {

    public interface OnColor {
        void onColor(int color);
    }

    public static final int[] COLORS = {
            0xFFFFFFFF, 0xFF000000, 0xFF9575E8, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA,
            0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1, 0xFF00897B,
            0xFF43A047, 0xFF7CB342, 0xFFC0CA33, 0xFFFDD835, 0xFFFFB300, 0xFFFB8C00,
            0xFFF4511E, 0xFF6D4C41, 0xFF757575, 0xFF546E7A, 0xFF201E29, 0xFFFDFBFC
    };

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Fills the row with a rainbow "custom" swatch followed by preset colors. */
    public static void populate(final LinearLayout row, final int selected, final OnColor cb) {
        final Context ctx = row.getContext();
        row.removeAllViews();

        View custom = new View(ctx);
        GradientDrawable rainbow = new GradientDrawable();
        rainbow.setShape(GradientDrawable.OVAL);
        rainbow.setGradientType(GradientDrawable.SWEEP_GRADIENT);
        rainbow.setColors(new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000});
        rainbow.setStroke(dp(ctx, 1), 0xFFFFFFFF);
        custom.setBackground(rainbow);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40));
        clp.setMargins(dp(ctx, 6), 0, dp(ctx, 6), 0);
        row.addView(custom, clp);
        custom.setOnClickListener(v -> showCustom(ctx, selected, cb));

        for (final int color : COLORS) {
            View v = new View(ctx);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            if (color == selected) {
                d.setStroke(dp(ctx, 3), 0xFF9575E8);
            } else {
                d.setStroke(dp(ctx, 1), 0xFF6B6880);
            }
            v.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 40));
            lp.setMargins(dp(ctx, 6), 0, dp(ctx, 6), 0);
            row.addView(v, lp);
            v.setOnClickListener(x -> cb.onColor(color));
        }
    }

    public static void showCustom(Context ctx, int initial, final OnColor cb) {
        final int[] rgb = {Color.red(initial), Color.green(initial), Color.blue(initial)};
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(ctx, 20);
        root.setPadding(p, p, p, 0);

        final View preview = new View(ctx);
        preview.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
        root.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 48)));

        String[] labels = {"R", "G", "B"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(ctx);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = new TextView(ctx);
            t.setText(labels[i]);
            r.addView(t, new LinearLayout.LayoutParams(dp(ctx, 24), LinearLayout.LayoutParams.WRAP_CONTENT));
            SeekBar sb = new SeekBar(ctx);
            sb.setMax(255);
            sb.setProgress(rgb[i]);
            r.addView(sb, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                    rgb[idx] = v;
                    preview.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            root.addView(r);
        }

        new AlertDialog.Builder(ctx)
                .setTitle("Custom color")
                .setView(root)
                .setPositiveButton("OK", (d, w) -> cb.onColor(Color.rgb(rgb[0], rgb[1], rgb[2])))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
