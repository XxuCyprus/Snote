package me.minetsh.imaging.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by felix on 2017/11/22 下午6:13.
 */

public class IMGPath {

    protected Path path;

    private int color = Color.RED;

    private IMGMode mode = IMGMode.DOODLE;

    private float width = BASE_DOODLE_WIDTH;

    // 缓存路径点，避免序列化时重复计算
    private float[] cachedPoints = null;
    private boolean pointsCacheDirty = true;

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
        pointsCacheDirty = true;
        cachedPoints = null;
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
        pointsCacheDirty = true;
        cachedPoints = null;
    }

    /**
     * 擦除路径中靠近 (x, y) 的部分，返回剩余的路径段列表
     * 如果没有擦除任何内容，返回包含原始路径的单元素列表
     */
    public List<IMGPath> eraseNear(float x, float y, float radius) {
        List<IMGPath> result = new ArrayList<>();
        float[] points = approximatePathPoints();

        // 特殊处理：零长度路径（点）— approximatePathPoints 返回空数组
        if (points.length < 2) {
            android.graphics.RectF bounds = new android.graphics.RectF();
            path.computeBounds(bounds, true);
            // 使用边界框的左上角作为点的坐标（moveTo 的位置）
            float dotX = bounds.left;
            float dotY = bounds.top;
            float dx = dotX - x;
            float dy = dotY - y;
            if (dx * dx + dy * dy <= radius * radius) {
                return result;  // 点在擦除区域内 → 擦除（返回空列表）
            } else {
                result.add(this);
                return result;  // 点在擦除区域外 → 保留（返回自身引用）
            }
        }

        // 标记每个点是否在擦除区域内
        boolean[] inside = new boolean[points.length / 2];
        boolean anyInside = false;
        int i = 0;
        for (; i < points.length; i += 2) {
            float dx = points[i] - x;
            float dy = points[i + 1] - y;
            if (dx * dx + dy * dy <= radius * radius) {
                inside[i / 2] = true;
                anyInside = true;
            }
        }

        // 检测线段穿过圆但两端都在外的情形
        for (i = 0; i < points.length - 2; i += 2) {
            if (inside[i / 2] || inside[i / 2 + 1]) continue;
            float d = pointToSegmentDist(x, y, points[i], points[i + 1], points[i + 2], points[i + 3]);
            if (d <= radius) {
                inside[i / 2] = true;
                inside[i / 2 + 1] = true;
                anyInside = true;
            }
        }

        // 没有任何点被擦除 → 返回原始路径，不做任何重建
        if (!anyInside) {
            result.add(this);
            return result;
        }

        // 拆分路径（用采样点重建）
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
        // 使用缓存避免重复计算
        if (!pointsCacheDirty && cachedPoints != null) {
            return cachedPoints;
        }
        android.graphics.PathMeasure pm = new android.graphics.PathMeasure(path, false);
        List<Float> pts = new ArrayList<>();
        float[] pos = new float[2];
        float length = pm.getLength();
        if (length <= 0) {
            cachedPoints = new float[0];
            pointsCacheDirty = false;
            return cachedPoints;
        }
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
        cachedPoints = result;
        pointsCacheDirty = false;
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

    /**
     * 序列化为 JSON（精简格式：短key、扁平数组、坐标取整）
     */
    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("c", color);
        obj.put("w", Math.round(width));

        float[] pts = approximatePathPoints();
        JSONArray arr = new JSONArray();
        for (int i = 0; i < pts.length; i++) {
            arr.put(Math.round(pts[i]));
        }
        obj.put("p", arr);
        return obj;
    }

    /**
     * 从 JSON 反序列化（兼容精简格式和旧格式）
     */
    public static IMGPath fromJson(JSONObject obj) throws JSONException {
        int color = obj.optInt("c", obj.optInt("color", Color.RED));
        float width = (float) obj.optDouble("w", obj.optDouble("width", BASE_DOODLE_WIDTH));

        Path path = new Path();
        if (obj.has("p")) {
            // 精简格式：扁平数组 [x1,y1,x2,y2,...]
            JSONArray arr = obj.getJSONArray("p");
            if (arr.length() >= 2) {
                path.moveTo((float) arr.getDouble(0), (float) arr.getDouble(1));
                for (int i = 2; i < arr.length(); i += 2) {
                    path.lineTo((float) arr.getDouble(i), (float) arr.getDouble(i + 1));
                }
            }
        } else if (obj.has("points")) {
            // 旧格式：嵌套数组 [[x1,y1],[x2,y2],...]
            JSONArray arr = obj.getJSONArray("points");
            if (arr.length() > 0) {
                JSONArray first = arr.getJSONArray(0);
                path.moveTo((float) first.getDouble(0), (float) first.getDouble(1));
                for (int i = 1; i < arr.length(); i++) {
                    JSONArray pt = arr.getJSONArray(i);
                    path.lineTo((float) pt.getDouble(0), (float) pt.getDouble(1));
                }
            }
        }
        return new IMGPath(path, IMGMode.DOODLE, color, width);
    }

    /**
     * 批量序列化
     */
    public static JSONArray listToJson(List<IMGPath> paths) throws JSONException {
        JSONArray arr = new JSONArray();
        for (IMGPath p : paths) {
            arr.put(p.toJson());
        }
        return arr;
    }

    /**
     * 批量反序列化
     */
    public static List<IMGPath> listFromJson(JSONArray arr) throws JSONException {
        List<IMGPath> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            result.add(fromJson(arr.getJSONObject(i)));
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IMGPath)) return false;
        IMGPath other = (IMGPath) obj;
        if (color != other.color) return false;
        if (Float.compare(width, other.width) != 0) return false;
        if (mode != other.mode) return false;
        // 比较路径点
        float[] pts1 = approximatePathPoints();
        float[] pts2 = other.approximatePathPoints();
        if (pts1.length != pts2.length) return false;
        for (int i = 0; i < pts1.length; i++) {
            if (Float.compare(pts1[i], pts2[i]) != 0) return false;
        }
        return true;
    }
}
