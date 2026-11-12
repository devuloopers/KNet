# Fixed dark application appearance

Status: completed; automated verification passed (2026-11-11).

KNet uses its existing dark appearance independently of the host operating system. Theme controls are removed from
Settings and the legacy global toolbar. The remaining default-language control belongs to the Scripting category;
its intent, persistence and runtime behavior are unchanged. There is no theme preference to migrate.

## Ownership

UI core's `KNetApplicationTheme` supplies fixed dark tokens and the Material bridge to desktop bootstrap/MainWindow,
the shared Companion root and the iOS loading/error surface. Explicit light/system modes remain in the lower-level
`KNetTheme` for previews and future work. Production roots do not select system appearance.

Native appearance follows the product policy where KNet owns it:

- macOS startup and packaged JVM arguments set `apple.awt.application.appearance=NSAppearanceNameDarkAqua` before
  window creation. The window-background resize workaround consumes the shared dark tokens. See the
  [OpenJDK 21 implementation](https://github.com/openjdk/jdk21u/blob/master/src/java.desktop/macosx/native/libosxapp/NSApplicationAWT.m).
- Android Companion uses explicit dark status/navigation bar styles with its existing native dark scrim resource
  and light system icons, using the pinned Activity 1.13 API. See [SystemBarStyle](https://developer.android.com/reference/androidx/activity/SystemBarStyle).
- iOS declares Dark in its Info.plist and requests a dark SwiftUI color scheme for its window. See
  [UIUserInterfaceStyle](https://developer.apple.com/documentation/bundleresources/information-property-list/uiuserinterfacestyle).

External OS-owned interfaces, such as the system Settings app, retain their own appearance.

## Verification

```sh
./gradlew --init-script /tmp/knet-traffic-inspector-20261110.init.gradle.kts \
  verifyArchitectureFoundation \
  :ui:core:jvmTest :ui:desktop:settings:jvmTest :ui:desktop:app:jvmTest \
  :ui:companion:sharedUi:jvmTest :products:desktop:test :products:desktop:compileKotlin \
  :products:companion:androidApp:compileDebugKotlin \
  :products:companion:iosApp:compileKotlinIosSimulatorArm64 \
  --max-workers=3 --no-daemon --no-configuration-cache --console=plain
plutil -lint products/companion/iosApp/app/Info.plist
xcrun swiftc -frontend -parse products/companion/iosApp/app/KNetCompanionApp.swift
git diff --check
```

BUILD SUCCESSFUL in 1 minute 53 seconds; 323 actionable tasks (191 executed, 28 from cache, 104 up to date).
The five requested JVM test outputs contain **96 tests, zero failures/errors/skips**: UI core 21, Settings 10,
desktop app 14, shared Companion UI 12 and desktop product 39. All six architecture checks, desktop/Android product
and iOS simulator Kotlin compilation, plist validation and Swift syntax parsing passed.

Headless Settings checks simulate system light/dark/light changes, verify KNet and Material palettes stay dark,
check wide/compact navigation and removed controls, and select Kotlin through the scripting dropdown. The pinned
Compose system-theme local is used only in test code; a future Compose upgrade may require adapting that hook.
The native macOS startup test restores the previous process appearance property after checking configuration.

The wide/compact Settings renders under `ui/desktop/settings/build/reports/dark-appearance/` were inspected.
Runtime audit found no system-following mode in production roots. Logs and counted XML evidence are
`/tmp/knet-fixed-dark-qualification.log` and `/tmp/knet-fixed-dark-qualification-summary.json`.

Verification used isolated build outputs. No app was launched, running JAR replaced, host theme changed or user
database modified. Native appearance was not visually exercised in a launched app or on physical devices.
Rebuild/relaunch to load the policy.
