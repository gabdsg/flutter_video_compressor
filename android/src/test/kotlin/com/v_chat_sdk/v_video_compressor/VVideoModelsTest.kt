package com.v_chat_sdk.v_video_compressor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * Unit tests for Kotlin-side channel model compatibility.
 *
 * Once you have built the plugin's example app, you can run these tests from the command
 * line by running `./gradlew testDebugUnitTest` in the `example/android/` directory, or
 * you can run them directly from IDEs that support JUnit such as Android Studio.
 */

internal class VVideoModelsTest {
  @Test
  fun compressionConfig_preservesFallbackDefaultAndExplicitOptOut() {
    val legacyConfig = VVideoCompressionConfig.fromMap(mapOf("quality" to "MEDIUM"))
    val codecConversionConfig = VVideoCompressionConfig.fromMap(
      mapOf(
        "quality" to "MEDIUM",
        "fallbackToOriginalIfNotSmaller" to false
      )
    )

    assertTrue(legacyConfig.fallbackToOriginalIfNotSmaller)
    assertFalse(codecConversionConfig.fallbackToOriginalIfNotSmaller)
  }
}
