package com.snote.app.ui.screen.countdown

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snote.app.data.repository.CountdownRepository
import com.snote.app.ui.screen.reader.DeleteConfirmOverlay
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownScreen(
    onBackClick: () -> Unit,
    viewModel: CountdownViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()
    val editItem by viewModel.editDialogItem.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadData() }

    // FAB-origin animation for add dialog
    var fabBounds by remember { mutableStateOf(Rect.Zero) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(showAddDialog) {
        if (showAddDialog) animProgress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 450f))
        else animProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 350f))
    }

    var dialogVisible by remember { mutableStateOf(false) }
    if (showAddDialog) dialogVisible = true
    LaunchedEffect(showAddDialog) {
        if (!showAddDialog) {
            snapshotFlow { animProgress.value }.first { it <= 0.01f }
            dialogVisible = false
        }
    }

    // Edit dialog animation
    val editAnimProgress = remember { Animatable(0f) }
    LaunchedEffect(editItem) {
        if (editItem != null) editAnimProgress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 450f))
        else editAnimProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 350f))
    }

    // 持久化编辑项 — 退出动画期间保持内容可见
    var editItemSnapshot by remember { mutableStateOf<CountdownRepository.CountdownWithDays?>(null) }
    if (editItem != null) editItemSnapshot = editItem
    LaunchedEffect(editItem) {
        if (editItem == null) {
            snapshotFlow { editAnimProgress.value }.first { it <= 0.01f }
            editItemSnapshot = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("倒数日", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                modifier = Modifier
                    .graphicsLayer {
                        val p = (1f - animProgress.value).coerceIn(0f, 1f)
                        alpha = p
                        val s = 0.08f + 0.92f * p; scaleX = s; scaleY = s
                    }
                    .onGloballyPositioned { coords ->
                        fabBounds = Rect(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                    }
            ) { Icon(Icons.Rounded.Add, contentDescription = "添加倒数日") }
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
                        Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("还没有倒数日", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = items, key = { it.item.id }) { countdown ->
                        CountdownCard(
                            item = countdown,
                            formatDate = { viewModel.formatDate(it) },
                            onClick = { viewModel.showEditDialog(countdown) }
                        )
                    }
                }
            }
        }
    }

    // Add dialog overlay (FAB-origin)
    if (dialogVisible || animProgress.value > 0.01f) {
        val v = animProgress.value
        val pivotX = if (screenSize.width > 0) fabBounds.center.x / screenSize.width.toFloat() else 0.97f
        val pivotY = if (screenSize.height > 0) fabBounds.center.y / screenSize.height.toFloat() else 0.98f

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = v * 0.5f }.background(Color.Black)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { viewModel.hideAddDialog() })
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = v; val s = 0.08f + 0.92f * v; scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(pivotX, pivotY); clip = true
            },
            contentAlignment = Alignment.Center
        ) {
            AddCountdownDialogContent(
                onDismiss = { viewModel.hideAddDialog() },
                onAdd = { title, date -> viewModel.addCountdown(title, date) }
            )
        }
    }

    // Edit dialog overlay
    val ev = editAnimProgress.value
    if (editItemSnapshot != null || ev > 0.01f) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ev * 0.5f }.background(Color.Black)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { viewModel.hideEditDialog() })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = ev; val s = 0.08f + 0.92f * ev; scaleX = s; scaleY = s }, contentAlignment = Alignment.Center) {
                editItemSnapshot?.let { currentItem ->
                    EditCountdownDialogContent(
                        item = currentItem,
                        formatDate = { viewModel.formatDate(it) },
                        onDismiss = { viewModel.hideEditDialog() },
                        onSave = { title, date -> viewModel.updateCountdown(currentItem.item.id, title, date) },
                        onDelete = { viewModel.deleteCountdown(currentItem.item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownCard(
    item: CountdownRepository.CountdownWithDays,
    formatDate: (Long) -> String,
    onClick: () -> Unit
) {
    val colorGroup = when {
        item.daysRemaining in 0..2 -> listOf(Color(0xFFC62828), Color(0xFFD32F2F), Color(0xFFB71C1C))
        item.daysRemaining in 3..5 -> listOf(Color(0xFFE65100), Color(0xFFF57C00), Color(0xFFEF6C00))
        else -> listOf(Color(0xFF7B1FA2), Color(0xFFC4A8E8), Color(0xFFF0A0B0), Color(0xFF6A1B9A), Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFF00838F), Color(0xFFA0AAE8))
    }
    val themeColor = colorGroup[kotlin.math.abs(item.item.title.hashCode()) % colorGroup.size]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(Modifier.width(72.dp).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(themeColor.copy(alpha = 0.15f), themeColor.copy(alpha = 0.05f)))), contentAlignment = Alignment.Center) {
                Text("${item.daysRemaining}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = themeColor)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(item.item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(if (item.daysRemaining >= 0) "距离 ${formatDate(item.item.targetDate)}" else "已过 ${formatDate(item.item.targetDate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                if (item.daysRemaining > 0) "还剩 ${item.daysRemaining} 天" else if (item.daysRemaining == 0L) "就是今天！" else "已过去 ${-item.daysRemaining} 天",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = themeColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCountdownDialogContent(
    onDismiss: () -> Unit,
    onAdd: (title: String, targetDate: Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedDateMs by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val displayDate = selectedDateMs?.let {
        java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()).format(java.util.Date(it))
    } ?: "选择日期"

    Surface(
        modifier = Modifier.widthIn(max = 360.dp).padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("添加倒数日", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("事件名称") }, placeholder = { Text("例如：期末考试") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(displayDate)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val d = selectedDateMs ?: return@Button
                        if (title.isNotBlank()) onAdd(title.trim(), d)
                    },
                    enabled = title.isNotBlank() && selectedDateMs != null,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("添加") }
            }
        }
    }

    if (showDatePicker) {
        AnimatedDatePickerOverlay(
            datePickerState = datePickerState,
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    selectedDateMs = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCountdownDialogContent(
    item: CountdownRepository.CountdownWithDays,
    formatDate: (Long) -> String,
    onDismiss: () -> Unit,
    onSave: (title: String, targetDate: Long) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(item.item.title) }
    var selectedDateMs by remember { mutableStateOf(item.item.targetDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = Instant.ofEpochMilli(selectedDateMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    )

    val displayDate = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault()).format(java.util.Date(selectedDateMs))

    Surface(
        modifier = Modifier.widthIn(max = 360.dp).padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("编辑倒数日", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("事件名称") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(displayDate)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
                Row {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) onSave(title.trim(), selectedDateMs)
                        },
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("保存") }
                }
            }
        }
    }

    if (showDatePicker) {
        AnimatedDatePickerOverlay(
            datePickerState = datePickerState,
            onConfirm = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val localDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    selectedDateMs = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // 删除确认 - 使用统一 DeleteConfirmOverlay
    DeleteConfirmOverlay(
        visible = showDeleteConfirm,
        title = "确认删除",
        message = "确定要删除「${item.item.title}」吗？",
        cancelColor = MaterialTheme.colorScheme.primary,
        onConfirm = {
            onDelete()
            showDeleteConfirm = false
        },
        onDismiss = { showDeleteConfirm = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedDatePickerOverlay(
    datePickerState: DatePickerState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val animProgress = remember { Animatable(0f) }
    var isVisible by remember { mutableStateOf(true) }
    var shouldConfirm by remember { mutableStateOf(false) }

    // 打开动画
    LaunchedEffect(Unit) {
        animProgress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
    }

    // 关闭动画
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            animProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
            if (shouldConfirm) {
                onConfirm()
            } else {
                onDismiss()
            }
        }
    }

    val v = animProgress.value
    Box(Modifier.fillMaxSize()) {
        // 背景遮罩
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = v * 0.5f }
                .background(Color.Black)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    shouldConfirm = false
                    isVisible = false
                }
        )
        // DatePicker 卡片
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.graphicsLayer {
                    alpha = v
                    // 从 0.5 缩放到 1.0（50%变化，更明显）
                    val s = 0.5f + 0.5f * v
                    scaleX = s
                    scaleY = s
                }
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 352.dp).heightIn(max = 560.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column {
                        MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp))) {
                            DatePicker(
                                state = datePickerState,
                                colors = DatePickerDefaults.colors(
                                    containerColor = Color.White,
                                    headlineContentColor = MaterialTheme.colorScheme.primary,
                                    titleContentColor = MaterialTheme.colorScheme.primary,
                                    navigationContentColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                shouldConfirm = false
                                isVisible = false
                            }) { Text("取消", color = MaterialTheme.colorScheme.primary) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                // 先执行确认逻辑，再播放关闭动画
                                shouldConfirm = true
                                isVisible = false
                            }) { Text("确定", color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
}
