import XCTest

/// Launches the real app on the simulator and finds its first screen through the accessibility tree.
final class LaunchTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    func testLaunchesToTheWelcomeScreen() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30), "the app is running")

        // A fresh install opens on the welcome screen, drawn by the shared Compose UI.
        let welcome = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "Start your collection"))
            .firstMatch
        let found = welcome.waitForExistence(timeout: 60)

        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "Welcome screen"
        shot.lifetime = .keepAlways
        add(shot)
        XCTAssertTrue(found, "welcome text not found. Accessibility tree:\n\(app.debugDescription)")
    }
}
