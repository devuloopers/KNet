import KNetPacketTunnelRuntime
import NetworkExtension

final class PacketTunnelProvider: NEPacketTunnelProvider {
    private let runtime = KNetPacketTunnelRuntime()

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        runtime.start(provider: self, options: options) { error in
            completionHandler(error)
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        runtime.stop()
        completionHandler()
    }
}
