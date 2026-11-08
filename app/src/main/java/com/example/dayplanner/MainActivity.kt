package com.example.dayplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dayplanner.ui.MainScreen
import com.example.dayplanner.ui.PlannerTheme
import com.example.dayplanner.ui.PlannerViewModel
import com.example.dayplanner.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlannerTheme { PlannerApp() }
        }
    }
}

@Composable
private fun PlannerApp(vm: PlannerViewModel = viewModel()) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }

    if (showSettings) {
        SettingsScreen(vm) { showSettings = false }
    } else {
        MainScreen(vm) { showSettings = true }
    }
}
