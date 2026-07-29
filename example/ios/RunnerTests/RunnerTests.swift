import Flutter
import UIKit
import XCTest


@testable import v_video_compressor

// This demonstrates a simple unit test of the Swift portion of this plugin's implementation.
//
// See https://developer.apple.com/documentation/xctest for more information about using XCTest.

class RunnerTests: XCTestCase {

  func testGetPlatformVersion() {
    let plugin = VVideoCompressorPlugin()

    let call = FlutterMethodCall(methodName: "getPlatformVersion", arguments: [])

    let resultExpectation = expectation(description: "result block must be called.")
    plugin.handle(call) { result in
      XCTAssertEqual(result as! String, "iOS " + UIDevice.current.systemVersion)
      resultExpectation.fulfill()
    }
    waitForExpectations(timeout: 1)
  }

}

final class VVideoCropGeometryRunnerTests: XCTestCase {
  func testPreferredTransformCropTranslationAndRenderSize() throws {
    let portraitTransform = CGAffineTransform(
      a: 0,
      b: 1,
      c: -1,
      d: 0,
      tx: 1080,
      ty: 0
    )
    let portrait = try VVideoCropGeometry.displayedTransform(
      naturalSize: CGSize(width: 1920, height: 1080),
      preferredTransform: portraitTransform
    )
    XCTAssertEqual(portrait.size, VVideoOutputSize(width: 1080, height: 1920))

    let plan = try VVideoCropGeometry.createPlan(
      naturalSize: CGSize(width: 1000, height: 500),
      preferredTransform: .identity,
      explicitRotation: 0,
      cropRect: VVideoCropRect(left: 0.1, top: 0.1, right: 0.9, bottom: 0.9),
      quality: .high,
      customWidth: 800,
      customHeight: 400,
      dimensionHandling: .exact
    )
    XCTAssertEqual(
      plan.pixelCrop,
      VVideoPixelCrop(left: 100, top: 50, right: 900, bottom: 450)
    )
    XCTAssertEqual(plan.outputSize, VVideoOutputSize(width: 800, height: 400))
    let transformedCrop = CGRect(x: 100, y: 50, width: 800, height: 400)
      .applying(plan.transform)
    XCTAssertEqual(transformedCrop.minX, 0, accuracy: 0.001)
    XCTAssertEqual(transformedCrop.minY, 0, accuracy: 0.001)
    XCTAssertEqual(transformedCrop.width, 800, accuracy: 0.001)
    XCTAssertEqual(transformedCrop.height, 400, accuracy: 0.001)
  }

  func testDiscreteRotationsSizingValidationAndFallbackPolicy() throws {
    XCTAssertEqual(
      try VVideoCropGeometry.orientedSize(width: 1920, height: 1080, rotation: 90),
      VVideoOutputSize(width: 1080, height: 1920)
    )
    XCTAssertEqual(
      try VVideoCropGeometry.orientedSize(width: 1920, height: 1080, rotation: 270),
      VVideoOutputSize(width: 1080, height: 1920)
    )
    XCTAssertEqual(
      try VVideoCropGeometry.cropRotationRadians(90),
      -.pi / 2,
      accuracy: 0.0001
    )
    XCTAssertEqual(
      try VVideoCropGeometry.resolveOutputSize(
        cropSize: VVideoOutputSize(width: 160, height: 120),
        quality: .high,
        customWidth: nil,
        customHeight: nil,
        handling: .autoAlign
      ),
      VVideoOutputSize(width: 160, height: 120)
    )
    XCTAssertThrowsError(
      try VVideoAdvancedConfig.fromMap([
        "cropRect": ["left": 0.5, "top": 0, "right": 0.5, "bottom": 1]
      ])
    )
    XCTAssertThrowsError(
      try VVideoCropGeometry.resolveOutputSize(
        cropSize: VVideoOutputSize(width: 1000, height: 500),
        quality: .medium,
        customWidth: 640,
        customHeight: 480,
        handling: .exact
      )
    )

    let video = VVideoInfo(
      path: "/input.mp4",
      name: "input.mp4",
      fileSizeBytes: 100,
      durationMillis: 10_000,
      width: 100,
      height: 100,
      thumbnailPath: nil
    )
    let noEdit = try VVideoCompressionConfig.fromMap(["quality": "MEDIUM"])
    let crop = try VVideoCompressionConfig.fromMap([
      "quality": "MEDIUM",
      "advanced": [
        "cropRect": ["left": 0, "top": 0, "right": 0.5, "bottom": 1]
      ]
    ])
    XCTAssertFalse(
      VVideoCropGeometry.requiresEncodedOutput(video: video, config: noEdit)
    )
    XCTAssertTrue(
      VVideoCropGeometry.requiresEncodedOutput(video: video, config: crop)
    )
  }
}
