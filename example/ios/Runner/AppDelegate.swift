import Flutter
import UIKit
import AVFoundation
import AudioToolbox

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    let channel = FlutterMethodChannel(
      name: "v_video_compressor_example/inspection",
      binaryMessenger: engineBridge.applicationRegistrar.messenger()
    )
    channel.setMethodCallHandler { call, result in
      guard call.method == "inspectVideo" else {
        result(FlutterMethodNotImplemented)
        return
      }
      guard let arguments = call.arguments as? [String: Any],
            let path = arguments["path"] as? String else {
        result(FlutterError(
          code: "INVALID_ARGUMENT",
          message: "path is required",
          details: nil
        ))
        return
      }
      let asset = AVURLAsset(url: URL(fileURLWithPath: path))
      let audioTrack = asset.tracks(withMediaType: .audio).first
      let audioPresent = audioTrack != nil
      let attributes = try? FileManager.default.attributesOfItem(atPath: path)
      let fileSize = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
      let metadataValues = asset.commonMetadata.compactMap { item in
        item.stringValue
      }
      guard let videoTrack = asset.tracks(withMediaType: .video).first else {
        result([
          "hasVideo": false,
          "hasAudio": audioPresent,
          "durationMillis": Int64(CMTimeGetSeconds(asset.duration) * 1000),
          "fileSizeBytes": fileSize,
          "isPlayable": asset.isPlayable,
          "width": 0,
          "height": 0,
          "rotation": 0,
          "metadataValues": metadataValues
        ])
        return
      }
      let bounds = CGRect(
        origin: .zero,
        size: videoTrack.naturalSize
      ).applying(videoTrack.preferredTransform)
      let rotation = Int(
        (atan2(
          videoTrack.preferredTransform.b,
          videoTrack.preferredTransform.a
        ) * 180 / .pi).rounded()
      )
      var inspection: [String: Any] = [
        "hasVideo": true,
        "hasAudio": audioPresent,
        "durationMillis": Int64(CMTimeGetSeconds(asset.duration) * 1000),
        "fileSizeBytes": fileSize,
        "isPlayable": asset.isPlayable,
        "width": Int(abs(bounds.width).rounded()),
        "height": Int(abs(bounds.height).rounded()),
        "rotation": rotation,
        "videoCodec": self.codecName(for: videoTrack),
        "videoFrameRate": Double(videoTrack.nominalFrameRate),
        "videoEstimatedDataRate": videoTrack.estimatedDataRate,
        "metadataValues": metadataValues
      ]
      if let audioTrack {
        inspection["audioCodec"] = self.codecName(for: audioTrack)
        inspection["audioEstimatedDataRate"] = audioTrack.estimatedDataRate
        if let formatDescription = audioTrack.formatDescriptions.first {
          let audioDescription = formatDescription as! CMAudioFormatDescription
          if let stream = CMAudioFormatDescriptionGetStreamBasicDescription(
            audioDescription
          ) {
            inspection["audioSampleRate"] = stream.pointee.mSampleRate
            inspection["audioChannels"] = Int(stream.pointee.mChannelsPerFrame)
          }
        }
      }
      result(inspection)
    }
  }

  private func codecName(for track: AVAssetTrack) -> String {
    guard let description = track.formatDescriptions.first else {
      return "unknown"
    }
    let subtype = CMFormatDescriptionGetMediaSubType(
      description as! CMFormatDescription
    )
    let characters: [CChar] = [
      CChar((subtype >> 24) & 0xff),
      CChar((subtype >> 16) & 0xff),
      CChar((subtype >> 8) & 0xff),
      CChar(subtype & 0xff),
      0
    ]
    return String(cString: characters)
  }
}
