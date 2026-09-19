package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.WallpaperImageSlot
import java.io.File
import java.io.IOException

/**
 * Owns the app-private background file. A picked content Uri is only used during import;
 * the wallpaper process always reads an app-private [imageFile] instead.
 */
object WallpaperBackgroundStorage {
    private const val TAG = "GOAL_BACKGROUND"
    private const val DIRECTORY = "wallpaper"
    private const val LEGACY_FILE_NAME = "background.jpg"

    fun imageFile(context: Context, slot: WallpaperImageSlot): File = File(
        File(context.filesDir, DIRECTORY),
        when (slot) {
            WallpaperImageSlot.SINGLE -> "background-default.jpg"
            WallpaperImageSlot.DAY -> "background-day.jpg"
            WallpaperImageSlot.EVENING -> "background-evening.jpg"
        }
    )

    /** Supports one safe migration from the 2.0 single-photo filename after an app update. */
    fun imageFileOrLegacy(context: Context, slot: WallpaperImageSlot): File {
        val target = imageFile(context, slot)
        return if (slot == WallpaperImageSlot.SINGLE && !target.isFile) File(target.parentFile, LEGACY_FILE_NAME) else target
    }

    @Throws(IOException::class)
    fun copyFromUri(context: Context, uri: Uri, slot: WallpaperImageSlot) {
        val target = imageFile(context, slot)
        val directory = target.parentFile ?: throw IOException("Wallpaper directory is unavailable")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create wallpaper directory")
        }

        val temporaryFile = File(directory, ".${target.name}.tmp")
        if (temporaryFile.exists() && !temporaryFile.delete()) {
            throw IOException("Unable to clear previous temporary background")
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            } ?: throw IOException("Unable to open selected image")

            if (temporaryFile.length() == 0L) {
                throw IOException("Selected image is empty")
            }

            // The completed temp file is swapped only after the full copy succeeds, so a
            // restarted wallpaper process never observes a partially-written background.
            if (!temporaryFile.renameTo(target)) {
                throw IOException("Unable to finalize background import")
            }
            Log.i(TAG, "Image copied successfully for $slot: ${target.length()} bytes")
        } catch (error: IOException) {
            temporaryFile.delete()
            throw error
        } catch (error: Exception) {
            temporaryFile.delete()
            throw IOException("Unable to import selected image", error)
        }
    }

    fun delete(context: Context, slot: WallpaperImageSlot) {
        val target = imageFile(context, slot)
        if (target.exists() && !target.delete()) {
            Log.w(TAG, "Background file could not be deleted; config still uses black fallback")
        }
        if (slot == WallpaperImageSlot.SINGLE) {
            val legacy = File(target.parentFile, LEGACY_FILE_NAME)
            if (legacy.exists() && !legacy.delete()) Log.w(TAG, "Legacy background file could not be deleted")
        }
    }
}
