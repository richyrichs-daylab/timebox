package com.example.dayplanner

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.dayplanner.data.Design
import com.example.dayplanner.data.PasswordInfo
import com.example.dayplanner.ui.LockScreen
import com.example.dayplanner.ui.MainScreen
import com.example.dayplanner.ui.PlannerViewModel
import com.example.dayplanner.ui.SettingsScreen
import com.example.dayplanner.ui.SkinTheme
import com.example.dayplanner.ui.resolveSkin
import com.example.dayplanner.ui.resolveSkinFor

class MainActivity : ComponentActivity() {
    private val vm: PlannerViewModel by viewModels()
    private var wasStopped = false
    private var settingsRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { PlannerRoot(vm, settingsRequest) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_SETTINGS, false) == true) settingsRequest++
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

    companion object {
        const val EXTRA_SETTINGS = "open_settings"
    }
}

@Composable
private fun PlannerRoot(vm: PlannerViewModel, settingsRequest: Int) {
    val settings by vm.settings.collectAsState()
    val password by vm.password.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()

    val s = settings
    val pw = password
    val skin = remember(s?.customDesign, s?.design, s?.themeMode, dark) {
        if (s == null) resolveSkin(Design.STANDARD, 0, dark) else resolveSkinFor(context, s, dark)
    }

    SkinTheme(skin) {
        when {
            s == null || pw == null -> Box(Modifier.fillMaxSize().background(skin.bg))
            pw.type != PasswordInfo.NONE && !unlocked -> LockScreen(pw, vm)
            else -> PlannerApp(vm, settingsRequest)
        }
    }
}

@Composable
private fun PlannerApp(vm: PlannerViewModel, settingsRequest: Int) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settingsRequest) { if (settingsRequest > 0) showSettings = true }
    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        SettingsScreen(vm) { showSettings = false }
    } else {
        MainScreen(vm) { showSettings = true }
    }
}
