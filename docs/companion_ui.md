# Companion interface and saved desktops

The Android and iOS products render the same Compose Multiplatform interface from
`:ui:companion:sharedUi`. KNet's fixed dark palette remains authoritative. Companion supplies a mobile shape
configuration and readable mobile typography. The approved circular-control design uses the official vector
wordmark, an inline desktop selector, Connect/Trust/Inspect milestones, and one quiet inset summary. Hero pages
scroll on compact/larger-text screens; instruction pages keep Settings/Continue separate from scrollable guidance.
All artwork is code/vector based, with no chart/rendering dependency or packaged phone mockup bitmaps.

## Reusable visual contract

`CompanionRoundControl` renders one accessible action for the circle and its caption. Native capture, trust and
handoff state own its symbol, text, enabled policy and progress. Pairing/starting/stopping/switching progress cannot
be tapped. Start uses the KNet blue ring; verified/running states use restrained green. A connected transport alone
does not label inspection Active. Progress draws only while composed, with static arcs when KNet motion is disabled.
`CompanionHeroPage`, `CompanionPage`, `CompanionHeading`, `CompanionPanel` and status rows share spacing and mobile
typography across Android/iOS. System camera, certificate installation and VPN prompts remain native.

## Screen coverage

| Approved individual screens | Implemented states |
| --- | --- |
| 1–4 | Connect, native QR scanner, camera access, pairing progress |
| 5–10 | Checking trust, certificate download, Android installation, iOS installation/full trust, verified, verification unavailable |
| 11–15 | Ready, starting, running, stopping, desktop unavailable |
| 16–19 | Desktop manager, switching, switch failed, forget confirmation |
| 20–21 | App-owned VPN explanation, expired QR recovery |

The desktop selector is available during certificate setup and Home. Add desktop opens QR camera/gallery pairing
without clearing the existing selection or registrations. Back cancels invitation resolution, but cannot interrupt
remote pairing after it may have rotated a credential or interrupt a native handoff. Users without an active
selection can still reopen their saved desktops from Connect. Native camera/settings/VPN consent surfaces remain
owned by their Android/iOS products; the shared guidance does not imitate system dialogs.

Certificate trust is evaluated before download. Only the selected desktop's exact trusted root enables Continue.
Unavailable verification offers its reason and Retry. Installation guidance names the platform and export location;
returning from Settings triggers the existing authoritative trust check. Downloading a file does not imply trust.

## Desktop handoff

`SwitchCompanionDesktopUseCase` serializes handoffs. It stops capture and disconnects transport before authenticating
the target's pinned identity and credential, including local endpoint recovery. Authenticating an inactive desktop
cannot select it early. An unreachable target leaves the previous desktop selected and capture stopped; all saved
pairings remain. After successful authentication, the target becomes active and its own root is rechecked.

Previously running inspection resumes only when the target has completed onboarding for that exact root and passes
fresh trust verification. Native consent is still required when the platform asks for it. Missing trust/onboarding
opens setup rather than automatically starting limited capture. An explicit retry retains the original resume intent.
If capture restart fails after target selection, recovery shows the actual selected target, not a fabricated rollback.

Pairing another desktop keeps the old selection until the new pairing has been durably committed. Existing capture
is stopped before pairing can rotate credentials. Re-scanning an existing desktop updates it by desktop ID. Display
names do not identify registrations. Stored credentials and certificate enrollment remain separately scoped to each
desktop. Forget requires confirmation and removes only that pairing and its credential/enrollment. System-installed
certificates are not uninstalled by Forget.

## Qualification and physical checks

Application tests cover ordering, idle/running handoffs, unreachable targets, root mismatch, missing onboarding,
native consent, restart failure, duplicate/same/missing targets, serialization and cancellation. Presentation tests
cover manager cancellation, adding/re-pairing without duplicates, retry, repeated taps, scoped removal and stale
trust/availability. Persistence tests reload multiple registrations, selection and independent root enrollments.

Headless tests render the actual shared application, exercise the QR/permission controls, modal layers and recovery
actions, and check bottom-action visibility on compact and larger-text layouts. Android shared/app compilation,
unit tests, debug APK assembly, iOS shared compilation, simulator tests and simulator framework linking form the
build gate. No physical-device result is implied by these automated checks.

Physical checks remain: Android/iOS camera permission and gallery behavior, actual CA/profile installation and
foreground trust rechecks, VPN consent, switching between two real desktops while capture is active, unreachable
and changed-address recovery, process recreation, and independently forgetting a pairing. Existing platform VPN
provider/signing limitations are unchanged by this interface work.

Automated qualification (2026-11-13): 469 test executions passed with zero failures, errors or skipped cases.
`companionSharedQualification`, `companionIosFoundationQualification`, Android app `testDebugUnitTest` and
`assembleDebug`, and iOS app `linkDebugFrameworkIosSimulatorArm64` all passed, including the architecture gates.
This earlier qualification covered the previous visual treatment. The circular-control refresh is qualified below. Physical
checks listed above remain pending. No product application was installed or launched during qualification.

## Circular-control refresh qualification (2026-11-13)

All 474 automated tests passed with zero failures/errors/skips, including 36 shared UI tests. Reviewed actual
renders for all 21 approved screen/state designs, normal/larger-text platform guidance and compact scroll reachability.
Regression cases cover stable Home control positions, camera preview leaving composition during pairing,
non-interactive progress, reduced-motion image stability and transport/capture status separation. Shared qualification,
iOS foundation qualification, all six architecture gates, Android app unit tests/debug APK assembly and iOS simulator
framework linking passed. Builds used isolated outputs without installing/launching either product or accessing the
user database. Physical-device checks above remain pending. Final build log: `/tmp/knet-companion-circular-final.log`.

## Direct camera permission entry

Scan requests native camera permission on that tap, without a separate full-screen permission step. A denial leaves
Connect available; another explicit Scan tap retries while the native adapter reports permission can be requested.
When the OS requires Settings, a subsequent Scan tap opens the approved dismissible sheet with Open Settings,
Choose QR image and Not now. The sheet never appears automatically after a denial. Granting permission or returning
from Settings with access enabled opens the scanner once. Returning without a grant leaves Connect usable.

The native adapters publish a pending-request state and suppress duplicate prompts and lifecycle refresh while
that prompt is active. Android retains installation-local unresolved request history across recreation and clears
it after a grant, allowing a later one-time grant expiry to request permission again. Native permission facts, not
a hardcoded denial counter, select retry versus Settings; iOS can require Settings after its first denial. UI saves
only the explicit pending intent and cancels it on dismissal, gallery selection or opening desktop management.
Choose QR image is a shared bordered secondary button with an image icon and requires no camera permission.

Qualification: 481 automated tests passed with zero failures/errors/skips, including 41 shared UI tests and 13
Android app unit tests. Regressions cover denial/retry, blocked access, asynchronous/direct and Settings grants,
non-grant return, dismissal, duplicate taps, independent gallery selection and compact/larger-text sheet actions.
Actual main-button and normal/large sheet renders were reviewed. Shared/iOS foundation qualification, all six
architecture checks, Android APK assembly and iOS simulator framework linking passed. Final log:
`/tmp/knet-companion-permission-final.log`. Native prompt presentation and actual Settings/camera lifecycle still
require physical-device verification. No product was installed/launched or user database accessed.

## Desktop QR after a successful scan

The desktop drawer observes availability of its exact one-time bootstrap ID. Successful secret-authenticated
redemption consumes that invitation and immediately replaces the QR with “This QR code has expired. Click to
regenerate.” and a Regenerate QR button. Timed expiry uses the same recovery. A valid camera scan or gallery QR
can trigger this only after it reaches Desktop; an offline camera decode is not observable there. This state means
the QR is no longer usable, not that certificate setup or inspection has completed.

Availability is a bounded, process-local snapshot of pending IDs, with no payloads or credentials. The application
use case projects the selected identity, and the ViewModel combines it with the countdown in one collector. A
claim that occurs before subscription is still visible; invalid/replayed claims and late use of a different QR
cannot invalidate the current QR. Closing or replacing the drawer/network cancels observation. Regeneration
creates a new one-time invitation only after the user clicks the action.

Qualification: 119 relevant application/connectivity/presentation/gateway tests passed with zero failures/errors/
skips, including actual normal/compact drawer renders. Desktop compilation, all six architecture checks and
`git diff --check` passed. No product or user database was opened. Final log: `/tmp/knet-qr-consumed-final.log`.
A real Companion camera scan remains a manual check.
