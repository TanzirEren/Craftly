package com.craftly.studio.design;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;

/**
 * The design surface: draws the artboard and its layers, handles selection,
 * move / scale / rotate gestures, canvas zoom + pan, and undo/redo history.
 */
public class CanvasView extends View {

    public interface Listener {
        void onSelectionChanged(Layer layer);

        void onHistoryChanged();

        void onTextTapped(TextLayer layer);
    }

    private static final int MODE_NONE = 0, MODE_MOVE = 1, MODE_HANDLE = 2,
            MODE_PAN_VIEW = 3, MODE_PINCH_LAYER = 4, MODE_PINCH_VIEW = 5;
    private static final int H_NONE = 0, H_DELETE = 1, H_TRANSFORM = 2;
    private static final int ACCENT = 0xFF9575E8;

    private static class Snapshot {
        ArrayList<Layer> layers = new ArrayList<>();
        int selected = -1;
        int bgColor;
        Bitmap bgBitmap;
    }

    private int canvasW = 1080, canvasH = 1080;
    private int bgColor = Color.WHITE;
    private Bitmap bgBitmap;
    private final ArrayList<Layer> layers = new ArrayList<>();
    private Layer selected;
    private Listener listener;

    private final ArrayList<Snapshot> history = new ArrayList<>();
    private int hIndex = -1;

    private final Matrix viewMatrix = new Matrix();
    private final Matrix inverse = new Matrix();
    private boolean userAdjustedView = false;

    private Paint checkerPaint;
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleIcon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float density;
    private float touchSlop;

    // touch state
    private int mode = MODE_NONE;
    private float downX, downY, lastX, lastY;
    private boolean moved, changed, wasSelectedOnDown;
    private float grabDx, grabDy;
    private float hStartLen, hStartAngle, hStartScale, hStartRot;
    private float pStartDist, pStartAngle, pStartScale, pStartRot, pStartCx, pStartCy, pStartMidX, pStartMidY;
    private float vLastDist, vLastMidX, vLastMidY;

    public CanvasView(Context c) {
        super(c);
        init(c);
    }

    public CanvasView(Context c, AttributeSet a) {
        super(c, a);
        init(c);
    }

    private void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

        Bitmap tile = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                boolean dark = ((x / 24) + (y / 24)) % 2 == 0;
                tile.setPixel(x, y, dark ? 0xFFDDDDDD : 0xFFFFFFFF);
            }
        }
        checkerPaint = new Paint();
        checkerPaint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));

        selPaint.setStyle(Paint.Style.STROKE);
        selPaint.setStrokeWidth(2f * density);
        selPaint.setColor(ACCENT);
        handleFill.setStyle(Paint.Style.FILL);
        handleFill.setColor(ACCENT);
        handleIcon.setStyle(Paint.Style.STROKE);
        handleIcon.setStrokeWidth(2f * density);
        handleIcon.setStrokeCap(Paint.Cap.ROUND);
        handleIcon.setColor(Color.WHITE);
    }

    // ---------------------------------------------------------------- public API

    public void setListener(Listener l) {
        listener = l;
    }

    public void setCanvasSize(int w, int h) {
        canvasW = w;
        canvasH = h;
        userAdjustedView = false;
        fitToScreen();
    }

    public int getCanvasW() {
        return canvasW;
    }

    public int getCanvasH() {
        return canvasH;
    }

    public int getBgColor() {
        return bgColor;
    }

    public ArrayList<Layer> getLayers() {
        return layers;
    }

    public Layer getSelected() {
        return selected;
    }

    /** Sets the starting background without recording history. Call initHistory() afterwards. */
    public void initBackground(int color, Bitmap bmp) {
        bgColor = color;
        bgBitmap = bmp;
        invalidate();
    }

    public void initHistory() {
        history.clear();
        hIndex = -1;
        commit();
    }

    public void applyBackgroundColor(int color) {
        bgColor = color;
        bgBitmap = null;
        commit();
    }

    public void applyBackgroundBitmap(Bitmap bmp) {
        bgBitmap = bmp;
        commit();
    }

    public void selectLayer(Layer l) {
        selected = l;
        if (listener != null) listener.onSelectionChanged(l);
        invalidate();
    }

    public void addLayer(Layer l) {
        layers.add(l);
        selected = l;
        if (listener != null) listener.onSelectionChanged(l);
        commit();
    }

    public void deleteLayer(Layer l) {
        if (l == null) return;
        layers.remove(l);
        if (selected == l) {
            selected = null;
            if (listener != null) listener.onSelectionChanged(null);
        }
        commit();
    }

    public void deleteSelected() {
        deleteLayer(selected);
    }

    public void duplicateSelected() {
        if (selected == null) return;
        Layer c = selected.copy();
        c.cx += 30f;
        c.cy += 30f;
        c.locked = false;
        addLayer(c);
    }

    /** delta +1 brings the layer one step forward (toward the top), -1 sends it backward. */
    public void moveLayer(Layer l, int delta) {
        int i = layers.indexOf(l);
        int n = i + delta;
        if (i < 0 || n < 0 || n >= layers.size()) return;
        layers.remove(i);
        layers.add(n, l);
        commit();
    }

    public void fitToScreen() {
        if (getWidth() == 0 || getHeight() == 0) return;
        float pad = 24f * density;
        float s = Math.min((getWidth() - 2 * pad) / canvasW, (getHeight() - 2 * pad) / canvasH);
        if (s <= 0f) s = 0.1f;
        viewMatrix.reset();
        viewMatrix.postScale(s, s);
        viewMatrix.postTranslate((getWidth() - canvasW * s) / 2f, (getHeight() - canvasH * s) / 2f);
        invalidate();
    }

    public Bitmap render(float s) {
        Bitmap b = Bitmap.createBitmap(Math.round(canvasW * s), Math.round(canvasH * s), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(s, s);
        drawContent(c);
        return b;
    }

    // ---------------------------------------------------------------- history

    private Snapshot snapshot() {
        Snapshot s = new Snapshot();
        for (Layer l : layers) s.layers.add(l.copy());
        s.selected = layers.indexOf(selected);
        s.bgColor = bgColor;
        s.bgBitmap = bgBitmap;
        return s;
    }

    public void commit() {
        while (history.size() > hIndex + 1) history.remove(history.size() - 1);
        history.add(snapshot());
        hIndex++;
        if (history.size() > 60) {
            history.remove(0);
            hIndex--;
        }
        if (listener != null) listener.onHistoryChanged();
        invalidate();
    }

    public boolean canUndo() {
        return hIndex > 0;
    }

    public boolean canRedo() {
        return hIndex < history.size() - 1;
    }

    public boolean hasChanges() {
        return hIndex > 0;
    }

    public void undo() {
        if (!canUndo()) return;
        hIndex--;
        restore(history.get(hIndex));
    }

    public void redo() {
        if (!canRedo()) return;
        hIndex++;
        restore(history.get(hIndex));
    }

    private void restore(Snapshot s) {
        layers.clear();
        for (Layer l : s.layers) layers.add(l.copy());
        selected = (s.selected >= 0 && s.selected < layers.size()) ? layers.get(s.selected) : null;
        bgColor = s.bgColor;
        bgBitmap = s.bgBitmap;
        if (listener != null) {
            listener.onSelectionChanged(selected);
            listener.onHistoryChanged();
        }
        invalidate();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (!userAdjustedView || ow == 0 || oh == 0) {
            fitToScreen();
        } else {
            viewMatrix.postTranslate((w - ow) / 2f, (h - oh) / 2f);
        }
    }

    private void drawContent(Canvas c) {
        if (bgBitmap != null) {
            int bw = bgBitmap.getWidth(), bh = bgBitmap.getHeight();
            float scale = Math.max((float) canvasW / bw, (float) canvasH / bh);
            int sw = Math.round(canvasW / scale), sh = Math.round(canvasH / scale);
            int sx = (bw - sw) / 2, sy = (bh - sh) / 2;
            c.drawBitmap(bgBitmap, new Rect(sx, sy, sx + sw, sy + sh), new Rect(0, 0, canvasW, canvasH), bmpPaint);
        } else if (Color.alpha(bgColor) != 0) {
            c.drawColor(bgColor);
        }
        for (Layer l : layers) l.draw(c);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.concat(viewMatrix);
        canvas.clipRect(0, 0, canvasW, canvasH);
        if (bgBitmap == null && Color.alpha(bgColor) < 255) {
            canvas.drawRect(0, 0, canvasW, canvasH, checkerPaint);
        }
        drawContent(canvas);
        canvas.restore();
        drawSelection(canvas);
    }

    private float viewScale() {
        float[] v = new float[9];
        viewMatrix.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    /** Four corners (TL, TR, BR, BL) of the layer in screen coordinates. */
    private float[] screenCorners(Layer l) {
        float padLocal = (8f * density) / (viewScale() * l.scale);
        float hw = l.getWidth() / 2f + padLocal;
        float hh = l.getHeight() / 2f + padLocal;
        float[] pts = {-hw, -hh, hw, -hh, hw, hh, -hw, hh};
        Matrix m = new Matrix();
        m.postScale(l.scale, l.scale);
        m.postRotate(l.rotation);
        m.postTranslate(l.cx, l.cy);
        m.postConcat(viewMatrix);
        m.mapPoints(pts);
        return pts;
    }

    private void drawSelection(Canvas canvas) {
        if (selected == null || !selected.visible) return;
        float[] p = screenCorners(selected);
        Path path = new Path();
        path.moveTo(p[0], p[1]);
        path.lineTo(p[2], p[3]);
        path.lineTo(p[4], p[5]);
        path.lineTo(p[6], p[7]);
        path.close();
        canvas.drawPath(path, selPaint);
        if (selected.locked) return;

        float r = 12f * density;
        float d = 4.5f * density;
        // delete handle (top-right)
        canvas.drawCircle(p[2], p[3], r, handleFill);
        canvas.drawLine(p[2] - d, p[3] - d, p[2] + d, p[3] + d, handleIcon);
        canvas.drawLine(p[2] - d, p[3] + d, p[2] + d, p[3] - d, handleIcon);
        // scale + rotate handle (bottom-right)
        canvas.drawCircle(p[4], p[5], r, handleFill);
        canvas.drawLine(p[4] - d, p[5] - d, p[4] + d, p[5] + d, handleIcon);
        canvas.drawLine(p[4] + d, p[5] + d, p[4] + d, p[5] - d * 0.2f, handleIcon);
        canvas.drawLine(p[4] + d, p[5] + d, p[4] - d * 0.2f, p[5] + d, handleIcon);
    }

    // ---------------------------------------------------------------- touch

    private float[] toCanvas(float x, float y) {
        viewMatrix.invert(inverse);
        float[] p = {x, y};
        inverse.mapPoints(p);
        return p;
    }

    private Layer hitLayer(float x, float y) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = layers.get(i);
            if (!l.visible || l.locked) continue;
            if (l.hitTest(x, y)) return l;
        }
        return null;
    }

    private int hitHandle(float x, float y) {
        if (selected == null || selected.locked || !selected.visible) return H_NONE;
        float[] p = screenCorners(selected);
        float r = 24f * density;
        if (Math.hypot(x - p[4], y - p[5]) <= r) return H_TRANSFORM;
        if (Math.hypot(x - p[2], y - p[3]) <= r) return H_DELETE;
        return H_NONE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                onDown(e);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                onSecondDown(e);
                break;
            case MotionEvent.ACTION_MOVE:
                onMove(e);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onPointerUp();
                break;
            case MotionEvent.ACTION_UP:
                onUp(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                onUp(true);
                break;
            default:
                break;
        }
        return true;
    }

    private void onDown(MotionEvent e) {
        downX = lastX = e.getX();
        downY = lastY = e.getY();
        moved = false;
        changed = false;
        mode = MODE_NONE;

        int h = hitHandle(downX, downY);
        if (h == H_DELETE) {
            deleteSelected();
            return;
        }
        if (h == H_TRANSFORM) {
            float[] p = toCanvas(downX, downY);
            float vx = p[0] - selected.cx, vy = p[1] - selected.cy;
            hStartLen = Math.max(1f, (float) Math.hypot(vx, vy));
            hStartAngle = (float) Math.toDegrees(Math.atan2(vy, vx));
            hStartScale = selected.scale;
            hStartRot = selected.rotation;
            mode = MODE_HANDLE;
            return;
        }

        float[] p = toCanvas(downX, downY);
        Layer hit = hitLayer(p[0], p[1]);
        wasSelectedOnDown = hit != null && hit == selected;
        if (hit != null) {
            if (hit != selected) selectLayer(hit);
            grabDx = hit.cx - p[0];
            grabDy = hit.cy - p[1];
            mode = MODE_MOVE;
        } else {
            mode = MODE_PAN_VIEW;
        }
    }

    private void onSecondDown(MotionEvent e) {
        if (e.getPointerCount() != 2) return;
        moved = true;
        if ((mode == MODE_MOVE || mode == MODE_HANDLE) && selected != null && !selected.locked) {
            float[] a = toCanvas(e.getX(0), e.getY(0));
            float[] b = toCanvas(e.getX(1), e.getY(1));
            pStartDist = Math.max(1f, (float) Math.hypot(b[0] - a[0], b[1] - a[1]));
            pStartAngle = (float) Math.toDegrees(Math.atan2(b[1] - a[1], b[0] - a[0]));
            pStartMidX = (a[0] + b[0]) / 2f;
            pStartMidY = (a[1] + b[1]) / 2f;
            pStartScale = selected.scale;
            pStartRot = selected.rotation;
            pStartCx = selected.cx;
            pStartCy = selected.cy;
            mode = MODE_PINCH_LAYER;
        } else {
            vLastDist = (float) Math.hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0));
            vLastMidX = (e.getX(0) + e.getX(1)) / 2f;
            vLastMidY = (e.getY(0) + e.getY(1)) / 2f;
            mode = MODE_PINCH_VIEW;
        }
    }

    private void onMove(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (!moved && Math.hypot(x - downX, y - downY) > touchSlop) moved = true;

        switch (mode) {
            case MODE_MOVE:
                if (moved && selected != null) {
                    float[] p = toCanvas(x, y);
                    selected.cx = p[0] + grabDx;
                    selected.cy = p[1] + grabDy;
                    changed = true;
                    invalidate();
                }
                break;
            case MODE_HANDLE:
                if (moved && selected != null) {
                    float[] p = toCanvas(x, y);
                    float vx = p[0] - selected.cx, vy = p[1] - selected.cy;
                    float len = Math.max(1f, (float) Math.hypot(vx, vy));
                    float ang = (float) Math.toDegrees(Math.atan2(vy, vx));
                    selected.scale = Math.max(0.05f, Math.min(40f, hStartScale * len / hStartLen));
                    float r = hStartRot + (ang - hStartAngle);
                    float m = Math.round(r / 45f) * 45f;
                    if (Math.abs(r - m) < 3f) r = m;
                    selected.rotation = r;
                    changed = true;
                    invalidate();
                }
                break;
            case MODE_PAN_VIEW:
                if (moved) {
                    viewMatrix.postTranslate(x - lastX, y - lastY);
                    userAdjustedView = true;
                    invalidate();
                }
                break;
            case MODE_PINCH_LAYER:
                if (e.getPointerCount() >= 2 && selected != null) {
                    float[] a = toCanvas(e.getX(0), e.getY(0));
                    float[] b = toCanvas(e.getX(1), e.getY(1));
                    float dist = Math.max(1f, (float) Math.hypot(b[0] - a[0], b[1] - a[1]));
                    float ang = (float) Math.toDegrees(Math.atan2(b[1] - a[1], b[0] - a[0]));
                    selected.scale = Math.max(0.05f, Math.min(40f, pStartScale * dist / pStartDist));
                    selected.rotation = pStartRot + (ang - pStartAngle);
                    selected.cx = pStartCx + ((a[0] + b[0]) / 2f - pStartMidX);
                    selected.cy = pStartCy + ((a[1] + b[1]) / 2f - pStartMidY);
                    changed = true;
                    invalidate();
                }
                break;
            case MODE_PINCH_VIEW:
                if (e.getPointerCount() >= 2) {
                    float d = (float) Math.hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0));
                    float mx = (e.getX(0) + e.getX(1)) / 2f;
                    float my = (e.getY(0) + e.getY(1)) / 2f;
                    if (vLastDist > 0f && d > 0f) {
                        float f = d / vLastDist;
                        float ns = viewScale() * f;
                        if (ns > 0.05f && ns < 30f) viewMatrix.postScale(f, f, mx, my);
                    }
                    viewMatrix.postTranslate(mx - vLastMidX, my - vLastMidY);
                    vLastDist = d;
                    vLastMidX = mx;
                    vLastMidY = my;
                    userAdjustedView = true;
                    invalidate();
                }
                break;
            default:
                break;
        }
        lastX = x;
        lastY = y;
    }

    private void onPointerUp() {
        if (mode == MODE_PINCH_LAYER || mode == MODE_PINCH_VIEW) {
            mode = MODE_NONE;
        }
    }

    private void onUp(boolean cancel) {
        if (!cancel) {
            if (mode == MODE_MOVE && !moved && wasSelectedOnDown
                    && selected instanceof TextLayer && listener != null) {
                listener.onTextTapped((TextLayer) selected);
            }
            if (mode == MODE_PAN_VIEW && !moved && selected != null) {
                selectLayer(null);
            }
        }
        if (changed) {
            commit();
            changed = false;
        }
        mode = MODE_NONE;
    }
}
