package com.example.spatialwallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.spatialwallpaper.ml.DepthProcessor
import com.example.spatialwallpaper.service.SpatialWallpaperService
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var applyButton: Button
    private lateinit var previewImage: ImageView

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            statusText.text = "Extracting 3D subject depth map..."
            applyButton.isEnabled = false
            lifecycleScope.launch {
                DepthProcessor.processImageTo3DLayers(this@MainActivity, uri) { success, msg ->
                    statusText.text = msg
                    applyButton.isEnabled = success
                    if (success) {
                        val fgFile = File(filesDir, "fg_layer.png")
                        if (fgFile.exists()) {
                            previewImage.setImageBitmap(BitmapFactory.decodeFile(fgFile.absolutePath))
                        }
                        Toast.makeText(this@MainActivity, "3D Depth Ready!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("spatial_prefs", MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 80, 56, 56)
        }

        val title = TextView(this).apply {
            text = "Spatial 3D Wallpaper"
            textSize = 26f
        }

        statusText = TextView(this).apply {
            text = "Choose an image from your phone to create an iPhone-style 3D tilt wallpaper."
            setPadding(0, 24, 0, 32)
        }

        previewImage = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(420, 560).apply {
                bottomMargin = 32
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val depthLabel = TextView(this).apply {
            text = "3D Tilt Depth Intensity"
        }

        val depthSlider = Slider(this).apply {
            valueFrom = 0.2f
            valueTo = 2.0f
            value = prefs.getFloat("depth_strength", 1.0f)
            addOnChangeListener { _, value, _ ->
                prefs.edit().putFloat("depth_strength", value).apply()
            }
        }

        val clockSwitch = MaterialSwitch(this).apply {
            text = "Show iOS-style clock behind subject"
            isChecked = prefs.getBoolean("show_depth_clock", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("show_depth_clock", checked).apply()
            }
        }

        val pickButton = Button(this).apply {
            text = "1. Choose Image from Files"
            setOnClickListener { pickImageLauncher.launch("image/*") }
        }

        applyButton = Button(this).apply {
            text = "2. Set as 3D Live Wallpaper"
            isEnabled = File(filesDir, "fg_layer.png").exists()
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, SpatialWallpaperService::class.java)
                    )
                }
                startActivity(intent)
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(previewImage)
        layout.addView(depthLabel)
        layout.addView(depthSlider)
        layout.addView(clockSwitch)
        layout.addView(pickButton)
        layout.addView(applyButton)
        setContentView(layout)
    }
}
