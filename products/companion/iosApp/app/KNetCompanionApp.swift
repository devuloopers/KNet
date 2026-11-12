import SwiftUI

@main
struct KNetCompanionApp: App {
    var body: some Scene {
        WindowGroup {
            CompanionRootView()
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
        }
    }
}
