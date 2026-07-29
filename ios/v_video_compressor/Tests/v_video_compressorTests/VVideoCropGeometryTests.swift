import CoreGraphics
import XCTest
@testable import v_video_compressor

final class VVideoCropGeometryTests: XCTestCase {
    private let full = VVideoCropRect(left: 0, top: 0, right: 1, bottom: 1)

    func testPreferredTransformsProduceDisplayedLandscapeAndPortraitSizes() throws {
        let landscape = try VVideoCropGeometry.displayedTransform(
            naturalSize: CGSize(width: 1920, height: 1080),
            preferredTransform: .identity
        )
        XCTAssertEqual(landscape.size, VVideoOutputSize(width: 1920, height: 1080))

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
        let bounds = CGRect(x: 0, y: 0, width: 1920, height: 1080)
            .applying(portrait.transform)
        XCTAssertEqual(bounds.minX, 0, accuracy: 0.001)
        XCTAssertEqual(bounds.minY, 0, accuracy: 0.001)
    }

    func testEveryDiscreteRotationUsesExpectedOrientedDimensions() throws {
        XCTAssertEqual(
            try VVideoCropGeometry.orientedSize(width: 1920, height: 1080, rotation: 0),
            VVideoOutputSize(width: 1920, height: 1080)
        )
        XCTAssertEqual(
            try VVideoCropGeometry.orientedSize(width: 1920, height: 1080, rotation: 90),
            VVideoOutputSize(width: 1080, height: 1920)
        )
        XCTAssertEqual(
            try VVideoCropGeometry.orientedSize(width: 1920, height: 1080, rotation: 180),
            VVideoOutputSize(width: 1920, height: 1080)
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
            try VVideoCropGeometry.cropRotationRadians(270),
            -.pi * 1.5,
            accuracy: 0.0001
        )
    }

    func testPixelCropTranslationAndRenderSize() throws {
        let plan = try VVideoCropGeometry.createPlan(
            naturalSize: CGSize(width: 1000, height: 500),
            preferredTransform: .identity,
            explicitRotation: 0,
            cropRect: VVideoCropRect(
                left: 0.1,
                top: 0.1,
                right: 0.9,
                bottom: 0.9
            ),
            quality: .high,
            customWidth: 800,
            customHeight: 400,
            dimensionHandling: .exact
        )

        XCTAssertEqual(plan.pixelCrop, VVideoPixelCrop(left: 100, top: 50, right: 900, bottom: 450))
        XCTAssertEqual(plan.outputSize, VVideoOutputSize(width: 800, height: 400))
        XCTAssertEqual(
            plan.effectOrder,
            [.preferredOrientation, .cropTranslation, .outputSizing]
        )
        let transformedCrop = CGRect(x: 100, y: 50, width: 800, height: 400)
            .applying(plan.transform)
        XCTAssertEqual(transformedCrop.minX, 0, accuracy: 0.001)
        XCTAssertEqual(transformedCrop.minY, 0, accuracy: 0.001)
        XCTAssertEqual(transformedCrop.width, 800, accuracy: 0.001)
        XCTAssertEqual(transformedCrop.height, 400, accuracy: 0.001)
    }

    func testRotationPrecedesCropAndOutputSizing() throws {
        let plan = try VVideoCropGeometry.createPlan(
            naturalSize: CGSize(width: 1920, height: 1080),
            preferredTransform: .identity,
            explicitRotation: 90,
            cropRect: VVideoCropRect(left: 0, top: 0, right: 0.5, bottom: 1),
            quality: .high,
            customWidth: 540,
            customHeight: 1920,
            dimensionHandling: .exact
        )
        XCTAssertEqual(plan.orientedSize, VVideoOutputSize(width: 1080, height: 1920))
        XCTAssertEqual(plan.pixelCrop, VVideoPixelCrop(left: 0, top: 0, right: 540, bottom: 1920))
        XCTAssertEqual(
            plan.effectOrder,
            [.preferredOrientation, .explicitRotation, .cropTranslation, .outputSizing]
        )
    }

    func testSizingModesAndEncoderSafeDimensions() throws {
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
        XCTAssertEqual(
            try VVideoCropGeometry.resolveOutputSize(
                cropSize: VVideoOutputSize(width: 120, height: 320),
                quality: .high,
                customWidth: nil,
                customHeight: nil,
                handling: .autoAlign
            ),
            VVideoOutputSize(width: 120, height: 320)
        )
        let auto = try VVideoCropGeometry.resolveOutputSize(
            cropSize: VVideoOutputSize(width: 1001, height: 501),
            quality: .medium,
            customWidth: nil,
            customHeight: nil,
            handling: .autoAlign
        )
        XCTAssertEqual(auto.width % 2, 0)
        XCTAssertEqual(auto.height % 2, 0)

        let letterbox = try VVideoCropGeometry.resolveOutputSize(
            cropSize: VVideoOutputSize(width: 1000, height: 500),
            quality: .medium,
            customWidth: 640,
            customHeight: 480,
            handling: .letterbox
        )
        XCTAssertEqual(letterbox, VVideoOutputSize(width: 640, height: 480))

        XCTAssertThrowsError(
            try VVideoCropGeometry.resolveOutputSize(
                cropSize: VVideoOutputSize(width: 1000, height: 500),
                quality: .medium,
                customWidth: 640,
                customHeight: 480,
                handling: .exact
            )
        )
    }

    func testMalformedMapIsRejected() {
        XCTAssertThrowsError(
            try VVideoAdvancedConfig.fromMap([
                "cropRect": [
                    "left": 0.5,
                    "top": 0,
                    "right": 0.5,
                    "bottom": 1
                ]
            ])
        )
        XCTAssertThrowsError(
            try VVideoAdvancedConfig.fromMap(["cropRect": "invalid"])
        )
    }

    func testFallbackIsProhibitedForEffectiveEdits() throws {
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
        let fullCrop = try VVideoCompressionConfig.fromMap([
            "quality": "MEDIUM",
            "advanced": ["cropRect": full.toMap()]
        ])
        let crop = try VVideoCompressionConfig.fromMap([
            "quality": "MEDIUM",
            "advanced": [
                "cropRect": [
                    "left": 0,
                    "top": 0,
                    "right": 0.5,
                    "bottom": 1
                ]
            ]
        ])
        let rotation = try VVideoCompressionConfig.fromMap([
            "quality": "MEDIUM",
            "advanced": ["rotation": 90]
        ])

        XCTAssertFalse(
            VVideoCropGeometry.requiresEncodedOutput(video: video, config: noEdit)
        )
        XCTAssertFalse(
            VVideoCropGeometry.requiresEncodedOutput(video: video, config: fullCrop)
        )
        XCTAssertTrue(
            VVideoCropGeometry.requiresEncodedOutput(video: video, config: crop)
        )
        XCTAssertTrue(
            VVideoCropGeometry.requiresEncodedOutput(video: video, config: rotation)
        )
    }
}
