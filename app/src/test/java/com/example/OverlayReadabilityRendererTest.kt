package com.example

import android.graphics.Color
import com.example.model.OverlayReadabilityMode
import com.example.render.OverlayReadabilityRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayReadabilityRendererTest {
    @Test
    fun `light content keeps a local dark halo without a backdrop`() {
        val style = OverlayReadabilityRenderer().update(null, OverlayReadabilityMode.LIGHT)

        assertTrue(style.useLightContent)
        assertTrue(Color.alpha(style.colors.contrastHalo) > 0)
        assertTrue(Color.red(style.colors.contrastHalo) < 32)
    }

    @Test
    fun `dark content keeps a local light halo without a backdrop`() {
        val style = OverlayReadabilityRenderer().update(null, OverlayReadabilityMode.DARK)

        assertFalse(style.useLightContent)
        assertTrue(Color.alpha(style.colors.contrastHalo) > 0)
        assertTrue(Color.red(style.colors.contrastHalo) > 220)
    }
}
