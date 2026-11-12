package com.example.dayplanner.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.example.dayplanner.data.DayRepository
import com.example.dayplanner.data.SettingsRepository
import com.example.dayplanner.widget.WidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArraySet

/** Live-Hintergrund: zeigt den ganzen aktuellen Tag. Ohne Zahnrad, Schloss und Pfeile, nicht bedienbar. */
class TimeboxWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = TimeboxEngine()

    private inner class TimeboxEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var job: Job? = null
        private var width = 0
        private var height = 0
        private var visible = false

        private val minuteTick = Runnable { requestDraw() }
        private val externalRedraw = Runnable { handler.post { requestDraw() } }

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                requestDraw()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            redrawers.add(externalRedraw)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(
                this@TimeboxWallpaperService,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            requestDraw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                requestDraw()
            } else {
                handler.removeCallbacks(minuteTick)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(minuteTick)
        }

        override fun onDestroy() {
            super.onDestroy()
            redrawers.remove(externalRedraw)
            handler.removeCallbacks(minuteTick)
            runCatching { unregisterReceiver(receiver) }
            scope.cancel()
        }

        private fun requestDraw() {
            if (width <= 0 || height <= 0 || !visible) return
            job?.cancel()
            job = scope.launch {
                val app = applicationContext
                val settings = SettingsRepository(app).current()
                val date = LocalDate.now()
                val data = DayRepository(app).load(date)
                val bitmap = WidgetRenderer.renderWallpaper(app, settings, data, date, width, height)
                val holder = surfaceHolder
                try {
                    val canvas = holder.lockCanvas()
                    if (canvas != null) {
                        try {
                            canvas.drawBitmap(bitmap, 0f, 0f, null)
                        } finally {
                            holder.unlockCanvasAndPost(canvas)
                        }
                    }
                } catch (e: Exception) {
                    // Die Fläche wurde gerade zerstört: nichts zu zeichnen.
                }
                bitmap.recycle()

                // Beim Ausgrauen/Hervorheben jede volle Minute neu zeichnen.
                if (settings.grayPast || settings.highlightNow) {
                    val delay = 60_000L - System.currentTimeMillis() % 60_000L + 100L
                    handler.removeCallbacks(minuteTick)
                    handler.postDelayed(minuteTick, delay)
                }
            }
        }
    }

    companion object {
        private val redrawers = CopyOnWriteArraySet<Runnable>()

        /** Zeichnet alle aktiven Live-Hintergründe neu (nach Änderungen in der App). */
        fun redrawAll() {
            redrawers.forEach { it.run() }
        }
    }
}
