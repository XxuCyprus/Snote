package com.snote.app.ui.screen.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onMyNotesClick: () -> Unit,
    onTodoClick: () -> Unit,
    onStatsClick: () -> Unit,
    onCountdownClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val unfinishedCount by viewModel.unfinishedCount.collectAsState()
    val todayMinutes by viewModel.todayStudyMinutes.collectAsState()

    // 回到主页时刷新数据
    val mainLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(mainLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        mainLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { mainLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val visibility = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibility.targetState = true }

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
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
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
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 问候语
            Text(
                text = "今天想学点什么？",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (todayMinutes > 0) "今日已学习 ${todayMinutes} 分钟" else "开始专注学习吧",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 2x2 网格
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ModuleCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.MenuBook,
                        title = "我的笔记",
                        subtitle = "管理笔记本与学习资料",
                        badge = null,
                        colors = listOf(Color(0xFF1565C0), Color(0xFF1976D2)),
                        onClick = onMyNotesClick,
                        delayMs = 0,
                        visible = visibility.targetState
                    )
                    ModuleCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.CheckCircle,
                        title = "待办中心",
                        subtitle = "待办事项追踪与管理",
                        badge = if (unfinishedCount > 0) "$unfinishedCount" else null,
                        colors = listOf(Color(0xFF2E7D32), Color(0xFF43A047)),
                        onClick = onTodoClick,
                        delayMs = 80,
                        visible = visibility.targetState
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ModuleCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.BarChart,
                        title = "专注统计",
                        subtitle = "今日学习时长统计",
                        badge = null,
                        colors = listOf(Color(0xFFE65100), Color(0xFFFF7043)),
                        onClick = onStatsClick,
                        delayMs = 160,
                        visible = visibility.targetState
                    )
                    ModuleCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.DateRange,
                        title = "倒数日",
                        subtitle = "重要日期倒计时",
                        badge = null,
                        colors = listOf(Color(0xFF7B1FA2), Color(0xFFAB47BC)),
                        onClick = onCountdownClick,
                        delayMs = 240,
                        visible = visibility.targetState
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String?,
    colors: List<Color>,
    onClick: () -> Unit,
    delayMs: Int,
    visible: Boolean
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press"
    )

    // 入场动画
    val entrance by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            delayMillis = delayMs,
            easing = FastOutSlowInEasing
        ),
        label = "entrance"
    )

    Card(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                alpha = entrance
                scaleX = scale
                scaleY = scale
                translationY = (1f - entrance) * 40f
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                pressed = true
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // 解除按压状态
        LaunchedEffect(pressed) {
            if (pressed) {
                kotlinx.coroutines.delay(150)
                pressed = false
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 顶部渐变装饰条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        brush = Brush.horizontalGradient(colors = colors)
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    colors[0].copy(alpha = 0.15f),
                                    colors[1].copy(alpha = 0.08f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = colors[0]
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 标题 + badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Badge(
                            containerColor = colors[0],
                            contentColor = Color.White
                        ) {
                            Text(
                                text = badge,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
