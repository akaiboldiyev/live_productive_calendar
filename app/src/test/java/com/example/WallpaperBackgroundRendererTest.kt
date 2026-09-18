package com.example

import com.example.render.WallpaperBackgroundRenderer
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperBackgroundRendererTest {

    @Test
    fun `large photo is sampled to protect wallpaper process memory`() {
        assertEquals(
            3,
            WallpaperBackgroundRenderer.calculateInSampleSize(
                sourceWidth = 8000,
                sourceHeight = 6000,
                targetWidth = 1220,
                targetHeight = 2712
            )
        )
    }

    @Test
    fun `invalid dimensions do not attempt a decode sample`() {
        assertEquals(1, WallpaperBackgroundRenderer.calculateInSampleSize(0, 3000, 1080, 2400))
    }
}
