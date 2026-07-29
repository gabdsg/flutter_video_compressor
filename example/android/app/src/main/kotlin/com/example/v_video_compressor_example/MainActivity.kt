package com.example.v_video_compressor_example

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "v_video_compressor_example/inspection"
        ).setMethodCallHandler { call, result ->
            if (call.method != "inspectVideo") {
                result.notImplemented()
                return@setMethodCallHandler
            }
            val path = call.argument<String>("path")
            if (path == null) {
                result.error("INVALID_ARGUMENT", "path is required", null)
                return@setMethodCallHandler
            }
            try {
                result.success(inspectVideo(path))
            } catch (error: Exception) {
                result.error("INSPECTION_ERROR", error.message, null)
            }
        }
    }

    private fun inspectVideo(path: String): Map<String, Any> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val rawWidth = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
        )?.toIntOrNull() ?: 0
        val rawHeight = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
        )?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
        )?.toIntOrNull() ?: 0
        val duration = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: 0L
        retriever.release()

        var hasVideo = false
        var hasAudio = false
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
            hasVideo = hasVideo || mime.startsWith("video/")
            hasAudio = hasAudio || mime.startsWith("audio/")
        }
        extractor.release()
        val swapsDimensions = rotation % 180 != 0
        return mapOf(
            "hasVideo" to hasVideo,
            "hasAudio" to hasAudio,
            "durationMillis" to duration,
            "width" to if (swapsDimensions) rawHeight else rawWidth,
            "height" to if (swapsDimensions) rawWidth else rawHeight,
            "rotation" to rotation
        )
    }
}
