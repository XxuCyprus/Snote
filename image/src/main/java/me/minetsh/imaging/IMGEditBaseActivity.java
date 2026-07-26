package me.minetsh.imaging;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.ViewSwitcher;

import me.minetsh.imaging.core.IMGMode;
import me.minetsh.imaging.core.IMGText;
import me.minetsh.imaging.view.IMGColorGroup;
import me.minetsh.imaging.view.IMGView;

/**
 * Created by felix on 2017/12/5 下午3:08.
 */

abstract class IMGEditBaseActivity extends Activity implements View.OnClickListener,
        IMGTextEditDialog.Callback, RadioGroup.OnCheckedChangeListener,
        DialogInterface.OnShowListener, DialogInterface.OnDismissListener {

    protected IMGView mImgView;

    private RadioGroup mModeGroup;

    private IMGColorGroup mColorGroup;

    private IMGTextEditDialog mTextDialog;

    private View mLayoutOpSub;

    private ViewSwitcher mOpSwitcher, mOpSubSwitcher;

    private ImageButton mEraserBtn;

    private SeekBar mStrokeWidthBar;

    public static final int OP_HIDE = -1;

    public static final int OP_NORMAL = 0;

    public static final int OP_CLIP = 1;

    public static final int OP_SUB_DOODLE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 读取笔记主题色
        if (getIntent() != null) {
            ((IMGEditActivity) this).mThemeColor = getIntent().getIntExtra("THEME_COLOR", 0xFF1565C0);
        }
        Bitmap bitmap = getBitmap();
        if (bitmap != null) {
            setContentView(R.layout.image_edit_activity);
            initViews();
            applyThemeColor();
            mImgView.setImageBitmap(bitmap);
            onCreated();
        } else finish();
    }

    public void onCreated() {

    }

    private void applyThemeColor() {
        int color = ((IMGEditActivity) this).mThemeColor;
        // SeekBar 进度条和滑块颜色
        mStrokeWidthBar.getProgressDrawable().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        mStrokeWidthBar.getThumb().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        // 完成按钮文字色
        android.widget.TextView doneBtn = findViewById(R.id.tv_done);
        if (doneBtn != null) doneBtn.setTextColor(color);
    }

    private void initViews() {
        mImgView = findViewById(R.id.image_canvas);
        mModeGroup = findViewById(R.id.rg_modes);

        mOpSwitcher = findViewById(R.id.vs_op);
        mOpSubSwitcher = findViewById(R.id.vs_op_sub);

        mColorGroup = findViewById(R.id.cg_colors);
        mColorGroup.setOnCheckedChangeListener(this);

        mLayoutOpSub = findViewById(R.id.layout_op_sub);

        // 橡皮擦按钮
        mEraserBtn = findViewById(R.id.btn_eraser);
        mEraserBtn.setOnClickListener(this);

        // 笔刷大小 SeekBar
        mStrokeWidthBar = findViewById(R.id.sb_stroke_width);
        mStrokeWidthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mImgView.isEraserMode()) {
                    mImgView.setEraserSize(progress);
                } else {
                    mImgView.setPenStrokeWidth(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @Override
    public void onClick(View v) {
        int vid = v.getId();
        if (vid == R.id.rb_doodle) {
            onModeClick(IMGMode.DOODLE);
        } else if (vid == R.id.btn_text) {
            onTextModeClick();
        } else if (vid == R.id.btn_clip) {
            onModeClick(IMGMode.CLIP);
        } else if (vid == R.id.btn_undo) {
            onUndoClick();
        } else if (vid == R.id.btn_redo) {
            onRedoClick();
        } else if (vid == R.id.btn_eraser) {
            onEraserClick();
        } else if (vid == R.id.tv_done) {
            onDoneClick();
        } else if (vid == R.id.tv_cancel) {
            onCancelClick();
        } else if (vid == R.id.ib_clip_cancel) {
            onCancelClipClick();
        } else if (vid == R.id.ib_clip_done) {
            onDoneClipClick();
        } else if (vid == R.id.tv_clip_reset) {
            onResetClipClick();
        } else if (vid == R.id.ib_clip_rotate) {
            onRotateClipClick();
        }
    }

    private void onEraserClick() {
        boolean eraser = !mImgView.isEraserMode();
        mImgView.setEraserMode(eraser);
        mEraserBtn.setSelected(eraser);

        if (eraser) {
            mStrokeWidthBar.setProgress((int) mImgView.getEraserSize());
            // 橡皮擦选中时着色
            mEraserBtn.getDrawable().mutate().setColorFilter(
                ((IMGEditActivity) this).mThemeColor, android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            mStrokeWidthBar.setProgress((int) mImgView.getPenStrokeWidth());
            mEraserBtn.getDrawable().clearColorFilter();
        }
    }

    public void updateModeUI() {
        IMGMode mode = mImgView.getMode();
        int color = ((IMGEditActivity) this).mThemeColor;
        RadioButton rbDoodle = findViewById(R.id.rb_doodle);
        ImageButton btnText = findViewById(R.id.btn_text);
        ImageButton btnClip = findViewById(R.id.btn_clip);
        // 重置所有图标颜色（带null检查）
        if (rbDoodle != null && rbDoodle.getButtonDrawable() != null)
            rbDoodle.getButtonDrawable().clearColorFilter();
        if (btnText != null && btnText.getDrawable() != null)
            btnText.getDrawable().clearColorFilter();
        if (btnClip != null && btnClip.getDrawable() != null)
            btnClip.getDrawable().clearColorFilter();
        switch (mode) {
            case DOODLE:
                mModeGroup.check(R.id.rb_doodle);
                setOpSubDisplay(OP_SUB_DOODLE);
                // 标记图标着色为主题色
                if (rbDoodle != null && rbDoodle.getButtonDrawable() != null) {
                    rbDoodle.getButtonDrawable().mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                }
                break;
            case NONE:
                mModeGroup.clearCheck();
                setOpSubDisplay(OP_HIDE);
                break;
        }
    }

    public void onTextModeClick() {
        if (mTextDialog == null) {
            mTextDialog = new IMGTextEditDialog(this, this);
            mTextDialog.setOnShowListener(this);
            mTextDialog.setOnDismissListener(this);
        }
        mTextDialog.setThemeColor(((IMGEditActivity) this).mThemeColor);
        mTextDialog.show();
    }

    @Override
    public final void onCheckedChanged(RadioGroup group, int checkedId) {
        // 选择颜色时自动退出橡皮擦模式
        if (mImgView.isEraserMode()) {
            mImgView.setEraserMode(false);
            mEraserBtn.setSelected(false);
            mStrokeWidthBar.setProgress((int) mImgView.getPenStrokeWidth());
        }
        onColorChanged(mColorGroup.getCheckColor());
    }

    public void setOpDisplay(int op) {
        if (op >= 0) {
            mOpSwitcher.setDisplayedChild(op);
        }
    }

    public void setOpSubDisplay(int opSub) {
        if (opSub < 0) {
            mLayoutOpSub.setVisibility(View.GONE);
        } else {
            mOpSubSwitcher.setDisplayedChild(opSub);
            mLayoutOpSub.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onShow(DialogInterface dialog) {
        mOpSwitcher.setVisibility(View.GONE);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mOpSwitcher.setVisibility(View.VISIBLE);
    }

    public abstract Bitmap getBitmap();

    public abstract void onModeClick(IMGMode mode);

    public abstract void onUndoClick();

    public abstract void onRedoClick();

    public abstract void onCancelClick();

    public abstract void onDoneClick();

    public abstract void onCancelClipClick();

    public abstract void onDoneClipClick();

    public abstract void onResetClipClick();

    public abstract void onRotateClipClick();

    public abstract void onColorChanged(int checkedColor);

    @Override
    public abstract void onText(IMGText text);
}
