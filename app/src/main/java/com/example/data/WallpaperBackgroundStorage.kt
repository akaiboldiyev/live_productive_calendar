package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Owns the app-private background file. A picked content Uri is only used during import;
 * the wallpaper process always reads [backgroundFile] instead.
 */
object WallpaperBackgroundStorage {
    private const val TAG = "GOAL_BACKGROUND"
    private const val DIRECTORY = "wallpaper"
    private const val FILE_NAME = "background.jpg"
    private const val TEMP_FILE_NAME = "background.tmp"

    fun backgroundFile(context: Context): File =
        File(File(context.filesDir, DIRECTORY), FILE_NAME)

    @Throws(IOException::class)
    fun copyFromUri(context: Context, uri: Uri) {
        val target = backgroundFile(context)
        val directory = target.parentFile ?: throw IOException("Wallpaper directory is unavailable")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create wallpaper directory")
        }

        val temporaryFile = File(directory, TEMP_FILE_NAME)
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
            Log.i(TAG, "Background copied successfully: ${target.length()} bytes")
        } catch (error: IOException) {
            temporaryFile.delete()
            throw error
        } catch (error: Exception) {
            temporaryFile.delete()
            throw IOException("Unable to import selected image", error)
        }
    }

    fun delete(context: Context) {
        val target = backgroundFile(context)
        if (target.exists() && !target.delete()) {
            Log.w(TAG, "Background file could not be deleted; config still uses black fallback")
        }
    }
}
