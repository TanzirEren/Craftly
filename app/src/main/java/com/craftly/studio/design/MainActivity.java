package com.craftly.studio.design;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String[] PRESET_NAMES = {
            "Square (1:1)  1080 × 1080",
            "Instagram Post (4:5)  1080 × 1350",
            "Story / Reel (9:16)  1080 × 1920",
            "YouTube Thumbnail  1280 × 720",
            "Facebook Cover  820 × 312",
            "Twitter / X Header  1500 × 500",
            "Custom size…"
    };
    private static final int[][] PRESET_SIZES = {
            {1080, 1080}, {1080, 1350}, {1080, 1920}, {1280, 720}, {820, 312}, {1500, 500}
    };

    private final ActivityResultLauncher<String> pickPhoto = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Intent i = new Intent(MainActivity.this, EditorActivity.class);
                    i.setData(uri);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(i);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnNew).setOnClickListener(v -> showPresets());
        findViewById(R.id.btnOpen).setOnClickListener(v -> pickPhoto.launch("image/*"));
    }

    private void showPresets() {
        new AlertDialog.Builder(this)
                .setTitle("Choose canvas size")
                .setItems(PRESET_NAMES, (d, which) -> {
                    if (which < PRESET_SIZES.length) {
                        startEditor(PRESET_SIZES[which][0], PRESET_SIZES[which][1]);
                    } else {
                        showCustomSize();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomSize() {
        final View view = getLayoutInflater().inflate(R.layout.dialog_custom_size, null);
        final EditText etW = view.findViewById(R.id.etWidth);
        final EditText etH = view.findViewById(R.id.etHeight);
        new AlertDialog.Builder(this)
                .setTitle("Custom size")
                .setView(view)
                .setPositiveButton("Create", (d, w) -> {
                    int cw = parse(etW.getText().toString());
                    int ch = parse(etH.getText().toString());
                    if (cw < 100 || ch < 100 || cw > 4096 || ch > 4096) {
                        Toast.makeText(this, "Size must be between 100 and 4096 px", Toast.LENGTH_SHORT).show();
                    } else {
                        startEditor(cw, ch);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void startEditor(int w, int h) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra(EditorActivity.EXTRA_W, w);
        i.putExtra(EditorActivity.EXTRA_H, h);
        i.putExtra(EditorActivity.EXTRA_BG, Color.WHITE);
        startActivity(i);
    }
}
