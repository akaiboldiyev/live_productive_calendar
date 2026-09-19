package com.example.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.util.Log
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Draws the optional, app-private photo underneath the Goal Dots overlay.
 * It is owned by one wallpaper Engine and never decodes while drawing a frame.
 */
class WallpaperBackgroundRenderer {

    companion object {
        private const val TAG = "GOAL_BACKGROUND"
        private const val MAX_DECODED_PIXELS = 6_000_000L

        /**
         * Calculates a conservative sample size. It keeps enough source detail for a
         * full-surface center crop while placing a hard cap on decoded bitmap memory.
         */
        fun calculateInSampleSize(
            sourceWidth: Int,
            sourceHeight: Int,
            targetWidth: Int,
            targetHeight: Int
        ): Int {
            if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
                return 1
            }
            val coverageSample = min(
                sourceWidth / targetWidth,
                sourceHeight / targetHeight
            ).coerceAtLeast(1)
            val sourcePixels = sourceWidth.toLong() * sourceHeight.toLong()
            val memorySample = ceil(sqrt(sourcePixels.toDouble() / MAX_DECODED_PIXELS)).toInt()
                .coerceAtLeast(1)
            return max(coverageSample, memorySample)
        }

        /** Heavy I/O and decode work; call this off the Engine's render thread. */
        fun decodeBitmap(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
            if (!file.isFile || targetWidth <= 0 || targetHeight <= 0) {
                Log.w(TAG, "Background load failed: background file or surface is unavailable")
                return null
            }
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    Log.w(TAG, "Background load failed: unsupported or damaged image")
                    return null
                }

                val options = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(
                        bounds.outWidth,
                        bounds.outHeight,
                        targetWidth,
                        targetHeight
                    )
                    // Wallpaper photos do not require alpha. Halving pixel memory gives the
                    // service considerably more headroom on memory-constrained OEM devices.
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: run {
                    Log.w(TAG, "Background load failed: BitmapFactory returned null")
                    return null
                }
                val orientedBitmap = applyExifOrientation(file, bitmap)
                Log.i(TAG, "Background bitmap loaded: ${orientedBitmap.width}x${orientedBitmap.height}, sample=${options.inSampleSize}")
                orientedBitmap
            } catch (error: OutOfMemoryError) {
                Log.e(TAG, "Background load failed: insufficient memory", error)
                null
            } catch (error: Exception) {
                Log.e(TAG, "Background load failed", error)
                null
            }
        }

        private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
            val orientation = runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = android.graphics.Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    else -> return bitmap
                }
            }
            return try {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    .also { if (it !== bitmap) bitmap.recycle() }
            } catch (error: OutOfMemoryError) {
                Log.w(TAG, "EXIF orientation skipped because memory is low", error)
                bitmap
            }
        }
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var cachedBitmap: Bitmap? = null
    private var cachedKey: String? = null
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var fallbackLogged = false

    fun isCachedFor(key: String, width: Int, height: Int): Boolean =
        cachedKey == key && cachedBitmap?.isRecycled == false &&
            cachedWidth == width && cachedHeight == height

    fun setBitmap(bitmap: Bitmap?, key: String, width: Int, height: Int) {
        clearCache(logInvalidation = false)
        cachedBitmap = bitmap
        cachedKey = key
        cachedWidth = width
        cachedHeight = height
        fallbackLogged = false
    }

    fun invalidateCache() = clearCache(logInvalidation = true)

    fun currentBitmap(): Bitmap? = cachedBitmap?.takeUnless { it.isRecycled }

    fun drawBackground(canvas: Canvas, hasActiveImage: Boolean, width: Int, height: Int) {
        val bitmap = cachedBitmap
        if (!hasActiveImage || bitmap == null || bitmap.isRecycled) {
            canvas.drawColor(Color.BLACK)
            if (!fallbackLogged) {
                Log.w(TAG, "Background fallback used")
                fallbackLogged = true
            }
            return
        }

        val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val destination = RectF(
            (width - scaledWidth) / 2f,
            (height - scaledHeight) / 2f,
            (width + scaledWidth) / 2f,
            (height + scaledHeight) / 2f
        )
        canvas.drawBitmap(bitmap, null, destination, bitmapPaint)
    }

    fun release() = clearCache(logInvalidation = false)

    private fun clearCache(logInvalidation: Boolean) {
        cachedBitmap?.takeUnless { it.isRecycled }?.recycle()
        cachedBitmap = null
        cachedKey = null
        cachedWidth = 0
        cachedHeight = 0
        fallbackLogged = false
        if (logInvalidation) Log.i(TAG, "Background bitmap cache invalidated")
    }
}
