package com.v_chat_sdk.v_video_compressor

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** VVideoCompressorPlugin */
class VVideoCompressorPlugin: FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {
    
    /// The MethodChannel that will handle communication between Flutter and native Android
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private lateinit var context: Context
    
    // Core components
    private lateinit var compressionEngine: VVideoCompressionEngine
    
    // Coroutine scope for background operations
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Event sink for progress updates
    private var eventSink: EventChannel.EventSink? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        
        // Initialize core components
        compressionEngine = VVideoCompressionEngine(context)
        
        // Set up method channel
        methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, "v_video_compressor")
        methodChannel.setMethodCallHandler(this)
        
        // Set up event channel for progress updates
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "v_video_compressor/progress")
        eventChannel.setStreamHandler(this)
    }
    
    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            
            "getVideoInfo" -> {
                handleGetVideoInfo(call, result)
            }
            
            "getCompressionEstimate" -> {
                handleGetCompressionEstimate(call, result)
            }
            
            "compressVideo" -> {
                handleCompressVideo(call, result)
            }
            
            "compressVideos" -> {
                handleCompressVideos(call, result)
            }
            
            "cancelCompression" -> {
                handleCancelCompression(result)
            }
            
            "isCompressing" -> {
                result.success(compressionEngine.isCompressing())
            }
            
            "getVideoThumbnail" -> {
                handleGetVideoThumbnail(call, result)
            }
            
            "getVideoThumbnails" -> {
                handleGetVideoThumbnails(call, result)
            }
            
            "cleanup" -> {
                handleCleanup(result)
            }
            
            "cleanupFiles" -> {
                handleCleanupFiles(call, result)
            }
            
            else -> {
                result.notImplemented()
            }
        }
    }
    
    private fun handleGetVideoInfo(call: MethodCall, result: Result) {
        val videoPath = call.argument<String>("videoPath")
        if (videoPath == null) {
            result.error("INVALID_ARGUMENT", "Video path is required", null)
            return
        }
        
        pluginScope.launch {
            try {
                val videoInfo = withContext(Dispatchers.IO) {
                    compressionEngine.getVideoInfo(videoPath)
                }
                
                if (videoInfo != null) {
                    result.success(videoInfo.toMap())
                } else {
                    result.success(null)
                }
            } catch (e: Exception) {
                result.error("ERROR", "Failed to get video info: ${e.message}", null)
            }
        }
    }
    
    private fun handleGetCompressionEstimate(call: MethodCall, result: Result) {
        val videoPath = call.argument<String>("videoPath")
        val qualityStr = call.argument<String>("quality")
        val advancedMap = call.argument<Map<String, Any?>>("advanced")
        
        if (videoPath == null || qualityStr == null) {
            result.error("INVALID_ARGUMENT", "Video path and quality are required", null)
            return
        }
        
        pluginScope.launch {
            try {
                val videoInfo = withContext(Dispatchers.IO) {
                    compressionEngine.getVideoInfo(videoPath)
                }
                
                if (videoInfo == null) {
                    result.error("ERROR", "Could not get video info", null)
                    return@launch
                }
                
                val quality = VVideoCompressQuality.fromString(qualityStr)
                val advanced = VVideoAdvancedConfig.fromMap(advancedMap)
                val estimate = compressionEngine.estimateCompressionSize(videoInfo, quality, advanced)
                
                result.success(estimate.toMap())
            } catch (e: Exception) {
                result.error("ERROR", "Failed to get compression estimate: ${e.message}", null)
            }
        }
    }
    
    private fun handleCompressVideo(call: MethodCall, result: Result) {
        val videoPath = call.argument<String>("videoPath")
        // Support both legacy parameters (quality, outputPath, etc.) and new consolidated 'config' map
        val configMapFromArgs = call.argument<Map<String, Any?>>("config")
        val legacyQuality = call.argument<String>("quality")
        val compressionConfig = configMapFromArgs?.let {
            VVideoCompressionConfig.fromMap(it)
        } ?: legacyQuality?.let {
            VVideoCompressionConfig(
                quality = VVideoCompressQuality.valueOf(it),
                outputPath = call.argument("outputPath"),
                deleteOriginal = call.argument<Boolean>("deleteOriginal") ?: false,
                fallbackToOriginalIfNotSmaller =
                    call.argument<Boolean>("fallbackToOriginalIfNotSmaller") ?: true,
                advanced = parseAdvancedConfig(call.arguments as? Map<String, Any>)
            )
        }
        
        if (videoPath == null || compressionConfig == null) {
            result.error("INVALID_ARGUMENT", "Video path and config are required", null)
            return
        }
        
        pluginScope.launch {
            try {
                val videoInfo = withContext(Dispatchers.IO) {
                    compressionEngine.getVideoInfo(videoPath)
                }
                
                if (videoInfo == null) {
                    result.error("VIDEO_NOT_FOUND", "Could not read video file", null)
                    return@launch
                }
                
                compressionEngine.compressVideo(
                    videoInfo = videoInfo,
                    config = compressionConfig,
                    callback = object : VVideoCompressionEngine.CompressionCallback {
                        override fun onProgress(progress: Float) {
                            mainHandler.post {
                                eventSink?.success(mapOf(
                                    "progress" to progress,
                                    "videoPath" to videoPath
                                ))
                            }
                        }
                        
                        override fun onComplete(compressionResult: VVideoCompressionResult) {
                            mainHandler.post {
                                result.success(compressionResult.toMap())
                            }
                        }
                        
                        override fun onError(error: String) {
                            mainHandler.post {
                                result.error("COMPRESSION_ERROR", error, null)
                            }
                        }
                    }
                )
            } catch (e: OutOfMemoryError) {
                result.error("OUT_OF_MEMORY", "Out of memory during video compression. Please try with lower quality settings or free up device memory.", null)
            } catch (e: Exception) {
                result.error("ERROR", "Failed to compress video: ${e.message}", null)
            }
        }
    }
    
    private fun handleCompressVideos(call: MethodCall, result: Result) {
        val videoPaths = call.argument<List<String>>("videoPaths")
        val configMapFromArgs = call.argument<Map<String, Any?>>("config")
        val legacyQuality = call.argument<String>("quality")
        val compressionConfig = configMapFromArgs?.let {
            VVideoCompressionConfig.fromMap(it)
        } ?: legacyQuality?.let {
            VVideoCompressionConfig(
                quality = VVideoCompressQuality.valueOf(it),
                outputPath = call.argument("outputPath"),
                deleteOriginal = call.argument<Boolean>("deleteOriginal") ?: false,
                fallbackToOriginalIfNotSmaller =
                    call.argument<Boolean>("fallbackToOriginalIfNotSmaller") ?: true,
                advanced = parseAdvancedConfig(call.arguments as? Map<String, Any>)
            )
        }
        
        if (videoPaths == null || compressionConfig == null || videoPaths.isEmpty()) {
            result.error("INVALID_ARGUMENT", "Video paths and config are required", null)
            return
        }
        
        pluginScope.launch {
            val results = mutableListOf<Map<String, Any?>>()
            var hasError = false
            
            for ((index, videoPath) in videoPaths.withIndex()) {
                if (hasError) break
                
                try {
                    val videoInfo = withContext(Dispatchers.IO) {
                        compressionEngine.getVideoInfo(videoPath)
                    }
                    
                    if (videoInfo == null) {
                        results.add(mapOf(
                            "success" to false,
                            "error" to "Could not read video file",
                            "videoPath" to videoPath
                        ))
                        continue
                    }
                    
                    val compressionResult = withContext(Dispatchers.IO) {
                        var compressionResult: VVideoCompressionResult? = null
                        var compressionError: String? = null
                        
                        compressionEngine.compressVideo(
                            videoInfo = videoInfo,
                            config = compressionConfig,
                            callback = object : VVideoCompressionEngine.CompressionCallback {
                                override fun onProgress(progress: Float) {
                                    mainHandler.post {
                                        eventSink?.success(mapOf(
                                            "progress" to progress,
                                            "videoPath" to videoPath,
                                            "currentIndex" to index,
                                            "total" to videoPaths.size
                                        ))
                                    }
                                }
                                
                                override fun onComplete(result: VVideoCompressionResult) {
                                    compressionResult = result
                                }
                                
                                override fun onError(error: String) {
                                    compressionError = error
                                }
                            }
                        )
                        
                        // Wait for completion (simplified - in production you'd use proper synchronization)
                        while (compressionResult == null && compressionError == null && compressionEngine.isCompressing()) {
                            delay(100)
                        }
                        
                        if (compressionResult != null) {
                            compressionResult!!.toMap()
                        } else {
                            mapOf(
                                "success" to false,
                                "error" to (compressionError ?: "Unknown error"),
                                "videoPath" to videoPath
                            )
                        }
                    }
                    
                    results.add(compressionResult)
                } catch (e: OutOfMemoryError) {
                    results.add(mapOf(
                        "success" to false,
                        "error" to "Out of memory. Try lower quality or process fewer videos at once.",
                        "videoPath" to videoPath
                    ))
                    hasError = true
                } catch (e: Exception) {
                    results.add(mapOf(
                        "success" to false,
                        "error" to (e.message ?: "Unknown error"),
                        "videoPath" to videoPath
                    ))
                }
            }
            
            result.success(results)
        }
    }
    
    private fun handleCancelCompression(result: Result) {
        try {
            compressionEngine.cancelCompression()
            result.success(null)
        } catch (e: Exception) {
            result.error("ERROR", "Failed to cancel compression: ${e.message}", null)
        }
    }
    
    private fun handleGetVideoThumbnail(call: MethodCall, result: Result) {
        val videoPath = call.argument<String>("videoPath")
        val thumbnailConfig = call.arguments as? Map<String, Any>
        
        if (videoPath == null) {
            result.error("INVALID_ARGUMENT", "Video path is required", null)
            return
        }
        
        pluginScope.launch {
            try {
                val videoInfo = withContext(Dispatchers.IO) {
                    compressionEngine.getVideoInfo(videoPath)
                }
                
                if (videoInfo == null) {
                    result.error("VIDEO_NOT_FOUND", "Could not read video file", null)
                    return@launch
                }
                
                val config = parseThumbnailConfig(thumbnailConfig)
                
                val thumbnailResult = withContext(Dispatchers.IO) {
                    compressionEngine.getVideoThumbnail(videoInfo, config)
                }
                
                if (thumbnailResult != null) {
                    result.success(thumbnailResult.toMap())
                } else {
                    result.error("THUMBNAIL_ERROR", "Failed to generate thumbnail", null)
                }
            } catch (e: OutOfMemoryError) {
                result.error("OUT_OF_MEMORY", "Out of memory during thumbnail generation. Try smaller dimensions.", null)
            } catch (e: Exception) {
                result.error("ERROR", "Failed to generate thumbnail: ${e.message}", null)
            }
        }
    }
    
    private fun handleGetVideoThumbnails(call: MethodCall, result: Result) {
        val videoPath = call.argument<String>("videoPath")
        val configMaps = call.argument<List<Map<String, Any?>>>("configs")
        
        if (videoPath == null || configMaps == null) {
            result.error("INVALID_ARGUMENT", "Video path and configs are required", null)
            return
        }
        
        pluginScope.launch {
            try {
                val videoInfo = withContext(Dispatchers.IO) {
                    compressionEngine.getVideoInfo(videoPath)
                }
                
                if (videoInfo == null) {
                    result.error("ERROR", "Could not get video info", null)
                    return@launch
                }
                
                // Generate thumbnails on IO thread to avoid blocking the main thread
                val thumbnails = withContext(Dispatchers.IO) {
                    val results = mutableListOf<Map<String, Any?>>()

                    for (configMap in configMaps) {
                        val config = VVideoThumbnailConfig.fromMap(configMap as Map<String, Any?>)
                        val thumbnail = compressionEngine.getVideoThumbnail(videoInfo, config)

                        if (thumbnail != null) {
                            results.add(thumbnail.toMap())
                        }
                    }

                    results
                }

                result.success(thumbnails)
            } catch (e: OutOfMemoryError) {
                result.error("OUT_OF_MEMORY", "Out of memory during thumbnail generation. Try smaller dimensions or fewer thumbnails.", null)
            } catch (e: Exception) {
                result.error("ERROR", "Failed to get video thumbnails: ${e.message}", null)
            }
        }
    }
    
    private fun handleCleanup(result: Result) {
        pluginScope.launch {
            try {
                // Cancel any ongoing compression
                compressionEngine.cancelCompression()
                
                // Clean up all temporary files and cache
                val cleanupResult = compressionEngine.cleanup()
                
                result.success(cleanupResult)
            } catch (e: Exception) {
                result.error("CLEANUP_ERROR", "Failed to cleanup: ${e.message}", null)
            }
        }
    }
    
    private fun handleCleanupFiles(call: MethodCall, result: Result) {
        val deleteThumbnails = call.argument<Boolean>("deleteThumbnails") ?: true
        val deleteCompressedVideos = call.argument<Boolean>("deleteCompressedVideos") ?: false
        val clearCache = call.argument<Boolean>("clearCache") ?: true
        
        pluginScope.launch {
            try {
                val cleanupResult = compressionEngine.cleanupFiles(
                    deleteThumbnails = deleteThumbnails,
                    deleteCompressedVideos = deleteCompressedVideos,
                    clearCache = clearCache
                )
                
                result.success(cleanupResult)
            } catch (e: Exception) {
                result.error("CLEANUP_ERROR", "Failed to cleanup files: ${e.message}", null)
            }
        }
    }
    
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        
        // Cancel any ongoing compression
        compressionEngine.cancelCompression()
    }
    
    // EventChannel.StreamHandler implementation
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }
    
    override fun onCancel(arguments: Any?) {
        eventSink = null
    }
    
    // Helper methods for parsing configuration
    private fun parseAdvancedConfig(map: Map<String, Any>?): VVideoAdvancedConfig? {
        if (map == null) return null
        
        return VVideoAdvancedConfig(
            videoBitrate = (map["videoBitrate"] as? Number)?.toInt(),
            audioBitrate = (map["audioBitrate"] as? Number)?.toInt(),
            videoCodec = map["videoCodec"]?.let { 
                try { VVideoCodec.valueOf(it as String) } catch (e: Exception) { null }
            },
            audioCodec = map["audioCodec"]?.let { 
                try { VAudioCodec.valueOf(it as String) } catch (e: Exception) { null }
            },
            removeAudio = map["removeAudio"] as? Boolean,
            trimStartMs = (map["trimStartMs"] as? Number)?.toInt(),
            trimEndMs = (map["trimEndMs"] as? Number)?.toInt(),
            customWidth = (map["customWidth"] as? Number)?.toInt(),
            customHeight = (map["customHeight"] as? Number)?.toInt(),
            hardwareAcceleration = map["hardwareAcceleration"] as? Boolean,
            aggressiveCompression = map["aggressiveCompression"] as? Boolean,
            variableBitrate = map["variableBitrate"] as? Boolean,
            monoAudio = map["monoAudio"] as? Boolean,
            reducedFrameRate = (map["reducedFrameRate"] as? Number)?.toDouble(),
            encodingSpeed = map["encodingSpeed"]?.let { 
                try { VEncodingSpeed.valueOf(it as String) } catch (e: Exception) { null }
            }
        )
    }
    
    private fun parseThumbnailConfig(map: Map<String, Any>?): VVideoThumbnailConfig {
        return VVideoThumbnailConfig(
            timeMs = (map?.get("timeMs") as? Number)?.toInt() ?: 0,
            maxWidth = (map?.get("maxWidth") as? Number)?.toInt(),
            maxHeight = (map?.get("maxHeight") as? Number)?.toInt(),
            quality = (map?.get("quality") as? Number)?.toInt() ?: 85,
            format = map?.get("format")?.let { 
                try { VThumbnailFormat.valueOf(it as String) } catch (e: Exception) { VThumbnailFormat.JPEG }
            } ?: VThumbnailFormat.JPEG,
            outputPath = map?.get("outputPath") as? String
        )
    }
}
