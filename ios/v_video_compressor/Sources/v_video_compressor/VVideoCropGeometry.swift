import AVFoundation
import CoreGraphics

struct VVideoOutputSize: Equatable {
    let width: Int
    let height: Int
}

struct VVideoPixelCrop: Equatable {
    let left: Int
    let top: Int
    let right: Int
    let bottom: Int

    var width: Int { return right - left }
    var height: Int { return bottom - top }
}

enum VVideoTransformStep: String {
    case preferredOrientation
    case explicitRotation
    case cropTranslation
    case outputSizing
}

struct VVideoCropPlan {
    let displayedSize: VVideoOutputSize
    let orientedSize: VVideoOutputSize
    let pixelCrop: VVideoPixelCrop
    let outputSize: VVideoOutputSize
    let transform: CGAffineTransform
    let effectOrder: [VVideoTransformStep]
}

enum VVideoCropGeometry {
    static let minimumEncoderDimension = 16
    private static let maximumAspectError = 0.01

    static func displayedTransform(
        naturalSize: CGSize,
        preferredTransform: CGAffineTransform
    ) throws -> (transform: CGAffineTransform, size: VVideoOutputSize) {
        guard naturalSize.width > 0, naturalSize.height > 0 else {
            throw VVideoConfigurationError.invalidArgument(
                "Source dimensions must be positive"
            )
        }
        let sourceBounds = CGRect(origin: .zero, size: naturalSize)
        let preferredBounds = sourceBounds.applying(preferredTransform)
        var transform = preferredTransform.concatenating(
            CGAffineTransform(
                translationX: -preferredBounds.minX,
                y: -preferredBounds.minY
            )
        )
        let normalizedBounds = sourceBounds.applying(transform)
        if abs(normalizedBounds.minX) > 0.001 || abs(normalizedBounds.minY) > 0.001 {
            transform = transform.concatenating(
                CGAffineTransform(
                    translationX: -normalizedBounds.minX,
                    y: -normalizedBounds.minY
                )
            )
        }
        return (
            transform,
            VVideoOutputSize(
                width: Int(abs(normalizedBounds.width).rounded()),
                height: Int(abs(normalizedBounds.height).rounded())
            )
        )
    }

    static func orientedSize(
        width: Int,
        height: Int,
        rotation: Int
    ) throws -> VVideoOutputSize {
        guard [0, 90, 180, 270].contains(rotation) else {
            throw VVideoConfigurationError.invalidArgument(
                "rotation must be 0, 90, 180, or 270"
            )
        }
        if rotation == 90 || rotation == 270 {
            return VVideoOutputSize(width: height, height: width)
        }
        return VVideoOutputSize(width: width, height: height)
    }

    /// Converts the editor-facing counter-clockwise rotation convention to
    /// the clockwise-positive angle produced by the video compositor.
    static func cropRotationRadians(_ rotation: Int) throws -> CGFloat {
        guard [0, 90, 180, 270].contains(rotation) else {
            throw VVideoConfigurationError.invalidArgument(
                "rotation must be 0, 90, 180, or 270"
            )
        }
        return -CGFloat(rotation) * .pi / 180
    }

    static func pixelCrop(
        size: VVideoOutputSize,
        rect: VVideoCropRect
    ) throws -> VVideoPixelCrop {
        guard rect.isValid() else {
            throw VVideoConfigurationError.invalidArgument("Invalid crop rectangle")
        }
        let crop = VVideoPixelCrop(
            left: Int(ceil(rect.left * Double(size.width))),
            top: Int(ceil(rect.top * Double(size.height))),
            right: Int(floor(rect.right * Double(size.width))),
            bottom: Int(floor(rect.bottom * Double(size.height)))
        )
        guard crop.width >= minimumEncoderDimension,
              crop.height >= minimumEncoderDimension else {
            throw VVideoConfigurationError.invalidArgument(
                "Crop resolves to \(crop.width)x\(crop.height); encoded dimensions must be at least 16x16"
            )
        }
        return crop
    }

    static func createPlan(
        naturalSize: CGSize,
        preferredTransform: CGAffineTransform,
        explicitRotation: Int,
        cropRect: VVideoCropRect,
        quality: VVideoCompressQuality,
        customWidth: Int?,
        customHeight: Int?,
        dimensionHandling: VDimensionHandling?
    ) throws -> VVideoCropPlan {
        guard (customWidth == nil) == (customHeight == nil) else {
            throw VVideoConfigurationError.invalidArgument(
                "customWidth and customHeight must be specified together"
            )
        }

        let displayed = try displayedTransform(
            naturalSize: naturalSize,
            preferredTransform: preferredTransform
        )
        var transform = displayed.transform
        let sourceBounds = CGRect(origin: .zero, size: naturalSize)
        var order: [VVideoTransformStep] = [.preferredOrientation]

        if explicitRotation != 0 {
            let radians = try cropRotationRadians(explicitRotation)
            transform = transform.concatenating(
                CGAffineTransform(rotationAngle: radians)
            )
            let rotatedBounds = sourceBounds.applying(transform)
            transform = transform.concatenating(
                CGAffineTransform(
                    translationX: -rotatedBounds.minX,
                    y: -rotatedBounds.minY
                )
            )
            order.append(.explicitRotation)
        }

        let oriented = try orientedSize(
            width: displayed.size.width,
            height: displayed.size.height,
            rotation: explicitRotation
        )
        let crop = try pixelCrop(size: oriented, rect: cropRect)
        transform = transform.concatenating(
            CGAffineTransform(
                translationX: CGFloat(-crop.left),
                y: CGFloat(-crop.top)
            )
        )
        order.append(.cropTranslation)

        let handling = dimensionHandling ?? .autoAlign
        let output = try resolveOutputSize(
            cropSize: VVideoOutputSize(width: crop.width, height: crop.height),
            quality: quality,
            customWidth: customWidth,
            customHeight: customHeight,
            handling: handling
        )
        let scaleX = CGFloat(output.width) / CGFloat(crop.width)
        let scaleY = CGFloat(output.height) / CGFloat(crop.height)
        let scale = handling == .letterbox ? min(scaleX, scaleY) : max(scaleX, scaleY)
        transform = transform.concatenating(
            CGAffineTransform(scaleX: scale, y: scale)
        )
        let scaledWidth = CGFloat(crop.width) * scale
        let scaledHeight = CGFloat(crop.height) * scale
        transform = transform.concatenating(
            CGAffineTransform(
                translationX: (CGFloat(output.width) - scaledWidth) / 2,
                y: (CGFloat(output.height) - scaledHeight) / 2
            )
        )
        order.append(.outputSizing)

        return VVideoCropPlan(
            displayedSize: displayed.size,
            orientedSize: oriented,
            pixelCrop: crop,
            outputSize: output,
            transform: transform,
            effectOrder: order
        )
    }

    static func resolveOutputSize(
        cropSize: VVideoOutputSize,
        quality: VVideoCompressQuality,
        customWidth: Int?,
        customHeight: Int?,
        handling: VDimensionHandling
    ) throws -> VVideoOutputSize {
        guard (customWidth == nil) == (customHeight == nil) else {
            throw VVideoConfigurationError.invalidArgument(
                "customWidth and customHeight must be specified together"
            )
        }
        let aspect = Double(cropSize.width) / Double(cropSize.height)
        let maxDimension: Int
        switch quality {
        case .high: maxDimension = 1920
        case .medium: maxDimension = 1280
        case .low: maxDimension = 960
        case .veryLow: maxDimension = 640
        case .ultraLow: maxDimension = 432
        }
        let boundWidth = customWidth ?? min(cropSize.width, maxDimension)
        let boundHeight = customHeight ?? min(cropSize.height, maxDimension)
        guard boundWidth >= minimumEncoderDimension,
              boundHeight >= minimumEncoderDimension else {
            throw VVideoConfigurationError.invalidArgument(
                "Requested output dimensions must be at least 16x16"
            )
        }

        let result: VVideoOutputSize
        switch handling {
        case .letterbox:
            result = VVideoOutputSize(
                width: alignedCanvasDimension(boundWidth),
                height: alignedCanvasDimension(boundHeight)
            )
        case .exact:
            let width = evenFloor(boundWidth)
            let height = evenFloor(boundHeight)
            guard preservesAspectWithinOnePixel(
                width: width,
                height: height,
                aspect: aspect
            ) else {
                throw VVideoConfigurationError.invalidArgument(
                    "Exact dimensions \(width)x\(height) do not preserve crop aspect ratio"
                )
            }
            result = VVideoOutputSize(width: width, height: height)
        case .autoAlign:
            let fitted = fitInside(
                maxWidth: boundWidth,
                maxHeight: boundHeight,
                aspect: aspect
            )
            guard let aligned = alignedIfAspectSafe(
                fitted,
                aspect: aspect,
                alignment: 16
            ) ?? alignedIfAspectSafe(
                fitted,
                aspect: aspect,
                alignment: 2
            ) else {
                throw VVideoConfigurationError.invalidArgument(
                    "Crop cannot produce encoder-safe dimensions"
                )
            }
            result = aligned
        }
        guard result.width >= minimumEncoderDimension,
              result.height >= minimumEncoderDimension else {
            throw VVideoConfigurationError.invalidArgument(
                "Resolved output \(result.width)x\(result.height) is below 16x16"
            )
        }
        return result
    }

    static func requiresEncodedOutput(
        video: VVideoInfo,
        config: VVideoCompressionConfig
    ) -> Bool {
        guard config.includeAudio else { return true }
        guard let advanced = config.advanced else { return false }
        return (advanced.cropRect != nil && !advanced.cropRect!.isFullFrame()) ||
            (advanced.trimStartMs ?? 0) > 0 ||
            (advanced.trimEndMs != nil &&
                Int64(advanced.trimEndMs!) < video.durationMillis) ||
            (advanced.rotation ?? 0) != 0 ||
            advanced.customWidth != nil ||
            advanced.customHeight != nil ||
            advanced.removeAudio == true ||
            advanced.videoCodec != nil ||
            advanced.audioCodec != nil
    }

    private static func fitInside(
        maxWidth: Int,
        maxHeight: Int,
        aspect: Double
    ) -> VVideoOutputSize {
        if Double(maxWidth) / Double(maxHeight) > aspect {
            return VVideoOutputSize(
                width: Int((Double(maxHeight) * aspect).rounded()),
                height: maxHeight
            )
        }
        return VVideoOutputSize(
            width: maxWidth,
            height: Int((Double(maxWidth) / aspect).rounded())
        )
    }

    private static func alignedIfAspectSafe(
        _ fitted: VVideoOutputSize,
        aspect: Double,
        alignment: Int
    ) -> VVideoOutputSize? {
        let candidate = VVideoOutputSize(
            width: alignDown(fitted.width, alignment),
            height: alignDown(fitted.height, alignment)
        )
        guard candidate.width >= minimumEncoderDimension,
              candidate.height >= minimumEncoderDimension,
              aspectError(
                  width: candidate.width,
                  height: candidate.height,
                  aspect: aspect
              ) <= maximumAspectError else {
            return nil
        }
        return candidate
    }

    private static func alignedCanvasDimension(_ value: Int) -> Int {
        let aligned = alignDown(value, 16)
        return aligned >= minimumEncoderDimension ? aligned : evenFloor(value)
    }

    private static func preservesAspectWithinOnePixel(
        width: Int,
        height: Int,
        aspect: Double
    ) -> Bool {
        return abs(Double(width) - Double(height) * aspect) <= 1 ||
            abs(Double(height) - Double(width) / aspect) <= 1
    }

    private static func aspectError(
        width: Int,
        height: Int,
        aspect: Double
    ) -> Double {
        return abs(Double(width) / Double(height) - aspect) / aspect
    }

    private static func alignDown(_ value: Int, _ alignment: Int) -> Int {
        return (value / alignment) * alignment
    }

    private static func evenFloor(_ value: Int) -> Int {
        return value - value % 2
    }
}
