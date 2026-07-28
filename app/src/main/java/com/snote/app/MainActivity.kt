package com.snote.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.snote.app.ui.navigation.SnoteNavGraph
import com.snote.app.ui.screen.permission.PermissionRequiredScreen
import com.snote.app.ui.theme.SnoteTheme
import com.snote.app.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("snote_prefs", Context.MODE_PRIVATE)

        setContent {
            var themeMode by remember {
                mutableStateOf(
                    try {
                        ThemeMode.valueOf(prefs.getString("theme_mode", "PURPLE") ?: "PURPLE")
                    } catch (_: Exception) {
                        ThemeMode.PURPLE
                    }
                )
            }

            // 监听 SharedPreferences 变化，实现即时切换
            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "theme_mode") {
                        try {
                            themeMode = ThemeMode.valueOf(prefs.getString("theme_mode", "PURPLE") ?: "PURPLE")
                        } catch (_: Exception) {
                            themeMode = ThemeMode.PURPLE
                        }
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            SnoteTheme(themeMode = themeMode) {
                var hasPermission by remember { mutableStateOf(checkPermission()) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasPermission = checkPermission()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (hasPermission) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SnoteNavGraph()
                    }
                } else {
                    PermissionRequiredScreen(
                        onPermissionGranted = { hasPermission = true }
                    )
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
