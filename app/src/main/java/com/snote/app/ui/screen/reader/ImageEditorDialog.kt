package com.snote.app.ui.screen.reader

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

// ==================== 数据模型 ====================

/** 笔画数据 — 坐标全部基于「图片原始像素坐标」存储 */
private data class StrokeData(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float  // 图片像素单位
)

private enum class TopMode { MARK, CROP }
private enum class MarkSubMode { PEN, ERASER, MOVE }

private enum class CropHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT
}

// ==================== 主组件 ====================

@Composable
fun ImageEditorDialog(
    imagePath: String,
    themeColor: Color,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ---- 原始图片 ----
    val originalBitmap = remember(imagePath) {
        val baseFile = File("$imagePath.base")
        val loadPath = if (baseFile.exists()) baseFile.absolutePath else imagePath
        try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeFile(loadPath, opts)
        } catch (e: OutOfMemoryError) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            try { BitmapFactory.decodeFile(loadPath, opts) } catch (_: Exception) { null }
        } catch (_: Exception) { null }
    }
    if (originalBitmap == null) { onDismiss(); return }

    val imgWidth = originalBitmap.width.toFloat()
    val imgHeight = originalBitmap.height.toFloat()

    // 图片原始尺寸转 Dp（用于 Compose 布局）
    val imgWidthDp: Dp = with(density) { imgWidth.toDp() }
    val imgHeightDp: Dp = with(density) { imgHeight.toDp() }

    // ---- 编辑模式 ----
    var topMode by remember { mutableStateOf(TopMode.MARK) }
    var markSubMode by remember { mutableStateOf(MarkSubMode.PEN) }
    var showPenOptions by remember { mutableStateOf(true) }
    var showEraserOptions by remember { mutableStateOf(false) }

    // ---- 笔画状态（基于图片原始坐标）----
    var strokes by remember { mutableStateOf(emptyList<StrokeData>()) }
    var undoStack by remember { mutableStateOf(emptyList<List<StrokeData>>()) }
    var redoStack by remember { mutableStateOf(emptyList<List<StrokeData>>()) }
    val canUndo = undoStack.isNotEmpty()
    val canRedo = redoStack.isNotEmpty()

    // 当前正在绘制的笔画（图片原始坐标）
    val currentPoints = remember { mutableStateListOf<Offset>() }

    // ---- 画笔设置 ----
    var currentPenColor by remember { mutableStateOf(Color(0xFFE53935)) }
    var currentStrokeWidth by remember { mutableStateOf(6f) }
    var eraserSize by remember { mutableStateOf(20f) }       // 图片像素单位
    var eraserScreenPos by remember { mutableStateOf<Offset?>(null) }

    // ---- 裁切框（基于图片原始坐标）----
    var cropRect by remember { mutableStateOf<Rect?>(null) }

    // ---- 视图变换参数 ----
    // 变换顺序（图片坐标 → 屏幕坐标）：
    //   1. 以图片中心为原点旋转 rotationAngle 度
    //   2. 缩放 viewScale 倍
    //   3. 平移 (viewOffsetX, viewOffsetY)
    var viewScale by remember { mutableFloatStateOf(1f) }
    var viewOffsetX by remember { mutableFloatStateOf(0f) }
    var viewOffsetY by remember { mutableFloatStateOf(0f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    // ---- 容器尺寸 ----
    var containerSize by remember { mutableStateOf(Size.Zero) }

    // ---- 初始适配：图片居中完整显示 ----
    LaunchedEffect(containerSize) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return@LaunchedEffect
        // 考虑旋转后的包围盒
        val rad = Math.toRadians(rotationAngle.toDouble())
        val rotW = imgWidth * cos(rad).toFloat() + imgHeight * sin(rad).toFloat()
        val rotH = imgWidth * sin(rad).toFloat() + imgHeight * cos(rad).toFloat()
        val scale = minOf(containerSize.width / rotW, containerSize.height / rotH)
        viewScale = scale
        viewOffsetX = containerSize.width / 2f
        viewOffsetY = containerSize.height / 2f
    }

    // ---- 颜色列表 ----
    val penColors = remember {
        listOf(
            Color(0xFFE53935), Color.Black, Color(0xFFFF9800), Color(0xFFFDD835),
            Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color.White
        )
    }

    // ==================== 坐标转换 ====================

    /** 图片原始坐标 → 屏幕显示坐标 */
    fun imageToScreen(imgPos: Offset): Offset {
        val cx = imgWidth / 2f
        val cy = imgHeight / 2f
        // 1. 平移到原点
        val x1 = imgPos.x - cx
        val y1 = imgPos.y - cy
        // 2. 旋转
        val rad = Math.toRadians(rotationAngle.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val x2 = x1 * cosR - y1 * sinR
        val y2 = x1 * sinR + y1 * cosR
        // 3. 缩放
        val x3 = x2 * viewScale
        val y3 = y2 * viewScale
        // 4. 平移到屏幕
        return Offset(x3 + viewOffsetX, y3 + viewOffsetY)
    }

    /** 屏幕显示坐标 → 图片原始坐标 */
    fun screenToImage(screenPos: Offset): Offset {
        val cx = imgWidth / 2f
        val cy = imgHeight / 2f
        // 逆平移
        val x3 = screenPos.x - viewOffsetX
        val y3 = screenPos.y - viewOffsetY
        // 逆缩放
        val x2 = x3 / viewScale
        val y2 = y3 / viewScale
        // 逆旋转（转置矩阵）
        val rad = Math.toRadians(rotationAngle.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val x1 = x2 * cosR + y2 * sinR
        val y1 = -x2 * sinR + y2 * cosR
        // 平移回去
        return Offset(x1 + cx, y1 + cy)
    }

    /** 距离标量转换：屏幕距离 → 图片坐标距离 */
    fun screenDistToImage(dist: Float): Float = dist / viewScale

    // ---- 加载历史笔画 ----
    LaunchedEffect(imagePath) {
        val metaFile = File("$imagePath.strokes")
        if (metaFile.exists()) {
            try {
                val json = JSONObject(metaFile.readText())
                val loadedUndo = if (json.has("undoStack")) {
                    val usArr = json.getJSONArray("undoStack")
                    val us = mutableListOf<List<StrokeData>>()
                    for (i in 0 until usArr.length()) {
                        us.add(parseStrokesArray(usArr.getJSONArray(i)))
                    }
                    us
                } else null

                val loaded = parseStrokesArray(json.getJSONArray("strokes"))
                if (loaded.isNotEmpty()) {
                    strokes = loaded
                    undoStack = loadedUndo ?: listOf(emptyList())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已恢复 ${loaded.size} 笔编辑记录", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) { metaFile.delete() }
        }
    }

    // ---- 撤销/重做 ----
    fun pushUndo() {
        undoStack = undoStack + listOf(strokes)
        redoStack = emptyList()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack = redoStack + listOf(strokes)
            strokes = undoStack.last()
            undoStack = undoStack.dropLast(1)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack = undoStack + listOf(strokes)
            strokes = redoStack.last()
            redoStack = redoStack.dropLast(1)
        }
    }

    // ==================== UI 子组件 ====================

    @Composable
    fun ColorPicker(colors: List<Color>, selected: Color, onSelect: (Color) -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { c ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(
                            if (c == selected) Modifier.border(3.dp, Color.White, CircleShape)
                            else Modifier.border(1.dp, Color.Gray, CircleShape)
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onSelect(c) }
                )
            }
        }
    }

    // ==================== 主布局 ====================

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BackHandler(enabled = true) { onDismiss() }

        // ---- 中央画布区域（手势检测层，固定屏幕坐标）----
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 180.dp)
                .onGloballyPositioned { coords ->
                    containerSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                }
                .pointerInput(topMode, markSubMode) {
                    when (topMode) {
                        TopMode.MARK -> {
                            when (markSubMode) {
                                MarkSubMode.ERASER -> {
                                    // 橡皮擦：单指擦除，双指禁用
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        down.consume()
                                        pushUndo()
                                        eraserScreenPos = down.position
                                        val imgPos = screenToImage(down.position)
                                        strokes = eraseStrokes(imgPos, strokes, eraserSize)

                                        var dragging = true
                                        while (dragging) {
                                            val event = awaitPointerEvent()
                                            val ch = event.changes.firstOrNull { it.pressed }
                                            if (ch == null) {
                                                dragging = false
                                                eraserScreenPos = null
                                            } else {
                                                eraserScreenPos = ch.position
                                                val imgPos2 = screenToImage(ch.position)
                                                strokes = eraseStrokes(imgPos2, strokes, eraserSize)
                                                ch.consume()
                                            }
                                        }
                                    }
                                }
                                MarkSubMode.PEN -> {
                                    // 画笔：单指绘制，双指禁用
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        down.consume()
                                        currentPoints.clear()
                                        currentPoints.add(screenToImage(down.position))

                                        var dragging = true
                                        while (dragging) {
                                            val event = awaitPointerEvent()
                                            val ch = event.changes.firstOrNull { it.pressed }
                                            if (ch == null) {
                                                dragging = false
                                            } else {
                                                currentPoints.add(screenToImage(ch.position))
                                                ch.consume()
                                            }
                                        }

                                        if (currentPoints.size >= 2) {
                                            pushUndo()
                                            strokes = strokes + StrokeData(
                                                currentPoints.toList(),
                                                currentPenColor,
                                                currentStrokeWidth
                                            )
                                        } else if (currentPoints.size == 1) {
                                            pushUndo()
                                            val p = currentPoints[0]
                                            strokes = strokes + StrokeData(
                                                listOf(p, p),
                                                currentPenColor,
                                                currentStrokeWidth
                                            )
                                        }
                                        currentPoints.clear()
                                    }
                                }
                                MarkSubMode.MOVE -> {
                                    // 移动模式：单指平移，双指缩放+平移
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val oldScale = viewScale
                                        viewScale = (viewScale * zoom).coerceIn(0.3f, 8f)
                                        val ratio = viewScale / oldScale
                                        viewOffsetX = centroid.x - (centroid.x - viewOffsetX) * ratio
                                        viewOffsetY = centroid.y - (centroid.y - viewOffsetY) * ratio
                                        viewOffsetX += pan.x
                                        viewOffsetY += pan.y
                                    }
                                }
                            }
                        }
                        TopMode.CROP -> {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val cr = cropRect ?: return@awaitEachGesture

                                val touchImg = screenToImage(down.position)
                                val thresholdImg = screenDistToImage(48f)
                                val handle = detectCropHandle(touchImg, cr, thresholdImg)

                                if (handle != CropHandle.NONE) {
                                    // 触碰在裁切框边缘/角落 → 调整裁切框大小
                                    val imageBounds = Rect(0f, 0f, imgWidth, imgHeight)
                                    var currentCr = cr
                                    do {
                                        val event = awaitPointerEvent()
                                        val ch = event.changes.firstOrNull { it.pressed } ?: break
                                        val deltaScreen = ch.position - ch.previousPosition
                                        val deltaImg = Offset(
                                            deltaScreen.x / viewScale,
                                            deltaScreen.y / viewScale
                                        )
                                        currentCr = adjustCropRect(currentCr, handle, deltaImg, imageBounds, 8f)
                                        cropRect = currentCr
                                        ch.consume()
                                    } while (true)
                                } else {
                                    // 触碰在裁切框外 → 手动处理平移和缩放
                                    var lastPos = down.position
                                    var lastDist = 0f
                                    val pointers = mutableListOf(down)

                                    var dragging = true
                                    while (dragging) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }

                                        if (pressed.isEmpty()) {
                                            dragging = false
                                        } else if (pressed.size == 1) {
                                            // 单指：平移
                                            val ch = pressed[0]
                                            val pan = ch.position - lastPos
                                            viewOffsetX += pan.x
                                            viewOffsetY += pan.y
                                            lastPos = ch.position
                                            ch.consume()
                                        } else if (pressed.size >= 2) {
                                            // 双指：缩放 + 平移
                                            val p1 = pressed[0]
                                            val p2 = pressed[1]
                                            val centroid = Offset(
                                                (p1.position.x + p2.position.x) / 2f,
                                                (p1.position.y + p2.position.y) / 2f
                                            )
                                            val dist = (p1.position - p2.position).getDistance()

                                            if (lastDist > 0f) {
                                                val zoom = dist / lastDist
                                                val oldScale = viewScale
                                                viewScale = (viewScale * zoom).coerceIn(0.3f, 8f)
                                                val ratio = viewScale / oldScale
                                                viewOffsetX = centroid.x - (centroid.x - viewOffsetX) * ratio
                                                viewOffsetY = centroid.y - (centroid.y - viewOffsetY) * ratio
                                            }

                                            val pan = centroid - lastPos
                                            viewOffsetX += pan.x
                                            viewOffsetY += pan.y

                                            lastPos = centroid
                                            lastDist = dist
                                            p1.consume()
                                            p2.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // ---- 变换层：图片 + 笔画 + 裁切框 统一旋转、缩放、平移 ----
            Box(
                Modifier
                    .graphicsLayer {
                        // 以中心为锚点
                        transformOrigin = TransformOrigin.Center
                        scaleX = viewScale
                        scaleY = viewScale
                        translationX = viewOffsetX - size.width / 2f
                        translationY = viewOffsetY - size.height / 2f
                        rotationZ = rotationAngle
                    }
                    .size(imgWidthDp, imgHeightDp)
            ) {
                // 1. 图片层
                Image(
                    bitmap = originalBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // 2. 笔画层（与图片同尺寸，坐标 1:1 对应图片像素）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    strokes.forEach { s ->
                        if (s.points.size >= 2) {
                            val p = Path().apply {
                                moveTo(s.points[0].x, s.points[0].y)
                                for (j in 1 until s.points.size) {
                                    lineTo(s.points[j].x, s.points[j].y)
                                }
                            }
                            drawPath(
                                p, s.color,
                                style = Stroke(
                                    width = s.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                    // 当前正在绘制的笔画
                    if (currentPoints.size >= 2) {
                        val p = Path().apply {
                            moveTo(currentPoints[0].x, currentPoints[0].y)
                            for (j in 1 until currentPoints.size) {
                                lineTo(currentPoints[j].x, currentPoints[j].y)
                            }
                        }
                        drawPath(
                            p, currentPenColor,
                            style = Stroke(
                                width = currentStrokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // 3. 裁切框层
                if (topMode == TopMode.CROP && cropRect != null) {
                    val cr = cropRect!!
                    // 将屏幕像素单位转换为图片像素单位，确保不同分辨率图片的裁切框样式一致
                    val invScale = 1f / viewScale
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // 四周半透明遮罩（分四块绘制，避免使用 BlendMode.Clear）
                        val fullW = size.width
                        val fullH = size.height
                        val maskColor = Color.Black.copy(alpha = 0.55f)
                        // 上
                        drawRect(maskColor, Offset(0f, 0f), Size(fullW, cr.top))
                        // 下
                        drawRect(maskColor, Offset(0f, cr.bottom), Size(fullW, fullH - cr.bottom))
                        // 左
                        drawRect(maskColor, Offset(0f, cr.top), Size(cr.left, cr.height))
                        // 右
                        drawRect(maskColor, Offset(cr.right, cr.top), Size(fullW - cr.right, cr.height))

                        // 裁切框边框（加粗）
                        drawRect(
                            Color.White,
                            cr.topLeft,
                            cr.size,
                            style = Stroke(width = 5f * invScale)
                        )
                        drawRect(
                            themeColor,
                            cr.topLeft,
                            cr.size,
                            style = Stroke(width = 3f * invScale)
                        )

                        // 九宫格线
                        val thirdW = cr.width / 3f
                        val thirdH = cr.height / 3f
                        for (k in 1..2) {
                            drawLine(
                                Color.White.copy(alpha = 0.7f),
                                Offset(cr.left + thirdW * k, cr.top),
                                Offset(cr.left + thirdW * k, cr.bottom),
                                1.5f * invScale
                            )
                            drawLine(
                                Color.White.copy(alpha = 0.7f),
                                Offset(cr.left, cr.top + thirdH * k),
                                Offset(cr.right, cr.top + thirdH * k),
                                1.5f * invScale
                            )
                        }

                        // 四边中点拖拽指示圆点
                        val midIndicatorR = 6f * invScale
                        val midColor = Color.White.copy(alpha = 0.8f)
                        drawCircle(midColor, midIndicatorR, Offset(cr.center.x, cr.top))
                        drawCircle(midColor, midIndicatorR, Offset(cr.center.x, cr.bottom))
                        drawCircle(midColor, midIndicatorR, Offset(cr.left, cr.center.y))
                        drawCircle(midColor, midIndicatorR, Offset(cr.right, cr.center.y))

                        // 四角把手圆点（加大 + 外圈描边，更醒目）
                        val handleR = 14f * invScale
                        val handleOuterR = 18f * invScale
                        // 外圈深色描边
                        drawCircle(Color.Black.copy(alpha = 0.5f), handleOuterR, cr.topLeft)
                        drawCircle(Color.Black.copy(alpha = 0.5f), handleOuterR, Offset(cr.right, cr.top))
                        drawCircle(Color.Black.copy(alpha = 0.5f), handleOuterR, cr.bottomRight)
                        drawCircle(Color.Black.copy(alpha = 0.5f), handleOuterR, Offset(cr.left, cr.bottom))
                        // 内圈白色实心
                        drawCircle(Color.White, handleR, cr.topLeft)
                        drawCircle(Color.White, handleR, Offset(cr.right, cr.top))
                        drawCircle(Color.White, handleR, cr.bottomRight)
                        drawCircle(Color.White, handleR, Offset(cr.left, cr.bottom))
                    }
                }
            }

            // 橡皮擦预览（屏幕坐标空间，不参与变换）
            if (topMode == TopMode.MARK && markSubMode == MarkSubMode.ERASER && eraserScreenPos != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        Color.White.copy(alpha = 0.45f),
                        eraserSize * viewScale,
                        eraserScreenPos!!,
                        style = Stroke(width = 2f)
                    )
                }
            }
        }

        // ---- 顶部工具栏 ----
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("取消", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { undo() }, enabled = canUndo) {
                        Icon(
                            Icons.Rounded.Undo, "撤销",
                            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { redo() }, enabled = canRedo) {
                        Icon(
                            Icons.Rounded.Redo, "重做",
                            tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text("编辑图片", color = Color.White, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    val hasCrop = cropRect != null && cropRect!!.width > 5f && cropRect!!.height > 5f
                    val hasRotation = rotationAngle != 0f
                    if (strokes.isEmpty() && !hasCrop && !hasRotation) {
                        onDismiss()
                        return@TextButton
                    }
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val parentDir = File(imagePath).parentFile
                            if (parentDir == null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "无法获取文件存储目录", Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            parentDir.mkdirs()
                            val editedFile = File(parentDir, "edited_${UUID.randomUUID()}.jpg")

                            val baked = composeBitmap(originalBitmap, strokes, cropRect, rotationAngle)
                            FileOutputStream(editedFile).use { out ->
                                baked.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            baked.recycle()

                            // 干净底图（无笔画，供二次编辑）
                            val base = composeBitmap(originalBitmap, emptyList(), cropRect, rotationAngle)
                            FileOutputStream(File(parentDir, "${editedFile.name}.base")).use { out ->
                                base.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            base.recycle()

                            if (strokes.isNotEmpty()) {
                                saveStrokesMetadata(editedFile.absolutePath, strokes, undoStack)
                            }

                            val originalFile = File(imagePath)
                            if (editedFile.exists() && originalFile.exists()) {
                                File("${originalFile.absolutePath}.strokes").delete()
                                File("${originalFile.absolutePath}.base").delete()
                                originalFile.delete()
                            }
                            withContext(Dispatchers.Main) { onSave(editedFile.absolutePath) }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) { Text("完成", color = Color(0xFF4CAF50)) }
            }
        }

        // ---- 底部工具栏 ----
        Column(Modifier.align(Alignment.BottomCenter)) {
            Box(
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                    )
                )
            ) {
                Column {
                    // 面板内容（按模式切换）
                    AnimatedContent(
                        targetState = topMode,
                        transitionSpec = {
                            (slideInVertically { it / 2 } +
                                fadeIn(spring(dampingRatio = 0.6f, stiffness = 450f))) togetherWith
                                (slideOutVertically { it / 2 } +
                                    fadeOut(spring(dampingRatio = 0.7f, stiffness = 350f)))
                        },
                        label = "topMode"
                    ) { mode ->
                        when (mode) {
                            TopMode.MARK -> {
                                Column {
                                    // 画笔选项面板（点击画笔按钮展开/收起）
                                    if (markSubMode == MarkSubMode.PEN && showPenOptions) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            ColorPicker(penColors, currentPenColor) {
                                                currentPenColor = it
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("笔画粗细", color = Color.White, fontSize = 12.sp)
                                                Spacer(Modifier.width(8.dp))
                                                CustomSlider(
                                                    value = currentStrokeWidth,
                                                    onValueChange = { currentStrokeWidth = it },
                                                    valueRange = 1f..25f,
                                                    modifier = Modifier.weight(1f),
                                                    thumbColor = currentPenColor,
                                                    activeTrackColor = currentPenColor
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Box(
                                                    Modifier.size(20.dp).clip(CircleShape)
                                                        .background(currentPenColor)
                                                )
                                            }
                                        }
                                    }

                                    // 橡皮擦选项面板（点击橡皮擦按钮展开/收起）
                                    if (markSubMode == MarkSubMode.ERASER && showEraserOptions) {
                                        EraserOptionsPanel(eraserSize = eraserSize, onEraserSizeChange = { eraserSize = it })
                                    }
                                }
                            }
                            TopMode.CROP -> Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text("旋转: ${rotationAngle.toInt()}°", color = Color.White, fontSize = 12.sp)
                                CustomSlider(
                                    value = rotationAngle,
                                    onValueChange = { rotationAngle = it },
                                    valueRange = -180f..180f,
                                    modifier = Modifier.fillMaxWidth(),
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White
                                )
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf(-90f, 0f, 90f, 180f).forEach { d ->
                                        TextButton(onClick = { rotationAngle = d }) {
                                            Text("${d.toInt()}°", color = Color.White)
                                        }
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(onClick = {
                                        rotationAngle = 0f
                                        cropRect = Rect(0f, 0f, imgWidth, imgHeight)
                                        if (containerSize.width > 0 && containerSize.height > 0) {
                                            val rad = Math.toRadians(0.0)
                                            val rotW = imgWidth * cos(rad).toFloat() + imgHeight * sin(rad).toFloat()
                                            val rotH = imgWidth * sin(rad).toFloat() + imgHeight * cos(rad).toFloat()
                                            viewScale = minOf(containerSize.width / rotW, containerSize.height / rotH)
                                            viewOffsetX = containerSize.width / 2f
                                            viewOffsetY = containerSize.height / 2f
                                        }
                                    }) {
                                        Icon(
                                            Icons.Rounded.Restore, contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("还原", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 标记子模式按钮（仅 MARK 模式下显示）
                    if (topMode == TopMode.MARK) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ModeButton("画笔", Icons.Rounded.Draw, markSubMode == MarkSubMode.PEN, themeColor) {
                                if (markSubMode == MarkSubMode.PEN) {
                                    showPenOptions = !showPenOptions
                                } else {
                                    markSubMode = MarkSubMode.PEN
                                    showPenOptions = true
                                    showEraserOptions = false
                                }
                            }
                            ModeButton("橡皮擦", Icons.Rounded.AutoFixHigh, markSubMode == MarkSubMode.ERASER, themeColor) {
                                if (markSubMode == MarkSubMode.ERASER) {
                                    showEraserOptions = !showEraserOptions
                                } else {
                                    markSubMode = MarkSubMode.ERASER
                                    showEraserOptions = true
                                    showPenOptions = false
                                }
                            }
                            ModeButton("移动", Icons.Rounded.OpenWith, markSubMode == MarkSubMode.MOVE, themeColor) {
                                markSubMode = MarkSubMode.MOVE
                                showPenOptions = false
                                showEraserOptions = false
                            }
                        }
                    }

                    // 顶层模式切换按钮
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModeButton("标记", Icons.Rounded.Edit, topMode == TopMode.MARK, themeColor) {
                            topMode = TopMode.MARK
                        }
                        ModeButton("裁切", Icons.Rounded.Crop, topMode == TopMode.CROP, themeColor) {
                            topMode = TopMode.CROP
                            if (cropRect == null) {
                                cropRect = Rect(0f, 0f, imgWidth, imgHeight)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 笔画元数据序列化 ====================

private fun saveStrokesMetadata(
    imagePath: String,
    strokes: List<StrokeData>,
    undoStack: List<List<StrokeData>>
) {
    try {
        val json = JSONObject()
        json.put("strokes", strokesToJson(strokes))
        val usArr = JSONArray()
        undoStack.forEach { usArr.put(strokesToJson(it)) }
        json.put("undoStack", usArr)
        File("$imagePath.strokes").writeText(json.toString())
    } catch (_: Exception) {}
}

private fun strokesToJson(strokes: List<StrokeData>): JSONArray {
    val arr = JSONArray()
    strokes.forEach { s ->
        val so = JSONObject()
        so.put("color", colorToLong(s.color))
        so.put("width", s.strokeWidth.toDouble())
        val pts = JSONArray()
        s.points.forEach { p ->
            pts.put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble()))
        }
        so.put("points", pts)
        arr.put(so)
    }
    return arr
}

private fun parseStrokesArray(arr: JSONArray): List<StrokeData> {
    val result = mutableListOf<StrokeData>()
    for (i in 0 until arr.length()) {
        val s = arr.getJSONObject(i)
        val colorLong = s.optLong("color", 0)
        val color = Color(
            red = ((colorLong shr 16) and 0xFF).toInt() / 255f,
            green = ((colorLong shr 8) and 0xFF).toInt() / 255f,
            blue = (colorLong and 0xFF).toInt() / 255f,
            alpha = ((colorLong shr 24) and 0xFF).toInt() / 255f
        )
        val width = s.getDouble("width").toFloat()
        val pts = s.getJSONArray("points")
        val points = mutableListOf<Offset>()
        for (j in 0 until pts.length()) {
            val p = pts.getJSONArray(j)
            points.add(Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()))
        }
        if (points.size >= 2) {
            result.add(StrokeData(points, color, width))
        }
    }
    return result
}

private fun colorToLong(c: Color): Long {
    val a = (c.alpha * 255).toInt().coerceIn(0, 255).toLong()
    val r = (c.red * 255).toInt().coerceIn(0, 255).toLong()
    val g = (c.green * 255).toInt().coerceIn(0, 255).toLong()
    val b = (c.blue * 255).toInt().coerceIn(0, 255).toLong()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

// ==================== 橡皮擦逻辑 ====================

private fun eraseStrokes(
    imgPos: Offset,
    strokes: List<StrokeData>,
    eraserR: Float
): List<StrokeData> {
    val ex = imgPos.x
    val ey = imgPos.y
    val r2 = eraserR * eraserR
    val newStrokes = mutableListOf<StrokeData>()

    strokes.forEach { stroke ->
        val inside = BooleanArray(stroke.points.size)
        for (i in stroke.points.indices) {
            val p = stroke.points[i]
            val dx = p.x - ex
            val dy = p.y - ey
            if (dx * dx + dy * dy <= r2) inside[i] = true
        }
        for (i in 0 until stroke.points.size - 1) {
            if (inside[i] || inside[i + 1]) continue
            val d = pointToSegmentDistance(ex, ey, stroke.points[i], stroke.points[i + 1])
            if (d <= eraserR) {
                inside[i] = true
                inside[i + 1] = true
            }
        }

        val segments = mutableListOf<List<Offset>>()
        var currentSeg = mutableListOf<Offset>()
        for (i in stroke.points.indices) {
            if (inside[i]) {
                if (currentSeg.isNotEmpty()) {
                    segments.add(currentSeg.toList())
                    currentSeg = mutableListOf()
                }
            } else {
                currentSeg.add(stroke.points[i])
            }
        }
        if (currentSeg.size >= 2) segments.add(currentSeg)
        segments.filter { it.size >= 2 }.forEach { seg ->
            newStrokes.add(StrokeData(seg, stroke.color, stroke.strokeWidth))
        }
    }
    return newStrokes
}

private fun pointToSegmentDistance(px: Float, py: Float, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0f) {
        return Math.sqrt(((px - a.x) * (px - a.x) + (py - a.y) * (py - a.y)).toDouble()).toFloat()
    }
    var t = ((px - a.x) * dx + (py - a.y) * dy) / lenSq
    t = t.coerceIn(0f, 1f)
    val closestX = a.x + t * dx
    val closestY = a.y + t * dy
    return Math.sqrt(((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY)).toDouble()).toFloat()
}

// ==================== 裁切框逻辑 ====================

private fun detectCropHandle(touch: Offset, crop: Rect, threshold: Float): CropHandle {
    // 四角优先
    if ((touch - crop.topLeft).getDistance() < threshold) return CropHandle.TOP_LEFT
    if ((touch - Offset(crop.right, crop.top)).getDistance() < threshold) return CropHandle.TOP_RIGHT
    if ((touch - crop.bottomRight).getDistance() < threshold) return CropHandle.BOTTOM_RIGHT
    if ((touch - Offset(crop.left, crop.bottom)).getDistance() < threshold) return CropHandle.BOTTOM_LEFT

    // 四边
    if (kotlin.math.abs(touch.y - crop.top) < threshold &&
        touch.x in (crop.left - threshold)..(crop.right + threshold)
    ) return CropHandle.TOP
    if (kotlin.math.abs(touch.y - crop.bottom) < threshold &&
        touch.x in (crop.left - threshold)..(crop.right + threshold)
    ) return CropHandle.BOTTOM
    if (kotlin.math.abs(touch.x - crop.left) < threshold &&
        touch.y in (crop.top - threshold)..(crop.bottom + threshold)
    ) return CropHandle.LEFT
    if (kotlin.math.abs(touch.x - crop.right) < threshold &&
        touch.y in (crop.top - threshold)..(crop.bottom + threshold)
    ) return CropHandle.RIGHT

    return CropHandle.NONE
}

private fun moveCropRect(rect: Rect, dx: Float, dy: Float, bounds: Rect): Rect {
    var newLeft = rect.left + dx
    var newTop = rect.top + dy
    val w = rect.width
    val h = rect.height
    if (newLeft < bounds.left) newLeft = bounds.left
    if (newTop < bounds.top) newTop = bounds.top
    if (newLeft + w > bounds.right) newLeft = bounds.right - w
    if (newTop + h > bounds.bottom) newTop = bounds.bottom - h
    return Rect(newLeft, newTop, newLeft + w, newTop + h)
}

private fun adjustCropRect(
    rect: Rect,
    handle: CropHandle,
    delta: Offset,
    bounds: Rect,
    minSize: Float
): Rect {
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom

    when (handle) {
        CropHandle.TOP_LEFT -> { left += delta.x; top += delta.y }
        CropHandle.TOP_RIGHT -> { right += delta.x; top += delta.y }
        CropHandle.BOTTOM_LEFT -> { left += delta.x; bottom += delta.y }
        CropHandle.BOTTOM_RIGHT -> { right += delta.x; bottom += delta.y }
        CropHandle.TOP -> { top += delta.y }
        CropHandle.BOTTOM -> { bottom += delta.y }
        CropHandle.LEFT -> { left += delta.x }
        CropHandle.RIGHT -> { right += delta.x }
        else -> {}
    }

    // 最小尺寸
    if (right - left < minSize) {
        when (handle) {
            CropHandle.TOP_LEFT, CropHandle.LEFT, CropHandle.BOTTOM_LEFT -> left = right - minSize
            CropHandle.TOP_RIGHT, CropHandle.RIGHT, CropHandle.BOTTOM_RIGHT -> right = left + minSize
            else -> {}
        }
    }
    if (bottom - top < minSize) {
        when (handle) {
            CropHandle.TOP_LEFT, CropHandle.TOP, CropHandle.TOP_RIGHT -> top = bottom - minSize
            CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM, CropHandle.BOTTOM_RIGHT -> bottom = top + minSize
            else -> {}
        }
    }

    // 边界约束
    left = left.coerceIn(bounds.left, bounds.right - minSize)
    right = right.coerceIn(left + minSize, bounds.right)
    top = top.coerceIn(bounds.top, bounds.bottom - minSize)
    bottom = bottom.coerceIn(top + minSize, bounds.bottom)

    return Rect(left, top, right, bottom)
}

// ==================== UI 小组件 ====================

@Composable
private fun ModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            icon, label,
            tint = if (active) activeColor else Color.White,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label, fontSize = 10.sp,
            color = if (active) activeColor else Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun EraserOptionsPanel(eraserSize: Float, onEraserSizeChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("橡皮大小", color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            CustomSlider(
                value = eraserSize,
                onValueChange = onEraserSizeChange,
                valueRange = 5f..50f,
                modifier = Modifier.weight(1f),
                thumbColor = Color.White,
                activeTrackColor = Color.White
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .background(Color.Transparent)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

@Composable
private fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    activeTrackColor: Color = Color.White,
    thumbRadius: Float = 10f,
    trackHeight: Float = 4f
) {
    val range = valueRange.endInclusive - valueRange.start
    var sliderWidth by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .height(40.dp)
            .onGloballyPositioned { sliderWidth = it.size.width.toFloat() }
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val f = (down.position.x / sliderWidth).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + f * range)
                    down.consume()
                    do {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.pressed } ?: break
                        val frac = (ch.position.x / sliderWidth).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + frac * range)
                        ch.consume()
                    } while (true)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(Modifier.fillMaxWidth().height(trackHeight.dp)) {
            val cy = size.height / 2f
            val thPx = trackHeight.dp.toPx()
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, cy - thPx / 2f),
                size = Size(size.width, thPx),
                cornerRadius = CornerRadius(thPx / 2f)
            )
            val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
            val thumbX = fraction * size.width
            if (thumbX > 0f) {
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(0f, cy - thPx / 2f),
                    size = Size(thumbX, thPx),
                    cornerRadius = CornerRadius(thPx / 2f)
                )
            }
            drawCircle(
                color = thumbColor,
                radius = thumbRadius.dp.toPx(),
                center = Offset(thumbX, cy)
            )
        }
    }
}

// ==================== Bitmap 合成 ====================

private fun toSoftwareBitmap(src: Bitmap): Bitmap {
    val sw = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    AndroidCanvas(sw).drawBitmap(src, 0f, 0f, null)
    return sw
}

/**
 * 合成最终 Bitmap：旋转 → 绘制笔画 → 裁切
 */
private fun composeBitmap(
    original: Bitmap,
    strokes: List<StrokeData>,
    cropRect: Rect?,
    rotationAngle: Float
): Bitmap {
    val swOriginal = toSoftwareBitmap(original)

    // 1. 旋转
    val rotated = if (rotationAngle != 0f) {
        val matrix = Matrix().apply {
            postRotate(rotationAngle, swOriginal.width / 2f, swOriginal.height / 2f)
        }
        val result = Bitmap.createBitmap(
            swOriginal, 0, 0,
            swOriginal.width, swOriginal.height,
            matrix, true
        )
        swOriginal.recycle()
        result
    } else swOriginal

    // 2. 绘制笔画
    val withStrokes = Bitmap.createBitmap(
        rotated.width, rotated.height, Bitmap.Config.ARGB_8888
    )
    val canvas = AndroidCanvas(withStrokes)
    canvas.drawBitmap(rotated, 0f, 0f, null)

    if (strokes.isNotEmpty()) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { s ->
            if (s.points.size >= 2) {
                paint.color = composeToAndroidColor(s.color)
                paint.strokeWidth = s.strokeWidth
                val path = android.graphics.Path()
                path.moveTo(s.points[0].x, s.points[0].y)
                for (i in 1 until s.points.size) {
                    path.lineTo(s.points[i].x, s.points[i].y)
                }
                canvas.drawPath(path, paint)
            }
        }
    }
    rotated.recycle()

    // 3. 裁切
    return if (cropRect != null && cropRect.width > 5f && cropRect.height > 5f) {
        val cx = cropRect.left.toInt().coerceIn(0, withStrokes.width - 1)
        val cy = cropRect.top.toInt().coerceIn(0, withStrokes.height - 1)
        val cw = cropRect.width.toInt().coerceAtMost(withStrokes.width - cx).coerceAtLeast(1)
        val ch = cropRect.height.toInt().coerceAtMost(withStrokes.height - cy).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(withStrokes, cx, cy, cw, ch)
        withStrokes.recycle()
        cropped
    } else {
        withStrokes
    }
}

// ==================== 保存到相册 ====================

fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    try {
        val name = "SNOTE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Snote")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }
                v.clear()
                v.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, v, null, null)
            }
        } else {
            val d = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Snote"
            )
            d.mkdirs()
            FileOutputStream(File(d, name)).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun composeToAndroidColor(c: Color) = android.graphics.Color.argb(
    (c.alpha * 255).toInt().coerceIn(0, 255),
    (c.red * 255).toInt().coerceIn(0, 255),
    (c.green * 255).toInt().coerceIn(0, 255),
    (c.blue * 255).toInt().coerceIn(0, 255)
)
