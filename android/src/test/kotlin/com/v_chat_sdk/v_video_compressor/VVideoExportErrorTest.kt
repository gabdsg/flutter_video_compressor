package com.v_chat_sdk.v_video_compressor

import androidx.media3.transformer.ExportException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class VVideoExportErrorTest {
    @Test
    fun muxerFailuresRetryOnlyOnce() {
        assertTrue(
            VVideoExportError.shouldRetry(
                errorCode = ExportException.ERROR_CODE_MUXING_FAILED,
                isCodecCapacityError = false,
                retryCount = 0,
                maxRetries = 3
            )
        )
        assertFalse(
            VVideoExportError.shouldRetry(
                errorCode = ExportException.ERROR_CODE_MUXING_FAILED,
                isCodecCapacityError = false,
                retryCount = 1,
                maxRetries = 3
            )
        )
    }

    @Test
    fun codecFailuresRespectTheRetryLimit() {
        assertTrue(
            VVideoExportError.shouldRetry(
                errorCode = ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
                isCodecCapacityError = true,
                retryCount = 2,
                maxRetries = 3
            )
        )
        assertFalse(
            VVideoExportError.shouldRetry(
                errorCode = ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
                isCodecCapacityError = true,
                retryCount = 3,
                maxRetries = 3
            )
        )
    }

    @Test
    fun formattedMuxerErrorIncludesActionableDiagnostics() {
        val message = VVideoExportError.format(
            errorCode = ExportException.ERROR_CODE_MUXING_FAILED,
            errorCodeName = "ERROR_CODE_MUXING_FAILED",
            causeMessages = listOf("Muxer error", "Failed to write sample"),
            codecInfo = "c2.google.avc.encoder",
            availableStorageBytes = 512L * 1024 * 1024,
            deviceDescription = "Google Pixel 7 Pro (Android 16, API 36)"
        )

        assertTrue(message.contains("ERROR_CODE_MUXING_FAILED/7001"))
        assertTrue(message.contains("Failed to write sample"))
        assertTrue(message.contains("Free output storage: 512 MB"))
        assertTrue(message.contains("Google Pixel 7 Pro"))
    }
}
