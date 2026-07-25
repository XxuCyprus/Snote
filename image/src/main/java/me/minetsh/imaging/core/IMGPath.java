package me.minetsh.imaging.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Created by felix on 2017/11/22 下午6:13.
 */

public class IMGPath {

    protected Path path;

    private int color = Color.RED;

    private IMGMode mode = IMGMode.DOODLE;

    public static final float BASE_DOODLE_WIDTH = 20f;

    public IMGPath() {
        this(new Path());
    }

    public IMGPath(Path path) {
        this(path, IMGMode.DOODLE);
    }

    public IMGPath(Path path, IMGMode mode) {
        this(path, mode, Color.RED);
    }

    public IMGPath(Path path, IMGMode mode, int color) {
        this.path = path;
        this.mode = mode;
        this.color = color;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public IMGMode getMode() {
        return mode;
    }

    public void setMode(IMGMode mode) {
        this.mode = mode;
    }

    public void onDrawDoodle(Canvas canvas, Paint paint) {
        if (mode == IMGMode.DOODLE) {
            paint.setColor(color);
            paint.setStrokeWidth(BASE_DOODLE_WIDTH);
            canvas.drawPath(path, paint);
        }
    }

    public void transform(Matrix matrix) {
        path.transform(matrix);
    }
}
