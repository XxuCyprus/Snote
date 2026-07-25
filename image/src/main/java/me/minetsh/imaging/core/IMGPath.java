package me.minetsh.imaging.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by felix on 2017/11/22 下午6:13.
 */

public class IMGPath {

    protected Path path;

    private int color = Color.RED;

    private IMGMode mode = IMGMode.DOODLE;

    private float width = BASE_DOODLE_WIDTH;

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
        this(path, mode, color, BASE_DOODLE_WIDTH);
    }

    public IMGPath(Path path, IMGMode mode, int color, float width) {
        this.path = path;
        this.mode = mode;
        this.color = color;
        this.width = width;
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

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void onDrawDoodle(Canvas canvas, Paint paint) {
        if (mode == IMGMode.DOODLE) {
            paint.setColor(color);
            paint.setStrokeWidth(width);
            canvas.drawPath(path, paint);
        }
    }

    public void transform(Matrix matrix) {
        path.transform(matrix);
    }

    /**
     * 擦除路径中靠近 (x, y) 的部分，返回剩余的路径段列表
     */
    public List<IMGPath> eraseNear(float x, float y, float radius) {
        List<IMGPath> result = new ArrayList<>();
        float[] points = approximatePathPoints();
        if (points.length < 4) return result;

        // 标记每个线段是否在擦除区域内
        boolean[] inside = new boolean[points.length / 2];
        int i = 0;
        for (; i < points.length; i += 2) {
            float dx = points[i] - x;
            float dy = points[i + 1] - y;
            if (dx * dx + dy * dy <= radius * radius) {
                inside[i / 2] = true;
            }
        }

        // 检测线段穿过圆但两端都在外的情形
        for (i = 0; i < points.length - 2; i += 2) {
            if (inside[i / 2] || inside[i / 2 + 1]) continue;
            float d = pointToSegmentDist(x, y, points[i], points[i + 1], points[i + 2], points[i + 3]);
            if (d <= radius) {
                inside[i / 2] = true;
                inside[i / 2 + 1] = true;
            }
        }

        // 拆分路径
        Path currentSeg = new Path();
        boolean started = false;
        for (i = 0; i < points.length; i += 2) {
            if (inside[i / 2]) {
                if (started) {
                    result.add(new IMGPath(new Path(currentSeg), IMGMode.DOODLE, color, width));
                    currentSeg = new Path();
                    started = false;
                }
            } else {
                if (!started) {
                    currentSeg.moveTo(points[i], points[i + 1]);
                    started = true;
                } else {
                    currentSeg.lineTo(points[i], points[i + 1]);
                }
            }
        }
        if (started) {
            result.add(new IMGPath(new Path(currentSeg), IMGMode.DOODLE, color, width));
        }
        return result;
    }

    private float[] approximatePathPoints() {
        android.graphics.PathMeasure pm = new android.graphics.PathMeasure(path, false);
        List<Float> pts = new ArrayList<>();
        float[] pos = new float[2];
        float length = pm.getLength();
        if (length <= 0) return new float[0];
        float step = Math.max(2f, length / 100f);
        for (float d = 0; d <= length; d += step) {
            pm.getPosTan(d, pos, null);
            pts.add(pos[0]);
            pts.add(pos[1]);
        }
        // 确保最后一点
        pm.getPosTan(length, pos, null);
        pts.add(pos[0]);
        pts.add(pos[1]);

        float[] result = new float[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            result[i] = pts.get(i);
        }
        return result;
    }

    private static float pointToSegmentDist(float px, float py,
                                             float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float lenSq = dx * dx + dy * dy;
        if (lenSq == 0f) {
            float d = (px - ax) * (px - ax) + (py - ay) * (py - ay);
            return (float) Math.sqrt(d);
        }
        float t = ((px - ax) * dx + (py - ay) * dy) / lenSq;
        t = Math.max(0f, Math.min(1f, t));
        float cx = ax + t * dx;
        float cy = ay + t * dy;
        float d = (px - cx) * (px - cx) + (py - cy) * (py - cy);
        return (float) Math.sqrt(d);
    }
}
