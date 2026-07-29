package com.v_chat_sdk.v_video_compressor

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

data class VVideoSize(val width: Int, val height: Int)

data class VVideoPixelCrop(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class VVideoMedia3Crop(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float
)

enum class VVideoEffectStep {
    EXPLICIT_ROTATION,
    CROP,
    PRESENTATION
}

data class VVideoCropPlan(
    val orientedSize: VVideoSize,
    val pixelCrop: VVideoPixelCrop,
    val media3Crop: VVideoMedia3Crop,
    val outputSize: VVideoSize,
    val presentationLayout: Int,
    val effectOrder: List<VVideoEffectStep>
)

/**
 * Pure crop and encoder-output geometry shared by the Android engine and tests.
 */
@UnstableApi
object VVideoCropGeometry {
    const val MIN_ENCODER_DIMENSION = 16
    private const val MAX_ASPECT_ERROR = 0.01

    fun orientedSize(width: Int, height: Int, rotation: Int): VVideoSize {
        require(width > 0 && height > 0) { "Source dimensions must be positive" }
        require(rotation in listOf(0, 90, 180, 270)) {
            "rotation must be 0, 90, 180, or 270"
        }
        return if (rotation == 90 || rotation == 270) {
            VVideoSize(height, width)
        } else {
            VVideoSize(width, height)
        }
    }

    /**
     * Media3's ScaleAndRotateTransformation uses counter-clockwise-positive
     * degrees, matching the editor-facing rotation convention.
     */
    fun cropRotationDegrees(rotation: Int): Float {
        require(rotation in listOf(0, 90, 180, 270)) {
            "rotation must be 0, 90, 180, or 270"
        }
        return rotation.toFloat()
    }

    fun toMedia3Crop(rect: VVideoCropRect): VVideoMedia3Crop {
        require(rect.isValid()) { "Invalid crop rectangle" }
        return VVideoMedia3Crop(
            left = (2.0 * rect.left - 1.0).toFloat(),
            right = (2.0 * rect.right - 1.0).toFloat(),
            bottom = (1.0 - 2.0 * rect.bottom).toFloat(),
            top = (1.0 - 2.0 * rect.top).toFloat()
        )
    }

    fun pixelCrop(size: VVideoSize, rect: VVideoCropRect): VVideoPixelCrop {
        require(rect.isValid()) { "Invalid crop rectangle" }
        // Inward rounding guarantees that no sampled pixel lies outside the
        // normalized selection.
        val crop = VVideoPixelCrop(
            left = ceil(rect.left * size.width).toInt(),
            top = ceil(rect.top * size.height).toInt(),
            right = floor(rect.right * size.width).toInt(),
            bottom = floor(rect.bottom * size.height).toInt()
        )
        require(
            crop.width >= MIN_ENCODER_DIMENSION &&
                crop.height >= MIN_ENCODER_DIMENSION
        ) {
            "Crop resolves to ${crop.width}x${crop.height}; encoded dimensions must be at least 16x16"
        }
        return crop
    }

    fun createPlan(
        displayedWidth: Int,
        displayedHeight: Int,
        explicitRotation: Int,
        cropRect: VVideoCropRect,
        quality: VVideoCompressQuality,
        customWidth: Int?,
        customHeight: Int?,
        dimensionHandling: VDimensionHandling?
    ): VVideoCropPlan {
        require((customWidth == null) == (customHeight == null)) {
            "customWidth and customHeight must be specified together"
        }
        val oriented = orientedSize(displayedWidth, displayedHeight, explicitRotation)
        val pixels = pixelCrop(oriented, cropRect)
        val output = resolveOutputSize(
            cropSize = VVideoSize(pixels.width, pixels.height),
            quality = quality,
            customWidth = customWidth,
            customHeight = customHeight,
            handling = dimensionHandling ?: VDimensionHandling.AUTO_ALIGN
        )
        val layout = if (dimensionHandling == VDimensionHandling.LETTERBOX) {
            Presentation.LAYOUT_SCALE_TO_FIT
        } else {
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        }
        val order = buildList {
            if (explicitRotation != 0) add(VVideoEffectStep.EXPLICIT_ROTATION)
            add(VVideoEffectStep.CROP)
            add(VVideoEffectStep.PRESENTATION)
        }
        return VVideoCropPlan(
            orientedSize = oriented,
            pixelCrop = pixels,
            media3Crop = toMedia3Crop(cropRect),
            outputSize = output,
            presentationLayout = layout,
            effectOrder = order
        )
    }

    fun resolveOutputSize(
        cropSize: VVideoSize,
        quality: VVideoCompressQuality,
        customWidth: Int?,
        customHeight: Int?,
        handling: VDimensionHandling
    ): VVideoSize {
        require((customWidth == null) == (customHeight == null)) {
            "customWidth and customHeight must be specified together"
        }
        val aspect = cropSize.width.toDouble() / cropSize.height
        val maxDimension = when (quality) {
            VVideoCompressQuality.HIGH -> 1920
            VVideoCompressQuality.MEDIUM -> 1280
            VVideoCompressQuality.LOW -> 960
            VVideoCompressQuality.VERY_LOW -> 640
            VVideoCompressQuality.ULTRA_LOW -> 432
        }
        val boundWidth = customWidth ?: min(cropSize.width, maxDimension)
        val boundHeight = customHeight ?: min(cropSize.height, maxDimension)
        require(boundWidth >= MIN_ENCODER_DIMENSION && boundHeight >= MIN_ENCODER_DIMENSION) {
            "Requested output dimensions must be at least 16x16"
        }

        return when (handling) {
            VDimensionHandling.LETTERBOX -> VVideoSize(
                alignedCanvasDimension(boundWidth),
                alignedCanvasDimension(boundHeight)
            )
            VDimensionHandling.EXACT -> {
                val width = evenFloor(boundWidth)
                val height = evenFloor(boundHeight)
                require(preservesAspectWithinOnePixel(width, height, aspect)) {
                    "Exact dimensions ${width}x${height} do not preserve crop aspect ratio"
                }
                VVideoSize(width, height)
            }
            VDimensionHandling.AUTO_ALIGN -> {
                val fitted = fitInside(boundWidth, boundHeight, aspect)
                alignedIfAspectSafe(fitted, aspect, 16)
                    ?: alignedIfAspectSafe(fitted, aspect, 2)
                    ?: throw IllegalArgumentException(
                        "Crop cannot produce encoder-safe dimensions"
                    )
            }
        }.also {
            require(
                it.width >= MIN_ENCODER_DIMENSION &&
                    it.height >= MIN_ENCODER_DIMENSION
            ) {
                "Resolved output ${it.width}x${it.height} is below 16x16"
            }
        }
    }

    fun requiresEncodedOutput(
        video: VVideoInfo,
        config: VVideoCompressionConfig
    ): Boolean {
        val advanced = config.advanced
        return !config.includeAudio ||
            (advanced != null &&
                ((advanced.cropRect != null && !advanced.cropRect.isFullFrame()) ||
                    (advanced.trimStartMs ?: 0) > 0 ||
                    (advanced.trimEndMs != null &&
                        advanced.trimEndMs.toLong() < video.durationMillis) ||
                    (advanced.rotation ?: 0) != 0 ||
                    advanced.customWidth != null ||
                    advanced.customHeight != null ||
                    advanced.removeAudio == true ||
                    advanced.videoCodec != null ||
                    advanced.audioCodec != null))
    }

    private fun fitInside(maxWidth: Int, maxHeight: Int, aspect: Double): VVideoSize {
        return if (maxWidth.toDouble() / maxHeight > aspect) {
            VVideoSize((maxHeight * aspect).roundToInt(), maxHeight)
        } else {
            VVideoSize(maxWidth, (maxWidth / aspect).roundToInt())
        }
    }

    private fun alignedIfAspectSafe(
        fitted: VVideoSize,
        aspect: Double,
        alignment: Int
    ): VVideoSize? {
        val candidate = VVideoSize(
            alignDown(fitted.width, alignment),
            alignDown(fitted.height, alignment)
        )
        return candidate.takeIf {
            it.width >= MIN_ENCODER_DIMENSION &&
                it.height >= MIN_ENCODER_DIMENSION &&
                aspectError(it.width, it.height, aspect) <= MAX_ASPECT_ERROR
        }
    }

    private fun alignedCanvasDimension(value: Int): Int {
        val aligned = alignDown(value, 16)
        return if (aligned >= MIN_ENCODER_DIMENSION) aligned else evenFloor(value)
    }

    private fun preservesAspectWithinOnePixel(
        width: Int,
        height: Int,
        aspect: Double
    ): Boolean =
        abs(width - height * aspect) <= 1.0 ||
            abs(height - width / aspect) <= 1.0

    private fun aspectError(width: Int, height: Int, aspect: Double): Double =
        abs(width.toDouble() / height - aspect) / aspect

    private fun alignDown(value: Int, alignment: Int): Int =
        (value / alignment) * alignment

    private fun evenFloor(value: Int): Int = value - value % 2
}
