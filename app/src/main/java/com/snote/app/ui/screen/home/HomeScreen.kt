package com.snote.app.ui.screen.home

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.snote.app.data.model.Notebook
import com.snote.app.ui.theme.*
import com.snote.app.ui.screen.reader.DeleteConfirmOverlay

private enum class DialogKind { NONE, CREATE, EDIT }

/**
 * 首页 - 笔记本列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNotebookClick: (notebookId: String, chapterId: String) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val notebooks by viewModel.notebooks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val editingNotebook by viewModel.editingNotebook.collectAsState()
    val showStoragePermDialog by viewModel.showStoragePermissionDialog.collectAsState()
    val context = LocalContext.current
    var fabBounds by remember { mutableStateOf(Rect.Zero) }
    var homeRecyclerView by remember { mutableStateOf<RecyclerView?>(null) }
    var isRestoringScroll by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Notebook?>(null) }

    val prefs = remember { context.getSharedPreferences("snote_scroll", Context.MODE_PRIVATE) }

    // 统一动画进度：0f=关闭, 1f=对话框完全打开
    val animProgress = remember { Animatable(0f) }
    val dialogVisible = showCreateDialog || showEditDialog
    LaunchedEffect(dialogVisible) {
        if (dialogVisible) {
            animProgress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 450f))
        } else {
            animProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 350f))
        }
    }

    // 删除确认弹窗 FAB 动画
    val deleteAnimProgress = remember { Animatable(1f) }
    LaunchedEffect(deleteTarget != null) {
        if (deleteTarget != null) {
            deleteAnimProgress.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 450f))
        } else {
            deleteAnimProgress.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 400f))
        }
    }

    // 持久化对话框类型 — 退出动画期间保持内容可见
    var dialogKind by remember { mutableStateOf<DialogKind>(DialogKind.NONE) }
    if (showCreateDialog) dialogKind = DialogKind.CREATE
    else if (showEditDialog && editingNotebook != null) dialogKind = DialogKind.EDIT
    LaunchedEffect(dialogVisible) {
        if (!dialogVisible) {
            snapshotFlow { animProgress.value }
                .first { it <= 0.01f }
            dialogKind = DialogKind.NONE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Snote",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .graphicsLayer {
                        val dialogProgress = 1f - animProgress.value
                        val fabProgress = (dialogProgress * deleteAnimProgress.value).coerceIn(0f, 1f)
                        alpha = fabProgress
                        val s = 0.08f + 0.92f * fabProgress
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
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建笔记本")
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (notebooks.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            val notebookAdapter = remember {
                NotebookAdapter(
                    onClick = { notebook ->
                        val chapterId = viewModel.getLastReadChapterId(notebook.id)
                        onNotebookClick(notebook.id, chapterId)
                    },
                    onEdit = { viewModel.showEditDialog(it) },
                    onDelete = { deleteTarget = it }
                )
            }

            // 提交数据 + 恢复滚动（从 SharedPreferences 读取，跨一切生命周期持久）
            LaunchedEffect(homeRecyclerView, notebooks) {
                if (notebooks.isNotEmpty()) {
                    notebookAdapter.submitList(notebooks)
                    val savedPos = prefs.getInt("scroll_pos", 0)
                    val savedOff = prefs.getInt("scroll_off", 0)
                    if (savedPos > 0) {
                        homeRecyclerView?.let { rv ->
                            val lm = rv.layoutManager as? LinearLayoutManager ?: return@let
                            isRestoringScroll = true
                            lm.scrollToPositionWithOffset(savedPos, savedOff)
                            kotlinx.coroutines.delay(200)
                            isRestoringScroll = false
                        }
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                factory = { ctx ->
                    val px8 = (8 * ctx.resources.displayMetrics.density).toInt()
                    val px88 = (88 * ctx.resources.displayMetrics.density).toInt()
                    RecyclerView(ctx).apply {
                        homeRecyclerView = this
                        layoutManager = LinearLayoutManager(ctx)
                        clipToPadding = false
                        itemAnimator = null
                        adapter = notebookAdapter
                        setPadding(px8, px8, px8, px88)
                        // 实时保存滚动位置
                        addOnScrollListener(object : RecyclerView.OnScrollListener() {
                            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                                if (isRestoringScroll) return
                                val lm = rv.layoutManager as LinearLayoutManager
                                val pos = lm.findFirstVisibleItemPosition()
                                val offset = lm.findViewByPosition(pos)?.top ?: 0
                                if (pos > 0 || offset < -10) {
                                    prefs.edit().putInt("scroll_pos", pos).putInt("scroll_off", -offset).apply()
                                }
                            }
                        })
                    }
                }
            )

            // 删除确认弹窗
            DeleteConfirmOverlay(
                visible = deleteTarget != null,
                title = "确认删除",
                message = "确定要删除「${deleteTarget?.title ?: ""}」及其所有章节内容吗？此操作不可恢复。",
                onConfirm = {
                    deleteTarget?.let { viewModel.deleteNotebook(it.id) }
                    deleteTarget = null
                },
                onDismiss = { deleteTarget = null }
            )
        }
    }

    if (dialogVisible || animProgress.value > 0.01f) {
        DialogOverlay(
            animProgress = animProgress.asState(),
            fabBounds = fabBounds,
            onDismiss = {
                when (dialogKind) {
                    DialogKind.CREATE -> viewModel.hideCreateDialog()
                    DialogKind.EDIT -> viewModel.hideEditDialog()
                    else -> {}
                }
            }
        ) {
            when (dialogKind) {
                DialogKind.CREATE -> CreateNotebookDialog(
                    onDismiss = { viewModel.hideCreateDialog() },
                    onCreate = { title, description ->
                        viewModel.createNotebook(title, description)
                    }
                )
                DialogKind.EDIT -> editingNotebook?.let { notebook ->
                    EditNotebookDialog(
                        notebook = notebook,
                        onDismiss = { viewModel.hideEditDialog() },
                        onSave = { title, description ->
                            viewModel.updateNotebook(notebook.id, title, description)
                        }
                    )
                }
                else -> {}
            }
        }
    }

    // 存储权限弹窗 - 一键跳转系统设置
    if (showStoragePermDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissStoragePermissionDialog() },
            icon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
            title = { Text("需要存储权限") },
            text = {
                Text(
                    "Snote 将数据存储在 Documents/Snote/ 目录中，以便卸载重装后数据不丢失。" +
                    "请在接下来的设置页面中授予「所有文件访问权限」。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissStoragePermissionDialog()
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissStoragePermissionDialog() }) {
                    Text("稍后再说")
                }
            }
        )
    }
}

@Composable
fun DialogOverlay(
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

    // 卡片层 — scale from FAB pivot + fade
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

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
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
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    @Suppress("DEPRECATION") Icons.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "开始你的学习之旅",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击右下角按钮创建第一个笔记本\n把课堂笔记、截图、视频都整理在一起",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * 笔记本卡片 - 现代精美设计
 */
@Composable
fun NotebookCard(
    notebook: Notebook,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // 顶部渐变装饰条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        @Suppress("DEPRECATION") Icons.Rounded.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // 信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notebook.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (notebook.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = notebook.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${notebook.chapters.size} 个章节",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }

                // 操作按钮 - 更紧凑
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "删除",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${notebook.title}」吗？所有内容和文件都将被永久删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun CreateNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

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
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "创建新笔记本",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("笔记本名称") },
                placeholder = { Text("例如：线性代数") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("描述（可选）") },
                placeholder = { Text("例如：2026-2027第一学期") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onCreate(title.trim(), description.trim())
                        }
                    },
                    enabled = title.isNotBlank(),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("创建") }
            }
        }
    }
}

@Composable
fun EditNotebookDialog(
    notebook: Notebook,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String) -> Unit
) {
    var title by remember { mutableStateOf(notebook.title) }
    var description by remember { mutableStateOf(notebook.description) }

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
                Icons.Rounded.Edit,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "编辑笔记本",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("笔记本名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("描述（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(title.trim(), description.trim())
                        }
                    },
                    enabled = title.isNotBlank(),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("保存") }
            }
        }
    }
}
