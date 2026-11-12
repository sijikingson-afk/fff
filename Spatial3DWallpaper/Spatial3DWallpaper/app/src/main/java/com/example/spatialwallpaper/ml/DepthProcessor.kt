package com.example.spatialwallpaper.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DepthProcessor {

    suspend fun processImageTo3DLayers(
        context: Context,
        imageUri: Uri,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    .copy(Bitmap.Config.ARGB_8888, true)
            }

            // Downscale very large images to fit smoothly in wallpaper memory
            val maxDim = 2560
            val scale = minOf(1f, maxDim.toFloat() / maxOf(rawBitmap.width, rawBitmap.height))
            val originalBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    rawBitmap,
                    (rawBitmap.width * scale).toInt(),
                    (rawBitmap.height * scale).toInt(),
                    true
                )
            } else {
                rawBitmap
            }

            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()

            val segmenter = SubjectSegmentation.getClient(options)
            val inputImage = InputImage.fromBitmap(originalBitmap, 0)
            val result = segmenter.process(inputImage).await()

            val foregroundBitmap = result.foregroundBitmap
                ?: return@withContext withContext(Dispatchers.Main) {
                    onComplete(false, "Could not detect a clear foreground subject.")
                }

            val bgFile = File(context.filesDir, "bg_layer.png")
            FileOutputStream(bgFile).use { out ->
                originalBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }

            val fgFile = File(context.filesDir, "fg_layer.png")
            FileOutputStream(fgFile).use { out ->
                foregroundBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            withContext(Dispatchers.Main) {
                onComplete(true, "3D Spatial Layers Ready! Tap 'Set as 3D Live Wallpaper'.")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onComplete(false, "Error: ${e.localizedMessage}")
            }
        }
    }
}
