package com.snote.app.ui.screen.todo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.snote.app.data.model.ContentItem
import com.snote.app.data.model.ContentType
import java.io.File
import java.util.UUID

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
    val confirmTarget by viewModel.showConfirmDialog.collectAsState()

    val isCompleted = sectionId == "finished"
    val themeColor = if (isCompleted) Color(0xFF2E7D32) else Color(0xFFFF6B35)
    val context = LocalContext.current

    var fabBounds by remember { mutableStateOf(Rect.Zero) }
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(showAddContentDialog) {
        if (showAddContentDialog) animProgress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 450f))
        else animProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 350f))
    }

    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    val deleteAnimProgress = remember { Animatable(1f) }
    LaunchedEffect(deleteTargetId != null) {
        if (deleteTargetId != null) deleteAnimProgress.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 450f))
        else deleteAnimProgress.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 400f))
    }

    var fullscreenImagePath by remember { mutableStateOf<String?>(null) }
    var editingTextItem by remember { mutableStateOf<ContentItem?>(null) }
    var renamingContentItemId by remember { mutableStateOf<String?>(null) }
    var renamingContentFileName by remember { mutableStateOf("") }
    var videoTargetPath by remember { mutableStateOf<String?>(null) }
    var fileTargetPath by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addImageContent(it) } }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addVideoContent(it) } }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.addFileContent(it) } }

    // Video playback
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
            }
        } catch (_: Exception) {}
        videoTargetPath = null
    }

    // File open
    LaunchedEffect(fileTargetPath) {
        val path = fileTargetPath ?: return@LaunchedEffect
        try {
            val file = File(path)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mime = when {
                    path.endsWith(".pdf", true) -> "application/pdf"
                    path.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    path.endsWith(".doc", true) -> "application/msword"
                    path.endsWith(".xlsx", true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    else -> "*/*"
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "选择应用打开"))
            }
        } catch (_: Exception) {}
        fileTargetPath = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sectionTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
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
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (isCompleted) Icons.Rounded.TaskAlt else Icons.Rounded.PendingActions,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isCompleted) "暂无已完成项" else "暂无待办事项",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            val item = currentItems.find { ci -> ci.id == id }
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
                        onSwapUp = { id1, id2 -> viewModel.swapItems(id1, id2) },
                        onSwapDown = { id1, id2 -> viewModel.swapItems(id1, id2) },
                        onImageClick = { path -> fullscreenImagePath = path },
                        onVideoClick = { path -> videoTargetPath = path },
                        onImageEdit = { _, _ -> },
                        onSaveToGallery = { path ->
                            val bmp = BitmapFactory.decodeFile(path)
                            if (bmp != null) {
                                saveBitmapToGallery(context, bmp)
                                bmp.recycle()
                            }
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
                                clipToPadding = false
                                setItemViewCacheSize(10)
                                itemAnimator = null
                                setPadding(0, 0, 0, px88)
                                this.adapter = adapter
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
                DisposableEffect(adapter) { onDispose { adapter.release() } }
            }
        }
    }

    // Fullscreen image
    if (fullscreenImagePath != null) {
        FullscreenImageDialog(
            imagePath = fullscreenImagePath!!,
            onDismiss = { fullscreenImagePath = null }
        )
    }

    // Confirm dialog
    if (confirmTarget != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirm() },
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = themeColor) },
            title = { Text(if (isCompleted) "撤销完成？" else "确认完成？") },
            text = {
                Text(
                    if (isCompleted) "确定要将此项移回未完成列表吗？"
                    else "确定要将此项标记为已完成？"
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmComplete() }, shape = RoundedCornerShape(12.dp)) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissConfirm() }) { Text("取消") } }
        )
    }

    // Delete confirm
    if (deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除此项吗？") },
            confirmButton = {
                TextButton(
                    onClick = { deleteTargetId?.let { viewModel.deleteItem(it) }; deleteTargetId = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTargetId = null }) { Text("取消") } }
        )
    }

    // Add content overlay
    if (showAddContentDialog || animProgress.value > 0.01f) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = animProgress.value * 0.5f }
                    .background(Color.Black)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        viewModel.hideAddContentDialog()
                    }
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AddTodoContentDialog(
                    themeColor = themeColor,
                    onDismiss = { viewModel.hideAddContentDialog() },
                    onAddText = { viewModel.addTextContent(it) },
                    onAddImage = { imagePickerLauncher.launch("image/*") },
                    onAddVideo = { videoPickerLauncher.launch("video/*") },
                    onAddFile = { filePickerLauncher.launch("application/*") }
                )
            }
        }
    }

    // Text edit dialog
    if (editingTextItem != null) {
        EditTextDialogOverlay(
            initialText = editingTextItem?.content ?: "",
            onDismiss = { editingTextItem = null },
            onSave = { newText ->
                val id = editingTextItem?.id ?: return@EditTextDialogOverlay
                viewModel.addTextContent(newText)
                viewModel.deleteItem(id)
                editingTextItem = null
            }
        )
    }

    // Rename dialog
    if (renamingContentItemId != null) {
        EditTextDialogOverlay(
            initialText = renamingContentFileName,
            title = "重命名",
            onDismiss = { renamingContentItemId = null },
            onSave = { newName ->
                val id = renamingContentItemId ?: return@EditTextDialogOverlay
                if (newName.isNotBlank()) viewModel.renameTodoItem(id, newName.trim())
                renamingContentItemId = null
            }
        )
    }
}

@Composable
fun FullscreenImageDialog(imagePath: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            val bmp = remember { BitmapFactory.decodeFile(imagePath) }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun EditTextDialogOverlay(
    initialText: String,
    title: String = "编辑文字",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank(), shape = RoundedCornerShape(12.dp)) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun AddTodoContentDialog(
    themeColor: Color,
    onDismiss: () -> Unit,
    onAddText: (String) -> Unit,
    onAddImage: () -> Unit,
    onAddVideo: () -> Unit,
    onAddFile: () -> Unit
) {
    var showTextInput by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.widthIn(max = 340.dp).padding(24.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("添加待办", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ContentTypeBtn(Icons.Rounded.TextFields, "文字", themeColor) { showTextInput = true }
                ContentTypeBtn(Icons.Rounded.Image, "图片", themeColor, onAddImage)
                ContentTypeBtn(Icons.Rounded.Videocam, "视频", themeColor, onAddVideo)
                ContentTypeBtn(Icons.Rounded.InsertDriveFile, "文件", themeColor, onAddFile)
            }
            if (showTextInput) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = textInput, onValueChange = { textInput = it },
                    placeholder = { Text("输入待办内容...") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 3
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showTextInput = false; textInput = "" }) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (textInput.isNotBlank()) { onAddText(textInput.trim()); textInput = ""; showTextInput = false } },
                        enabled = textInput.isNotBlank(), shape = RoundedCornerShape(12.dp)
                    ) { Text("添加") }
                }
            }
        }
    }
}

@Composable
private fun ContentTypeBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Box(Modifier.size(48.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun saveBitmapToGallery(context: android.content.Context, bmp: android.graphics.Bitmap) {
    try {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "snote_${UUID.randomUUID()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out) }
        }
    } catch (_: Exception) {}
}
