# Smart Compatibility retirement

Status: completed; automated verification passed (2026-11-10).

The user chose manual handling from Traffic instead of automatic exception learning. Smart Compatibility is removed
from the product: no learner, temporary entry cache, automatic trial, setting, help control or Forget action remains.

## Retained workflow

1. Select a rejected, cancelled or tunnelled connection in Traffic. Captured diagnosis remains based on typed evidence;
   cancellation alone is not a certificate rejection.
2. Choose Always tunnel or Inspect next time. The application saves or updates the equivalent exact host/port/transport
   rule in Room, retaining its identity. Different capture IDs can share the same destination rule.
3. New matching connections use saved/default policy. The current winning exact rule remains selected on revisits;
   inherited, disabled and overridden rules stay distinct. Failed saves cannot fabricate a selected choice.
4. Review/edit/disable/delete rules and the default TLS action in Protected Traffic settings. No observed list or
   automatic compatibility control is duplicated there.

Repeated TLS failures never create a rule or change future handling. A client must reconnect after a manual change;
KNet does not replay an HTTP request or repair a failed TLS socket. A saved rule is durable across application restart.

## Compatibility and scope

Schema 34 remains unchanged. The old `smartCompatibilityEnabled` setting and nullable `smartEntryId` capture column
are retained solely to open existing databases without destructive migration. Runtime configuration does not expose
or read the retired setting. New captures write no automatic identity. Historical SMART origin and setup/trial reason
values remain readable as historical evidence; they never become live policy or restored exceptions.

TLS certificate-flight observation and supported rejection classification remain for accurate diagnostics. The default
and manual policy apply at the desktop CONNECT boundary; plain HTTP and UDP/QUIC are not newly governed by this change.
Network Conditions shaping, capture lifecycle, source attribution and existing imported manual exceptions remain intact.

## Verification

The replacement real-socket suite exercises TLS 1.2, TLS 1.3 and mTLS: repeated inspection rejection with zero origin
requests, manual SNI rule enablement, the original origin certificate/client authentication, actual 100 kbps delivery,
exactly one application request, then explicit Inspect restoration. Room tests cover restart, rule identity, same-
destination sharing, unchanged history, legacy setting on/off, and historical automatic evidence readability.

Application and UI regressions cover live saved/default policy, actions and failures, selection/recreation, overridden
rules, compact/keyboard controls and absence of automatic compatibility UI. `protectedTrafficQualification` replaces
the former Smart gate; the aggregate gate requires the manual reconnect suite. Physical qualification remains separate.

Executed the two feature gates and desktop compilation with all build outputs isolated under
`/tmp/knet-traffic-inspector-20261110` so no running application's JARs were replaced:

```sh
./gradlew --init-script /tmp/knet-traffic-inspector-20261110.init.gradle.kts \
  protectedTrafficQualification networkConditionsQualification :products:desktop:compileKotlin \
  --max-workers=3 --no-daemon --no-configuration-cache --console=plain
git diff --check
```

Result: **BUILD SUCCESSFUL in 5m 48s**, 289 actionable tasks (145 executed, 15 from cache, 129 up to date).
The 25 distinct feature-gate XML outputs contain **967 tests, zero failures/errors/skips**. This count excludes
separate canonical-load qualification. Relevant totals include domain 95, application 52, data 81, proxy 140,
simulator 58, Protected Traffic UI 6, Traffic UI 70, shared UI 21, workspace 12 and product 38. All six architecture
checks and the requirements-ledger check passed. The SC acceptance rows are explicitly retired/superseded by the
user decision; unrelated pending software and physical requirements remain open.

Visually inspected the current settings, rejected-flow inspector and compact 125% text saved-Tunnel render. Smart
controls are absent and the current saved action remains visible. Compiled domain/application/data/affected UI JARs
contain no SmartCompatibility classes. Old Room fields and captured provenance are inert compatibility data only.
The desktop app was not launched; rebuilding/relaunching the user's app is required to load the changed classes.
