package com.craftly.studio.design;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class EditorActivity extends AppCompatActivity implements CanvasView.Listener {

    public static final String EXTRA_W = "w";
    public static final String EXTRA_H = "h";
    public static final String EXTRA_BG = "bg";

    private static final int TAB_FONT = 0, TAB_COLOR = 1, TAB_STYLE = 2, TAB_ADJUST = 3, TAB_ACTIONS = 4;
    private static final int PICK_LAYER = 0, PICK_BACKGROUND = 1;

    private CanvasView canvas;
    private View panel;
    private ImageButton btnUndo, btnRedo;
    private TextView[] tabs;
    private View[] pages;
    private LinearLayout fontList, colorList;
    private final ArrayList<TextView> fontViews = new ArrayList<>();
    private TextView btnBold, btnItalic, btnAlignL, btnAlignC, btnAlignR;
    private SeekBar sbOpacity, sbSize, sbSpacing, sbLine;
    private View rowSize, rowSpacing, rowLine;
    private TextView actLock;

    private int currentTab = TAB_FONT;
    private boolean updatingUi = false;
    private int pickMode = PICK_LAYER;
    private Runnable pendingExport;

    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) loadPickedImage(uri);
            });

    private final ActivityResultLauncher<String> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingExport != null) {
                    pendingExport.run();
                } else {
                    Toast.makeText(this, "Storage permission is needed to save images", Toast.LENGTH_SHORT).show();
                }
                pendingExport = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        canvas = findViewById(R.id.canvasView);
        panel = findViewById(R.id.panel);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        fontList = findViewById(R.id.fontList);
        colorList = findViewById(R.id.colorList);
        btnBold = findViewById(R.id.btnBold);
        btnItalic = findViewById(R.id.btnItalic);
        btnAlignL = findViewById(R.id.btnAlignL);
        btnAlignC = findViewById(R.id.btnAlignC);
        btnAlignR = findViewById(R.id.btnAlignR);
        sbOpacity = findViewById(R.id.sbOpacity);
        sbSize = findViewById(R.id.sbSize);
        sbSpacing = findViewById(R.id.sbSpacing);
        sbLine = findViewById(R.id.sbLine);
        rowSize = findViewById(R.id.rowSize);
        rowSpacing = findViewById(R.id.rowSpacing);
        rowLine = findViewById(R.id.rowLine);
        actLock = findViewById(R.id.actLock);

        tabs = new TextView[]{
                findViewById(R.id.tabFont), findViewById(R.id.tabColor), findViewById(R.id.tabStyle),
                findViewById(R.id.tabAdjust), findViewById(R.id.tabActions)};
        pages = new View[]{
                findViewById(R.id.pageFont), findViewById(R.id.pageColor), findViewById(R.id.pageStyle),
                findViewById(R.id.pageAdjust), findViewById(R.id.pageActions)};
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            tabs[i].setOnClickListener(v -> showTab(tab));
        }

        sbSize.setMax(580);
        sbSpacing.setMax(50);
        sbLine.setMax(120);
        sbOpacity.setMax(100);

        buildFontList();
        setupSeekBars();
        setupButtons();

        canvas.setListener(this);
        setupCanvasFromIntent();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    // ------------------------------------------------------------------ setup

    private void setupCanvasFromIntent() {
        final Uri data = getIntent().getData();
        if (data != null) {
            new Thread(() -> {
                try {
                    final Bitmap bmp = BitmapUtils.decode(this, data, 2048);
                    runOnUiThread(() -> {
                        canvas.setCanvasSize(bmp.getWidth(), bmp.getHeight());
                        canvas.initBackground(Color.WHITE, bmp);
                        canvas.initHistory();
                    });
                } catch (Throwable t) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Could not open that photo", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            }).start();
        } else {
            int w = getIntent().getIntExtra(EXTRA_W, 1080);
            int h = getIntent().getIntExtra(EXTRA_H, 1080);
            int bg = getIntent().getIntExtra(EXTRA_BG, Color.WHITE);
            canvas.setCanvasSize(w, h);
            canvas.initBackground(bg, null);
            canvas.initHistory();
        }
    }

    private void buildFontList() {
        fontList.removeAllViews();
        fontViews.clear();
        for (int i = 0; i < TextLayer.FONTS.length; i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(TextLayer.FONT_NAMES[i]);
            tv.setTypeface(Typeface.create(TextLayer.FONTS[i], Typeface.NORMAL));
            tv.setTextSize(17f);
            tv.setTextColor(Color.WHITE);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.chip_bg);
            tv.setPadding(ColorPalette.dp(this, 18), 0, ColorPalette.dp(this, 18), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ColorPalette.dp(this, 52));
            lp.setMargins(ColorPalette.dp(this, 5), 0, ColorPalette.dp(this, 5), 0);
            fontList.addView(tv, lp);
            fontViews.add(tv);
            tv.setOnClickListener(v -> {
                TextLayer t = selectedText();
                if (t == null) return;
                t.fontIndex = idx;
                t.markDirty();
                canvas.commit();
                syncUi(t);
            });
        }
    }

    private abstract class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        abstract void onChange(int progress);

        @Override
        public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
            if (!updatingUi && fromUser) onChange(p);
        }

        @Override
        public void onStartTrackingTouch(SeekBar s) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar s) {
            canvas.commit();
        }
    }

    private void setupSeekBars() {
        sbOpacity.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void onChange(int p) {
                Layer l = canvas.getSelected();
                if (l == null) return;
                l.opacity = Math.round(p * 255f / 100f);
                canvas.invalidate();
            }
        });
        sbSize.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void onChange(int p) {
                TextLayer t = selectedText();
                if (t == null) return;
                t.textSize = p + 20;
                t.markDirty();
                canvas.invalidate();
            }
        });
        sbSpacing.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void onChange(int p) {
                TextLayer t = selectedText();
                if (t == null) return;
                t.letterSpacing = p / 100f;
                t.markDirty();
                canvas.invalidate();
            }
        });
        sbLine.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void onChange(int p) {
                TextLayer t = selectedText();
                if (t == null) return;
                t.lineSpacing = 0.8f + p / 100f;
                t.markDirty();
                canvas.invalidate();
            }
        });
    }

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> confirmExit());
        btnUndo.setOnClickListener(v -> canvas.undo());
        btnRedo.setOnClickListener(v -> canvas.redo());
        findViewById(R.id.btnLayers).setOnClickListener(v -> showLayersDialog());
        findViewById(R.id.btnExport).setOnClickListener(v -> showExportDialog());

        findViewById(R.id.btnAddText).setOnClickListener(v -> showTextDialog(null));
        findViewById(R.id.btnAddImage).setOnClickListener(v -> {
            pickMode = PICK_LAYER;
            pickImage.launch("image/*");
        });
        findViewById(R.id.btnBackground).setOnClickListener(v -> showBackgroundDialog());

        btnBold.setOnClickListener(v -> {
            TextLayer t = selectedText();
            if (t == null) return;
            t.bold = !t.bold;
            t.markDirty();
            canvas.commit();
            syncUi(t);
        });
        btnItalic.setOnClickListener(v -> {
            TextLayer t = selectedText();
            if (t == null) return;
            t.italic = !t.italic;
            t.markDirty();
            canvas.commit();
            syncUi(t);
        });
        btnAlignL.setOnClickListener(v -> setAlign(Layout.Alignment.ALIGN_NORMAL));
        btnAlignC.setOnClickListener(v -> setAlign(Layout.Alignment.ALIGN_CENTER));
        btnAlignR.setOnClickListener(v -> setAlign(Layout.Alignment.ALIGN_OPPOSITE));
        findViewById(R.id.btnEditText).setOnClickListener(v -> {
            TextLayer t = selectedText();
            if (t != null) showTextDialog(t);
        });

        findViewById(R.id.actDup).setOnClickListener(v -> canvas.duplicateSelected());
        findViewById(R.id.actDel).setOnClickListener(v -> canvas.deleteSelected());
        findViewById(R.id.actFront).setOnClickListener(v -> {
            if (canvas.getSelected() != null) canvas.moveLayer(canvas.getSelected(), 1);
        });
        findViewById(R.id.actBack).setOnClickListener(v -> {
            if (canvas.getSelected() != null) canvas.moveLayer(canvas.getSelected(), -1);
        });
        actLock.setOnClickListener(v -> {
            Layer l = canvas.getSelected();
            if (l == null) return;
            l.locked = !l.locked;
            canvas.commit();
            syncUi(l);
        });
    }

    private void setAlign(Layout.Alignment a) {
        TextLayer t = selectedText();
        if (t == null) return;
        t.align = a;
        t.markDirty();
        canvas.commit();
        syncUi(t);
    }

    private TextLayer selectedText() {
        Layer l = canvas.getSelected();
        return (l instanceof TextLayer) ? (TextLayer) l : null;
    }

    // ------------------------------------------------------------------ canvas listener

    @Override
    public void onSelectionChanged(Layer l) {
        if (l == null) {
            panel.setVisibility(View.GONE);
            return;
        }
        panel.setVisibility(View.VISIBLE);
        boolean isText = l instanceof TextLayer;
        tabs[TAB_FONT].setVisibility(isText ? View.VISIBLE : View.GONE);
        tabs[TAB_COLOR].setVisibility(isText ? View.VISIBLE : View.GONE);
        tabs[TAB_STYLE].setVisibility(isText ? View.VISIBLE : View.GONE);
        rowSize.setVisibility(isText ? View.VISIBLE : View.GONE);
        rowSpacing.setVisibility(isText ? View.VISIBLE : View.GONE);
        rowLine.setVisibility(isText ? View.VISIBLE : View.GONE);
        if (!isText && currentTab <= TAB_STYLE) currentTab = TAB_ADJUST;
        showTab(currentTab);
        syncUi(l);
    }

    @Override
    public void onHistoryChanged() {
        btnUndo.setAlpha(canvas.canUndo() ? 1f : 0.35f);
        btnRedo.setAlpha(canvas.canRedo() ? 1f : 0.35f);
        Layer l = canvas.getSelected();
        if (l != null) syncUi(l);
    }

    @Override
    public void onTextTapped(TextLayer layer) {
        showTextDialog(layer);
    }

    private void showTab(int tab) {
        currentTab = tab;
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == tab ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(i == tab ? 0xFF9575E8 : 0xFFA8A5B8);
        }
    }

    private void syncUi(final Layer l) {
        updatingUi = true;
        sbOpacity.setProgress(Math.round(l.opacity * 100f / 255f));
        actLock.setText(l.locked ? "Unlock" : "Lock");
        actLock.setCompoundDrawablesWithIntrinsicBounds(0, l.locked ? R.drawable.ic_lock : R.drawable.ic_lock_open, 0, 0);
        if (l instanceof TextLayer) {
            final TextLayer t = (TextLayer) l;
            sbSize.setProgress(Math.max(0, Math.min(580, Math.round(t.textSize) - 20)));
            sbSpacing.setProgress(Math.max(0, Math.min(50, Math.round(t.letterSpacing * 100f))));
            sbLine.setProgress(Math.max(0, Math.min(120, Math.round((t.lineSpacing - 0.8f) * 100f))));
            btnBold.setSelected(t.bold);
            btnItalic.setSelected(t.italic);
            btnAlignL.setSelected(t.align == Layout.Alignment.ALIGN_NORMAL);
            btnAlignC.setSelected(t.align == Layout.Alignment.ALIGN_CENTER);
            btnAlignR.setSelected(t.align == Layout.Alignment.ALIGN_OPPOSITE);
            for (int i = 0; i < fontViews.size(); i++) fontViews.get(i).setSelected(i == t.fontIndex);
            ColorPalette.populate(colorList, t.color, c -> {
                TextLayer cur = selectedText();
                if (cur == null) return;
                cur.color = c;
                cur.markDirty();
                canvas.commit();
                syncUi(cur);
            });
        }
        updatingUi = false;
    }

    // ------------------------------------------------------------------ dialogs

    private void showTextDialog(final TextLayer existing) {
        final EditText et = new EditText(this);
        et.setText(existing != null ? existing.text : "");
        et.setHint("Type your text");
        et.setMinLines(2);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setSelection(et.getText().length());
        FrameLayout wrap = new FrameLayout(this);
        int m = ColorPalette.dp(this, 20);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(m, m / 2, m, 0);
        wrap.addView(et, lp);

        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add text" : "Edit text")
                .setView(wrap)
                .setPositiveButton("OK", (d, w) -> {
                    String s = et.getText().toString();
                    if (s.trim().isEmpty()) return;
                    if (existing == null) {
                        TextLayer t = new TextLayer();
                        t.text = s;
                        t.textSize = canvas.getCanvasW() * 0.1f;
                        t.cx = canvas.getCanvasW() / 2f;
                        t.cy = canvas.getCanvasH() / 2f;
                        boolean dark = Color.alpha(canvas.getBgColor()) > 0
                                && (Color.red(canvas.getBgColor()) * 0.299f + Color.green(canvas.getBgColor()) * 0.587f
                                + Color.blue(canvas.getBgColor()) * 0.114f) < 100f;
                        t.color = dark ? Color.WHITE : Color.BLACK;
                        canvas.addLayer(t);
                    } else {
                        existing.text = s;
                        existing.markDirty();
                        canvas.commit();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
    }

    private void showBackgroundDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_background, null);
        LinearLayout row = v.findViewById(R.id.bgColors);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Background")
                .setView(v)
                .setNegativeButton("Close", null)
                .create();
        ColorPalette.populate(row, canvas.getBgColor(), c -> {
            canvas.applyBackgroundColor(c);
            dlg.dismiss();
        });
        v.findViewById(R.id.bgTransparent).setOnClickListener(x -> {
            canvas.applyBackgroundColor(Color.TRANSPARENT);
            dlg.dismiss();
        });
        v.findViewById(R.id.bgImage).setOnClickListener(x -> {
            dlg.dismiss();
            pickMode = PICK_BACKGROUND;
            pickImage.launch("image/*");
        });
        dlg.show();
    }

    private void showLayersDialog() {
        final ListView lv = new ListView(this);
        final BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return canvas.getLayers().size();
            }

            @Override
            public Object getItem(int pos) {
                return canvas.getLayers().get(canvas.getLayers().size() - 1 - pos);
            }

            @Override
            public long getItemId(int pos) {
                return pos;
            }

            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                View v = convert != null ? convert
                        : getLayoutInflater().inflate(R.layout.item_layer, parent, false);
                final Layer l = (Layer) getItem(pos);
                TextView name = v.findViewById(R.id.itemName);
                if (l instanceof TextLayer) {
                    name.setText("T   " + ((TextLayer) l).text.replace('\n', ' '));
                } else {
                    name.setText("▣   " + l.name);
                }
                name.setAlpha(l == canvas.getSelected() ? 1f : 0.75f);
                name.setTextColor(l == canvas.getSelected() ? 0xFF9575E8 : Color.WHITE);
                ImageButton vis = v.findViewById(R.id.itemVis);
                vis.setImageResource(l.visible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
                ImageButton lock = v.findViewById(R.id.itemLock);
                lock.setImageResource(l.locked ? R.drawable.ic_lock : R.drawable.ic_lock_open);
                vis.setOnClickListener(x -> {
                    l.visible = !l.visible;
                    canvas.commit();
                    notifyDataSetChanged();
                });
                lock.setOnClickListener(x -> {
                    l.locked = !l.locked;
                    canvas.commit();
                    notifyDataSetChanged();
                });
                v.findViewById(R.id.itemUp).setOnClickListener(x -> {
                    canvas.moveLayer(l, 1);
                    notifyDataSetChanged();
                });
                v.findViewById(R.id.itemDown).setOnClickListener(x -> {
                    canvas.moveLayer(l, -1);
                    notifyDataSetChanged();
                });
                v.findViewById(R.id.itemDel).setOnClickListener(x -> {
                    canvas.deleteLayer(l);
                    notifyDataSetChanged();
                });
                return v;
            }
        };
        lv.setAdapter(adapter);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Layers")
                .setView(lv)
                .setNegativeButton("Close", null)
                .create();
        lv.setOnItemClickListener((p, v, pos, id) -> {
            canvas.selectLayer((Layer) adapter.getItem(pos));
            dlg.dismiss();
        });
        dlg.show();
        if (canvas.getLayers().isEmpty()) {
            Toast.makeText(this, "No layers yet. Add text or an image.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showExportDialog() {
        final View v = getLayoutInflater().inflate(R.layout.dialog_export, null);
        final RadioGroup rgFormat = v.findViewById(R.id.rgFormat);
        final RadioGroup rgSize = v.findViewById(R.id.rgSize);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Export")
                .setView(v)
                .setPositiveButton("Save", null)
                .setNeutralButton("Share", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.setOnShowListener(x -> {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
                dlg.dismiss();
                doExport(rgFormat.getCheckedRadioButtonId() == R.id.rbPng,
                        rgSize.getCheckedRadioButtonId() == R.id.rb2x ? 2f : 1f, false);
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(b -> {
                dlg.dismiss();
                doExport(rgFormat.getCheckedRadioButtonId() == R.id.rbPng,
                        rgSize.getCheckedRadioButtonId() == R.id.rb2x ? 2f : 1f, true);
            });
        });
        dlg.show();
    }

    private void confirmExit() {
        if (!canvas.hasChanges()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Leave editor?")
                .setMessage("Your changes will be lost. Project saving arrives in Phase 2.")
                .setPositiveButton("Leave", (d, w) -> finish())
                .setNegativeButton("Stay", null)
                .show();
    }

    // ------------------------------------------------------------------ images

    private void loadPickedImage(final Uri uri) {
        final int mode = pickMode;
        new Thread(() -> {
            try {
                final Bitmap bmp = BitmapUtils.decode(this, uri, 2048);
                runOnUiThread(() -> {
                    if (mode == PICK_BACKGROUND) {
                        canvas.applyBackgroundBitmap(bmp);
                    } else {
                        ImageLayer l = new ImageLayer(bmp);
                        l.cx = canvas.getCanvasW() / 2f;
                        l.cy = canvas.getCanvasH() / 2f;
                        l.scale = Math.min(0.8f * canvas.getCanvasW() / bmp.getWidth(),
                                0.8f * canvas.getCanvasH() / bmp.getHeight());
                        canvas.addLayer(l);
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this, "Could not load that image", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ------------------------------------------------------------------ export

    private void doExport(final boolean png, final float scale, final boolean share) {
        if (Build.VERSION.SDK_INT < 29
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingExport = () -> doExport(png, scale, share);
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        Bitmap rendered;
        try {
            rendered = canvas.render(scale);
        } catch (Throwable t) {
            Toast.makeText(this, "Not enough memory for this size. Try 'Original'.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!png) {
            Bitmap flat = Bitmap.createBitmap(rendered.getWidth(), rendered.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(flat);
            c.drawColor(Color.WHITE);
            c.drawBitmap(rendered, 0, 0, null);
            rendered.recycle();
            rendered = flat;
        }
        final Bitmap out = rendered;
        Toast.makeText(this, "Saving…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final Uri uri = ExportUtils.save(this, out, png, 95, "Craftly_" + System.currentTimeMillis());
                out.recycle();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Saved to Pictures/Craftly", Toast.LENGTH_LONG).show();
                    if (share) {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType(png ? "image/png" : "image/jpeg");
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "Share design"));
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + t.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
