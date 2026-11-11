# KNet Smart Compatibility implementation plan

**Retired by user decision (2026-11-10).** Smart Compatibility has been removed. The implementation, matrix and
execution records below describe historical work, not current product behavior. See
[the retirement record](smart_compatibility_retirement.md) for the current manual-rule workflow and verification.


Status: **IN PROGRESS**

Target phase: Phase 136, extending Phase 135 Protected Traffic

Last updated: 2026-11-10

The [combined completion plan](network_conditions_protected_traffic_completion_plan.md) assigns actual presentation
evidence, remaining Smart matrix assertions, native integration and final CI qualification to C00–C12. Physical
acceptance becomes the only pending stage after those software and delivery requirements are verified.

## Approved direction

Smart Compatibility is **on by default**, as requested on 2026-11-10. This replaces the earlier discussion
recommendation to make automatic handling opt-in. The software implementation and qualification work started on 2026-11-10.
See [qualification evidence and remaining platform gates](smart_compatibility_qualification.md).

KNet should learn inspection incompatibilities for unfamiliar services without requiring users to maintain a
catalogue of Google, Apple, payment, identity, DRM, or other applications. It preserves original encryption by
selecting `TUNNEL` for eligible subsequent connections. Metadata capture and eligible Network Conditions continue.
It does not select platform `BYPASS`, decrypt protected content, or replay a failed connection or application request.

An unfamiliar service's first inspection attempt can still fail. Available handshake evidence cannot prove every
case of pinning or predict every protected connection. Uncertain evidence remains a suggestion.

## Preset retirement decision (2026-11-10)

The user accepts a first inspection failure as the learning step. **Remove compatibility preset controls and the
production service catalogue.** Smart Compatibility is the normal path; Advanced contains only default connection
handling and saved rules. There is no selectable Google/Apple checklist and no automatic service-specific exclusion
on fresh installs.

- Schema 34 imports only previously enabled v1 preset exceptions as visible saved rules. Disabled groups produce no
  rules. Original selectors, IDs and lower priority are preserved; explicit user rules still win.
- Imported exceptions can be disabled or deleted without returning after restart. Deliberate editing creates an
  explicit user rule; it must not overwrite an equivalent existing user rule.
- The old service-group flags are retired and their mutation API is removed. Versioned historical values exist only
  in the one-time migration and legacy test fixtures, not in runtime discovery or a UI catalogue.
- Existing settings, user rules, Traffic evidence, and Network Conditions remain intact. Historical preset evidence
  stays readable. Fresh configuration contains no built-in rules.
- No blanket Google/Apple, whole-network, or automatically generated whole-application exclusions.

## Defaults, migration, and controls

1. Add an independent, persisted `smartCompatibilityEnabled` setting, defaulting to `true` for fresh and upgraded
   installations. A subsequently saved `false` survives restart and future migration.
2. Preserve the existing global default action, explicit rules, the effects of previously enabled legacy choices, and retained Traffic.
   Smart on does not change the fallback to global tunnelling.
3. Use an explicit non-destructive Room migration at the next available schema version. Do not rewrite unrelated
   Network Conditions or capture settings. Test both a settings row and no settings row on upgrade.
4. Wait for the authoritative settings snapshot before activating automatic decisions. A loading/read failure must
   not overwrite a saved setting or use a provisional enabled value to override a persisted disable.
5. Show Smart's default-on state and explain that automatic tunnelling reduces payload visibility. Disabling Smart
   removes its temporary decisions from future policy evaluation; saved rules and imported exceptions still apply.
6. Turning Smart off, expiry, or policy edits never switches an already-open TLS stream between inspect and tunnel.
   Existing Network Conditions live-change behaviour remains independently owned by its current engine.

## Decision precedence

Retain the existing deterministic policy evaluator and its rule precedence. Add Smart only at the default-inspection
fallback boundary:

| Existing policy result | Smart behaviour |
|---|---|
| User rule selects Inspect, Tunnel, Block, or Bypass | Preserve that result; an explicit Inspect rule cannot be auto-overridden |
| Imported legacy exception selects an action | Preserve its lower priority and imported provenance; user rules win |
| Global default is Tunnel, Block, or Bypass | Preserve it; do not learn a conflicting action |
| Global default is Inspect; Smart is off | Inspect; existing failure reporting and manual actions remain |
| Global default is Inspect; Smart is on; eligible temporary entry matches | Tunnel with Smart evidence |
| Global default is Inspect; Smart is on; no eligible entry | Inspect and observe bounded failure evidence |

KNet's control-plane, pairing, self-recursion, and platform routing exclusions remain authoritative. Smart must never
override them or claim that a truly bypassed connection received capture or throttling.

## Evidence and eligibility

The current `TlsInspectionOutcomeHandler` callback exposes a generic failure. Replace that boundary with a typed,
bounded observation before adding learning. Do not build a detector by treating every failed TLS event as pinning.

Record only the information required to explain a decision:

- which side failed, handshake stage, and whether KNet actually presented its generated certificate;
- a supported certificate-related TLS alert/cause when available, with an explicit unknown category;
- canonical CONNECT authority, visible SNI and any authority mismatch, port, and transport;
- verified source application and ingress/paired-device identity when available, including attribution capability;
- policy provenance/generation, certificate generation, occurrence time, and the failed flow ID.

An automatic candidate requires a supported downstream certificate-rejection observation after certificate
presentation, a stable scoped identity, and no disqualifying policy, setup, or upstream failure. Label the reason
`Inspection certificate rejected`, not `Pinning confirmed`. Unknown or conflicting evidence produces a suggestion.

Never learn from DNS/connect errors, upstream certificate validation errors, malformed/oversized ClientHello,
unsupported TLS versions/ALPN, KNet certificate-generation errors, policy blocks, HTTP status codes, timeouts,
ordinary EOF/reset, cancellation, queue pressure, or deliberately injected Network Conditions failures.

If missing/expired KNet certificate setup or a burst of rejection across unrelated destinations suggests a setup
problem, suspend new automatic learning for that ingress and show certificate guidance. The initial bounded burst
threshold is five distinct destinations in 60 seconds, scoped to one ingress and tested independently. Do not turn a broken CA installation into a
large collection of automatic exclusions. Do not infer an application's trust-store contents from one failure.

## Scope, lifetime, and resource limits

- A learned key includes workspace/proxy session, trusted ingress/paired device, canonical exact hostname, port, and
  TCP/TLS transport. Include verified source identity where available; never invent an application identity.
- When app attribution is unavailable, show `Destination only` and scope the entry to that ingress and exact host.
  This may affect several applications using that destination; make that limitation visible.
- Absent/ambiguous hostname, ECH-hidden identity, conflicting authority, or an IP-only shared destination must not
  create an automatic broad exclusion. Use a suggestion or an explicit manual rule instead.
- Initial limits: at most **512 total evidence/candidate/temporary/cooldown keys per workspace**, **30 minutes absolute
  lifetime** for temporary entries, and at most **one automatic trial per key in a 10-minute window**. These are
  central internal defaults, not extra product controls. Byte progress must not extend the lifetime indefinitely.
- Use an injected monotonic clock for expiry and test scheduling. Retain bounded wall-clock timestamps for display.
  Cap metadata strings, queued observations, UI lists, and retained decision history as well as the key count.
- Temporary decisions remain in memory and expire at TTL or proxy-session end, whichever comes first. Capture
  rotation does not start a new policy session. Persist only settings, existing user rules, and bounded decision
  evidence attached to canonical flow records under existing capture retention.
- CA replacement, pairing/identity changes, relevant policy edits, workspace changes, and Smart disable invalidate
  affected candidates and temporary entries. Preserve existing flow-opening evidence.
- A manual `Always tunnel` action promotes a choice to the existing durable user-rule path. `Forget` removes learning
  for the key; Smart can observe a new failure later. A durable Inspect rule suppresses automatic handling for it.

No ML model, cloud lookup, whole-payload buffering, new polling loop per connection, or per-packet database access is
required. Use bounded metadata events and cached immutable policy snapshots; never block a Netty event loop.

## Connection lifecycle

| State | Trigger | Result |
|---|---|---|
| Observing | Default inspection opens | Record only bounded relevant evidence |
| Suggestion | Evidence is inconclusive or identity cannot be safely scoped | Keep policy unchanged; offer a manual action |
| Awaiting next connection | Eligible certificate rejection | Reserve a scoped temporary decision with reason and expiry |
| Automatic trial | The client independently reconnects | Atomically admit one tunnel trial for the key |
| Temporary tunnel | Trial establishes a raw path and transfers bytes | Keep the scoped decision until absolute expiry; record observed transport progress |
| Cooldown / suggestion | Trial fails or closes before useful progress | Withdraw the automatic decision; no repeated automatic retry loop |
| Cleared | Disable, expiry, invalidation, or Forget | Future flows use current explicit policy/default |

Define transport progress explicitly in implementation tests. Encrypted bytes flowing are not proof of a successful
TLS handshake, pinning, login, subscription retrieval, or purchase; do not label a trial as verified application
success. TLS-layer observability after tunnelling is limited.

Deduplicate repeated SNI/handshake failure callbacks and race-safe candidate updates. Concurrent connections cannot
create unbounded trials: while the single trial is pending, other new connections follow existing explicit/default
policy without waiting on an event loop. A stale failure cannot recreate an entry after disable or a policy edit.

Never reuse a failed TLS socket, change its certificate mid-handshake, replay ClientHello/application bytes, issue a
test purchase/request, or synthesize a client retry. Preserve the failed row and correlate subsequent Smart evidence
where possible without claiming a one-to-one business-operation relationship.

## UI and Traffic

Reuse **Protected Traffic** and KNet's existing theme and responsive components; no new sidebar screen is needed.

### Protected Traffic settings

The latest design direction (2026-11-10) removes the duplicate observed-connections list, decision drawer and
Overview/Advanced tabs. The header contains the Smart Compatibility switch, its current state and question-mark
hover help. Smart remains on by default; omit the redundant default-setting label. The settings ViewModel observes
configuration only, without a runtime-observation subscription. Opening or closing settings does not alter learning
or proxy/capture sessions.

Show these sections immediately in one scrollable settings page:

- **Default TLS connection handling:** Inspect, Tunnel, Block, and capability-dependent Bypass. Applies to proxy
  CONNECT connections, including HTTPS, GraphQL over HTTPS, WSS and gRPC over TLS. Plain HTTP, ws://, UDP and
  QUIC/HTTP/3 are outside this control. Matching saved rules take priority; changes affect new connections.
  Inspect remains the normal
  fallback. On the current explicit-proxy path, do not present Bypass as available direct routing; explain the
  effective tunnel mapping of an already-persisted Bypass policy without silently rewriting that policy.
- **Manual rules:** add/edit/enable/disable/delete typed application or destination/port/transport rules. Verified
  application selectors are capability-gated. Explicit rules override Smart learning.
- Previously enabled legacy exceptions appear in the saved rules list with an Imported badge; no preset catalogue
  or group toggle remains.

### Connection details and actions in Traffic

Traffic's right inspector owns captured rejection diagnosis, qualified possible causes, scoped live Smart evidence,
current future-connection handling and destination actions. Keep the captured outcome separate from current policy.
Always tunnel and Inspect next time save exact destination/port/transport rules in place. The active winning exact
saved choice remains checked on revisits; loading, failed saves, inherited handling and overridden choices must not
pretend to be a selected saved action. Forget automatic decision lives in overflow and only clears the matching
trusted ingress entry. Saved rules and historical captures remain unchanged by Forget.

Use shared UI-core wrapping, scrolling, action reflow and disclosure components. Technical details begins collapsed.
Keyboard actions, visible overflow, compact text scaling and accessible active/disabled states remain required.
No connection-specific inspector or action list is duplicated in Protected Traffic settings.

Do not add graphs, protection scores, adjustable learning internals, or decorative metrics. The images illustrate
the content and hierarchy; runtime UI must use KNet's actual reusable components and current canonical data.

Traffic rows/details retain original failure and opaque tunnel records. Add typed decision origin (manual, preset,
Smart, or global default), reason, scope, and related failure evidence without fabricating an HTTP response/body.
Changing a rule or forgetting a decision does not rewrite historical evidence. Clearing captured Traffic also clears
identifying links from the learning cache without resurrecting cleared rows.

Use accessible labels, keyboard actions, visible overflow chrome, virtualized lists, and design-system spacing and
motion. No AI confidence percentages, invented real-time activity, or unqualified `Verified service` badges.

## Module ownership and integration

| Module | Planned responsibility |
|---|---|
| `:core:domain` | Typed Smart settings, evidence classification, eligibility, scoped keys, lifetime/state transitions, pure precedence decisions |
| `:core:traffic` | Bounded typed decision-origin and failure evidence on canonical flows; no Netty/platform dependencies |
| `:application:desktop` | Observe/toggle/forget/retry/promote use cases and contracts for a session-scoped learner and decision snapshots |
| `:engine:proxy` | Typed handshake observations through injected ports, exact connection-stage evidence, existing raw relay and lifecycle |
| `:data:desktop` | Runtime adapters, bounded session cache, atomic snapshot publication, persistence mappings and capture evidence |
| `:storage` | Explicit settings/evidence migration and retained-history compatibility; no durable automatic allowlist |
| `:ui:core` | Shared tabs/detail-pane primitives, opt-in pane motion if needed, responsive bounds, focus/overflow and reduced-motion behaviour |
| `:ui:desktop:protectedTraffic` | Configuration-only state and ViewModel, Smart controls/help, default handling and saved-rule editing |
| `:ui:desktop:traffic` | Captured diagnosis, current scoped policy/Smart evidence, saved destination choices and context actions |
| `:products:desktop` | Explicit Koin composition, clock/limits/ports injection, startup/shutdown ownership and qualification wiring |
| Companion modules | Trusted ingress/source metadata and capability reporting through existing authenticated boundaries |

Feature ViewModels use application use cases rather than directly injecting repositories. Public APIs need KDoc;
states/reasons use enums or sealed types. Engine code must not import Room, feature UI, or platform app services.

## Platform boundaries

Smart learning first applies to eligible **TCP/TLS connections actually routed through KNet's shared proxy**. The same
decision engine should work for desktop and companion-carried TCP without service-name special cases.

Android and iOS currently expose translated sockets without original source attribution. Destination-only Smart
handling must work honestly in that state. Completing original-flow attribution remains Phase 135.4/135.5 work; do
not mark it complete because Smart exists. A desktop caller's identity must not be mistaken for the mobile owner.

The signed macOS transparent adapter, iOS provider entitlement/device qualification, opaque UDP/QUIC flow reporting,
and packet conditions retain their independent Phase 134/135 gates. Smart does not detect pinning inside opaque
UDP/QUIC, inspect HTTP/3, or make excluded system traffic visible. Never promote those capabilities from a TCP test.

## Delivery checklist

- [x] **136.1 Settings and domain contracts:** default-on policy, upgrade semantics, typed evidence/state, precedence,
  scoped keys, limits, injected clocks, and pure transition tests.
- [ ] **136.2 Failure classification:** typed hook, supported downstream alert classification and suggestion
  fallback implemented; actual certificate-flight evidence and full setup/fault exclusion regressions remain open.
- [x] **136.3 Runtime learning:** bounded session ownership, atomic candidate/trial lifecycle, cooldown/expiry,
  generation invalidation, disable/restart handling, and non-blocking proxy integration.
- [x] **136.4 Persistence and evidence:** additive settings/evidence migration, legacy decoding, exact origin/reason
  capture, restart/default-false preservation, retention and capture-clearing behaviour.
- [x] **136.5 Presentation:** Smart switch and hover help, immediate default/manual controls and imported legacy
  exceptions in Protected Traffic settings. Connection evidence and saved destination actions live in Traffic;
  duplicate observations, decision drawer and settings tabs retired. Shared responsive primitives and
  keyboard/accessibility tests cover the current settings and inspector ownership.
- [ ] **136.6 Automated qualification:** dedicated unit/migration/socket/UI/cross-module gate passes; complete
  false-positive, capture-lifecycle, protocol and resource matrix plus CI enforcement remain open.
- [ ] **136.7 Platform qualification:** packaged desktop, Android protected workloads beyond Billing, and independent
  entitled Darwin validation; record evidence and outstanding platform limits before capability promotion.

The [2026-11-10 readiness audit](network_conditions_protected_traffic_readiness_audit.md) reopens the evidence and
complete-qualification items above. Passing the existing gate does not mean that only physical tests remain.

## Required automated test matrix

| Area | Required cases |
|---|---|
| Defaults/migration | Fresh on; upgraded row/no-row on; saved off survives restart/upgrade; delayed/corrupt snapshot; preserve default/rules/groups/Traffic |
| Precedence | Explicit Inspect/Tunnel/Block/Bypass wins; legacy preset import/disable/delete and explicit precedence preserved; non-Inspect fallback unchanged; Smart off excludes learned state |
| Positive evidence | Supported downstream certificate rejection after actual presentation; exact stage/side/provenance; failed row retained; subsequent client reconnect tunnels |
| False positives | No learning for DNS/connect/upstream trust errors, missing/expired KNet setup, TLS/ALPN incompatibility, malformed/slow ClientHello, timeout/EOF/reset, cancellation, HTTP failures, injected faults |
| Evidence uncertainty | Unknown alert/cause, unavailable presentation evidence, missing/conflicting host, IP-only shared endpoint, absent identity; suggestion only where eligibility cannot be established |
| Scope/isolation | Normalized IDN/case/trailing-dot host rules; wildcard never generated; distinct ports/devices/workspaces/apps; destination-only label; authenticated versus spoofed attribution |
| Lifecycle | Absolute TTL and boundaries; monotonic time/wall-clock jumps; retry/cooldown; concurrent single trial; duplicate callbacks; failure after disable/edit; CA/pairing change; stop/restart |
| Limits | Key and observation-queue caps; metadata length bounds; eviction behaviour; no payload retention; UI history cap; bounded resource use under churn |
| Relay correctness | Origin certificate unchanged on reconnect; pinned test client; supported TLS versions/ALPN and mTLS fixtures; no first-connection replay, duplicate bytes, hidden requests, or midstream reclassification |
| Conditions | Aggregate 100 kbps across inspected/Smart tunnels; condition evidence; latency/offline/recovery; no learning from simulated faults; no double shaping |
| Capture/storage | Exactly-once terminal events; reason/origin round trip; old rows decode; history immutable after forget; capture rotation/clear/retention; references to deleted rows removed |
| Actions/UI | Default-on switch/help; loading/disabled behaviour; immediate default and saved-rule settings; no duplicate observed list/tabs; no preset catalogue; visible imported exceptions; Bypass availability; keyboard/focus/scroll; compact layout |
| Traffic inspector | Captured diagnosis versus scoped live handling; learning/suggestion/expiry/cooldown; persistent exact saved choices on revisits/recreation; overridden and inherited handling; failed/loading/saving states; exact-ingress Forget; keyboard/disclosure/overflow; compact text scaling; historical evidence unchanged |
| Composition | Injected clocks/limits/ports; authoritative config loading; one learner per intended session; shutdown cleanup; no engine-to-UI/storage or ViewModel-to-repository shortcuts |

The implemented `smartCompatibilityQualification` Gradle gate covers affected domain, application, storage, data,
proxy, simulator, shared UI motion/layout, Protected Traffic UI, Traffic UI, app UI, product DI, and companion
trust-boundary tests, plus
`verifyArchitectureFoundation`. Repeat the Network Conditions qualification when shared shaping/runtime composition
changes. Record the actual commands and outcomes; test presence alone is not a pass.

Do not introduce implementation-mirroring tests or rerun unrelated release soaks for documentation changes. Plan-only
validation consists of consistent status/defaults, valid local references, and `git diff --check`.

## Physical acceptance and completion

Desktop qualification must exercise an inspectable client, unfamiliar pinned client, and mTLS workload through the
packaged JVM proxy. Demonstrate initial rejection where applicable, independent reconnect, preserved origin TLS,
metadata-only Traffic, manual Inspect/Block overrides, expiry/disable recovery, and real protected 100 kbps transfer.

Android qualification requires a Play-enabled physical device plus at least one independent protected application
or controlled pinned client. Billing product/subscription retrieval is a workload, not a detector special case.
Verify destination-only behaviour now and verified package isolation separately when the tuple adapter is ready.

Darwin qualification independently covers routed StoreKit/App Store and a separate protected client on entitled
devices/providers. Record unavailable routes and missing identity; do not infer native capability from JVM tests or
Simulator linking. Validate application-owned inspectable backend calls alongside protected service flows.

Completion requires the automated matrix, measured resource bounds, platform-specific evidence, updated MODULE and
capability documentation, main-plan status, and `git diff --check`. Report remaining transport/platform gaps rather
than claiming universal automatic detection or zero first-attempt failures.

## Related plans and technical references

- [Protected Traffic foundation and platform gates](protected_traffic_passthrough_implementation_plan.md)
- [Network Conditions and packet boundaries](network_conditions_implementation_plan.md)
- [Main implementation board](implementation_plan.md#phase-136-knet-smart-compatibility-in-progress)
- [TLS 1.3 alert definitions](https://www.rfc-editor.org/rfc/rfc8446.html#section-6.2)
- [Android app-specific trust and pinning](https://developer.android.com/privacy-and-security/security-config)
