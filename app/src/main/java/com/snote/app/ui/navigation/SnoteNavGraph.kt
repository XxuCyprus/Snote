package com.snote.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.snote.app.ui.screen.home.HomeScreen
import com.snote.app.ui.screen.reader.ReaderScreen
import com.snote.app.ui.screen.search.SearchScreen
import com.snote.app.ui.screen.settings.SettingsScreen

// 统一的页面过渡动画时长和缓动
private const val TRANSITION_DURATION = 350
private val TRANSITION_EASING = androidx.compose.animation.core.FastOutSlowInEasing

/**
 * 前进动画：页面从右侧滑入 + 淡入
 */
private fun forwardEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    initialOffsetX = { it }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING))

/**
 * 前进时旧页面：向左滑出 + 淡出
 */
private fun forwardExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    targetOffsetX = { -it / 3 }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))

/**
 * 返回动画：页面从左侧滑入 + 淡入（跟前进相反）
 */
private fun backEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    initialOffsetX = { -it / 3 }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING))

/**
 * 返回时旧页面：向右滑出 + 淡出
 */
private fun backExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    targetOffsetX = { it }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION / 2, easing = TRANSITION_EASING))

@Composable
fun SnoteNavGraph() {
    val navController = rememberNavController()

    // 防止快速连续返回导致白屏
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
        startDestination = "home",
        enterTransition = { forwardEnterTransition() },
        exitTransition = { forwardExitTransition() },
        popEnterTransition = { backEnterTransition() },
        popExitTransition = { backExitTransition() }
    ) {
        composable("home") {
            HomeScreen(
                onNotebookClick = { notebookId, chapterId ->
                    navController.navigate("reader/$notebookId/$chapterId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onSearchClick = {
                    navController.navigate("search")
                }
            )
        }

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

        composable("search") {
            SearchScreen(
                onBackClick = safeBack,
                onResultClick = { notebookId, chapterId ->
                    navController.navigate("reader/$notebookId/$chapterId")
                }
            )
        }

        composable("settings") {
            SettingsScreen(onBackClick = safeBack)
        }
    }
}
