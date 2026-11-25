package com.craftly.studio.design;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

public class ImageLayer extends Layer {
    public final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    public ImageLayer(Bitmap bitmap) {
        this.bitmap = bitmap;
        this.name = "Image";
    }

    @Override
    public float getWidth() {
        return bitmap.getWidth();
    }

    @Override
    public float getHeight() {
        return bitmap.getHeight();
    }

    @Override
    protected void drawLocal(Canvas c) {
        c.drawBitmap(bitmap, -bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f, paint);
    }

    @Override
    public Layer copy() {
        ImageLayer l = new ImageLayer(bitmap);
        copyBaseTo(l);
        return l;
    }
}
