package com.example.spatialwallpaper.service

import android.graphics.*
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.spatialwallpaper.sensor.GyroParallaxTracker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SpatialWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SpatialEngine()

    inner class SpatialEngine : Engine() {
        private var bgBitmap: Bitmap? = null
        private var fgBitmap: Bitmap? = null
        private var tiltX = 0f
        private var tiltY = 0f
        private var isVisible = false

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setShadowLayer(20f, 0f, 6f, Color.argb(95, 0, 0, 0))
        }

        private val tracker = GyroParallaxTracker(applicationContext) { x, y ->
            tiltX = x
            tiltY = y
            if (isVisible) drawFrame()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                loadLayers()
                tracker.start()
                drawFrame()
            } else {
                tracker.stop()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            isVisible = false
            tracker.stop()
        }

        private fun loadLayers() {
            val bgFile = File(filesDir, "bg_layer.png")
            val fgFile = File(filesDir, "fg_layer.png")
            if (bgFile.exists()) bgBitmap = BitmapFactory.decodeFile(bgFile.absolutePath)
            if (fgFile.exists()) fgBitmap = BitmapFactory.decodeFile(fgFile.absolutePath)
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()

                canvas.drawColor(Color.BLACK)

                val bg = bgBitmap ?: return
                val fg = fgBitmap

                val prefs = getSharedPreferences("spatial_prefs", MODE_PRIVATE)
                val depthMultiplier = prefs.getFloat("depth_strength", 1.0f)

                val scale = maxOf(w / bg.width, h / bg.height) * 1.12f
                val scaledW = bg.width * scale
                val scaledH = bg.height * scale
                val baseLeft = (w - scaledW) / 2f
                val baseTop = (h - scaledH) / 2f

                // 1. Background layer shifts opposite to tilt
                val bgShiftX = -tiltX * (w * 0.035f) * depthMultiplier
                val bgShiftY = -tiltY * (h * 0.025f) * depthMultiplier
                canvas.drawBitmap(
                    bg,
                    null,
                    RectF(baseLeft + bgShiftX, baseTop + bgShiftY, baseLeft + scaledW + bgShiftX, baseTop + scaledH + bgShiftY),
                    paint
                )

                // 2. iOS-Style Depth Clock Layer tucked behind subject
                if (prefs.getBoolean("show_depth_clock", true)) {
                    clockPaint.textSize = w * 0.23f
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    val clockX = w / 2f + (tiltX * w * 0.01f * depthMultiplier)
                    val clockY = h * 0.26f + (tiltY * h * 0.01f * depthMultiplier)
                    canvas.drawText(timeStr, clockX, clockY, clockPaint)
                }

                // 3. Foreground subject shifts forward with tilt
                if (fg != null) {
                    val fgShiftX = tiltX * (w * 0.048f) * depthMultiplier
                    val fgShiftY = tiltY * (h * 0.034f) * depthMultiplier
                    canvas.drawBitmap(
                        fg,
                        null,
                        RectF(baseLeft + fgShiftX, baseTop + fgShiftY, baseLeft + scaledW + fgShiftX, baseTop + scaledH + fgShiftY),
                        paint
                    )
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
