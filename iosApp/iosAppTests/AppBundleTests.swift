import UIKit
import XCTest

/// Runs inside the app on the simulator, so `Bundle.main` is the built app: checks what the build put in it.
final class AppBundleTests: XCTestCase {
    private let app = Bundle.main

    func testPrivacyManifestIsBundled() throws {
        let url = try XCTUnwrap(app.url(forResource: "PrivacyInfo", withExtension: "xcprivacy"), "PrivacyInfo.xcprivacy is in the app")
        let manifest = try XCTUnwrap(
            PropertyListSerialization.propertyList(from: Data(contentsOf: url), format: nil) as? [String: Any]
        )
        XCTAssertEqual(manifest["NSPrivacyTracking"] as? Bool, false)
        XCTAssertEqual((manifest["NSPrivacyCollectedDataTypes"] as? [Any])?.count, 0, "nothing is collected")
        XCTAssertFalse((manifest["NSPrivacyAccessedAPITypes"] as? [Any] ?? []).isEmpty)
    }

    func testPurposeStringsArePresent() {
        for key in ["NSCameraUsageDescription", "NSFaceIDUsageDescription", "NSPhotoLibraryAddUsageDescription"] {
            let text = app.object(forInfoDictionaryKey: key) as? String ?? ""
            XCTAssertFalse(text.isEmpty, "\(key) is set")
        }
    }

    func testOneWindowOnly() {
        let scenes = app.object(forInfoDictionaryKey: "UIApplicationSceneManifest") as? [String: Any]
        XCTAssertEqual(scenes?["UIApplicationSupportsMultipleScenes"] as? Bool, false)
    }

    func testIconAndLaunchColour() {
        let icons = app.object(forInfoDictionaryKey: "CFBundleIcons") as? [String: Any]
        let primary = icons?["CFBundlePrimaryIcon"] as? [String: Any]
        XCTAssertEqual(primary?["CFBundleIconName"] as? String, "AppIcon")
        XCTAssertNotNil(UIColor(named: "LaunchBackground", in: app, compatibleWith: nil))
    }

    func testVersionComesFromTheSharedFile() {
        let version = app.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        XCTAssertNotNil(version.range(of: #"^\d+\.\d+\.\d+$"#, options: .regularExpression), "numbers only, got \(version)")
        let build = app.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
        XCTAssertNotNil(build.range(of: #"^\d+(\.\d+){0,2}$"#, options: .regularExpression), "got \(build)")
    }

    func testFontsAreBundled() throws {
        // Compose Multiplatform copies the shared code's resources into the app when Xcode builds it.
        let root = try XCTUnwrap(app.resourceURL)
        let files = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil)?.allObjects as? [URL] ?? []
        let fonts = files.filter { $0.pathExtension == "ttf" }.map(\.lastPathComponent)
        XCTAssertFalse(fonts.isEmpty, "no bundled fonts")
    }
}
