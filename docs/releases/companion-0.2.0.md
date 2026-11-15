
### What's new

- Add, retain and switch between multiple configured KNet desktops without clearing app data.
- Use the redesigned pairing, QR scanning, certificate setup and connection screens.
- Check whether the desktop certificate is already installed before requesting installation.
- Improve camera-permission handling and provide a button for choosing a QR image.
- Report the connected device to Desktop through the authenticated control session, supporting
  separate device tabs and device-specific capture and policy behavior.
- Recover the control session after Desktop restarts while the VPN remains connected.
- Show capture-readiness guidance in a bottom sheet when Desktop recording is stopped or paused.
- Improve VPN shutdown and reconnect handling.

### Compatibility and scope

- Use KNet Desktop 0.2.0 with this Companion release for the coordinated device-session changes.
- The downloadable Android APK uses the existing KNet release signing key; versionCode is 2000.
- iOS production sources and the unsigned Simulator app are qualified by the release workflow.
  No signed physical-device IPA is distributed by this workflow.
- Android packet-level shaping remains experimental and requires physical-device validation for the
  intended workload; the iOS packet adapter remains unavailable.
