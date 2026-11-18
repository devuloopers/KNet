
### What's fixed

- Clear a device's complete retained traffic without removing its workspace, regardless of device presence or
  whether capture is running. Recover interrupted capture sessions so Clear works immediately after restarting.
- Close an offline device workspace only after its traffic has been deleted successfully. Keep pairing and
  other devices' history, retain tabs across restarts, and preserve a tab if its device reconnects during Close.
- Use native Windows minimize, maximize/restore and close controls in the integrated workspace title bar.
  Keep the native caption stable while the application updates and preserve system decorations on unsupported runtimes.
- Keep short dropdown labels readable at fractional display scales, including Windows 125%, while retaining
  bounded truncation for genuinely long values.
- Open the desktop window centered at 80% of the usable screen width and height, respecting readable minimums
  and the space reserved for the taskbar or dock.
- Provide a scoped Windows development firewall helper for Companion access when changing Java runtimes.

### Compatibility and scope

- Compatible with KNet Companion 0.2.0; this patch does not require a Companion upgrade.
- macOS and Windows packages remain unsigned development distributions.
- Windows development launches require a Private-LAN firewall rule for the actual Java executable. Installed
  application permissions do not automatically transfer to a development runtime; the helper is opt-in.
