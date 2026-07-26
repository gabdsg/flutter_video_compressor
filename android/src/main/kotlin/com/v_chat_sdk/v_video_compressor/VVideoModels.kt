package com.v_chat_sdk.v_video_compressor

import android.net.Uri
import java.io.File

/**
 * Compression quality levels
 */
enum class VVideoCompressQuality(val value: String, val displayName: String, val description: String) {
    HIGH("HIGH", "1080p HD", "High quality with better file size"),
    MEDIUM("MEDIUM", "720p", "Balanced quality and compression"),
    LOW("LOW", "480p", "Good compression for sharing"),
    VERY_LOW("VERY_LOW", "360p", "High compression, smaller files"),
    ULTRA_LOW("ULTRA_LOW", "240p", "Maximum compression, smallest files");

    companion object {
        fun fromString(value: String): VVideoCompressQuality {
            return values().find { it.value == value } ?: MEDIUM
        }
    }
}

/**
 * Video codec types
 */
enum class VVideoCodec(val value: String) {
    H264("H264"),
    H265("H265");

    companion object {
        fun fromString(value: String?): VVideoCodec? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Audio codec types
 */
enum class VAudioCodec(val value: String) {
    AAC("AAC"),
    MP3("MP3");

    companion object {
        fun fromString(value: String?): VAudioCodec? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Encoding speed types
 */
enum class VEncodingSpeed(val value: String) {
    ULTRAFAST("ULTRAFAST"),
    SUPERFAST("SUPERFAST"),
    VERYFAST("VERYFAST"),
    FASTER("FASTER"),
    FAST("FAST"),
    MEDIUM("MEDIUM"),
    SLOW("SLOW"),
    SLOWER("SLOWER"),
    VERYSLOW("VERYSLOW");

    companion object {
        fun fromString(value: String?): VEncodingSpeed? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Advanced video compression configuration
 */
data class VVideoAdvancedConfig(
    val videoBitrate: Int? = null,
    val audioBitrate: Int? = null,
    val customWidth: Int? = null,
    val customHeight: Int? = null,
    val frameRate: Double? = null,
    val videoCodec: VVideoCodec? = null,
    val audioCodec: VAudioCodec? = null,
    val encodingSpeed: VEncodingSpeed? = null,
    val crf: Int? = null,
    val twoPassEncoding: Boolean? = null,
    val hardwareAcceleration: Boolean? = null,
    val trimStartMs: Int? = null,
    val trimEndMs: Int? = null,
    val rotation: Int? = null,
    val audioSampleRate: Int? = null,
    val audioChannels: Int? = null,
    val removeAudio: Boolean? = null,
    val autoCorrectOrientation: Boolean? = null,
    val brightness: Double? = null,
    val contrast: Double? = null,
    val saturation: Double? = null,
    val variableBitrate: Boolean? = null,
    val keyframeInterval: Int? = null,
    val bFrames: Int? = null,
    val reducedFrameRate: Double? = null,
    val aggressiveCompression: Boolean? = null,
    val noiseReduction: Boolean? = null,
    val monoAudio: Boolean? = null
) {
    companion object {
        fun fromMap(map: Map<String, Any?>?): VVideoAdvancedConfig? {
            if (map == null) return null
            
            return VVideoAdvancedConfig(
                videoBitrate = (map["videoBitrate"] as? Number)?.toInt(),
                audioBitrate = (map["audioBitrate"] as? Number)?.toInt(),
                customWidth = (map["customWidth"] as? Number)?.toInt(),
                customHeight = (map["customHeight"] as? Number)?.toInt(),
                frameRate = (map["frameRate"] as? Number)?.toDouble(),
                videoCodec = VVideoCodec.fromString(map["videoCodec"] as? String),
                audioCodec = VAudioCodec.fromString(map["audioCodec"] as? String),
                encodingSpeed = VEncodingSpeed.fromString(map["encodingSpeed"] as? String),
                crf = (map["crf"] as? Number)?.toInt(),
                twoPassEncoding = map["twoPassEncoding"] as? Boolean,
                hardwareAcceleration = map["hardwareAcceleration"] as? Boolean,
                trimStartMs = (map["trimStartMs"] as? Number)?.toInt(),
                trimEndMs = (map["trimEndMs"] as? Number)?.toInt(),
                rotation = (map["rotation"] as? Number)?.toInt(),
                audioSampleRate = (map["audioSampleRate"] as? Number)?.toInt(),
                audioChannels = (map["audioChannels"] as? Number)?.toInt(),
                removeAudio = map["removeAudio"] as? Boolean,
                autoCorrectOrientation = map["autoCorrectOrientation"] as? Boolean,
                brightness = (map["brightness"] as? Number)?.toDouble(),
                contrast = (map["contrast"] as? Number)?.toDouble(),
                saturation = (map["saturation"] as? Number)?.toDouble(),
                variableBitrate = map["variableBitrate"] as? Boolean,
                keyframeInterval = (map["keyframeInterval"] as? Number)?.toInt(),
                bFrames = (map["bFrames"] as? Number)?.toInt(),
                reducedFrameRate = (map["reducedFrameRate"] as? Number)?.toDouble(),
                aggressiveCompression = map["aggressiveCompression"] as? Boolean,
                noiseReduction = map["noiseReduction"] as? Boolean,
                monoAudio = map["monoAudio"] as? Boolean
            )
        }
    }
}

/**
 * Video information model
 */
data class VVideoInfo(
    val path: String,
    val name: String,
    val fileSizeBytes: Long,
    val durationMillis: Long,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailPath: String? = null
) {
    val fileSizeMB: Double
        get() = fileSizeBytes / (1024.0 * 1024.0)
    
    val durationFormatted: String
        get() = formatDuration(durationMillis)
    
    val fileSizeFormatted: String
        get() = formatFileSize(fileSizeBytes)
    
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.1f KB", kb)
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "path" to path,
            "name" to name,
            "fileSizeBytes" to fileSizeBytes,
            "durationMillis" to durationMillis,
            "width" to width,
            "height" to height,
            "thumbnailPath" to thumbnailPath
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): VVideoInfo {
            return VVideoInfo(
                path = map["path"] as? String ?: "",
                name = map["name"] as? String ?: "",
                fileSizeBytes = (map["fileSizeBytes"] as? Number)?.toLong() ?: 0L,
                durationMillis = (map["durationMillis"] as? Number)?.toLong() ?: 0L,
                width = (map["width"] as? Number)?.toInt() ?: 0,
                height = (map["height"] as? Number)?.toInt() ?: 0,
                thumbnailPath = map["thumbnailPath"] as? String
            )
        }
    }
}

/**
 * Compression configuration
 */
data class VVideoCompressionConfig(
    val quality: VVideoCompressQuality,
    val outputPath: String? = null,
    val deleteOriginal: Boolean = false,
    val fallbackToOriginalIfNotSmaller: Boolean = true,
    val advanced: VVideoAdvancedConfig? = null
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): VVideoCompressionConfig {
            return VVideoCompressionConfig(
                quality = VVideoCompressQuality.fromString(map["quality"] as? String ?: "MEDIUM"),
                outputPath = map["outputPath"] as? String,
                deleteOriginal = map["deleteOriginal"] as? Boolean ?: false,
                fallbackToOriginalIfNotSmaller =
                    map["fallbackToOriginalIfNotSmaller"] as? Boolean ?: true,
                advanced = VVideoAdvancedConfig.fromMap(map["advanced"] as? Map<String, Any?>)
            )
        }
    }
}

/**
 * Compression estimation result
 */
data class VVideoCompressionEstimate(
    val estimatedSizeBytes: Long,
    val estimatedSizeFormatted: String,
    val compressionRatio: Float,
    val bitrateMbps: Float
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "estimatedSizeBytes" to estimatedSizeBytes,
            "estimatedSizeFormatted" to estimatedSizeFormatted,
            "compressionRatio" to compressionRatio,
            "bitrateMbps" to bitrateMbps
        )
    }
}

/**
 * Compression result
 */
data class VVideoCompressionResult(
    val originalVideo: VVideoInfo,
    val compressedFilePath: String,
    val galleryUri: String? = null,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val compressionRatio: Float,
    val timeTaken: Long,
    val quality: VVideoCompressQuality,
    val originalResolution: String,
    val compressedResolution: String,
    val spaceSaved: Long,
    val usedOriginalFile: Boolean = false
) {
    val spaceSavedFormatted: String
        get() = formatFileSize(spaceSaved)
    
    val compressionPercentage: Int
        get() = ((1 - compressionRatio) * 100).toInt()
    
    val originalSizeFormatted: String
        get() = formatFileSize(originalSizeBytes)
    
    val compressedSizeFormatted: String
        get() = formatFileSize(compressedSizeBytes)
    
    val timeTakenFormatted: String
        get() = formatTime(timeTaken)
    
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.1f KB", kb)
        }
    }
    
    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        
        return when {
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "originalVideo" to originalVideo.toMap(),
            "compressedFilePath" to compressedFilePath,
            "galleryUri" to galleryUri,
            "originalSizeBytes" to originalSizeBytes,
            "compressedSizeBytes" to compressedSizeBytes,
            "compressionRatio" to compressionRatio,
            "timeTaken" to timeTaken,
            "quality" to quality.value,
            "originalResolution" to originalResolution,
            "compressedResolution" to compressedResolution,
            "spaceSaved" to spaceSaved,
            "usedOriginalFile" to usedOriginalFile
        )
    }
}

/**
 * Thumbnail output format
 */
enum class VThumbnailFormat(val value: String, val mimeType: String, val extension: String) {
    JPEG("JPEG", "image/jpeg", ".jpg"),
    PNG("PNG", "image/png", ".png");

    companion object {
        fun fromString(value: String?): VThumbnailFormat {
            return values().find { it.value == value } ?: JPEG
        }
    }
}

/**
 * Video thumbnail configuration
 */
data class VVideoThumbnailConfig(
    val timeMs: Int = 0,
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val format: VThumbnailFormat = VThumbnailFormat.JPEG,
    val quality: Int = 80,
    val outputPath: String? = null
) {
    fun isValid(): Boolean {
        if (timeMs < 0) return false
        if (maxWidth != null && maxWidth <= 0) return false
        if (maxHeight != null && maxHeight <= 0) return false
        if (quality < 0 || quality > 100) return false
        return true
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): VVideoThumbnailConfig {
            return VVideoThumbnailConfig(
                timeMs = (map["timeMs"] as? Number)?.toInt() ?: 0,
                maxWidth = (map["maxWidth"] as? Number)?.toInt(),
                maxHeight = (map["maxHeight"] as? Number)?.toInt(),
                format = VThumbnailFormat.fromString(map["format"] as? String),
                quality = (map["quality"] as? Number)?.toInt() ?: 80,
                outputPath = map["outputPath"] as? String
            )
        }
    }
}

/**
 * Video thumbnail result
 */
data class VVideoThumbnailResult(
    val thumbnailPath: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: VThumbnailFormat,
    val timeMs: Int
) {
    val fileSizeFormatted: String
        get() = formatFileSize(fileSizeBytes)

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0

        return when {
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "thumbnailPath" to thumbnailPath,
            "width" to width,
            "height" to height,
            "fileSizeBytes" to fileSizeBytes,
            "format" to format.value,
            "timeMs" to timeMs
        )
    }
}
