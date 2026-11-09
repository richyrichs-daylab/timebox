package com.example.dayplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.dayplanner.data.Settings
import kotlinx.coroutines.delay

/** Aktuelle Zeit, die sich beim Ablauf einer Zeit-/Tagessperre selbst aktualisiert. */
@Composable
fun rememberNow(lockUntil: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockUntil) {
        while (true) {
            now = System.currentTimeMillis()
            if (lockUntil <= now) break
            delay(minOf(lockUntil - now + 50, 30_000L))
        }
    }
    return now
}

@Composable
fun rememberLockActive(s: Settings): Boolean = s.lockActiveAt(rememberNow(s.lockUntil))
