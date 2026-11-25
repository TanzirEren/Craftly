package com.craftly.studio.design;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ExportUtils {

    /** Saves into Pictures/Craftly and returns the content Uri. Call from a background thread. */
    public static Uri save(Context ctx, Bitmap bmp, boolean png, int quality, String baseName) throws IOException {
        String mime = png ? "image/png" : "image/jpeg";
        String fileName = baseName + (png ? ".png" : ".jpg");
        Bitmap.CompressFormat fmt = png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
        ContentResolver cr = ctx.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        v.put(MediaStore.Images.Media.MIME_TYPE, mime);

        if (Build.VERSION.SDK_INT >= 29) {
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Craftly");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("Could not create file");
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null || !bmp.compress(fmt, quality, os)) throw new IOException("Could not write image");
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            return uri;
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Craftly");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create folder");
            File f = new File(dir, fileName);
            try (FileOutputStream os = new FileOutputStream(f)) {
                if (!bmp.compress(fmt, quality, os)) throw new IOException("Could not write image");
            }
            v.put(MediaStore.Images.Media.DATA, f.getAbsolutePath());
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("Could not register image");
            return uri;
        }
    }
}
