package com.v_chat_sdk.v_video_compressor

import androidx.media3.effect.Presentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class VVideoCropGeometryTest {
    private val full = VVideoCropRect(0.0, 0.0, 1.0, 1.0)

    @Test
    fun normalizedCoordinates_convertFromTopLeftToMedia3Ndc() {
        val cases = mapOf(
            VVideoCropRect(0.0, 0.0, 0.5, 0.5) to
                VVideoMedia3Crop(-1f, 0f, 0f, 1f),
            VVideoCropRect(0.5, 0.0, 1.0, 0.5) to
                VVideoMedia3Crop(0f, 1f, 0f, 1f),
            VVideoCropRect(0.0, 0.5, 0.5, 1.0) to
                VVideoMedia3Crop(-1f, 0f, -1f, 0f),
            VVideoCropRect(0.5, 0.5, 1.0, 1.0) to
                VVideoMedia3Crop(0f, 1f, -1f, 0f),
            VVideoCropRect(0.25, 0.25, 0.75, 0.75) to
                VVideoMedia3Crop(-0.5f, 0.5f, -0.5f, 0.5f),
            full to VVideoMedia3Crop(-1f, 1f, -1f, 1f)
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, VVideoCropGeometry.toMedia3Crop(input))
        }
    }

    @Test
    fun orientedDimensions_handleEveryDiscreteRotation() {
        assertEquals(VVideoSize(1920, 1080), VVideoCropGeometry.orientedSize(1920, 1080, 0))
        assertEquals(VVideoSize(1080, 1920), VVideoCropGeometry.orientedSize(1920, 1080, 90))
        assertEquals(VVideoSize(1920, 1080), VVideoCropGeometry.orientedSize(1920, 1080, 180))
        assertEquals(VVideoSize(1080, 1920), VVideoCropGeometry.orientedSize(1920, 1080, 270))
        assertEquals(0f, VVideoCropGeometry.cropRotationDegrees(0))
        assertEquals(90f, VVideoCropGeometry.cropRotationDegrees(90))
        assertEquals(180f, VVideoCropGeometry.cropRotationDegrees(180))
        assertEquals(270f, VVideoCropGeometry.cropRotationDegrees(270))
    }

    @Test
    fun cropPlan_ordersRotationCropThenPresentation() {
        val plan = VVideoCropGeometry.createPlan(
            displayedWidth = 1920,
            displayedHeight = 1080,
            explicitRotation = 90,
            cropRect = VVideoCropRect(0.0, 0.0, 0.5, 1.0),
            quality = VVideoCompressQuality.HIGH,
            customWidth = 540,
            customHeight = 960,
            dimensionHandling = VDimensionHandling.AUTO_ALIGN
        )

        assertEquals(
            listOf(
                VVideoEffectStep.EXPLICIT_ROTATION,
                VVideoEffectStep.CROP,
                VVideoEffectStep.PRESENTATION
            ),
            plan.effectOrder
        )
        assertEquals(VVideoSize(1080, 1920), plan.orientedSize)
        assertEquals(VVideoPixelCrop(0, 0, 540, 1920), plan.pixelCrop)
        assertEquals(0, plan.outputSize.width % 2)
        assertEquals(0, plan.outputSize.height % 2)
    }

    @Test
    fun presentationModes_preserveCropWithoutStretching() {
        assertEquals(
            VVideoSize(160, 120),
            VVideoCropGeometry.resolveOutputSize(
                VVideoSize(160, 120),
                VVideoCompressQuality.HIGH,
                null,
                null,
                VDimensionHandling.AUTO_ALIGN
            )
        )
        assertEquals(
            VVideoSize(120, 320),
            VVideoCropGeometry.resolveOutputSize(
                VVideoSize(120, 320),
                VVideoCompressQuality.HIGH,
                null,
                null,
                VDimensionHandling.AUTO_ALIGN
            )
        )
        val auto = VVideoCropGeometry.resolveOutputSize(
            VVideoSize(1000, 500),
            VVideoCompressQuality.MEDIUM,
            640,
            480,
            VDimensionHandling.AUTO_ALIGN
        )
        assertTrue(kotlin.math.abs(auto.width.toDouble() / auto.height - 2.0) < 0.01)

        val letterbox = VVideoCropGeometry.createPlan(
            1000,
            500,
            0,
            full,
            VVideoCompressQuality.MEDIUM,
            640,
            480,
            VDimensionHandling.LETTERBOX
        )
        assertEquals(VVideoSize(640, 480), letterbox.outputSize)
        assertEquals(Presentation.LAYOUT_SCALE_TO_FIT, letterbox.presentationLayout)

        assertFailsWith<IllegalArgumentException> {
            VVideoCropGeometry.resolveOutputSize(
                VVideoSize(1000, 500),
                VVideoCompressQuality.MEDIUM,
                640,
                480,
                VDimensionHandling.EXACT
            )
        }
        assertEquals(
            VVideoSize(640, 320),
            VVideoCropGeometry.resolveOutputSize(
                VVideoSize(1000, 500),
                VVideoCompressQuality.MEDIUM,
                640,
                320,
                VDimensionHandling.EXACT
            )
        )
    }

    @Test
    fun inwardPixelRoundingAndTinyCrop_areDeterministic() {
        assertEquals(
            VVideoPixelCrop(101, 51, 899, 449),
            VVideoCropGeometry.pixelCrop(
                VVideoSize(1000, 500),
                VVideoCropRect(0.1001, 0.1001, 0.8999, 0.8999)
            )
        )
        assertFailsWith<IllegalArgumentException> {
            VVideoCropGeometry.pixelCrop(
                VVideoSize(100, 100),
                VVideoCropRect(0.0, 0.0, 0.1, 0.1)
            )
        }
    }

    @Test
    fun malformedNativePayload_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            VVideoAdvancedConfig.fromMap(
                mapOf(
                    "cropRect" to mapOf(
                        "left" to 0.5,
                        "top" to 0.0,
                        "right" to 0.5,
                        "bottom" to 1.0
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            VVideoAdvancedConfig.fromMap(mapOf("cropRect" to "invalid"))
        }
    }

    @Test
    fun originalFallback_isProhibitedForEffectiveEdits() {
        val video = VVideoInfo("/input.mp4", "input.mp4", 100, 10_000, 100, 100)
        val noEdit = VVideoCompressionConfig(VVideoCompressQuality.MEDIUM)
        val fullCrop = noEdit.copy(advanced = VVideoAdvancedConfig(cropRect = full))
        val crop = noEdit.copy(
            advanced = VVideoAdvancedConfig(
                cropRect = VVideoCropRect(0.0, 0.0, 0.5, 1.0)
            )
        )

        assertFalse(VVideoCropGeometry.requiresEncodedOutput(video, noEdit))
        assertFalse(VVideoCropGeometry.requiresEncodedOutput(video, fullCrop))
        assertTrue(VVideoCropGeometry.requiresEncodedOutput(video, crop))
        assertTrue(
            VVideoCropGeometry.requiresEncodedOutput(
                video,
                noEdit.copy(advanced = VVideoAdvancedConfig(trimStartMs = 1))
            )
        )
        assertTrue(
            VVideoCropGeometry.requiresEncodedOutput(
                video,
                noEdit.copy(advanced = VVideoAdvancedConfig(rotation = 90))
            )
        )
        assertTrue(
            VVideoCropGeometry.requiresEncodedOutput(
                video,
                noEdit.copy(advanced = VVideoAdvancedConfig(videoCodec = VVideoCodec.H264))
            )
        )
    }
}
