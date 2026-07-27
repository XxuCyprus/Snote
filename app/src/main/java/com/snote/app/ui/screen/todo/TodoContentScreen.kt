package com.snote.app.ui.screen.todo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.snote.app.data.model.ContentItem
import com.snote.app.data.model.ContentType
import com.snote.app.ui.screen.reader.AddContentDialog
import com.snote.app.ui.screen.reader.AudioRecorderDialogContent
import com.snote.app.ui.screen.reader.EditTextDialogContent
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import me.minetsh.imaging.IMGEditActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoContentScreen(
    sectionId: String,
    onBackClick: () -> Unit,
    viewModel: TodoContentViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val sectionTitle = viewModel.sectionTitle
    val showAddContentDialog by viewModel.showAddContentDialog.collectAsState()
    val showRecorderDialog by viewModel.showRecorderDialog.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()
    val recorderError by viewModel.recorderError.collectAsState()
    val recordingCompleted by viewModel.recordingCompleted.collectAsState()
    val confirmTarget by viewModel.showConfirmDialog.collectAsState()

    val isCompleted = sectionId == "finished"
    val themeColor = if (isCompleted) Color(0xFF2E7D32) else Color(0xFFFF6B35)
    val context = LocalContext.current

    // FAB-origin animation
    var fabBounds by remember { mutableStateOf(Rect.Zero) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val animProgress = remember { Animatable(0f) }
    val isDialogActive = showAddContentDialog || showRecorderDialog
    LaunchedEffect(isDialogActive) {
        if (isDialogActive) animProgress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 450f))
        else animProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 350f))
    }

    var dialogKind by remember { mutableStateOf<String?>(null) }
    if (showAddContentDialog) dialogKind = "add"
    else if (showRecorderDialog) dialogKind = "record"
    LaunchedEffect(showAddContentDialog, showRecorderDialog) {
        if (!showAddContentDialog && !showRecorderDialog) {
            snapshotFlow { animProgress.value }.first { it <= 0.01f }
            dialogKind = null
        }
    }

    // 图片编辑
    var pendingEditSavePath by remember { mutableStateOf<String?>(null) }
    var pendingEditItemId by remember { mutableStateOf<String?>(null) }
    var pendingEditOriginalPath by remember { mutableStateOf<String?>(null) }
    val imageEditLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val savedPath = pendingEditSavePath
            val itemId = pendingEditItemId
            if (savedPath != null && itemId != null) {
                val dataDirPath = viewModel.getAbsolutePath("").removeSuffix("/")
                val relativePath = savedPath.removePrefix("$dataDirPath/")
                viewModel.updateImageContent(itemId, relativePath)
                // 保存涂鸦数据 + 原图路径
                val doodleFilePath = result.data?.getStringExtra(IMGEditActivity.EXTRA_DOODLE_FILE_PATH)
                if (doodleFilePath != null) {
                    val doodleFile = File(doodleFilePath)
                    if (doodleFile.exists()) {
                        val doodleJson = doodleFile.readText()
                        val json = try { org.json.JSONObject(doodleJson) } catch (_: Exception) { org.json.JSONObject() }
                        json.put("originalPath", pendingEditOriginalPath ?: "")
                        File(savedPath + ".doodles.json").writeText(json.toString())
                    }
                }
            }
        }
        pendingEditSavePath = null
        pendingEditItemId = null
        pendingEditOriginalPath = null
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addImageContent(it) } }
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addVideoContent(it) } }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addFileContent(it) } }

    var fullscreenImagePath by remember { mutableStateOf<String?>(null) }
    var editingTextItem by remember { mutableStateOf<ContentItem?>(null) }
    var renamingContentItemId by remember { mutableStateOf<String?>(null) }
    var renamingContentFileName by remember { mutableStateOf("") }
    var videoTargetPath by remember { mutableStateOf<String?>(null) }
    var fileTargetPath by remember { mutableStateOf<String?>(null) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    // 文字编辑动画
    val editAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(editingTextItem) {
        if (editingTextItem != null) editAnimProgress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 350f))
        else editAnimProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
    }
    // 重命名动画
    val renameAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(renamingContentItemId) {
        if (renamingContentItemId != null) renameAnimProgress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 350f))
        else renameAnimProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
    }

    val deleteAnimProgress = remember { Animatable(1f) }
    LaunchedEffect(deleteTargetId != null) {
        if (deleteTargetId != null) deleteAnimProgress.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 450f))
        else deleteAnimProgress.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 400f))
    }

    LaunchedEffect(videoTargetPath) {
        val p = videoTargetPath ?: return@LaunchedEffect
        try {
            val file = File(p)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }, "选择播放器"))
            } else {
                android.widget.Toast.makeText(context, "视频文件不存在: ${file.name}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "无法播放视频: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
        videoTargetPath = null
    }

    LaunchedEffect(fileTargetPath) {
        val p = fileTargetPath ?: return@LaunchedEffect
        try {
            val file = File(p)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mime = when {
                    p.endsWith(".pdf", true) -> "application/pdf"
                    p.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    p.endsWith(".doc", true) -> "application/msword"
                    else -> "*/*"
                }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }, "选择应用打开"))
            } else {
                android.widget.Toast.makeText(context, "文件不存在: ${file.name}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "无法打开文件: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
        fileTargetPath = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sectionTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (!isCompleted) {
                FloatingActionButton(
                    onClick = { viewModel.showAddContentDialog() },
                    containerColor = themeColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    modifier = Modifier
                        .graphicsLayer {
                            val p = (1f - animProgress.value) * deleteAnimProgress.value
                            alpha = p.coerceIn(0f, 1f)
                            val s = 0.08f + 0.92f * p.coerceIn(0f, 1f)
                            scaleX = s; scaleY = s
                        }
                        .onGloballyPositioned { coords ->
                            fabBounds = Rect(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                        }
                ) { Icon(Icons.Rounded.Add, contentDescription = "添加待办") }
            }
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onGloballyPositioned { screenSize = it.size }
        ) {
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (isCompleted) Icons.Rounded.TaskAlt else Icons.Rounded.PendingActions,
                            contentDescription = null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(if (isCompleted) "暂无已完成项" else "暂无待办事项", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val currentItems = items
                val adapter = remember {
                    TodoItemAdapter(
                        themeColor = themeColor.hashCode(),
                        getAbsolutePath = { rel -> viewModel.getAbsolutePath(rel) },
                        onDelete = { deleteTargetId = it },
                        onToggleComplete = { viewModel.requestComplete(items.find { ci -> ci.id == it } ?: return@TodoItemAdapter) },
                        onEdit = { id ->
                            val item = viewModel.items.value.find { ci -> ci.id == id }
                            if (item != null) {
                                when (item.type) {
                                    ContentType.TEXT -> editingTextItem = item
                                    else -> { renamingContentItemId = item.id; renamingContentFileName = File(item.content).nameWithoutExtension }
                                }
                            }
                        },
                        onSwapUp = { id1, id2 -> viewModel.swapItems(id1, id2) },
                        onSwapDown = { id1, id2 -> viewModel.swapItems(id1, id2) },
                        onImageClick = { path -> fullscreenImagePath = path },
                        onVideoClick = { path -> videoTargetPath = path },
                        onImageEdit = { path, itemId ->
                            val parentDir = File(path).parentFile
                            val saveFile = File(parentDir, "edited_${UUID.randomUUID()}.jpg")
                            pendingEditSavePath = saveFile.absolutePath
                            pendingEditItemId = itemId

                            // 加载已有涂鸦数据
                            val existingJsonFile = File("$path.doodles.json")
                            val originalPath: String = if (existingJsonFile.exists()) {
                                try {
                                    val json = org.json.JSONObject(existingJsonFile.readText())
                                    val op = json.optString("originalPath", "")
                                    if (op.isNotEmpty()) op else path
                                } catch (_: Exception) { path }
                            } else path
                            pendingEditOriginalPath = originalPath

                            // 使用原始未编辑图片作为编辑底图
                            val cleanPath = path + "_clean.jpg"
                            if (!File(cleanPath).exists()) {
                                try { File(originalPath).copyTo(File(cleanPath), overwrite = true) } catch (_: Exception) {}
                            }
                            val loadPath = cleanPath

                            val intent = Intent(context, IMGEditActivity::class.java)
                                .putExtra(IMGEditActivity.EXTRA_IMAGE_URI, Uri.fromFile(File(loadPath)))
                                .putExtra(IMGEditActivity.EXTRA_IMAGE_SAVE_PATH, saveFile.absolutePath)
                                .putExtra("THEME_COLOR", themeColor.hashCode())
                            if (existingJsonFile.exists()) {
                                intent.putExtra(IMGEditActivity.EXTRA_DOODLE_FILE_PATH, existingJsonFile.absolutePath)
                            }
                            imageEditLauncher.launch(intent)
                        },
                        onSaveToGallery = { path ->
                            val bmp = BitmapFactory.decodeFile(path)
                            if (bmp != null) { saveBitmapToGallery(context, bmp); bmp.recycle() }
                        },
                        onFileClick = { path -> fileTargetPath = path },
                        onRenameContent = { _, itemId ->
                            renamingContentItemId = itemId
                            renamingContentFileName = File(viewModel.items.value.find { it.id == itemId }?.content ?: "").nameWithoutExtension
                        },
                        isCompleted = isCompleted
                    )
                }
                LaunchedEffect(currentItems) { adapter.submitList(currentItems) }
                LaunchedEffect(sectionTitle) { adapter.headerTitle = sectionTitle }
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    AndroidView(
                        modifier = Modifier.weight(1f).clipToBounds(),
                        factory = { ctx ->
                            val px88 = (88 * ctx.resources.displayMetrics.density).toInt()
                            RecyclerView(ctx).apply {
                                layoutManager = LinearLayoutManager(ctx)
                                clipToPadding = false; setItemViewCacheSize(10); itemAnimator = null
                                setPadding(0, 0, 0, px88); this.adapter = adapter
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
                DisposableEffect(adapter) { onDispose { adapter.release() } }
            }
        }
    }

    // 全屏图片
    if (fullscreenImagePath != null) {
        Dialog(onDismissRequest = { fullscreenImagePath = null }) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { fullscreenImagePath = null }, contentAlignment = Alignment.Center) {
                val bmp = remember { BitmapFactory.decodeFile(fullscreenImagePath!!) }
                if (bmp != null) {
                    androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // 确认弹窗
    if (confirmTarget != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirm() },
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = themeColor) },
            title = { Text(if (isCompleted) "撤销完成？" else "确认完成？") },
            text = { Text(if (isCompleted) "确定要将此项移回未完成列表吗？" else "确定要将此项标记为已完成？") },
            confirmButton = { Button(onClick = { viewModel.confirmComplete() }, shape = RoundedCornerShape(12.dp)) { Text("确认") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissConfirm() }) { Text("取消") } }
        )
    }

    // 删除确认
    if (deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("确认删除") }, text = { Text("确定要删除此项吗？") },
            confirmButton = { TextButton(onClick = { deleteTargetId?.let { viewModel.deleteItem(it) }; deleteTargetId = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTargetId = null }) { Text("取消") } }
        )
    }

    // FAB-origin 覆盖层动画（与笔记模块一致）
    if (isDialogActive || animProgress.value > 0.01f) {
        val v = animProgress.value
        val pivotX = if (screenSize.width > 0) fabBounds.center.x / screenSize.width.toFloat() else 0.97f
        val pivotY = if (screenSize.height > 0) fabBounds.center.y / screenSize.height.toFloat() else 0.98f

        Box(Modifier.fillMaxSize()) {
            // Scrim
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = v * 0.5f }.background(Color.Black)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    when (dialogKind) {
                        "add" -> viewModel.hideAddContentDialog()
                        "record" -> if (!isRecording && !recordingCompleted) viewModel.cancelRecording()
                    }
                })
            // Card from FAB pivot
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = v; val s = 0.08f + 0.92f * v; scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(pivotX, pivotY); clip = true
                },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = dialogKind,
                    transitionSpec = { fadeIn(spring(dampingRatio = 0.65f, stiffness = 500f)) togetherWith fadeOut(spring(dampingRatio = 0.7f, stiffness = 350f)) },
                    label = "dialogMode"
                ) { mode ->
                    when (mode) {
                        "add" -> AddContentDialog(
                            themeColor = themeColor,
                            onDismiss = { viewModel.hideAddContentDialog() },
                            onAddText = { viewModel.addTextContent(it) },
                            onAddImage = { imagePickerLauncher.launch("image/*") },
                            onAddVideo = { videoPickerLauncher.launch("video/*") },
                            onAddAudio = { viewModel.switchToRecorder() },
                            onAddFile = { filePickerLauncher.launch("application/*") }
                        )
                        "record" -> AudioRecorderDialogContent(
                            themeColor = themeColor,
                            isRecording = isRecording,
                            recordingSeconds = recordingSeconds,
                            errorMessage = recorderError,
                            recordingCompleted = recordingCompleted,
                            onStartRecording = { viewModel.startRecording() },
                            onStopRecording = { viewModel.stopRecording() },
                            onSaveRecording = { viewModel.saveRecording() },
                            onCancel = { viewModel.cancelRecording() },
                            onClearError = { viewModel.clearRecorderError() }
                        )
                    }
                }
            }
        }
    }

    // Text edit dialog
    val ev = editAnimProgress.value
    if (editingTextItem != null || ev > 0.01f) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ev * 0.5f }.background(Color.Black)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { editingTextItem = null })
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Box(Modifier.graphicsLayer { alpha = ev; val s = 0.85f + 0.15f * ev; scaleX = s; scaleY = s }) {
                    EditTextDialogContent(
                        initialText = editingTextItem?.content ?: "",
                        themeColor = themeColor,
                        onDismiss = { editingTextItem = null },
                        onSave = { newText ->
                            val id = editingTextItem?.id ?: return@EditTextDialogContent
                            viewModel.addTextContent(newText); viewModel.deleteItem(id); editingTextItem = null
                        }
                    )
                }
            }
        }
    }

    // Rename dialog
    val rv = renameAnimProgress.value
    if (renamingContentItemId != null || rv > 0.01f) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = rv * 0.5f }.background(Color.Black)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { renamingContentItemId = null })
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Box(Modifier.graphicsLayer { alpha = rv; val s = 0.85f + 0.15f * rv; scaleX = s; scaleY = s }) {
                    EditTextDialogContent(
                        initialText = renamingContentFileName,
                        themeColor = themeColor,
                        title = "重命名",
                        onDismiss = { renamingContentItemId = null },
                        onSave = { newName ->
                            val id = renamingContentItemId ?: return@EditTextDialogContent
                            if (newName.isNotBlank()) viewModel.renameTodoItem(id, newName.trim()); renamingContentItemId = null
                        }
                    )
                }
            }
        }
    }
}

private fun saveBitmapToGallery(context: android.content.Context, bmp: Bitmap) {
    try {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "snote_${UUID.randomUUID()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { context.contentResolver.openOutputStream(it)?.use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 95, out) } }
    } catch (_: Exception) {}
}
