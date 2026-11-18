# Desktop 0.2.1 release qualification

Status: **IN PROGRESS — cross-platform qualification and native packaging.**

The user authorized creating and publishing this Desktop patch release. The canonical release tag is
`desktop-v0.2.1`, following the repository's independent product-release workflow. Companion remains 0.2.0.
Existing public releases and their tags are immutable.

## Local evidence

- All 137 targeted Clear/presence/workspace cases passed, including real socket forwarding through Clear,
  authenticated device/control sessions and four real Room/DataStore Clear/Close composition cases.
- Six native title-bar lifecycle/layout/policy cases and five foundation cases passed. A JBR probe verified
  startup geometry against the actual 125% display work area.
- Rendered dropdown and toolbar checks cover 100/125/150/175/200% display scales, enlarged fonts and narrow/wide
  toolbars. The broader run passed 158 cases; one unchanged Linux-path assumption failed on Windows and remains
  documented in `traffic_auto_scroll.md`.
- Desktop and shared UI-core Android compilation and all six architecture checks passed for the feature changes.
- Windows native controls were checked in the packaged development app. Cross-monitor DPI transitions and macOS
  native gestures remain physical acceptance follow-up, separate from hosted tests and packaging.

## Hosted qualification and publication

Initial master CI exposed a WebSocket executor fixture that assumed a send-completion callback always precedes
the peer's echo callback. Full-duplex callbacks are independent. Both one-shot and interactive fixtures now check
exactly one outbound and inbound message with the expected text/bytes and unique sequence values, while preserving
Started/Completed checks. The interactive collector subscribes before sending. No production behavior or gate
was disabled to address this scheduling-dependent assertion.

The first versioned CI run also exposed insufficient offered duration in the sustained-load fixture. Upload
measured 99,982.5 bit/s over 64.011 seconds, with the last flow's first byte at 4.438 seconds (inside the existing
five-second bound); only 59.573 seconds remained after all flows started. The fixture now offers 72 seconds of
payload, leaving more than a minute even at its existing 105 kbps upper-rate and five-second startup limits.
Rate, startup, exact-byte/hash, EOF, queue, heap and direct-memory assertions remain enabled. The first draft
workflow is cancelled before advancing the new unpublished tag with an explicit remote-object lease.

The revised local gate passed all three cases: download measured 99,342.9 bit/s with a 71.518-second shared window;
upload measured 99,960.7 bit/s with a 71.669-second shared window. The 500 MiB integrity and bounded-memory case
also passed. Evidence: `build/release-0.2.1/load-window-verification.log` and the task's TSV/XML reports.

The next hosted run exposed three additional fixture assumptions. The sustained-transfer clock included TCP and
CONNECT setup, so a late connection could consume the fairness allowance before payload transfer began. All four
tunnels now finish setup before a shared payload-start barrier; the existing five-second first-byte, one-minute
shared window and rate/integrity/memory limits remain unchanged. Tunnel setup has its own bounded deadline.

On Linux, enlarged fonts made the long streaming-profile label exceed the toolbar's intentional 30% width cap at
minimum window width. Short Conditions/Auto scroll labels must still fit at every tested size; the long profile
must fit at wide width, while the narrow case retains the existing bounded-overflow and no-overlap requirements.
The Clear/Close integration fixture now uses a single-thread Main dispatcher and dispatches UI actions there,
matching production event dispatch instead of allowing Unconfined callbacks to race on database threads. Its
original five-second waits remain. The revised four integration and six toolbar cases passed locally. All three
load cases and architecture checks also passed (`cross-platform-fixture-verification.log`): download measured
99,971.1 bit/s with a 0.493-second last first byte, and upload 99,981.3 bit/s with a 1.643-second last first byte.
Both shared transfer windows exceeded 70 seconds; the 500 MiB case retained integrity and memory checks.

Pending complete CI, Desktop release qualification and all five packaging jobs on the final source commit.
The workflow stages an unpublished draft with 13 packages, checksums and provenance. Publication will follow
source/tag verification and asset inspection; the public release and Latest marker will then be checked.
