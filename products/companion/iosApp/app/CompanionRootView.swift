import KNetCompanionIos
import SwiftUI
import UIKit

private final class CompanionKotlinApplicationHolder: ObservableObject {
    let application = CompanionIosApplication()
}

struct CompanionRootView: View {
    @StateObject private var holder = CompanionKotlinApplicationHolder()

    var body: some View {
        CompanionController(application: holder.application)
    }
}

private struct CompanionController: UIViewControllerRepresentable {
    let application: CompanionIosApplication

    func makeUIViewController(context: Context) -> UIViewController {
        application.rootViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Shared state is reactive; SwiftUI does not imperatively update the Compose controller.
    }
}
