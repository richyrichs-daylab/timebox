package com.example.dayplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.dayplanner.data.Design
import com.example.dayplanner.data.PasswordInfo
import com.example.dayplanner.ui.LockScreen
import com.example.dayplanner.ui.MainScreen
import com.example.dayplanner.ui.PlannerViewModel
import com.example.dayplanner.ui.SettingsScreen
import com.example.dayplanner.ui.SkinTheme
import com.example.dayplanner.ui.resolveSkin

class MainActivity : ComponentActivity() {
    private val vm: PlannerViewModel by viewModels()
    private var wasStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlannerRoot(vm) }
    }

    override fun onStart() {
        super.onStart()
        // Beim Start und bei jeder Rückkehr aus dem Hintergrund: immer der aktuelle Tag.
        if (wasStopped) vm.goToday()
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
    }
}

@Composable
private fun PlannerRoot(vm: PlannerViewModel) {
    val settings by vm.settings.collectAsState()
    val password by vm.password.collectAsState()
    val unlocked by vm.unlocked.collectAsState()

    val s = settings
    val pw = password
    val skin = resolveSkin(s?.designId ?: Design.STANDARD, s?.themeMode ?: 0, isSystemInDarkTheme())

    SkinTheme(skin) {
        when {
            s == null || pw == null -> Box(Modifier.fillMaxSize().background(skin.bg))
            pw.type != PasswordInfo.NONE && !unlocked -> LockScreen(pw, vm)
            else -> PlannerApp(vm)
        }
    }
}

@Composable
private fun PlannerApp(vm: PlannerViewModel) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        SettingsScreen(vm) { showSettings = false }
    } else {
        MainScreen(vm) { showSettings = true }
    }
}
