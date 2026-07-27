package com.snote.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.snote.app.ui.screen.countdown.CountdownScreen
import com.snote.app.ui.screen.home.HomeScreen
import com.snote.app.ui.screen.main.MainScreen
import com.snote.app.ui.screen.reader.ReaderScreen
import com.snote.app.ui.screen.search.SearchScreen
import com.snote.app.ui.screen.settings.SettingsScreen
import com.snote.app.ui.screen.stats.FocusStatsScreen
import com.snote.app.ui.screen.todo.TodoCenterScreen
import com.snote.app.ui.screen.todo.TodoContentScreen

private const val TRANSITION_DURATION = 350
private val TRANSITION_EASING = androidx.compose.animation.core.FastOutSlowInEasing

private fun forwardEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    initialOffsetX = { it }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING))

private fun forwardExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    targetOffsetX = { -it / 3 }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))

private fun backEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    initialOffsetX = { -it / 3 }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING))

private fun backExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    targetOffsetX = { it }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))

@Composable
fun SnoteNavGraph() {
    val navController = rememberNavController()

    val safeBack: () -> Unit = remember {
        var lastBackTime = 0L
        {
            val now = System.currentTimeMillis()
            if (now - lastBackTime > 400 &&
                navController.previousBackStackEntry != null
            ) {
                lastBackTime = now
                navController.popBackStack()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "main",
        enterTransition = { forwardEnterTransition() },
        exitTransition = { forwardExitTransition() },
        popEnterTransition = { backEnterTransition() },
        popExitTransition = { backExitTransition() }
    ) {
        // 主页仪表盘
        composable("main") {
            MainScreen(
                onMyNotesClick = { navController.navigate("my_notes") },
                onTodoClick = { navController.navigate("todo_center") },
                onStatsClick = { navController.navigate("focus_stats") },
                onCountdownClick = { navController.navigate("countdown") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }

        // 我的笔记（原首页）
        composable("my_notes") {
            HomeScreen(
                onNotebookClick = { notebookId, chapterId ->
                    navController.navigate("reader/$notebookId/$chapterId")
                },
                onSettingsClick = { navController.navigate("settings") },
                onSearchClick = { navController.navigate("search") },
                onBackClick = safeBack
            )
        }

        // 阅读器
        composable(
            route = "reader/{notebookId}/{chapterId}",
            arguments = listOf(
                navArgument("notebookId") { type = NavType.StringType },
                navArgument("chapterId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val notebookId = backStackEntry.arguments?.getString("notebookId") ?: ""
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            ReaderScreen(
                notebookId = notebookId,
                chapterId = chapterId,
                onBackClick = safeBack
            )
        }

        // 搜索
        composable("search") {
            SearchScreen(
                onBackClick = safeBack,
                onResultClick = { notebookId, chapterId ->
                    navController.navigate("reader/$notebookId/$chapterId")
                }
            )
        }

        // 设置
        composable("settings") {
            SettingsScreen(onBackClick = safeBack)
        }

        // 待办中心
        composable("todo_center") {
            TodoCenterScreen(
                onBackClick = safeBack,
                onSectionClick = { sectionId ->
                    navController.navigate("todo_content/$sectionId")
                }
            )
        }

        // 待办内容
        composable(
            route = "todo_content/{sectionId}",
            arguments = listOf(
                navArgument("sectionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sectionId = backStackEntry.arguments?.getString("sectionId") ?: "unfinished"
            TodoContentScreen(
                sectionId = sectionId,
                onBackClick = safeBack
            )
        }

        // 专注统计
        composable("focus_stats") {
            FocusStatsScreen(onBackClick = safeBack)
        }

        // 倒数日
        composable("countdown") {
            CountdownScreen(onBackClick = safeBack)
        }
    }
}
