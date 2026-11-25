package com.craftly.studio.design;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

public class TextLayer extends Layer {
    public static final String[] FONTS = {
            "sans-serif", "sans-serif-light", "sans-serif-medium", "sans-serif-black",
            "sans-serif-condensed", "serif", "monospace", "cursive", "casual", "serif-monospace"
    };
    public static final String[] FONT_NAMES = {
            "Sans", "Light", "Medium", "Black", "Condensed", "Serif", "Mono", "Cursive", "Casual", "Serif Mono"
    };

    public String text = "Your text";
    public float textSize = 100f;
    public int color = Color.BLACK;
    public int fontIndex = 0;
    public boolean bold = false;
    public boolean italic = false;
    public float letterSpacing = 0f;
    public float lineSpacing = 1f;
    public Layout.Alignment align = Layout.Alignment.ALIGN_CENTER;

    private StaticLayout layout;
    private boolean dirty = true;

    public TextLayer() {
        name = "Text";
    }

    public void markDirty() {
        dirty = true;
    }

    private void ensure() {
        if (!dirty && layout != null) return;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(textSize);
        int style = (bold && italic) ? Typeface.BOLD_ITALIC
                : bold ? Typeface.BOLD
                : italic ? Typeface.ITALIC : Typeface.NORMAL;
        tp.setTypeface(Typeface.create(FONTS[fontIndex], style));
        tp.setLetterSpacing(letterSpacing);
        tp.setColor(color);
        String t = text.isEmpty() ? " " : text;
        float w = 0f;
        for (String line : t.split("\n", -1)) {
            w = Math.max(w, tp.measureText(line));
        }
        int iw = Math.max(1, (int) Math.ceil(w) + 2);
        layout = StaticLayout.Builder.obtain(t, 0, t.length(), tp, iw)
                .setAlignment(align)
                .setLineSpacing(0f, lineSpacing)
                .setIncludePad(false)
                .build();
        dirty = false;
    }

    @Override
    public float getWidth() {
        ensure();
        return layout.getWidth();
    }

    @Override
    public float getHeight() {
        ensure();
        return layout.getHeight();
    }

    @Override
    protected void drawLocal(Canvas c) {
        ensure();
        c.translate(-layout.getWidth() / 2f, -layout.getHeight() / 2f);
        layout.draw(c);
    }

    @Override
    public Layer copy() {
        TextLayer t = new TextLayer();
        copyBaseTo(t);
        t.text = text;
        t.textSize = textSize;
        t.color = color;
        t.fontIndex = fontIndex;
        t.bold = bold;
        t.italic = italic;
        t.letterSpacing = letterSpacing;
        t.lineSpacing = lineSpacing;
        t.align = align;
        return t;
    }
}
