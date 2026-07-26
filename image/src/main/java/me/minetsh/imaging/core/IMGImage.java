package me.minetsh.imaging.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;

import me.minetsh.imaging.core.clip.IMGClip;
import me.minetsh.imaging.core.clip.IMGClipWindow;
import me.minetsh.imaging.core.homing.IMGHoming;
import me.minetsh.imaging.core.sticker.IMGSticker;
import me.minetsh.imaging.core.util.IMGUtils;

import me.minetsh.imaging.view.IMGStickerTextView;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by felix on 2017/11/21 下午10:03.
 */

public class IMGImage {

    private static final String TAG = "IMGImage";

    private Bitmap mImage;

    /**
     * 完整图片边框
     */
    private RectF mFrame = new RectF();

    /**
     * 裁剪图片边框（显示的图片区域）
     */
    private RectF mClipFrame = new RectF();

    private RectF mTempClipFrame = new RectF();

    /**
     * 裁剪模式前状态备份
     */
    private RectF mBackupClipFrame = new RectF();

    private float mBackupClipRotate = 0;

    private float mRotate = 0, mTargetRotate = 0;

    private boolean isRequestToBaseFitting = false;

    private boolean isAnimCanceled = false;

    /**
     * 裁剪模式时当前触摸锚点
     */
    private IMGClip.Anchor mAnchor;

    private boolean isSteady = true;

    private Path mShade = new Path();

    /**
     * 裁剪窗口
     */
    private IMGClipWindow mClipWin = new IMGClipWindow();

    private boolean isDrawClip = false;

    /**
     * 编辑模式
     */
    private IMGMode mMode = IMGMode.NONE;

    private boolean isFreezing = mMode == IMGMode.CLIP;

    /**
     * 可视区域，无Scroll 偏移区域
     */
    private RectF mWindow = new RectF();

    /**
     * 是否初始位置
     */
    private boolean isInitialHoming = false;

    /**
     * 当前选中贴片
     */
    private IMGSticker mForeSticker;

    /**
     * 为被选中贴片
     */
    private List<IMGSticker> mBackStickers = new ArrayList<>();

    /**
     * 涂鸦路径
     */
    private List<IMGPath> mDoodles = new ArrayList<>();

    /**
     * 线性历史：每个元素是一次操作前的完整 mDoodles 快照
     */
    private List<List<IMGPath>> mHistory = new ArrayList<>();

    /**
     * 当前状态在历史中的位置（mDoodles 对应 mHistory[mHistoryIndex]）
     */
    private int mHistoryIndex = 0;

    private static final int MAX_HISTORY = 700;

    /**
     * 序列化用：记录撤销操作（增量格式，非快照）
     * 与 mHistory 一一对应，但只存增量数据
     * null = ADD 操作（路径就是 mHistory 对应快照的最后一个元素）
     * 非 null = ERASE 操作（存储被擦除的路径 + 擦除前快照）
     */
    private static class UndoOp {
        final List<IMGPath> removed;
        final List<Integer> positions;
        final List<IMGPath> preEraseSnapshot;
        final List<IMGPath> postEraseState;

        UndoOp(List<IMGPath> removed, List<Integer> positions, List<IMGPath> snapshot, List<IMGPath> postState) {
            this.removed = removed;
            this.positions = positions;
            this.preEraseSnapshot = snapshot;
            this.postEraseState = postState;
        }
    }

    private List<UndoOp> mUndoOps = new ArrayList<>();

    /**
     * 临时隐藏涂鸦（用于生成干净底图）
     */
    private List<IMGPath> mDoodlesBackup = null;

    private static final int MIN_SIZE = 500;

    private static final int MAX_SIZE = 10000;

    private Paint mPaint, mShadePaint;

    private Matrix M = new Matrix();

    private final Object mLock = new Object();

    /**
     * 涂鸦层位图缓存：避免每帧重绘所有路径
     */
    private Bitmap mDoodlesCache = null;
    private Canvas mDoodlesCacheCanvas = null;
    private boolean mDoodlesCacheDirty = true;

    private static final boolean DEBUG = false;

    private static final Bitmap DEFAULT_IMAGE;

    private static final int COLOR_SHADE = 0xCC000000;

    static {
        DEFAULT_IMAGE = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
    }

    {
        mShade.setFillType(Path.FillType.WINDING);

        // Doodle&Mosaic 's paint
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(IMGPath.BASE_DOODLE_WIDTH);
        mPaint.setColor(Color.RED);
        mPaint.setPathEffect(new CornerPathEffect(IMGPath.BASE_DOODLE_WIDTH));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public IMGImage() {
        mImage = DEFAULT_IMAGE;

        if (mMode == IMGMode.CLIP) {
            initShadePaint();
        }
    }

    public void setBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        this.mImage = bitmap;

        onImageChanged();
    }

    public IMGMode getMode() {
        return mMode;
    }

    public void setMode(IMGMode mode) {

        if (this.mMode == mode) return;

        moveToBackground(mForeSticker);

        if (mode == IMGMode.CLIP) {
            setFreezing(true);
        }

        this.mMode = mode;

        if (mMode == IMGMode.CLIP) {

            // 初始化Shade 画刷
            initShadePaint();

            // 备份裁剪前Clip 区域
            mBackupClipRotate = getRotate();
            mBackupClipFrame.set(mClipFrame);

            float scale = 1 / getScale();
            M.setTranslate(-mFrame.left, -mFrame.top);
            M.postScale(scale, scale);
            M.mapRect(mBackupClipFrame);

            // 重置裁剪区域
            mClipWin.reset(mClipFrame, getTargetRotate());
        } else {

            mClipWin.setClipping(false);
        }
    }

    // TODO
    private void rotateStickers(float rotate) {
        M.setRotate(rotate, mClipFrame.centerX(), mClipFrame.centerY());
        for (IMGSticker sticker : mBackStickers) {
            M.mapRect(sticker.getFrame());
            sticker.setRotation(sticker.getRotation() + rotate);
            sticker.setX(sticker.getFrame().centerX() - sticker.getPivotX());
            sticker.setY(sticker.getFrame().centerY() - sticker.getPivotY());
        }
    }

    private void initShadePaint() {
        if (mShadePaint == null) {
            mShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mShadePaint.setColor(COLOR_SHADE);
            mShadePaint.setStyle(Paint.Style.FILL);
        }
    }

    public boolean isDoodleEmpty() {
        return mDoodles.isEmpty();
    }

    private void invalidateDoodlesCache() {
        mDoodlesCacheDirty = true;
    }

    public void undoDoodle() {
        Log.d(TAG, "undoDoodle: mHistoryIndex=" + mHistoryIndex + ", mHistory.size=" + mHistory.size());
        if (mHistoryIndex <= 0) {
            Log.d(TAG, "undoDoodle: BLOCKED (at beginning)");
            return;
        }
        mHistoryIndex--;
        mDoodles = new ArrayList<>(mHistory.get(mHistoryIndex));
        Log.d(TAG, "undoDoodle: restored to index " + mHistoryIndex + ", doodles=" + mDoodles.size());
        invalidateDoodlesCache();
    }

    public void redoDoodle() {
        Log.d(TAG, "redoDoodle: mHistoryIndex=" + mHistoryIndex + ", mHistory.size=" + mHistory.size());
        if (mHistoryIndex >= mHistory.size() - 1) {
            Log.d(TAG, "redoDoodle: BLOCKED (at end)");
            return;
        }
        mHistoryIndex++;
        mDoodles = new ArrayList<>(mHistory.get(mHistoryIndex));
        Log.d(TAG, "redoDoodle: restored to index " + mHistoryIndex + ", doodles=" + mDoodles.size());
        invalidateDoodlesCache();
    }

    public boolean canRedo() {
        return mHistoryIndex < mHistory.size() - 1;
    }

    /**
     * 操作前保存当前状态快照到历史栈
     */
    private void pushHistory() {
        // 截断 redo 历史（新操作使后续历史失效）
        while (mHistory.size() > mHistoryIndex + 1) {
            mHistory.remove(mHistory.size() - 1);
        }
        while (mUndoOps.size() > mHistoryIndex) {
            mUndoOps.remove(mUndoOps.size() - 1);
        }
        // 保存当前状态
        mHistory.add(new ArrayList<>(mDoodles));
        mUndoOps.add(null); // 占位，后续可能被替换为 ERASE 操作
        mHistoryIndex = mHistory.size() - 1;
        // 限制历史大小
        while (mHistory.size() > MAX_HISTORY) {
            mHistory.remove(0);
            mUndoOps.remove(0);
            mHistoryIndex--;
        }
    }

    /**
     * 序列化为 JSON（紧凑增量格式）
     * 只保存当前涂鸦 + 最近 N 条撤销操作（非完整快照）
     */
    public String serializeDoodles() {
        try {
            long startTime = System.currentTimeMillis();
            JSONObject root = new JSONObject();
            root.put("doodles", IMGPath.listToJson(mDoodles));

            // 只保存到当前历史位置的撤销操作（不保存被撤销的步骤）
            JSONArray undoArr = new JSONArray();
            // 存储初始状态（mHistory[0]），防止反序列化时丢失第一个 addPath
            if (mHistoryIndex > 0 && !mHistory.get(0).isEmpty()) {
                JSONObject initEntry = new JSONObject();
                initEntry.put("t", "i");
                initEntry.put("s", IMGPath.listToJson(mHistory.get(0)));
                undoArr.put(initEntry);
            }
            for (int i = 0; i < mHistoryIndex; i++) {
                try {
                    UndoOp op = mUndoOps.get(i);
                    JSONObject entry = new JSONObject();
                    if (op == null) {
                        // ADD 操作：路径就是 mHistory[i+1] 的最后一个元素
                        List<IMGPath> nextSnapshot = mHistory.get(i + 1);
                        if (!nextSnapshot.isEmpty()) {
                            entry.put("t", "a");
                            entry.put("p", nextSnapshot.get(nextSnapshot.size() - 1).toJson());
                        }
                    } else {
                        // ERASE 操作
                        entry.put("t", "e");
                        entry.put("r", IMGPath.listToJson(op.removed));
                        JSONArray posArr = new JSONArray();
                        for (int p : op.positions) posArr.put(p);
                        entry.put("pos", posArr);
                        entry.put("snap", IMGPath.listToJson(op.preEraseSnapshot));
                        if (op.postEraseState != null) {
                            entry.put("post", IMGPath.listToJson(op.postEraseState));
                        }
                    }
                    undoArr.put(entry);
                } catch (Exception e) {
                    Log.w(TAG, "serializeDoodles: skip entry " + i, e);
                }
            }
            root.put("undo", undoArr);
            long elapsed = System.currentTimeMillis() - startTime;
            Log.d(TAG, "serializeDoodles: mHistory.size=" + mHistory.size()
                + ", mUndoOps.size=" + mUndoOps.size()
                + ", undoArr.length=" + undoArr.length()
                + ", mHistoryIndex=" + mHistoryIndex
                + ", currentDoodles=" + mDoodles.size()
                + ", elapsed=" + elapsed + "ms");

            return root.toString();
        } catch (JSONException e) {
            Log.e(TAG, "serializeDoodles failed", e);
            return "{}";
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "serializeDoodles OOM", e);
            System.gc();
            return "{}";
        }
    }

    /**
     * 从 JSON 反序列化涂鸦状态（兼容旧格式）
     * 线程安全：持有 mLock 以防止与 onDrawDoodles() 并发
     */
    public void deserializeDoodles(String json) {
        if (json == null) return;
        synchronized (mLock) {
            try {
                JSONObject root = new JSONObject(json);

                mDoodles = IMGPath.listFromJson(root.getJSONArray("doodles"));

                mHistory.clear();
                mUndoOps.clear();

                if (root.has("undo")) {
                    JSONArray undoArr = root.getJSONArray("undo");
                    mHistory.add(new ArrayList<IMGPath>());
                    Log.d(TAG, "deserializeDoodles: undoArr.length=" + undoArr.length() + ", currentDoodles=" + mDoodles.size());
                    for (int i = 0; i < undoArr.length(); i++) {
                        try {
                            JSONObject entry = undoArr.getJSONObject(i);
                            String type = entry.optString("t", "");
                            if (type.equals("i")) {
                                // 初始状态：添加为新条目，保留空的 mHistory[0]
                                mHistory.add(IMGPath.listFromJson(entry.getJSONArray("s")));
                                mUndoOps.add(null);
                            } else if (type.equals("a")) {
                                IMGPath added = IMGPath.fromJson(entry.getJSONObject("p"));
                                List<IMGPath> prev = mHistory.get(mHistory.size() - 1);
                                List<IMGPath> next = new ArrayList<>(prev);
                                next.add(added);
                                mHistory.add(next);
                                mUndoOps.add(null);
                            } else if (type.equals("e")) {
                                List<IMGPath> snapshot = IMGPath.listFromJson(entry.getJSONArray("snap"));
                                List<IMGPath> removed = IMGPath.listFromJson(entry.getJSONArray("r"));
                                JSONArray posArr = entry.getJSONArray("pos");
                                List<Integer> positions = new ArrayList<>();
                                for (int j = 0; j < posArr.length(); j++) positions.add(posArr.getInt(j));
                                List<IMGPath> afterErase;
                                if (entry.has("post")) {
                                    // 新格式：直接使用存储的擦除后状态（最可靠）
                                    afterErase = IMGPath.listFromJson(entry.getJSONArray("post"));
                                } else {
                                    // 旧格式：从 snapshot 中移除被擦除路径
                                    afterErase = new ArrayList<>(snapshot);
                                    for (int j = positions.size() - 1; j >= 0; j--) {
                                        int pos = positions.get(j);
                                        if (pos >= 0 && pos < afterErase.size()) {
                                            afterErase.remove(pos);
                                        }
                                    }
                                }
                                mHistory.add(afterErase);
                                mUndoOps.add(new UndoOp(removed, positions, snapshot, afterErase));
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "deserializeDoodles: skip entry " + i, e);
                        }
                    }
                    mHistoryIndex = mHistory.size() - 1;
                    // 检查重建的历史最后一项是否与当前涂鸦一致
                    List<IMGPath> lastHistory = mHistory.get(mHistoryIndex);
                    boolean matches = lastHistory.size() == mDoodles.size();
                    if (matches) {
                        for (int i = 0; i < mDoodles.size(); i++) {
                            // 比较路径内容而非对象引用
                            if (!lastHistory.get(i).equals(mDoodles.get(i))) { matches = false; break; }
                        }
                    }
                    Log.d(TAG, "deserializeDoodles: rebuilt history, size=" + mHistory.size()
                        + ", mHistoryIndex=" + mHistoryIndex
                        + ", lastHistorySize=" + lastHistory.size()
                        + ", currentDoodlesSize=" + mDoodles.size()
                        + ", matches=" + matches);
                    if (!matches) {
                        // 历史与当前状态不一致，添加当前状态作为额外条目
                        mHistory.add(new ArrayList<>(mDoodles));
                        mUndoOps.add(null);
                        mHistoryIndex = mHistory.size() - 1;
                        Log.d(TAG, "deserializeDoodles: added current state, new size=" + mHistory.size()
                            + ", new mHistoryIndex=" + mHistoryIndex);
                    }
                } else if (root.has("history")) {
                    JSONArray histArr = root.getJSONArray("history");
                    for (int i = 0; i < histArr.length(); i++) {
                        mHistory.add(IMGPath.listFromJson(histArr.getJSONArray(i)));
                    }
                    mHistoryIndex = root.optInt("historyIndex", mHistory.size() - 1);
                    for (int i = 0; i < mHistory.size() - 1; i++) mUndoOps.add(null);
                } else {
                    mHistory.add(new ArrayList<>(mDoodles));
                    mHistoryIndex = 0;
                }
            } catch (JSONException e) {
                Log.e(TAG, "deserializeDoodles failed", e);
                // 确保至少有一个历史条目
                if (mHistory.isEmpty()) {
                    mHistory.add(new ArrayList<>(mDoodles));
                    mHistoryIndex = 0;
                }
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "deserializeDoodles OOM", e);
                System.gc();
                // 降级：只保留当前状态
                mHistory.clear();
                mUndoOps.clear();
                mHistory.add(new ArrayList<>(mDoodles));
                mHistoryIndex = 0;
            } catch (Exception e) {
                Log.e(TAG, "deserializeDoodles unexpected error", e);
                // 降级：只保留当前状态
                mHistory.clear();
                mUndoOps.clear();
                mHistory.add(new ArrayList<>(mDoodles));
                mHistoryIndex = 0;
            }
        }
        invalidateDoodlesCache();
    }

    /**
     * 序列化文字贴纸为 JSON 数组
     */
    public JSONArray serializeStickers() {
        JSONArray arr = new JSONArray();

        for (IMGSticker sticker : mBackStickers) {
            if (sticker instanceof IMGStickerTextView) {
                IMGStickerTextView tv = (IMGStickerTextView) sticker;
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("text", tv.getText().toJson());
                    obj.put("x", tv.getX());
                    obj.put("y", tv.getY());
                    obj.put("scale", tv.getScale());
                    obj.put("rotation", tv.getRotation());
                    arr.put(obj);
                } catch (JSONException e) {
                    Log.e(TAG, "serializeStickers failed", e);
                }
            }
        }
        return arr;
    }

    /**
     * 反序列化文字贴纸（在主线程调用，布局完成后立即恢复位置）
     */
    public void deserializeStickers(JSONArray arr, android.content.Context context,
                                     android.widget.FrameLayout parent) {
        if (arr == null || arr.length() == 0) return;

        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                IMGText text = IMGText.fromJson(obj.getJSONObject("text"));
                float x = (float) obj.optDouble("x", 0);
                float y = (float) obj.optDouble("y", 0);
                float stickerScale = (float) obj.optDouble("scale", 1.0);
                float rotation = (float) obj.optDouble("rotation", 0.0);

                IMGStickerTextView tv = new IMGStickerTextView(context);
                tv.setText(text);
                tv.setRotation(rotation);

                android.widget.FrameLayout.LayoutParams lp =
                        new android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
                parent.addView(tv, lp);
                tv.registerCallback((IMGSticker.Callback) parent);
                addSticker(tv);

                // 布局完成后立即恢复位置，无延迟
                final float vx = x;
                final float vy = y;
                final float sc = stickerScale;
                tv.getViewTreeObserver().addOnGlobalLayoutListener(
                        new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        tv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        tv.setScale(sc);
                        tv.setX(vx);
                        tv.setY(vy);
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "deserializeStickers failed", e);
            }
        }
    }

    /**
     * 临时移除所有贴纸（保存干净图片用）
     */
    public List<IMGSticker> clearStickersForSave() {
        List<IMGSticker> backup = new ArrayList<>(mBackStickers);
        mBackStickers.clear();
        if (mForeSticker != null) {
            backup.add(mForeSticker);
            mForeSticker = null;
        }
        return backup;
    }

    /**
     * 恢复贴纸
     */
    public void restoreStickers(List<IMGSticker> stickers) {
        if (stickers == null) return;
        for (IMGSticker s : stickers) {
            if (!mBackStickers.contains(s)) {
                mBackStickers.add(s);
            }
        }
    }

    private boolean mEraseSessionActive = false;
    private boolean mEraseStrokeStarted = false;
    private List<IMGPath> mEraseRemoved = new ArrayList<>();
    private List<Integer> mErasePositions = new ArrayList<>();
    private List<IMGPath> mErasePreState = null;

    public void eraseBegin() {
        if (mEraseSessionActive) return;
        mEraseSessionActive = true;
        mEraseStrokeStarted = false;
        mEraseRemoved.clear();
        mErasePositions.clear();
        mErasePreState = null;
    }

    public void eraseEnd() {
        if (mEraseSessionActive && mEraseStrokeStarted && !mEraseRemoved.isEmpty()) {
            int idx = mHistoryIndex;
            // 更新 mHistory 当前条目为擦除后的状态
            if (idx >= 0 && idx < mHistory.size()) {
                mHistory.set(idx, new ArrayList<>(mDoodles));
            }
            // EraseOp 描述从 idx-1 到 idx 的变换，存到 mUndoOps[idx-1]
            int opIdx = idx - 1;
            if (opIdx >= 0 && opIdx < mUndoOps.size()) {
                mUndoOps.set(opIdx, new UndoOp(
                        new ArrayList<>(mEraseRemoved),
                        new ArrayList<>(mErasePositions),
                        mErasePreState,
                        new ArrayList<>(mDoodles)  // 擦除后完整状态
                ));
            }
        } else if (mEraseSessionActive && mEraseStrokeStarted) {
            // 擦除笔画没有实际擦除任何东西：回退 pushHistory() 创建的幽灵条目
            if (mHistory.size() > 1 && mHistoryIndex == mHistory.size() - 1) {
                mHistory.remove(mHistory.size() - 1);
                mUndoOps.remove(mUndoOps.size() - 1);
                mHistoryIndex = mHistory.size() - 1;
            }
        }
        mEraseSessionActive = false;
        mEraseStrokeStarted = false;
        mErasePreState = null;
        invalidateDoodlesCache();
    }

    /**
     * 临时隐藏涂鸦（生成干净底图时用）
     */
    public void clearDoodlesTemporarily() {
        mDoodlesBackup = new ArrayList<>(mDoodles);
        mDoodles = new ArrayList<>();
        invalidateDoodlesCache();
    }

    /**
     * 恢复涂鸦（生成干净底图后调用）
     */
    public void restoreDoodles() {
        if (mDoodlesBackup != null) {
            mDoodles = mDoodlesBackup;
            mDoodlesBackup = null;
            invalidateDoodlesCache();
        }
    }

    /**
     * 擦除涂鸦中靠近 (x, y) 的路径部分
     * @param x 屏幕坐标X（View空间）
     * @param y 屏幕坐标Y（View空间）
     * @param radius 擦除半径（屏幕像素）
     * @param scrollX 当前滚动偏移X
     * @param scrollY 当前滚动偏移Y
     */
    public void eraseDoodleAt(float x, float y, float radius, float scrollX, float scrollY) {
        if (mDoodles.isEmpty()) return;

        // 第一次触摸时保存历史（每个擦除笔画一个独立的撤销步骤）
        if (!mEraseStrokeStarted) {
            mEraseStrokeStarted = true;
            pushHistory();
            mErasePreState = new ArrayList<>(mDoodles);
            mEraseRemoved.clear();
            mErasePositions.clear();
        }

        // 应用与 addPath 相同的坐标变换（屏幕坐标 → 路径存储坐标）
        float[] pt = {x, y};
        M.setTranslate(scrollX, scrollY);
        M.postRotate(-getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
        M.postTranslate(-mFrame.left, -mFrame.top);
        float invScale = 1f / getScale();
        M.postScale(invScale, invScale);
        M.mapPoints(pt);

        float imgX = pt[0];
        float imgY = pt[1];
        float imgR = radius * invScale;

        List<IMGPath> newDoodles = new ArrayList<>();
        for (int idx = 0; idx < mDoodles.size(); idx++) {
            IMGPath path = mDoodles.get(idx);
            List<IMGPath> parts = path.eraseNear(imgX, imgY, imgR);
            if (parts.size() == 1 && parts.get(0) == path) {
                newDoodles.add(path);
            } else {
                // 记录被擦除路径
                mEraseRemoved.add(path);
                int snapIdx = mErasePreState.indexOf(path);
                mErasePositions.add(snapIdx >= 0 ? snapIdx : idx);
                newDoodles.addAll(parts);
            }
        }
        mDoodles = newDoodles;
        invalidateDoodlesCache();
    }

    public RectF getClipFrame() {
        return mClipFrame;
    }

    /**
     * 裁剪区域旋转回原始角度后形成新的裁剪区域，旋转中心发生变化，
     * 因此需要将视图窗口平移到新的旋转中心位置。
     */
    public IMGHoming clip(float scrollX, float scrollY) {
        RectF frame = mClipWin.getOffsetFrame(scrollX, scrollY);

        M.setRotate(-getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
        M.mapRect(mClipFrame, frame);

        return new IMGHoming(
                scrollX + (mClipFrame.centerX() - frame.centerX()),
                scrollY + (mClipFrame.centerY() - frame.centerY()),
                getScale(), getRotate()
        );
    }

    public void toBackupClip() {
        M.setScale(getScale(), getScale());
        M.postTranslate(mFrame.left, mFrame.top);
        M.mapRect(mClipFrame, mBackupClipFrame);
        setTargetRotate(mBackupClipRotate);
        isRequestToBaseFitting = true;
    }

    public void resetClip() {
        // TODO 就近旋转
        setTargetRotate(getRotate() - getRotate() % 360);
        mClipFrame.set(mFrame);
        mClipWin.reset(mClipFrame, getTargetRotate());
    }

    private void onImageChanged() {
        isInitialHoming = false;
        onWindowChanged(mWindow.width(), mWindow.height());

        if (mMode == IMGMode.CLIP) {
            mClipWin.reset(mClipFrame, getTargetRotate());
        }
    }

    public RectF getFrame() {
        return mFrame;
    }

    public boolean onClipHoming() {
        return mClipWin.homing();
    }

    public IMGHoming getStartHoming(float scrollX, float scrollY) {
        return new IMGHoming(scrollX, scrollY, getScale(), getRotate());
    }

    public IMGHoming getEndHoming(float scrollX, float scrollY) {
        IMGHoming homing = new IMGHoming(scrollX, scrollY, getScale(), getTargetRotate());

        if (mMode == IMGMode.CLIP) {
            RectF frame = new RectF(mClipWin.getTargetFrame());
            frame.offset(scrollX, scrollY);
            if (mClipWin.isResetting()) {

                RectF clipFrame = new RectF();
                M.setRotate(getTargetRotate(), mClipFrame.centerX(), mClipFrame.centerY());
                M.mapRect(clipFrame, mClipFrame);

                homing.rConcat(IMGUtils.fill(frame, clipFrame));
            } else {
                RectF cFrame = new RectF();

                // cFrame要是一个暂时clipFrame
                if (mClipWin.isHoming()) {
//
//                    M.mapRect(cFrame, mClipFrame);

//                    mClipWin
                    // TODO 偏移中心

                    M.setRotate(getTargetRotate() - getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
                    M.mapRect(cFrame, mClipWin.getOffsetFrame(scrollX, scrollY));

                    homing.rConcat(IMGUtils.fitHoming(frame, cFrame, mClipFrame.centerX(), mClipFrame.centerY()));


                } else {
                    M.setRotate(getTargetRotate(), mClipFrame.centerX(), mClipFrame.centerY());
                    M.mapRect(cFrame, mFrame);
                    homing.rConcat(IMGUtils.fillHoming(frame, cFrame, mClipFrame.centerX(), mClipFrame.centerY()));
                }

            }
        } else {
            RectF clipFrame = new RectF();
            M.setRotate(getTargetRotate(), mClipFrame.centerX(), mClipFrame.centerY());
            M.mapRect(clipFrame, mClipFrame);

            RectF win = new RectF(mWindow);
            win.offset(scrollX, scrollY);
            homing.rConcat(IMGUtils.fitHoming(win, clipFrame, isRequestToBaseFitting));
            isRequestToBaseFitting = false;
        }

        return homing;
    }

    public <S extends IMGSticker> void addSticker(S sticker) {
        if (sticker != null) {
            moveToForeground(sticker);
        }
    }

    public void addPath(IMGPath path, float sx, float sy) {
        if (path == null) return;

        float scale = 1f / getScale();

        M.setTranslate(sx, sy);
        M.postRotate(-getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
        M.postTranslate(-mFrame.left, -mFrame.top);
        M.postScale(scale, scale);
        path.transform(M);

        // 宽度不缩放，保持基础宽度
        // path.setWidth(path.getWidth() * scale);

        switch (path.getMode()) {
            case DOODLE:
                pushHistory();
                mDoodles.add(path);
                // 更新历史条目为添加后的状态（确保序列化时历史与当前一致）
                mHistory.set(mHistoryIndex, new ArrayList<>(mDoodles));
                invalidateDoodlesCache();
                break;
        }
    }

    private void moveToForeground(IMGSticker sticker) {
        if (sticker == null) return;

        moveToBackground(mForeSticker);

        if (sticker.isShowing()) {
            mForeSticker = sticker;
            // 从BackStickers中移除
            mBackStickers.remove(sticker);
        } else sticker.show();
    }

    private void moveToBackground(IMGSticker sticker) {
        if (sticker == null) return;

        if (!sticker.isShowing()) {
            // 加入BackStickers中
            if (!mBackStickers.contains(sticker)) {
                mBackStickers.add(sticker);
            }

            if (mForeSticker == sticker) {
                mForeSticker = null;
            }
        } else sticker.dismiss();
    }

    public void stickAll() {
        moveToBackground(mForeSticker);
    }

    public void onDismiss(IMGSticker sticker) {
        moveToBackground(sticker);
    }

    public void onShowing(IMGSticker sticker) {
        if (mForeSticker != sticker) {
            moveToForeground(sticker);
        }
    }

    public void onRemoveSticker(IMGSticker sticker) {
        if (mForeSticker == sticker) {
            mForeSticker = null;
        } else {
            mBackStickers.remove(sticker);
        }
    }

    public void onWindowChanged(float width, float height) {
        if (width == 0 || height == 0) {
            return;
        }

        mWindow.set(0, 0, width, height);

        // 即使 isInitialHoming 已被设置为 false（例如通过 onImageChanged），
        // 只要 mFrame 还是空的（未初始化），就必须执行初始化。
        // 这防止 setMode() 在 onLayout() 之前触发的动画使用空 frame。
        if (!isInitialHoming || mFrame.isEmpty()) {
            onInitialHoming(width, height);
        } else {

            // Pivot to fit window.
            M.setTranslate(mWindow.centerX() - mClipFrame.centerX(), mWindow.centerY() - mClipFrame.centerY());
            M.mapRect(mFrame);
            M.mapRect(mClipFrame);
        }

        mClipWin.setClipWinSize(width, height);
    }

    private void onInitialHoming(float width, float height) {
        mFrame.set(0, 0, mImage.getWidth(), mImage.getHeight());
        mClipFrame.set(mFrame);
        mClipWin.setClipWinSize(width, height);

        if (mClipFrame.isEmpty()) {
            return;
        }

        toBaseHoming();

        isInitialHoming = true;
        onInitialHomingDone();
    }

    private void toBaseHoming() {
        if (mClipFrame.isEmpty()) {
            // Bitmap invalidate.
            return;
        }

        float scale = Math.min(
                mWindow.width() / mClipFrame.width(),
                mWindow.height() / mClipFrame.height()
        );

        // Scale to fit window.
        M.setScale(scale, scale, mClipFrame.centerX(), mClipFrame.centerY());
        M.postTranslate(mWindow.centerX() - mClipFrame.centerX(), mWindow.centerY() - mClipFrame.centerY());
        M.mapRect(mFrame);
        M.mapRect(mClipFrame);
    }

    private void onInitialHomingDone() {
        if (mMode == IMGMode.CLIP) {
            mClipWin.reset(mClipFrame, getTargetRotate());
        }
    }

    public void onDrawImage(Canvas canvas) {

        // 裁剪区域
        canvas.clipRect(mClipWin.isClipping() ? mFrame : mClipFrame);

        // 绘制图片
        canvas.drawBitmap(mImage, null, mFrame, null);

        if (DEBUG) {
            // Clip 区域
            mPaint.setColor(Color.RED);
            mPaint.setStrokeWidth(6);
            canvas.drawRect(mFrame, mPaint);
            canvas.drawRect(mClipFrame, mPaint);
        }
    }

    public void onDrawDoodles(Canvas canvas) {
        if (isDoodleEmpty()) return;
        synchronized (mLock) {
            canvas.save();
            float scale = getScale();
            canvas.translate(mFrame.left, mFrame.top);
            canvas.scale(scale, scale);

            int imgW = mImage.getWidth();
            int imgH = mImage.getHeight();

            if (imgW <= 0 || imgH <= 0) {
                canvas.restore();
                return;
            }

            // 按需重建缓存
            if (mDoodlesCacheDirty || mDoodlesCache == null
                    || mDoodlesCache.getWidth() != imgW || mDoodlesCache.getHeight() != imgH) {
                if (mDoodlesCache != null) {
                    mDoodlesCache.recycle();
                }
                mDoodlesCache = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888);
                mDoodlesCacheCanvas = new Canvas(mDoodlesCache);
                mDoodlesCacheDirty = false;
            } else {
                // 缓存有效，直接绘制
                canvas.drawBitmap(mDoodlesCache, 0, 0, null);
                canvas.restore();
                return;
            }

            // 重新渲染缓存
            mDoodlesCache.eraseColor(Color.TRANSPARENT);
            for (IMGPath path : mDoodles) {
                path.onDrawDoodle(mDoodlesCacheCanvas, mPaint);
            }

            canvas.drawBitmap(mDoodlesCache, 0, 0, null);
            canvas.restore();
        }
    }

    public void onDrawStickerClip(Canvas canvas) {
        M.setRotate(getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
        M.mapRect(mTempClipFrame, mClipWin.isClipping() ? mFrame : mClipFrame);
        canvas.clipRect(mTempClipFrame);
    }

    public void onDrawStickers(Canvas canvas) {
        if (mBackStickers.isEmpty()) return;
        canvas.save();
        for (IMGSticker sticker : mBackStickers) {
            if (!sticker.isShowing()) {
                float tPivotX = sticker.getX() + sticker.getPivotX();
                float tPivotY = sticker.getY() + sticker.getPivotY();

                canvas.save();
                M.setTranslate(sticker.getX(), sticker.getY());
                M.postScale(sticker.getScale(), sticker.getScale(), tPivotX, tPivotY);
                M.postRotate(sticker.getRotation(), tPivotX, tPivotY);

                canvas.concat(M);
                sticker.onSticker(canvas);
                canvas.restore();
            }
        }
        canvas.restore();
    }

    public void onDrawShade(Canvas canvas) {
        if (mMode == IMGMode.CLIP && isSteady) {
            mShade.reset();
            mShade.addRect(mFrame.left - 2, mFrame.top - 2, mFrame.right + 2, mFrame.bottom + 2, Path.Direction.CW);
            mShade.addRect(mClipFrame, Path.Direction.CCW);
            canvas.drawPath(mShade, mShadePaint);
        }
    }

    public void onDrawClip(Canvas canvas, float scrollX, float scrollY) {
        if (mMode == IMGMode.CLIP) {
            mClipWin.onDraw(canvas);
        }
    }

    public void onTouchDown(float x, float y) {
        isSteady = false;
        moveToBackground(mForeSticker);
        if (mMode == IMGMode.CLIP) {
            mAnchor = mClipWin.getAnchor(x, y);
        }
    }

    public void onTouchUp(float scrollX, float scrollY) {
        if (mAnchor != null) {
            mAnchor = null;
        }
    }

    public void onSteady(float scrollX, float scrollY) {
        isSteady = true;
        onClipHoming();
        mClipWin.setShowShade(true);
    }

    public void onScaleBegin() {

    }

    public IMGHoming onScroll(float scrollX, float scrollY, float dx, float dy) {
        if (mMode == IMGMode.CLIP) {
            mClipWin.setShowShade(false);
            if (mAnchor != null) {
                mClipWin.onScroll(mAnchor, dx, dy);

                RectF clipFrame = new RectF();
                M.setRotate(getRotate(), mClipFrame.centerX(), mClipFrame.centerY());
                M.mapRect(clipFrame, mFrame);

                RectF frame = mClipWin.getOffsetFrame(scrollX, scrollY);
                IMGHoming homing = new IMGHoming(scrollX, scrollY, getScale(), getTargetRotate());
                homing.rConcat(IMGUtils.fillHoming(frame, clipFrame, mClipFrame.centerX(), mClipFrame.centerY()));
                return homing;
            }
        }
        return null;
    }

    public float getTargetRotate() {
        return mTargetRotate;
    }

    public void setTargetRotate(float targetRotate) {
        this.mTargetRotate = targetRotate;
    }

    /**
     * 在当前基础上旋转
     */
    public void rotate(int rotate) {
        mTargetRotate = Math.round((mRotate + rotate) / 90f) * 90;
        mClipWin.reset(mClipFrame, getTargetRotate());
    }

    public float getRotate() {
        return mRotate;
    }

    public void setRotate(float rotate) {
        mRotate = rotate;
    }

    public float getScale() {
        return 1f * mFrame.width() / mImage.getWidth();
    }

    public void setScale(float scale) {
        setScale(scale, mClipFrame.centerX(), mClipFrame.centerY());
    }

    public void setScale(float scale, float focusX, float focusY) {
        onScale(scale / getScale(), focusX, focusY);
    }

    public void onScale(float factor, float focusX, float focusY) {

        if (factor == 1f) return;

        if (Math.max(mClipFrame.width(), mClipFrame.height()) >= MAX_SIZE
                || Math.min(mClipFrame.width(), mClipFrame.height()) <= MIN_SIZE) {
            factor += (1 - factor) / 2;
        }

        M.setScale(factor, factor, focusX, focusY);
        M.mapRect(mFrame);
        M.mapRect(mClipFrame);

        // 修正clip 窗口
        if (!mFrame.contains(mClipFrame)) {
            // TODO
//            mClipFrame.intersect(mFrame);
        }

        for (IMGSticker sticker : mBackStickers) {
            M.mapRect(sticker.getFrame());
            float tPivotX = sticker.getX() + sticker.getPivotX();
            float tPivotY = sticker.getY() + sticker.getPivotY();
            sticker.addScale(factor);
            sticker.setX(sticker.getX() + sticker.getFrame().centerX() - tPivotX);
            sticker.setY(sticker.getY() + sticker.getFrame().centerY() - tPivotY);
        }
    }

    public void onScaleEnd() {

    }

    public void onHomingStart(boolean isRotate) {
        isAnimCanceled = false;
        isDrawClip = true;
    }

    public void onHoming(float fraction) {
        mClipWin.homing(fraction);
    }

    public boolean onHomingEnd(float scrollX, float scrollY, boolean isRotate) {
        isDrawClip = true;
        if (mMode == IMGMode.CLIP) {
            // 开启裁剪模式

            boolean clip = !isAnimCanceled;

            mClipWin.setHoming(false);
            mClipWin.setClipping(true);
            mClipWin.setResetting(false);

            return clip;
        } else {
            if (isFreezing && !isAnimCanceled) {
                setFreezing(false);
            }
        }
        return false;
    }

    public boolean isFreezing() {
        return isFreezing;
    }

    private void setFreezing(boolean freezing) {
        if (freezing != isFreezing) {
            rotateStickers(freezing ? -getRotate() : getTargetRotate());
            isFreezing = freezing;
        }
    }

    public void onHomingCancel(boolean isRotate) {
        isAnimCanceled = true;
        Log.d(TAG, "Homing cancel");
    }

    public void release() {
        if (mDoodlesCache != null) {
            mDoodlesCache.recycle();
            mDoodlesCache = null;
            mDoodlesCacheCanvas = null;
        }
        if (mImage != null && !mImage.isRecycled()) {
            mImage.recycle();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (DEFAULT_IMAGE != null) {
            DEFAULT_IMAGE.recycle();
        }
    }
}
