package com.snote.app.ui.screen.stats

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusStatsScreen(
    onBackClick: () -> Unit,
    viewModel: FocusStatsViewModel = hiltViewModel()
) {
    val slices by viewModel.slices.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()
    val isEmpty by viewModel.isEmpty.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val dateDisplay = viewModel.selectedDateDisplay

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.parse(selectedDate)
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    LaunchedEffect(Unit) { viewModel.loadData() }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("专注统计", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "今日学习时长",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(totalDuration),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 日期选择器
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = {
                    val d = LocalDate.parse(selectedDate).minusDays(1).toString()
                    viewModel.setDate(d)
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "前一天", modifier = Modifier.size(20.dp))
                }
                TextButton(onClick = { showDatePicker = true }) {
                    Text(dateDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                IconButton(
                    onClick = {
                        val d = LocalDate.parse(selectedDate).plusDays(1).toString()
                        if (d <= LocalDate.now().toString()) viewModel.setDate(d)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "后一天", modifier = Modifier.size(20.dp))
                }
            }

            if (isEmpty) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Rounded.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "今天还没有学习记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "打开笔记开始学习吧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.height(24.dp))

                // 扇形图
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val progress = animationProgress.value
                    val background = MaterialTheme.colorScheme.background

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasSize = this.size
                        val radius = canvasSize.minDimension / 2
                        val donutRadius = radius * 0.38f
                        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)

                        var startAngle = -90f
                        for (slice in slices) {
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = slice.sweepAngle * progress,
                                useCenter = true,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2)
                            )
                            startAngle += slice.sweepAngle
                        }

                        // Donut hole
                        drawCircle(
                            color = background,
                            radius = donutRadius,
                            center = center
                        )
                    }

                    // Center text
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(totalDuration + 59) / 60}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 图例
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    slices.forEach { slice ->
                        LegendItem(slice)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDatePicker) {
        val animProgress = remember { Animatable(0f) }
        var isVisible by remember { mutableStateOf(true) }
        var shouldConfirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            animProgress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
        }
        LaunchedEffect(isVisible) {
            if (!isVisible) {
                animProgress.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f))
                if (shouldConfirm) {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                        viewModel.setDate(date)
                    }
                }
                showDatePicker = false
            }
        }

        val v = animProgress.value
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = v * 0.5f }
                    .background(Color.Black)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        shouldConfirm = false
                        isVisible = false
                    }
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.graphicsLayer {
                    alpha = v
                    val s = 0.5f + 0.5f * v; scaleX = s; scaleY = s
                }) {
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
}

@Composable
private fun LegendItem(slice: PieSlice) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(slice.color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = slice.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = formatDuration(slice.durationSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val totalMinutes = (seconds + 59) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分钟"
        else -> "${minutes}分钟"
    }
}
