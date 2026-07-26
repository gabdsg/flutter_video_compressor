package com.v_chat_sdk.v_video_compressor

import androidx.media3.transformer.ExportException

/**
 * Pure helpers for classifying and formatting Media3 export failures.
 *
 * Keeping this logic independent from Android framework state makes the
 * retry policy deterministic and unit-testable.
 */
internal object VVideoExportError {
    private val muxerErrorCodes = setOf(
        ExportException.ERROR_CODE_MUXING_FAILED,
        ExportException.ERROR_CODE_MUXING_TIMEOUT,
        ExportException.ERROR_CODE_MUXING_APPEND
    )

    fun isMuxerError(errorCode: Int): Boolean = errorCode in muxerErrorCodes

    fun shouldRetry(
        errorCode: Int,
        isCodecCapacityError: Boolean,
        retryCount: Int,
        maxRetries: Int
    ): Boolean {
        if (retryCount >= maxRetries) return false
        if (isCodecCapacityError) return true

        // Muxer failures receive one retry with a fresh output file. Repeating
        // them more often is unlikely to fix malformed input or storage errors.
        return retryCount == 0 && isMuxerError(errorCode)
    }

    fun format(
        errorCode: Int,
        errorCodeName: String,
        causeMessages: List<String>,
        codecInfo: String?,
        availableStorageBytes: Long?,
        deviceDescription: String
    ): String {
        val summary = when (errorCode) {
            ExportException.ERROR_CODE_MUXING_FAILED ->
                "Failed to write the MP4 output"
            ExportException.ERROR_CODE_MUXING_TIMEOUT ->
                "Timed out while writing MP4 samples"
            ExportException.ERROR_CODE_MUXING_APPEND ->
                "The input tracks could not be appended to the MP4 output"
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "The input video file was not found"
            ExportException.ERROR_CODE_IO_NO_PERMISSION ->
                "The input or output file could not be accessed"
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED ->
                "Failed to initialize the video encoder"
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED ->
                "The requested video format is not supported"
            else -> "Video compression failed"
        }

        val details = mutableListOf("$summary [$errorCodeName/$errorCode]")
        causeMessages
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(4)
            .takeIf { it.isNotEmpty() }
            ?.let { details += it.joinToString(" -> ") }
        codecInfo?.takeIf(String::isNotBlank)?.let { details += "Codec: $it" }
        availableStorageBytes?.let {
            details += "Free output storage: ${it / (1024 * 1024)} MB"
        }
        details += "Device: $deviceDescription"

        return details.joinToString("; ")
    }
}
