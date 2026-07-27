package com.snote.app.ui.screen.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.first
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.snote.app.data.model.Chapter
import com.snote.app.data.model.ContentItem
import com.snote.app.data.model.ContentType
import java.io.File
import java.util.UUID
import me.minetsh.imaging.IMGEditActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    notebookId: String,
    chapterId: String,
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val notebook by viewModel.notebook.collectAsState()
    val currentChapter by viewModel.currentChapter.collectAsState()
    val showDrawer by viewModel.showDrawer.collectAsState()
    val showAddContentDialog by viewModel.showAddContentDialog.collectAsState()
    val showAddChapterDialog by viewModel.showAddChapterDialog.collectAsState()
    val parentChapterId by viewModel.parentChapterId.collectAsState()
    val refreshToken by viewModel.contentRefreshToken.collectAsState()
    val ancestors by viewModel.chapterAncestors.collectAsState()
    val drawerScrollTarget by viewModel.drawerScrollTarget.collectAsState()

    val showRecorderDialog by viewModel.showRecorderDialog.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()
    val recorderError by viewModel.recorderError.collectAsState()
    val recordingCompleted by viewModel.recordingCompleted.collectAsState()
    val filterMode by viewModel.showMarkedOnly.collectAsState()

    val context = LocalContext.current
    val listState = rememberLazyListState()

    // 监听前台/后台切换，暂停/恢复学习计时
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.pauseStudyTimer()
                Lifecycle.Event.ON_START -> viewModel.resumeStudyTimer()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 全屏图片查看状态
    var fullscreenImagePath by remember { mutableStateOf<String?>(null) }

    // 文字编辑状态
    var editingTextItem by remember { mutableStateOf<ContentItem?>(null) }

    // 媒体/文件重命名状态
    var renamingContentItemId by remember { mutableStateOf<String?>(null) }
    var renamingContentFileName by remember { mutableStateOf("") }

    // 图片编辑器 Activity 启动器
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
                val dataDirPath = viewModel.getAbsolutePath("")
                val relativePath = savedPath.removePrefix("$dataDirPath/")
                viewModel.updateImageContent(itemId, relativePath)
                // 保存涂鸦数据 + 原图路径，用于下次重新编辑时恢复
                val doodleFilePath = result.data?.getStringExtra(IMGEditActivity.EXTRA_DOODLE_FILE_PATH)
                if (doodleFilePath != null) {
                    val doodleFile = java.io.File(doodleFilePath)
                    if (doodleFile.exists()) {
                        val doodleJson = doodleFile.readText()
                        val json = try { org.json.JSONObject(doodleJson) } catch (e: Exception) { org.json.JSONObject() }
                        json.put("originalPath", pendingEditOriginalPath ?: "")
                        java.io.File(savedPath + ".doodles.json").writeText(json.toString())
                    }
                }
            }
        }
        pendingEditSavePath = null
        pendingEditItemId = null
        pendingEditOriginalPath = null
    }

    // 章节重命名状态
    var renamingChapterId by remember { mutableStateOf<String?>(null) }

    // 目录章节删除确认状态
    var deleteChapterTarget by remember { mutableStateOf<String?>(null) }
    // 内容条目删除确认状态
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    // FAB 位置追踪 + 统一动画进度：0f=关闭, 1f=对话框完全打开
    var fabBounds by remember { mutableStateOf(Rect.Zero) }
    val animProgress = remember { Animatable(0f) }
    val isDialogActive = showAddContentDialog || showRecorderDialog
    LaunchedEffect(isDialogActive) {
        if (isDialogActive) {
            animProgress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 450f))
        } else {
            animProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 350f))
        }
    }

    // AddChapterDialog 动画进度 — 底部弹簧弹入
    val chapterAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(showAddChapterDialog) {
        if (showAddChapterDialog) {
            chapterAnimProgress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f))
        } else {
            chapterAnimProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
        }
    }

    // EditTextDialog 动画进度 — 中央弹簧弹入
    val editTextAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(editingTextItem) {
        if (editingTextItem != null) {
            editTextAnimProgress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 350f))
        } else {
            editTextAnimProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
        }
    }

    // 章节重命名 动画进度
    val renameAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(renamingChapterId) {
        if (renamingChapterId != null) {
            renameAnimProgress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 350f))
        } else {
            renameAnimProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
        }
    }

    // 媒体/文件重命名 动画进度
    val renameContentAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(renamingContentItemId) {
        if (renamingContentItemId != null) {
            renameContentAnimProgress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 350f))
        } else {
            renameContentAnimProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
        }
    }

    // 抽屉打开/关闭 FAB 动画
    val drawerAnimProgress = remember { Animatable(1f) }
    LaunchedEffect(showDrawer) {
        if (showDrawer) {
            drawerAnimProgress.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 450f))
        } else {
            drawerAnimProgress.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 400f))
        }
    }

    // 删除确认弹窗 FAB 动画
    val deleteDialogVisible = deleteTargetId != null || deleteChapterTarget != null
    val deleteAnimProgress = remember { Animatable(1f) }
    LaunchedEffect(deleteDialogVisible) {
        if (deleteDialogVisible) {
            deleteAnimProgress.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 450f))
        } else {
            deleteAnimProgress.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 400f))
        }
    }

    // 持久化对话框类型 — 退出动画期间保持内容可见（匹配 HomeScreen 模式）
    var dialogKind by remember { mutableStateOf<String?>(null) }
    if (showAddContentDialog) dialogKind = "add"
    else if (showRecorderDialog) dialogKind = "record"
    LaunchedEffect(showAddContentDialog, showRecorderDialog) {
        if (!showAddContentDialog && !showRecorderDialog) {
            snapshotFlow { animProgress.value }
                .first { it <= 0.01f }
            dialogKind = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addImageContent(it) }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addVideoContent(it) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addFileContent(it) }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = currentChapter?.title ?: notebook?.title ?: "阅读",
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (ancestors.size > 1) {
                            val scrollState = rememberScrollState()
                            LaunchedEffect(ancestors) {
                                scrollState.scrollTo(scrollState.maxValue)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(scrollState),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ancestors.forEachIndexed { i, (id, title) ->
                                    if (i > 0) {
                                        Text(
                                            "  ›  ",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                    val isLast = i == ancestors.lastIndex
                                    Text(
                                        text = if (title.length > 10) title.take(10) + "…" else title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isLast)
                                            viewModel.themeColor
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        modifier = if (!isLast) Modifier.clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            viewModel.switchChapter(id)
                                        } else Modifier
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onBackClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { viewModel.toggleDrawer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Menu, contentDescription = "目录")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddContentDialog() },
                containerColor = viewModel.themeColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                modifier = Modifier
                    .graphicsLayer {
                        val dialogProgress = (1f - animProgress.value).coerceIn(0f, 1f)
                        val chapterProgress = if (currentChapter != null) 1f else 0f
                        val finalProgress = (dialogProgress * drawerAnimProgress.value * deleteAnimProgress.value * chapterProgress).coerceIn(0f, 1f)
                        alpha = finalProgress
                        val s = 0.08f + 0.92f * finalProgress
                        scaleX = s
                        scaleY = s
                    }
                    .onGloballyPositioned { coords ->
                        fabBounds = Rect(
                            coords.positionInRoot(),
                            Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                        )
                    }
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "添加内容")
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            val chapter = currentChapter
            if (chapter == null) {
                EmptyChapterState(
                    modifier = Modifier.fillMaxSize(),
                    themeColor = viewModel.themeColor,
                    onAddChapter = { viewModel.showAddChapterDialog(null) }
                )
            } else {
                val sortedItems = remember(chapter.id, refreshToken) {
                    chapter.items.sortedBy { it.order }
                }

                // 删除确认状态
                var videoTargetPath by remember { mutableStateOf<String?>(null) }
                var fileTargetPath by remember { mutableStateOf<String?>(null) }

                // RecyclerView 适配器 — 不可用 remember(chapter.id)，否则切换章节时
                // 创建的 adapter 实例不会挂到 RecyclerView 上（factory 只执行一次）
                val contentAdapter = remember {
                    ContentItemAdapter(
                        themeColor = viewModel.themeColor.hashCode(),
                        getAbsolutePath = { rel -> viewModel.getAbsolutePath(rel) },
                        onDelete = { deleteTargetId = it },
                        onEdit = { id ->
                            val item = currentChapter?.items?.find { ci -> ci.id == id }
                            if (item != null) {
                                when (item.type) {
                                    ContentType.TEXT -> editingTextItem = item
                                    else -> {
                                        renamingContentItemId = item.id
                                        renamingContentFileName = File(item.content).nameWithoutExtension
                                    }
                                }
                            }
                        },
                        onToggleMark = { viewModel.toggleContentMarked(it) },
                        onSwapUp = { id1, id2 -> viewModel.swapContentItems(id1, id2) },
                        onSwapDown = { id1, id2 -> viewModel.swapContentItems(id1, id2) },
                        onImageClick = { path -> fullscreenImagePath = path },
                        onVideoClick = { path -> videoTargetPath = path },
                        onImageEdit = { path, itemId ->
                            val parentDir = File(path).parentFile
                            val saveFile = File(parentDir, "edited_${UUID.randomUUID()}.jpg")
                            pendingEditSavePath = saveFile.absolutePath
                            pendingEditItemId = itemId

                            // 加载当前文件（可能是原图或已编辑的裁切版本）
                            // 已有编辑记录时加载当前文件来保留裁切效果
                            val existingJsonFile = File("$path.doodles.json")
                            val originalPath: String = if (existingJsonFile.exists()) {
                                try {
                                    val json = org.json.JSONObject(existingJsonFile.readText())
                                    val op = json.optString("originalPath", "")
                                    if (op.isNotEmpty()) op else path
                                } catch (e: Exception) { path }
                            } else path
                            pendingEditOriginalPath = originalPath

                            // 使用原始未编辑图片作为编辑底图，确保涂鸦坐标始终对齐
                            val cleanPath = path + "_clean.jpg"
                            if (!File(cleanPath).exists()) {
                                // 首次编辑：将原始图片复制为 _clean.jpg
                                try {
                                    File(originalPath).copyTo(File(cleanPath), overwrite = true)
                                } catch (e: Exception) {
                                    // 复制失败时使用原路径
                                }
                            }
                            val loadPath = cleanPath

                            val intent = Intent(context, IMGEditActivity::class.java)
                                .putExtra(IMGEditActivity.EXTRA_IMAGE_URI, Uri.fromFile(File(loadPath)))
                                .putExtra(IMGEditActivity.EXTRA_IMAGE_SAVE_PATH, saveFile.absolutePath)
                                .putExtra("THEME_COLOR", viewModel.themeColor.hashCode())
                            // 加载已有涂鸦数据（通过文件路径避免 TransactionTooLargeException）
                            if (existingJsonFile.exists()) {
                                intent.putExtra(IMGEditActivity.EXTRA_DOODLE_FILE_PATH, existingJsonFile.absolutePath)
                            }
                            imageEditLauncher.launch(intent)
                        },
                        onSaveToGallery = { path ->
                            val file = File(path)
                            if (file.exists()) {
                                val bmp = BitmapFactory.decodeFile(path)
                                if (bmp != null) {
                                    saveBitmapToGallery(context, bmp)
                                    bmp.recycle()
                                }
                            }
                        },
                        onFileClick = { path -> fileTargetPath = path },
                        onRenameContent = { path, itemId ->
                            renamingContentItemId = itemId
                            renamingContentFileName = File(path).nameWithoutExtension
                        }
                    )
                }
                LaunchedEffect(sortedItems) {
                    contentAdapter.submitList(sortedItems)
                }
                LaunchedEffect(chapter.title) {
                    contentAdapter.headerTitle = chapter.title
                }

                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    if (chapter.items.isEmpty()) {
                        Spacer(Modifier.weight(1f))
                        EmptyContentHint()
                        Spacer(Modifier.weight(1f))
                    } else {
                        AndroidView(
                            modifier = Modifier.weight(1f).clipToBounds(),
                            factory = { ctx ->
                                val a = contentAdapter
                                val px88 = (88 * ctx.resources.displayMetrics.density).toInt()
                                RecyclerView(ctx).apply {
                                    layoutManager = LinearLayoutManager(ctx)
                                    clipToPadding = false
                                    setItemViewCacheSize(10)
                                    itemAnimator = null
                                    setPadding(0, 0, 0, px88)
                                    this.adapter = a
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // 删除确认弹窗（内容条目）
                DeleteConfirmOverlay(
                    visible = deleteTargetId != null,
                    title = "确认删除",
                    message = "确定要删除这个内容吗？",
                    offsetY = -40,
                    cancelColor = viewModel.themeColor,
                    onConfirm = {
                        deleteTargetId?.let { viewModel.deleteContentItem(it) }
                        deleteTargetId = null
                    },
                    onDismiss = { deleteTargetId = null }
                )

                // 视频播放
                LaunchedEffect(videoTargetPath) {
                    val path = videoTargetPath ?: return@LaunchedEffect
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(intent, "选择播放器"))
                        } else {
                            android.widget.Toast.makeText(context, "视频文件不存在", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "无法播放：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    videoTargetPath = null
                }

                // 文件打开（用系统应用）
                LaunchedEffect(fileTargetPath) {
                    val path = fileTargetPath ?: return@LaunchedEffect
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val mimeType = when {
                                path.endsWith(".pdf", true) -> "application/pdf"
                                path.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                path.endsWith(".doc", true) -> "application/msword"
                                path.endsWith(".xlsx", true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                path.endsWith(".xls", true) -> "application/vnd.ms-excel"
                                else -> "*/*"
                            }
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(intent, "选择应用打开"))
                        } else {
                            android.widget.Toast.makeText(context, "文件不存在", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "无法打开：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    fileTargetPath = null
                }

                // 离开页面时释放音频播放器
                DisposableEffect(contentAdapter) {
                    onDispose { contentAdapter.release() }
                }
            }

            // 遮罩层（在内容区，不覆盖 TopAppBar，hamburger 始终可点击）
            AnimatedVisibility(
                visible = showDrawer,
                enter = fadeIn(animationSpec = spring(stiffness = 200f)),
                exit = fadeOut(animationSpec = spring(stiffness = 200f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .clickable { viewModel.closeDrawer() }
                )
            }

            // 抽屉面板（从左侧弹入，圆角只在右侧）
            AnimatedVisibility(
                visible = showDrawer,
                enter = slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                ) { -it },
                exit = slideOutHorizontally(
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                ) { -it }
            ) {
                val chapters = notebook?.chapters ?: emptyList()
                Surface(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(viewModel.themeColor)
                                .padding(20.dp)
                        ) {
                            Column {
                                Icon(
                                    Icons.Rounded.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = notebook?.title ?: "目录",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "${chapters.size} 个章节",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }

                        TextButton(
                            onClick = { viewModel.showAddChapterDialog(null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = viewModel.themeColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("添加章节", style = MaterialTheme.typography.bodyMedium, color = viewModel.themeColor)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = !filterMode,
                                onClick = { viewModel.setShowMarkedOnly(false) },
                                label = { Text("全部", fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                            FilterChip(
                                selected = filterMode,
                                onClick = { viewModel.setShowMarkedOnly(true) },
                                label = { Text("已标记", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (filterMode) Color(0xFFFFA000)
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        val expandedIds by viewModel.expandedChapterIds.collectAsState()
                        val currentChapterId = currentChapter?.id
                        // 扁平化的可见节点列表（只包含因展开而可见的节点）
                        val flatNodes = remember(chapters, expandedIds, currentChapterId, filterMode) {
                            val source = if (filterMode) filterChaptersKeepAncestors(chapters) else chapters
                            flattenChapterTree(source, expandedIds, currentChapterId)
                        }

                        var scrollTargetIdx by remember { mutableStateOf(-1) }

                        // 打开抽屉时自动滚动到当前章节
                        val scrollTarget = drawerScrollTarget
                        LaunchedEffect(scrollTarget) {
                            if (scrollTarget != null) {
                                val idx = flatNodes.indexOfFirst { it.chapter.id == scrollTarget }
                                if (idx >= 0) scrollTargetIdx = idx
                                viewModel.consumeDrawerScrollTarget()
                            }
                        }

                        // 筛选"已标记"时，自动滚动到第一个标记项
                        LaunchedEffect(filterMode) {
                            if (filterMode) {
                                scrollTargetIdx = flatNodes.indexOfFirst { it.chapter.isMarked }.coerceAtLeast(0)
                            }
                        }

                        // RecyclerView 适配器 — 用原生 View 渲染，绕过 Compose 渲染瓶颈
                        val adapter = remember {
                            ChapterAdapter(
                                onSwitchChapter = { viewModel.switchChapter(it) },
                                onToggleExpand = { viewModel.toggleChapterExpanded(it) },
                                onToggleMark = { viewModel.toggleChapterMarked(it) },
                                onDelete = { deleteChapterTarget = it },
                                onSwapUp = { id1, id2 -> viewModel.swapChapters(id1, id2) },
                                onSwapDown = { id1, id2 -> viewModel.swapChapters(id1, id2) },
                                onAddChild = { viewModel.showAddChapterDialog(it) },
                                onRename = { renamingChapterId = it }
                            )
                        }
                        LaunchedEffect(flatNodes) {
                            adapter.submitList(flatNodes)
                        }

                        AndroidView(
                            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                            factory = { ctx ->
                                val a = adapter
                                RecyclerView(ctx).apply {
                                    layoutManager = LinearLayoutManager(ctx)
                                    clipToPadding = false
                                    setItemViewCacheSize(10)
                                    itemAnimator = null
                                    this.adapter = a
                                }
                            },
                            update = { rv ->
                                val lm = rv.layoutManager as LinearLayoutManager
                                if (scrollTargetIdx >= 0) {
                                    lm.scrollToPositionWithOffset(scrollTargetIdx, 0)
                                    scrollTargetIdx = -1
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 删除确认弹窗（目录章节）
    DeleteConfirmOverlay(
        visible = deleteChapterTarget != null,
        title = "确认删除",
        message = "确定要删除该章节及其所有子章节和内容吗？",
        cancelColor = viewModel.themeColor,
        onConfirm = {
            deleteChapterTarget?.let { viewModel.deleteChapter(it) }
            deleteChapterTarget = null
        },
        onDismiss = { deleteChapterTarget = null }
    )

    // 统一覆盖层 — AddContent + AudioRecorder 共享 FAB-origin 动画
    if (showAddContentDialog || showRecorderDialog || animProgress.value > 0.01f) {
        ReaderDialogOverlay(
            animProgress = animProgress.asState(),
            fabBounds = fabBounds,
            onDismiss = {
                when (dialogKind) {
                    "add" -> viewModel.hideAddContentDialog()
                    "record" -> if (!isRecording && !recordingCompleted) viewModel.cancelRecording()
                }
            }
        ) {
            AnimatedContent(
                targetState = dialogKind,
                transitionSpec = {
                    fadeIn(spring(dampingRatio = 0.65f, stiffness = 500f)) togetherWith
                        fadeOut(spring(dampingRatio = 0.7f, stiffness = 350f))
                },
                label = "dialogMode"
            ) { mode ->
                when (mode) {
                    "add" -> AddContentDialog(
                        themeColor = viewModel.themeColor,
                        onDismiss = { viewModel.hideAddContentDialog() },
                        onAddText = { text -> viewModel.addTextContent(text) },
                        onAddImage = { imagePickerLauncher.launch("image/*") },
                        onAddVideo = { videoPickerLauncher.launch("video/*") },
                        onAddAudio = { viewModel.switchToRecorder() },
                        onAddFile = { filePickerLauncher.launch("application/*") }
                    )
                    "record" -> AudioRecorderDialogContent(
                        themeColor = viewModel.themeColor,
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

    // AddChapterDialog 内联叠加层 — 底部弹簧弹入
    val cv = chapterAnimProgress.value
    if (showAddChapterDialog || cv > 0.01f) {
        Box(Modifier.fillMaxSize()) {
            // scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = cv * 0.5f }
                    .background(Color.Black)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { viewModel.hideAddChapterDialog() }
                    )
            )
            // 卡片 — 从底部 slideUp + scale + fade
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.graphicsLayer {
                        alpha = cv
                        val s = 0.85f + 0.15f * cv
                        scaleX = s
                        scaleY = s
                        translationY = (1f - cv) * 120f
                    }
                ) {
                    AddChapterDialogContent(
                        parentChapterId = parentChapterId,
                        themeColor = viewModel.themeColor,
                        onDismiss = { viewModel.hideAddChapterDialog() },
                        onAdd = { title ->
                            if (parentChapterId != null) {
                                viewModel.addChildChapter(parentChapterId!!, title)
                            } else {
                                viewModel.addChapter(title)
                            }
                        }
                    )
                }
            }
        }
    }

    // BackHandler 放在所有 overlay 之后，确保最高优先级
    val anyDialogOpen = showDrawer || showAddChapterDialog || showAddContentDialog || showRecorderDialog || editingTextItem != null || renamingChapterId != null || renamingContentItemId != null || deleteTargetId != null || deleteChapterTarget != null
    BackHandler(enabled = anyDialogOpen) {
        when {
            showAddChapterDialog -> viewModel.hideAddChapterDialog()
            showAddContentDialog -> viewModel.hideAddContentDialog()
            showRecorderDialog -> if (!isRecording && !recordingCompleted) viewModel.cancelRecording()
            editingTextItem != null -> editingTextItem = null
            renamingChapterId != null -> renamingChapterId = null
            renamingContentItemId != null -> renamingContentItemId = null
            deleteTargetId != null -> deleteTargetId = null
            deleteChapterTarget != null -> deleteChapterTarget = null
            showDrawer -> viewModel.closeDrawer()
        }
    }

    // 全屏图片查看
    if (fullscreenImagePath != null) {
        FullscreenImageDialog(
            imagePath = fullscreenImagePath!!,
            onDismiss = { fullscreenImagePath = null }
        )
    }

    // 文字编辑弹窗 — 内联 graphicsLayer 覆盖层
    val ev = editTextAnimProgress.value
    if (editingTextItem != null || ev > 0.01f) {
        Box(Modifier.fillMaxSize()) {
            // scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ev * 0.5f }
                    .background(Color.Black)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { editingTextItem = null }
                    )
            )
            // 卡片 — 中央 scale + fade
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.graphicsLayer {
                        alpha = ev
                        val s = 0.85f + 0.15f * ev
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    EditTextDialogContent(
                        initialText = editingTextItem?.content ?: "",
                        themeColor = viewModel.themeColor,
                        onDismiss = { editingTextItem = null },
                        onSave = { newText ->
                            val itemId = editingTextItem?.id
                            if (itemId != null) {
                                viewModel.editContentText(itemId, newText)
                            }
                            editingTextItem = null
                        }
                    )
                }
            }
        }
    }

    // 章节重命名弹窗 — 内联 graphicsLayer 覆盖层
    val rnv = renameAnimProgress.value
    if (renamingChapterId != null || rnv > 0.01f) {
        val chapters = notebook?.chapters ?: emptyList()
        val currentTitle = if (renamingChapterId != null) findChapterTitle(chapters, renamingChapterId!!) ?: "" else ""
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = rnv * 0.5f }
                    .background(Color.Black)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { renamingChapterId = null }
                    )
            )
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.graphicsLayer {
                        alpha = rnv
                        val s = 0.85f + 0.15f * rnv
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    EditTextDialogContent(
                        initialText = currentTitle,
                        themeColor = viewModel.themeColor,
                        title = "重命名章节",
                        onDismiss = { renamingChapterId = null },
                        onSave = { newTitle ->
                            val cid = renamingChapterId
                            if (cid != null && newTitle.isNotBlank() && newTitle != currentTitle) {
                                viewModel.renameChapter(cid, newTitle.trim())
                            }
                            renamingChapterId = null
                        }
                    )
                }
            }
        }
    }

    // 内容条目重命名弹窗（视频/音频/文件）
    val rcv = renameContentAnimProgress.value
    if (renamingContentItemId != null || rcv > 0.01f) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = rcv * 0.5f }
                    .background(Color.Black)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { renamingContentItemId = null }
                    )
            )
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.graphicsLayer {
                        alpha = rcv
                        val s = 0.85f + 0.15f * rcv
                        scaleX = s
                        scaleY = s
                    }
                ) {
                    EditTextDialogContent(
                        initialText = renamingContentFileName,
                        themeColor = viewModel.themeColor,
                        title = "重命名",
                        onDismiss = { renamingContentItemId = null },
                        onSave = { newName ->
                            val id = renamingContentItemId
                            if (id != null && newName.isNotBlank()) {
                                viewModel.renameContentItem(id, newName.trim())
                            }
                            renamingContentItemId = null
                        }
                    )
                }
            }
        }
    }
}

/**
 * 在章节列表中递归查找章节标题
 */
private fun findChapterTitle(chapters: List<Chapter>, chapterId: String): String? {
    for (ch in chapters) {
        if (ch.id == chapterId) return ch.title
        val found = findChapterTitle(ch.children, chapterId)
        if (found != null) return found
    }
    return null
}

/**
 * 扁平化的目录树节点，用于 LazyColumn 虚拟化渲染
 */
data class FlatChapterNode(
    val chapter: Chapter,
    val level: Int,
    val index: Int,
    val siblings: List<Chapter>,
    val isExpanded: Boolean,
    val isCurrent: Boolean,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false
)

/**
 * 根据 expandedChapterIds 预计算可见的扁平节点列表。
 * 只有展开节点的子节点才被包含，折叠节点的子节点被排除。
 */
private fun flattenChapterTree(
    chapters: List<Chapter>,
    expandedIds: Set<String>,
    currentChapterId: String?,
    level: Int = 0
): List<FlatChapterNode> {
    val result = mutableListOf<FlatChapterNode>()
    for ((idx, ch) in chapters.withIndex()) {
        val isExpanded = ch.id in expandedIds
        result.add(
            FlatChapterNode(
                chapter = ch,
                level = level,
                index = idx,
                siblings = chapters,
                isExpanded = isExpanded,
                isCurrent = ch.id == currentChapterId,
                canMoveUp = idx > 0,
                canMoveDown = idx < chapters.size - 1
            )
        )
        if (isExpanded && ch.children.isNotEmpty()) {
            result.addAll(
                flattenChapterTree(ch.children, expandedIds, currentChapterId, level + 1)
            )
        }
    }
    return result
}

/**
 * 过滤章节列表：保留已标记章节及其所有祖先
 */
private fun filterChaptersKeepAncestors(chapters: List<Chapter>): List<Chapter> {
    return chapters.filter { hasMarkedDescendant(it) }
}

private fun hasMarkedDescendant(chapter: Chapter): Boolean {
    if (chapter.isMarked) return true
    return chapter.children.any { hasMarkedDescendant(it) }
}

@Composable
fun EmptyChapterState(modifier: Modifier = Modifier, themeColor: Color, onAddChapter: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(themeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = themeColor
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "还没有章节",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "添加你的第一个章节，开始记录学习内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddChapter,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加章节")
            }
        }
    }
}

@Composable
fun ChapterHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun EmptyContentHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.NoteAdd,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "点击右下角按钮添加内容",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 目录树节点 — 含展开/折叠、上移、下移、添加子章节、删除
 */
@Composable
fun ChapterTreeItem(
    chapter: Chapter,
    level: Int,
    viewModel: ReaderViewModel,
    siblings: List<Chapter> = emptyList(),
    isExpanded: Boolean = false,
    isCurrent: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false
) {
    val idx = remember(chapter.id, siblings) { siblings.indexOfFirst { it.id == chapter.id } }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sharedSource = remember { MutableInteractionSource() }

    val colorScheme = MaterialTheme.colorScheme
    val levelColor = remember(level) {
        when (level) {
            0 -> colorScheme.primary
            1 -> colorScheme.secondary
            2 -> colorScheme.tertiary
            else -> colorScheme.outline
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = levelColor,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
            .then(
                if (isCurrent) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) else Modifier
            )
            .clickable(
                indication = null,
                interactionSource = sharedSource,
                onClick = { viewModel.switchChapter(chapter.id) }
            )
            .padding(
                start = (12 + level * 16).dp,
                top = 8.dp,
                bottom = 8.dp,
                end = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
            // 展开/折叠（轻量，无ripple）
            if (chapter.children.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(indication = null, interactionSource = sharedSource) {
                            viewModel.toggleChapterExpanded(chapter.id)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(30.dp))
            }

            // 章节标题
            Text(
                text = chapter.title,
                style = when (level) {
                    0 -> MaterialTheme.typography.titleSmall
                    1 -> MaterialTheme.typography.bodyLarge
                    else -> MaterialTheme.typography.bodyMedium
                },
                fontWeight = when (level) {
                    0 -> FontWeight.Bold
                    1 -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                },
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 操作按钮区（全部无ripple）
            Row {
                // 标记
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(indication = null, interactionSource = sharedSource) {
                            viewModel.toggleChapterMarked(chapter.id)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (chapter.isMarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "标记",
                        modifier = Modifier.size(14.dp),
                        tint = if (chapter.isMarked) Color(0xFFFFA000)
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                // 删除
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(indication = null, interactionSource = sharedSource) {
                            showDeleteConfirm = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "删除",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                }
                // 上移
                if (canMoveUp) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(indication = null, interactionSource = sharedSource) {
                                viewModel.swapChapters(chapter.id, siblings[idx - 1].id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(14.dp))
                    }
                }
                // 下移
                if (canMoveDown) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(indication = null, interactionSource = sharedSource) {
                                viewModel.swapChapters(chapter.id, siblings[idx + 1].id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(14.dp))
                    }
                }
                // 添加子章节
                if (chapter.level < 6) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(indication = null, interactionSource = sharedSource) {
                                viewModel.showAddChapterDialog(chapter.id)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加子章节", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

}

/**
 * 内容条目卡片 - 支持图片点击放大、视频播放
 */
@Composable
fun ContentItemCard(
    item: ContentItem,
    absolutePath: String,
    onDelete: () -> Unit,
    onEditContent: (() -> Unit)? = null,
    onToggleMark: (() -> Unit)? = null,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onImageClick: (path: String) -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val markColor = Color(0xFFFFA000)

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (item.isMarked) Modifier.border(1.5.dp, markColor, shape)
                else Modifier
            )
            .background(if (item.isMarked) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Column {
            // 标记星标 + 内容
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
            when (item.type) {
                ContentType.TEXT -> {
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 26.sp
                    )
                }

                ContentType.IMAGE -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(absolutePath))
                            .crossfade(false)
                            .build(),
                        contentDescription = "图片，点击可放大",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageClick(absolutePath) },
                        contentScale = ContentScale.FillWidth
                    )
                }

                ContentType.VIDEO -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val file = File(absolutePath)
                                if (!file.exists()) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "视频文件不存在",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    return@clickable
                                }
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "video/*")
                                        addFlags(
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    or Intent.FLAG_ACTIVITY_NEW_TASK
                                        )
                                    }
                                    val chooser = Intent.createChooser(intent, "选择播放器")
                                    context.startActivity(chooser)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "无法播放：${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "播放视频",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "视频文件 - 点击播放",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = File(absolutePath).name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                ContentType.AUDIO -> {
                    AudioPlayerBar(audioPath = absolutePath)
                }

                ContentType.FILE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.InsertDriveFile,
                                contentDescription = "文件",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = File(absolutePath).name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
                }
                if (onToggleMark != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onToggleMark() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (item.isMarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "标记",
                            modifier = Modifier.size(20.dp),
                            tint = if (item.isMarked) markColor
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Row {
                    if (canMoveUp) {
                        Box(
                            modifier = Modifier
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onMoveUp)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("上移", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (canMoveDown) {
                        Box(
                            modifier = Modifier
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onMoveDown)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("下移", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (onEditContent != null) {
                    Box(
                        modifier = Modifier
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onEditContent)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("编辑", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Box(
                    modifier = Modifier
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = { showDeleteConfirm = true })
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

}

/**
 * 全屏图片查看对话框 - 支持双指缩放
 */
@Composable
fun FullscreenImageDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (scale != 1f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        onDismiss()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "全屏查看",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .clip(RoundedCornerShape(0.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun AddContentDialog(
    themeColor: Color,
    onDismiss: () -> Unit,
    onAddText: (String) -> Unit,
    onAddImage: () -> Unit,
    onAddVideo: () -> Unit,
    onAddAudio: () -> Unit,
    onAddFile: () -> Unit
) {
    var textContent by remember { mutableStateOf("") }
    var showTextInput by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterHorizontally),
                tint = themeColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "添加内容",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = showTextInput,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
                            initialOffsetX = { it / 4 }
                        ) + fadeIn(tween(200)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
                                targetOffsetX = { -it / 4 }
                            ) + fadeOut(tween(150))
                        )
                    } else {
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
                            initialOffsetX = { -it / 4 }
                        ) + fadeIn(tween(200)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
                                targetOffsetX = { it / 4 }
                            ) + fadeOut(tween(150))
                        )
                    }
                },
                label = "addContentPanel"
            ) { isTextInput ->
                if (isTextInput) {
                    OutlinedTextField(
                        value = textContent,
                        onValueChange = { textContent = it },
                        label = { Text("输入文字内容") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 8,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = themeColor,
                            focusedLabelColor = themeColor,
                            cursorColor = themeColor
                        )
                    )
                } else {
                    Column {
                        ContentOptionButton(
                            icon = Icons.Rounded.TextFields,
                            title = "添加文字",
                            themeColor = themeColor,
                            onClick = { showTextInput = true }
                        )
                        ContentOptionButton(
                            icon = Icons.Rounded.Image,
                            title = "添加图片",
                            themeColor = themeColor,
                            onClick = onAddImage
                        )
                        ContentOptionButton(
                            icon = Icons.Rounded.Videocam,
                            title = "添加视频",
                            themeColor = themeColor,
                            onClick = onAddVideo
                        )
                        ContentOptionButton(
                            icon = Icons.Rounded.AudioFile,
                            title = "添加音频",
                            themeColor = themeColor,
                            onClick = onAddAudio
                        )
                        ContentOptionButton(
                            icon = Icons.Rounded.InsertDriveFile,
                            title = "添加文件",
                            themeColor = themeColor,
                            onClick = onAddFile
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    if (showTextInput) showTextInput = false else onDismiss()
                }) {
                    Text(if (showTextInput) "返回" else "取消", color = themeColor)
                }
                if (showTextInput) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onAddText(textContent) },
                        enabled = textContent.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("添加") }
                }
            }
        }
    }
}

@Composable
fun ContentOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    themeColor: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = themeColor)
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), color = themeColor)
    }
}

@Composable
fun AddChapterDialogContent(
    parentChapterId: String?,
    themeColor: Color,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                Icons.Rounded.CreateNewFolder,
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (parentChapterId != null) "添加子章节" else "添加章节",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("章节标题") },
                placeholder = {
                    Text(if (parentChapterId != null) "例如：1.1 行列式的定义" else "例如：第1章 行列式")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themeColor, focusedLabelColor = themeColor)
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = themeColor)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { if (title.isNotBlank()) onAdd(title.trim()) },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                ) { Text("添加") }
            }
        }
    }
}

@Composable
fun AudioRecorderDialogContent(
    themeColor: Color,
    isRecording: Boolean,
    recordingSeconds: Int,
    errorMessage: String?,
    recordingCompleted: Boolean = false,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveRecording: () -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    val secs = recordingSeconds
    val timeText = "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"

    // 录制中呼吸红点动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 录音权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartRecording()
    }

    val startWithPermission: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onStartRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // 确认按钮颜色动画: primary → 红色
    val confirmColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFE53935) else themeColor,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "confirmColor"
    )

    Surface(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = null,
                tint = if (isRecording) Color(0xFFE53935) else themeColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))

            // 使用 AnimatedContent 做录制状态切换
            AnimatedContent(
                targetState = when {
                    recordingCompleted -> "completed"
                    isRecording -> "recording"
                    errorMessage != null -> "error"
                    else -> "idle"
                },
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "recorderState"
            ) { state ->
                when (state) {
                    "recording" -> RecordInProgressContent(pulseScale, timeText)
                    "error" -> Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE53935),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    "completed" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "录音完成",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            timeText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "选择保存或取消",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> IdleRecorderContent()
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (recordingCompleted) {
                    TextButton(onClick = onCancel) {
                        Text("取消", color = themeColor)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onSaveRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("保存")
                    }
                } else {
                    TextButton(onClick = onCancel) {
                        Text(if (isRecording) "取消" else "返回", color = themeColor)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = if (isRecording) onStopRecording else startWithPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = confirmColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isRecording) {
                            Text("停止录音")
                        } else {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("开始录音")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleRecorderContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "录音",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "需要麦克风权限才能录音",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "点击下方按钮授权并开始",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordInProgressContent(pulseScale: Float, timeText: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "正在录音...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))

        // 红点弹入
        val dotEnterScale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
            label = "dotEnter"
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = pulseScale * dotEnterScale
                    scaleY = pulseScale * dotEnterScale
                }
                .clip(CircleShape)
                .background(Color(0xFFE53935)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Spacer(Modifier.height(16.dp))

        // 计时器滑入
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                animationSpec = spring(0.5f, 500f),
                initialOffsetY = { -16 }
            ) + fadeIn(tween(300))
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "点击停止完成录音",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 文字编辑弹窗
 */
@Composable
fun EditTextDialogContent(
    initialText: String,
    themeColor: Color,
    title: String = "编辑文字",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    Surface(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 10,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themeColor)
            )

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = themeColor)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { onSave(text.trim()) },
                    enabled = text.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("保存") }
            }
        }
    }
}

/**
 * 内建音频播放条 — 在线播放录音/音频文件
 */
@Composable
fun AudioPlayerBar(audioPath: String) {
    val file = File(audioPath)
    var isPlaying by remember { mutableStateOf(false) }
    var currentMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    val mediaPlayer = remember { MediaPlayer() }

    LaunchedEffect(audioPath) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(audioPath)
            mediaPlayer.prepare()
            durationMs = mediaPlayer.duration
            currentMs = 0
            isPlaying = false
        } catch (_: Exception) {
            durationMs = 0
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (mediaPlayer.isPlaying) {
                currentMs = mediaPlayer.currentPosition
                kotlinx.coroutines.delay(200)
            }
            isPlaying = false
            currentMs = mediaPlayer.currentPosition
        }
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    val progress = if (durationMs > 0) currentMs.toFloat() / durationMs.toFloat() else 0f

    val fmt: (Int) -> String = { ms ->
        val s = ms / 1000
        "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                            currentMs = mediaPlayer.currentPosition
                        } else {
                            if (currentMs >= durationMs && durationMs > 0) {
                                mediaPlayer.seekTo(0)
                                currentMs = 0
                            }
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fmt(currentMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(3f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = fmt(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderDialogOverlay(
    animProgress: State<Float>,
    fabBounds: Rect,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val v by animProgress

    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    val pivotX = if (screenSize.width > 0)
        fabBounds.center.x / screenSize.width.toFloat()
    else 0.97f
    val pivotY = if (screenSize.height > 0)
        fabBounds.center.y / screenSize.height.toFloat()
    else 0.98f

    // scrim 层
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = v * 0.5f }
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss
            )
            .onGloballyPositioned { screenSize = it.size }
    )

    // 卡片层 — scale from FAB pivot + fade，scrim 与卡片同步驱动（无 stagger）
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = true
                alpha = v
                val s = 0.08f + 0.92f * v
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(pivotX, pivotY)
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 统一删除确认弹窗 — 中央弹簧弹入 + 纯白界面 + 红色警示图标
 */
@Composable
fun DeleteConfirmOverlay(
    visible: Boolean,
    title: String,
    message: String,
    offsetY: Int = 0,
    cancelColor: Color = MaterialTheme.colorScheme.primary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            animProgress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 350f))
        } else {
            animProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
        }
    }
    val v = animProgress.value
    if (visible || v > 0.01f) {
        Box(Modifier.fillMaxSize()) {
            // scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = v * 0.5f }
                    .background(Color.Black)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss
                    )
            )
            // 卡片 — 中央 scale + fade
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .offset(y = offsetY.dp)
                        .graphicsLayer {
                            alpha = v
                            val s = 0.85f + 0.15f * v
                            scaleX = s
                            scaleY = s
                        }
                ) {
                    DeleteConfirmContent(
                        title = title,
                        message = message,
                        cancelColor = cancelColor,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmContent(
    title: String,
    message: String,
    cancelColor: Color = MaterialTheme.colorScheme.primary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .heightIn(min = 280.dp)
            .padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color(0xFFE53935),
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = cancelColor)
                }
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                ) {
                    Text("删除", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 保存图片到系统相册 */
private fun saveBitmapToGallery(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    try {
        val name = "SNOTE_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.jpg"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val v = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/Snote")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)
                }
                v.clear()
                v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, v, null, null)
            }
        } else {
            val d = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
                "Snote"
            )
            d.mkdirs()
            java.io.FileOutputStream(java.io.File(d, name)).use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "已保存到相册", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "保存失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
