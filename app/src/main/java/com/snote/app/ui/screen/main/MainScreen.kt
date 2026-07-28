package com.snote.app.ui.screen.main

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snote.app.ui.theme.LocalSnoteGradientColors

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
    val currentQuote by viewModel.currentQuote.collectAsState()

    val mainLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(mainLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        mainLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { mainLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 四张卡片的入场动画 stagger
    val visible1 by animateFloatAsState(1f, tween(400, easing = FastOutSlowInEasing), label = "v1")
    val visible2 by animateFloatAsState(1f, tween(400, 80, FastOutSlowInEasing), label = "v2")
    val visible3 by animateFloatAsState(1f, tween(400, 160, FastOutSlowInEasing), label = "v3")
    val visible4 by animateFloatAsState(1f, tween(400, 240, FastOutSlowInEasing), label = "v4")

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
                    IconButton(onClick = { viewModel.prepareNextQuote(); onSettingsClick() }) {
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = currentQuote,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "今日已学习 ${todayMinutes} 分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 2x2 网格
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ModuleCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = Icons.Rounded.MenuBook,
                        title = "我的笔记",
                        subtitle = "管理笔记本与学习资料",
                        badge = null,
                        colors = listOf(LocalSnoteGradientColors.current.start, LocalSnoteGradientColors.current.end),
                        onClick = { viewModel.prepareNextQuote(); onMyNotesClick() },
                        progress = visible1
                    )
                    ModuleCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = Icons.Rounded.CheckCircle,
                        title = "待办中心",
                        subtitle = "待办事项追踪与管理",
                        badge = if (unfinishedCount > 0) "$unfinishedCount" else null,
                        colors = listOf(Color(0xFF2E7D32), Color(0xFF43A047)),
                        onClick = { viewModel.prepareNextQuote(); onTodoClick() },
                        progress = visible2
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ModuleCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = Icons.Rounded.BarChart,
                        title = "专注统计",
                        subtitle = "今日学习时长统计",
                        badge = null,
                        colors = listOf(Color(0xFFE65100), Color(0xFFFF7043)),
                        onClick = { viewModel.prepareNextQuote(); onStatsClick() },
                        progress = visible3
                    )
                    ModuleCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        icon = Icons.Rounded.DateRange,
                        title = "倒数日",
                        subtitle = "重要日期倒计时",
                        badge = null,
                        colors = listOf(Color(0xFF7B1FA2), Color(0xFFAB47BC)),
                        onClick = { viewModel.prepareNextQuote(); onCountdownClick() },
                        progress = visible4
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
    progress: Float
) {
    var pressed by remember { mutableStateOf(false) }
    val scale = if (pressed) 0.95f else 1f

    Card(
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                scaleX = scale
                scaleY = scale
                translationY = (1f - progress) * 30f
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
        LaunchedEffect(pressed) {
            if (pressed) {
                kotlinx.coroutines.delay(150)
                pressed = false
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(brush = Brush.horizontalGradient(colors = colors))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
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
                            Text(text = badge, fontSize = 11.sp)
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
