# Windows integrated workspace title bar

## Behavior

Windows uses one dark workspace strip with the OS-owned minimize, maximize/restore and close controls on the
right. Native caption hit testing owns empty-space dragging, double-click and system-menu behavior. Compose
owns device-tab selection, tab Close, hover help and scrolling. `NativeDeviceTitleBar` remains a product-layer
platform adapter; shared feature modules do not import AWT or JBR.

Following JetBrains Jewel, the adapter applies native decoration from the measured title-bar layout, then reads
native left/right control insets. Equal-size shell recompositions do no native work. UI core owns the initial
control-space reservation. Compose reports interactive tab rectangles in window pixels; window-scoped mouse
listeners use the AWT graphics-device transform to reach that coordinate space before native hit testing.
Listeners are removed when the window composition is disposed. macOS keeps its left-side controls and fullscreen
handling; Linux and unsupported runtimes keep the complete system title bar above the workspace strip.

## Development and packaging

Windows and macOS Compose run/package tasks select JetBrains Runtime 21 through the existing toolchain resolver.
`-Pknet.desktop.javaHome=<absolute JBR directory>` remains an explicit override. IDE launches must also select
a compatible JBR to use the integrated strip; an Azul/ordinary OpenJDK launch falls back to standard decoration.
The Gradle daemon and Kotlin compiler may continue to run on another JDK.

Windows Firewall permissions are executable-specific. A development allow rule for the former Azul Java path does
not apply to the selected JBR Java path. Companion development requires a Private-network rule for the actual
runtime executable and the companion TCP ports (8181, 8182, 8183), limited to the local subnet. Automatic firewall
or security-setting changes are not part of window decoration.

The development helper [`Enable-CompanionFirewall.ps1`](../tools/development/Enable-CompanionFirewall.ps1) previews
or applies a runtime-specific Private/LocalSubnet TCP rule. See [development setup](../tools/development/README.md).

## Qualification (2026-11-18)

- Six focused title-bar policy/layout/lifecycle tests passed. The lifecycle regression reproduces non-reflexive
  JBR proxy equality; the measured-layout regression covers 100%, 125% and 200% density and unrelated shell updates.
- All 39 app-shell tests and `verifyArchitectureFoundation` passed. `createDistributable` produced a Windows
  application image with JBR 21.0.11. The existing renderer selection was retained.
- On Windows 11 Home (build 26200), the packaged app was checked at 125% display scaling in an isolated profile.
  Native minimize, maximize, restore and close buttons worked; native Close exited the test app. Tab double-click
  remained a client interaction; blank-space double-click maximized the frame. Blank-space drag moved the frame
  by the corresponding scaled distance. Caption painting remained stable across those operations.
- A system Snap Layout picker was observed during the initial native-control investigation. Physical Snap placement,
  corner resizing and multi-monitor DPI transitions remain manual acceptance follow-up. Scene-density tests do not
  substitute for those shell/device checks. macOS native regression acceptance requires a macOS host.

Evidence: `build/title-bar-verification.log`, `build/title-bar-app-tests.log` and module test XML/reports. Test profiles
and diagnostic Java probes are under ignored `build/title-bar-qa`; no production preferences or device registrations
were copied into those profiles.

## Primary implementation references

- [Jewel Windows title bar](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/decorated-window/src/main/kotlin/org/jetbrains/jewel/window/TitleBar.Windows.kt)
- [Jewel measured title-bar layout](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/decorated-window/src/main/kotlin/org/jetbrains/jewel/window/TitleBar.kt)
- [JBR native title-bar binding and lifecycle](https://github.com/JetBrains/JetBrainsRuntime/blob/jbr21/src/java.desktop/share/classes/java/awt/Window.java)

The first Windows prototype installed decoration before layout and used a global Toolkit mouse listener.
It passed pure policy checks but failed native acceptance with visibly unstable caption painting. The revised
implementation uses Jewel's layout and per-window event model; passing policy tests alone is not native acceptance.

The continuous caption fluctuation was traced to JBR API proxy equality: on JBR 21.0.11, a native title-bar proxy
reports false for `equals(self)`. Using it directly as a Compose remember/effect key repeatedly reset installation
state and disposed the native caption. `NativeTitleBarHandle` provides a stable identity key. A regression scene
uses the same non-reflexive proxy behavior and verifies one mount through repeated recompositions and one final
disposal. No direct JBR proxy is used as a Compose key.

## Initial floating window size (2026-11-18)

Desktop startup seeds Compose's remembered native window state with 80% of the usable screen width and height,
centered inside those bounds. The product reads `GraphicsEnvironment.maximumWindowBounds`, which excludes native
taskbar/dock/menu reservations. AWT returns logical coordinates, so no additional display-scale division applies.

UI core owns the fraction, centering and minimum-size policy. Initial bounds respect the shared 1024 by 600 dp
minimums where they fit and remain inside smaller work areas. These are startup values only; normal user resizing,
maximization and restoration continue through Compose's existing remembered window state.

Desktop/shared Android compilation, five existing foundation tests, six native title-bar tests and all six
architecture checks passed. The JBR 21.0.11 screen probe at 125% scaling reported 1536 by 912 usable logical units
and a centered initial size of 1228.8 by 729.6 dp. This verifies the native screen-to-startup-policy boundary
without resizing the running app. Logs: `build/initial-window-size-verification.log` and
`build/initial-window-size-native.log`.
