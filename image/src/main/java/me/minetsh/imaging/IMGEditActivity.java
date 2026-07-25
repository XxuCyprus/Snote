package me.minetsh.imaging;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import me.minetsh.imaging.core.IMGMode;
import me.minetsh.imaging.core.IMGText;
import me.minetsh.imaging.core.file.IMGAssetFileDecoder;
import me.minetsh.imaging.core.file.IMGDecoder;
import me.minetsh.imaging.core.file.IMGFileDecoder;
import me.minetsh.imaging.core.util.IMGUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by felix on 2017/11/14 下午2:26.
 */

public class IMGEditActivity extends IMGEditBaseActivity {

    private static final int MAX_WIDTH = 2048;

    private static final int MAX_HEIGHT = 2048;

    public static final String EXTRA_IMAGE_URI = "IMAGE_URI";

    public static final String EXTRA_IMAGE_SAVE_PATH = "IMAGE_SAVE_PATH";

    public static final String EXTRA_DOODLE_JSON = "DOODLE_JSON";

    @Override
    public void onCreated() {
        // 从 Intent extra 加载已有的涂鸦数据
        String doodleJson = getIntent().getStringExtra(EXTRA_DOODLE_JSON);
        if (doodleJson != null && !doodleJson.isEmpty()) {
            mImgView.deserializeDoodles(doodleJson);
            // 有已有涂鸦时自动进入涂鸦模式，否则撤销按钮不响应
            if (!mImgView.isDoodleEmpty()) {
                mImgView.setMode(IMGMode.DOODLE);
                updateModeUI();
            }
        }
    }

    @Override
    public Bitmap getBitmap() {
        Intent intent = getIntent();
        if (intent == null) {
            return null;
        }

        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uri = intent.getParcelableExtra(EXTRA_IMAGE_URI, Uri.class);
        } else {
            uri = intent.getParcelableExtra(EXTRA_IMAGE_URI);
        }
        if (uri == null) {
            return null;
        }

        // 如果存在 .base 干净底图，用它来加载（涂鸦通过 JSON 单独恢复）
        // 先验证 base 文件是有效图片，防止旧代码残留的黑图
        String uriPath = uri.getPath();
        if (uriPath != null) {
            File baseFile = new File(uriPath + ".base");
            if (baseFile.exists() && baseFile.length() > 0) {
                BitmapFactory.Options checkOpts = new BitmapFactory.Options();
                checkOpts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(baseFile.getAbsolutePath(), checkOpts);
                if (checkOpts.outWidth > 0 && checkOpts.outHeight > 0) {
                    uri = Uri.fromFile(baseFile);
                } else {
                    // 无效文件，删除之（下次编辑会重新生成）
                    baseFile.delete();
                }
            }
        }

        IMGDecoder decoder = null;

        String path = uri.getPath();
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
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        options.inJustDecodeBounds = true;

        decoder.decode(options);

        if (options.outWidth > MAX_WIDTH) {
            options.inSampleSize = IMGUtils.inSampleSize(Math.round(1f * options.outWidth / MAX_WIDTH));
        }

        if (options.outHeight > MAX_HEIGHT) {
            options.inSampleSize = Math.max(options.inSampleSize,
                    IMGUtils.inSampleSize(Math.round(1f * options.outHeight / MAX_HEIGHT)));
        }

        options.inJustDecodeBounds = false;

        Bitmap bitmap = decoder.decode(options);
        if (bitmap == null) {
            return null;
        }

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

    @Override
    public void onDoneClick() {
        String path = getIntent().getStringExtra(EXTRA_IMAGE_SAVE_PATH);
        if (!TextUtils.isEmpty(path)) {
            Bitmap bitmap = mImgView.saveBitmap();
            if (bitmap != null) {
                FileOutputStream fout = null;
                try {
                    fout = new FileOutputStream(path);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fout);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    if (fout != null) {
                        try {
                            fout.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                bitmap.recycle();

                // 将源图片复制为干净底图，放在保存路径旁边（用于二次编辑时撤销/重做）
                // 必须放在保存路径（而非源路径）上，因为 updateImageContentPath 会删除旧路径文件
                Uri sourceUri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    sourceUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI, Uri.class);
                } else {
                    sourceUri = getIntent().getParcelableExtra(EXTRA_IMAGE_URI);
                }
                if (sourceUri != null) {
                    String sourcePath = sourceUri.getPath();
                    if (sourcePath != null) {
                        copyFile(new File(sourcePath), new File(path + ".base"));
                    }
                }

                // 将涂鸦数据放入返回 Intent
                String doodleJson = mImgView.serializeDoodles();
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_DOODLE_JSON, doodleJson);
                setResult(RESULT_OK, resultIntent);
                finish();
                return;
            }
        }
        setResult(RESULT_CANCELED);
        finish();
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

    private void copyFile(File src, File dst) {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
