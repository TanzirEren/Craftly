package com.craftly.studio.design;

import android.graphics.Canvas;

/** Base class for everything that can be placed on the Craftly canvas. */
public abstract class Layer {
    public String name = "Layer";
    /** Center position in canvas coordinates. */
    public float cx, cy;
    public float scale = 1f;
    public float rotation = 0f;
    public int opacity = 255;
    public boolean visible = true;
    public boolean locked = false;

    public abstract float getWidth();

    public abstract float getHeight();

    /** Draw centered on (0,0), unscaled, unrotated. */
    protected abstract void drawLocal(Canvas c);

    public abstract Layer copy();

    protected void copyBaseTo(Layer o) {
        o.name = name;
        o.cx = cx;
        o.cy = cy;
        o.scale = scale;
        o.rotation = rotation;
        o.opacity = opacity;
        o.visible = visible;
        o.locked = locked;
    }

    public void draw(Canvas c) {
        if (!visible) return;
        c.save();
        c.translate(cx, cy);
        c.rotate(rotation);
        c.scale(scale, scale);
        if (opacity < 255) {
            float w = getWidth();
            float h = getHeight();
            float pad = Math.max(w, h) * 0.25f;
            int sc = c.saveLayerAlpha(-w / 2f - pad, -h / 2f - pad, w / 2f + pad, h / 2f + pad, opacity);
            drawLocal(c);
            c.restoreToCount(sc);
        } else {
            drawLocal(c);
        }
        c.restore();
    }

    /** x,y in canvas coordinates. */
    public boolean hitTest(float x, float y) {
        float dx = x - cx;
        float dy = y - cy;
        double r = Math.toRadians(-rotation);
        float rx = (float) (dx * Math.cos(r) - dy * Math.sin(r));
        float ry = (float) (dx * Math.sin(r) + dy * Math.cos(r));
        rx /= scale;
        ry /= scale;
        return Math.abs(rx) <= getWidth() / 2f && Math.abs(ry) <= getHeight() / 2f;
    }
}
