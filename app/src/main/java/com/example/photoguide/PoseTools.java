package com.example.photoguide;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.content.Context;
import androidx.exifinterface.media.ExifInterface;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;

final class PoseTools {
    static final int[] IDS = {11,12,13,14,15,16,23,24,25,26,27,28};
    static Bitmap load(Context c, Uri uri) throws IOException {
        byte[] bytes;
        try (InputStream in = c.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("无法读取图片");
            byte[] buf = new byte[16384]; int n; long size = 0;
            while ((n = in.read(buf)) != -1) { size += n; if (size > 40L * 1024 * 1024) throw new IOException("图片超过 40 MB"); out.write(buf, 0, n); }
            bytes = out.toByteArray();
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2;
        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (b == null) throw new IOException("图片格式不受支持");
        ExifInterface exif = new ExifInterface(new ByteArrayInputStream(bytes));
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: m.setRotate(90); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: m.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: m.setRotate(-90); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.setRotate(-90); break;
        }
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }
    static float[][] points(Pose pose, int width, int height) {
        float[][] p = new float[33][3];
        for (int id : IDS) { PoseLandmark l = pose.getPoseLandmark(id); if (l != null) { p[id][0] = l.getPosition().x / width; p[id][1] = l.getPosition().y / height; p[id][2] = l.getInFrameLikelihood(); } }
        return p;
    }
    static boolean usable(float[][] p) { return p[11][2] > .5 && p[12][2] > .5 && p[23][2] > .5 && p[24][2] > .5; }
    static void save(Context c, Bitmap b, float[][] p) throws Exception {
        try (FileOutputStream out = c.openFileOutput("reference.jpg", Context.MODE_PRIVATE)) { if (!b.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw new IOException(); }
        JSONArray a = new JSONArray(); for (float[] point : p) { JSONArray v = new JSONArray(); for (float x : point) v.put((double)x); a.put(v); }
        try (FileOutputStream out = c.openFileOutput("pose.json", Context.MODE_PRIVATE)) { out.write(a.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    static float[][] read(Context c) throws Exception {
        byte[] data; try (InputStream in = c.openFileInput("pose.json"); ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] buf = new byte[4096]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n); data = out.toByteArray(); }
        JSONArray a = new JSONArray(new String(data, java.nio.charset.StandardCharsets.UTF_8)); float[][] p = new float[33][3];
        for (int i = 0; i < 33; i++) for (int j = 0; j < 3; j++) p[i][j] = (float)a.getJSONArray(i).getDouble(j); return p;
    }
    static String advice(float[][] r, float[][] p) {
        if (!usable(p)) return "未识别到完整躯干，请让人物进入画面";
        float rcx = (r[11][0]+r[12][0]+r[23][0]+r[24][0])/4, pcx = (p[11][0]+p[12][0]+p[23][0]+p[24][0])/4;
        float rh = Math.abs((r[23][1]+r[24][1]-r[11][1]-r[12][1])/2), ph = Math.abs((p[23][1]+p[24][1]-p[11][1]-p[12][1])/2);
        if (rh < .025f || ph < .025f) return "请让人物躯干清晰出现在画面中";
        // Compare joints relative to the torso, so framing and limb corrections are independent.
        for (int side = 0; side < 2; side++) {
            int shoulder = 11 + side, wrist = 15 + side;
            if (r[wrist][2] > .6 && p[wrist][2] > .6) {
                float delta = (p[wrist][1]-p[shoulder][1])/ph - (r[wrist][1]-r[shoulder][1])/rh;
                if (Math.abs(delta) > .45f) {
                    String label = p[shoulder][0] < p[12-side][0] ? "画面左侧的手" : "画面右侧的手";
                    return delta > 0 ? "抬高" + label : "放低" + label;
                }
            }
        }
        // Screen coordinates include camera controls; framing advice is approximate.
        if (pcx - rcx > .18f) return "人物尝试向画面左侧移动";
        if (pcx - rcx < -.18f) return "人物尝试向画面右侧移动";
        if (ph < rh * .65f) return "人物尝试靠近镜头";
        if (ph > rh * 1.5f) return "人物尝试远离镜头";
        return "姿势接近参考，微调后拍摄";
    }
}
