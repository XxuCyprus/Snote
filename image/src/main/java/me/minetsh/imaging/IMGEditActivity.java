package me.minetsh.imaging;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.graphics.Color;

import me.minetsh.imaging.core.IMGMode;
import me.minetsh.imaging.core.IMGText;
import me.minetsh.imaging.core.file.IMGAssetFileDecoder;
import me.minetsh.imaging.core.file.IMGDecoder;
import me.minetsh.imaging.core.file.IMGFileDecoder;
import me.minetsh.imaging.core.util.IMGUtils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by felix on 2017/11/14 下午2:26.
 */

public class IMGEditActivity extends IMGEditBaseActivity {

    private static final int MAX_WIDTH = 2048;

    private static final int MAX_HEIGHT = 2048;

    public static final String EXTRA_IMAGE_URI = "IMAGE_URI";

    public static final String EXTRA_IMAGE_SAVE_PATH = "IMAGE_SAVE_PATH";

    public static final String EXTRA_DOODLE_JSON = "DOODLE_JSON";

    public static final String EXTRA_DOODLE_FILE_PATH = "DOODLE_FILE_PATH";

    private static final String TAG = "IMGEdit";

    private android.widget.TextView mDialogText;
    private android.app.AlertDialog mCurrentDialog;

    private void showLoadingDialog(String message) {
        dismissLoadingDialog();

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        bg.setColor(0xFFFFFFFF);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (40 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.setBackground(bg);
        layout.setMinimumWidth((int) (260 * getResources().getDisplayMetrics().density));

        android.widget.ProgressBar progress = new android.widget.ProgressBar(this);
        progress.getIndeterminateDrawable().setColorFilter(0xFF1565C0, android.graphics.PorterDuff.Mode.SRC_IN);
        android.widget.LinearLayout.LayoutParams pp = new android.widget.LinearLayout.LayoutParams(
                (int) (48 * getResources().getDisplayMetrics().density),
                (int) (48 * getResources().getDisplayMetrics().density));
        pp.gravity = android.view.Gravity.CENTER;
        progress.setLayoutParams(pp);
        layout.addView(progress);

        mDialogText = new android.widget.TextView(this);
        mDialogText.setText(message);
        mDialogText.setGravity(android.view.Gravity.CENTER);
        mDialogText.setTextColor(0xFF212121);
        mDialogText.setTextSize(16);
        mDialogText.setPadding(0, (int) (20 * getResources().getDisplayMetrics().density), 0, 0);
        layout.addView(mDialogText);

        mCurrentDialog = new android.app.AlertDialog.Builder(this)
                .setView(layout)
                .setCancelable(false)
                .create();
        mCurrentDialog.show();
    }

    private void updateDialogText(String text) {
        if (mDialogText != null) {
            mDialogText.setText(text);
        }
    }

    private void dismissLoadingDialog() {
        mCurrentDialog = null;
        mDialogText = null;
    }

    @Override
    public void onCreated() {
        String doodleFilePath = getIntent().getStringExtra(EXTRA_DOODLE_FILE_PATH);
        String doodleJsonFromIntent = getIntent().getStringExtra(EXTRA_DOODLE_JSON);

        if (doodleFilePath == null && doodleJsonFromIntent == null) return;

        showLoadingDialog("正在恢复编辑记录...");

        final String filePath = doodleFilePath;
        final String jsonFallback = doodleJsonFromIntent;
        final long showTime = System.currentTimeMillis();

        new Thread(() -> {
            String doodleJson = null;
            int undoCount = 0;

            if (filePath != null) {
                java.io.File doodleFile = new java.io.File(filePath);
                if (doodleFile.exists()) {
                    try {
                        byte[] bytes = new byte[(int) doodleFile.length()];
                        java.io.FileInputStream fis = new java.io.FileInputStream(doodleFile);
                        fis.read(bytes);
                        fis.close();
                        doodleJson = new String(bytes, "UTF-8");
                    } catch (IOException e) {
                        Log.e(TAG, "onCreated: failed to read doodle file", e);
                    }
                }
            }
            if (doodleJson == null) {
                doodleJson = jsonFallback;
            }

            if (doodleJson != null && !doodleJson.isEmpty()) {
                try {
                    org.json.JSONObject root = new org.json.JSONObject(doodleJson);
                    if (root.has("undo")) {
                        undoCount = root.getJSONArray("undo").length();
                    } else if (root.has("history")) {
                        undoCount = root.getJSONArray("history").length();
                    }
                } catch (Exception ignored) {}
            }

            final String json = doodleJson;
            final int count = undoCount;

            runOnUiThread(() -> {
                if (json != null && !json.isEmpty()) {
                    // 如果是编辑过的文件（已有裁切），JPEG中已包含涂鸦，不需要恢复
                    String uriPath = getIntent().getStringExtra(EXTRA_IMAGE_URI);
                    boolean isEdited = uriPath != null && new java.io.File(uriPath).getName().startsWith("edited_");
                    if (!isEdited) {
                        mImgView.deserializeDoodles(json);
                    }
                    // 恢复文字贴纸（View坐标直接复原）
                    try {
                        org.json.JSONObject root = new org.json.JSONObject(json);
                        if (root.has("stickers")) {
                            mImgView.deserializeStickers(root.getJSONArray("stickers"));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "onCreated: failed to restore stickers", e);
                    }
                    // 强制刷新视图，确保贴纸立即可见
                    mImgView.invalidate();
                    if (!mImgView.isDoodleEmpty()) {
                        mImgView.post(() -> {
                            mImgView.setMode(IMGMode.DOODLE);
                            updateModeUI();
                        });
                    }
                }
                updateDialogText("已恢复 " + count + " 条编辑记录");

                long elapsed = System.currentTimeMillis() - showTime;
                long remaining = 2000 - elapsed;
                if (remaining > 0) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (mCurrentDialog != null) mCurrentDialog.dismiss();
                        dismissLoadingDialog();
                    }, remaining);
                } else {
                    if (mCurrentDialog != null) mCurrentDialog.dismiss();
                    dismissLoadingDialog();
                }
            });
        }).start();
    }


    @Override
    public Bitmap getBitmap() {
        Log.d(TAG, "=== getBitmap() START ===");
        Intent intent = getIntent();
        if (intent == null) {
            Log.e(TAG, "getBitmap: intent is null");
            return null;
        }

        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uri = intent.getParcelableExtra(EXTRA_IMAGE_URI, Uri.class);
        } else {
            uri = intent.getParcelableExtra(EXTRA_IMAGE_URI);
        }
        Log.d(TAG, "getBitmap: uri=" + uri);
        if (uri == null) {
            Log.e(TAG, "getBitmap: URI is null");
            return null;
        }

        String path = uri.getPath();
        java.io.File imgFile = new java.io.File(path);
        Log.d(TAG, "getBitmap: path=" + path);
        Log.d(TAG, "getBitmap: file exists=" + imgFile.exists() + ", size=" + (imgFile.exists() ? imgFile.length() : 0));

        IMGDecoder decoder = null;

        if (!TextUtils.isEmpty(path)) {
            switch (uri.getScheme()) {
                case "asset":
                    decoder = new IMGAssetFileDecoder(this, uri);
                    break;
                case "file":
                    decoder = new IMGFileDecoder(uri);
                    break;
            }
        }

        if (decoder == null) {
            Log.e(TAG, "getBitmap: decoder is null");
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        options.inJustDecodeBounds = true;

        decoder.decode(options);
        Log.d(TAG, "getBitmap: decode bounds outWidth=" + options.outWidth + " outHeight=" + options.outHeight);

        if (options.outWidth > MAX_WIDTH) {
            options.inSampleSize = IMGUtils.inSampleSize(Math.round(1f * options.outWidth / MAX_WIDTH));
        }

        if (options.outHeight > MAX_HEIGHT) {
            options.inSampleSize = Math.max(options.inSampleSize,
                    IMGUtils.inSampleSize(Math.round(1f * options.outHeight / MAX_HEIGHT)));
        }

        options.inJustDecodeBounds = false;

        Bitmap bitmap = decoder.decode(options);
        Log.d(TAG, "getBitmap: bitmap=" + (bitmap == null ? "null" : bitmap.getWidth() + "x" + bitmap.getHeight() + " config=" + bitmap.getConfig()));
        if (bitmap != null) {
            int pixel = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2);
            Log.d(TAG, "getBitmap: center pixel color=#" + Integer.toHexString(pixel) + " (isBlack=" + (pixel == Color.BLACK) + ")");
        }
        Log.d(TAG, "=== getBitmap() END ===");
        return bitmap;
    }

    @Override
    public void onText(IMGText text) {
        mImgView.addStickerText(text);
    }

    @Override
    public void onModeClick(IMGMode mode) {
        IMGMode cm = mImgView.getMode();
        if (cm == mode) {
            mode = IMGMode.NONE;
        }
        mImgView.setMode(mode);
        updateModeUI();

        if (mode == IMGMode.CLIP) {
            setOpDisplay(OP_CLIP);
        }
    }

    @Override
    public void onUndoClick() {
        IMGMode mode = mImgView.getMode();
        if (mode == IMGMode.DOODLE) {
            mImgView.undoDoodle();
        }
    }

    @Override
    public void onRedoClick() {
        IMGMode mode = mImgView.getMode();
        if (mode == IMGMode.DOODLE) {
            mImgView.redoDoodle();
        }
    }

    @Override
    public void onCancelClick() {
        finish();
    }

    private boolean mIsSaving = false;

    @Override
    public void onDoneClick() {
        if (mIsSaving) return;
        mIsSaving = true;

        String path = getIntent().getStringExtra(EXTRA_IMAGE_SAVE_PATH);
        if (TextUtils.isEmpty(path)) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // 停止所有动画和回调，防止后台读取冲突
        mImgView.prepareForSave();

        showLoadingDialog("正在保存...");
        final long showTime = System.currentTimeMillis();

        final String savePath = path;
        final String jsonPath = path + ".doodles.json";
        new Thread(() -> {
            // 先将所有贴纸移到背景列表（包括前景中的贴纸）
            mImgView.prepareForSaveStickers();

            // 序列化文字贴纸数据（用于重新编辑时恢复）
            org.json.JSONArray stickerArr = mImgView.serializeStickers();

            // 保存图片（文字贴纸会烧录到JPEG中，内容页可见）
            Bitmap bitmap = mImgView.saveBitmap();

            if (bitmap == null) {
                runOnUiThread(() -> {
                    if (mCurrentDialog != null) mCurrentDialog.dismiss();
                    dismissLoadingDialog();
                    setResult(RESULT_CANCELED);
                    finish();
                });
                return;
            }

            // 序列化涂鸦 + 文字贴纸
            String doodleJson = mImgView.serializeDoodles();
            try {
                org.json.JSONObject root = new org.json.JSONObject(doodleJson);
                if (stickerArr.length() > 0) {
                    root.put("stickers", stickerArr);
                }
                doodleJson = root.toString();
            } catch (Exception e) {
                Log.e(TAG, "onDoneClick: failed to merge stickers", e);
            }

            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(savePath);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fout);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "onDoneClick: compress failed", e);
            } finally {
                if (fout != null) {
                    try { fout.close(); } catch (IOException ignored) {}
                }
            }
            bitmap.recycle();

            java.io.FileWriter fw = null;
            try {
                fw = new java.io.FileWriter(jsonPath);
                fw.write(doodleJson);
            } catch (IOException e) {
                Log.e(TAG, "onDoneClick: failed to write doodle file", e);
            } finally {
                if (fw != null) {
                    try { fw.close(); } catch (IOException ignored) {}
                }
            }

            long elapsed = System.currentTimeMillis() - showTime;
            long remaining = 3000 - elapsed;
            if (remaining > 0) {
                try { Thread.sleep(remaining); } catch (InterruptedException ignored) {}
            }

            runOnUiThread(() -> {
                if (mCurrentDialog != null) mCurrentDialog.dismiss();
                dismissLoadingDialog();
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_DOODLE_FILE_PATH, jsonPath);
                setResult(RESULT_OK, resultIntent);
                finish();
            });
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (mIsSaving) return;
        super.onBackPressed();
    }

    @Override
    public void onCancelClipClick() {
        mImgView.cancelClip();
        setOpDisplay(mImgView.getMode() == IMGMode.CLIP ? OP_CLIP : OP_NORMAL);
    }

    @Override
    public void onDoneClipClick() {
        mImgView.doClip();
        setOpDisplay(mImgView.getMode() == IMGMode.CLIP ? OP_CLIP : OP_NORMAL);
    }

    @Override
    public void onResetClipClick() {
        mImgView.resetClip();
    }

    @Override
    public void onRotateClipClick() {
        mImgView.doRotate();
    }

    @Override
    public void onColorChanged(int checkedColor) {
        mImgView.setPenColor(checkedColor);
    }
}
