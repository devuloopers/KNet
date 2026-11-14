# Shared rule libraries and per-device activation

- Created: 2026-11-14
- Planning: [COMPLETED]
- Implementation: [COMPLETED] — software gates passed; additional physical paths remain in P09.
- Applies to: Intercepts, Network Conditions and Protected Traffic.
- Goal: reuse rule definitions across devices while controlling each device's behavior independently.

## 1. Product contract

Creation and activation are separate operations, combined atomically by the create workflow.

| Created while Device 2 is selected | Definition availability | Activation after saving |
|---|---|---|
| All devices | Every workspace can discover and reuse the definition. | Device 2 only. |
| Only this device | Only Device 2's workspace can discover and activate it. | Device 2 only. |

Shared means reusable; it is not an all-device execution scope. Creating, enabling or disabling a
shared rule from Device 2 never changes Device 1's activation. New devices can discover shared rules,
but do not automatically enable them. This replaces the earlier discussion's global-default inheritance
proposal for rule activation. There is no implicit all-device activation layer in this increment.

Use the actual device name and existing short identity suffix throughout the UI. Full authenticated
`RegisteredDeviceId` is the ownership/matching key; names, suffixes, IP addresses and tab indices are not.

Saved definitions and activations survive disconnect, desktop/Companion restart, Traffic Clear and tab
Close. Reinstallation/data clearing that produces a new device identity does not transfer settings.
Explicit Forget device removes its registration/credentials and that identity's private definitions, activations and workspace settings;
shared definitions and other devices' data remain. Revocation stops authentication and live channels;
saved configuration remains until explicit removal. Hidden offline tabs do not erase saved policies.

User-facing wording uses **Available to**, **All devices**, and **Only this device — [device name]**.
Rule lists reuse the shorter All devices / Only this device labels. The internal Shared/private model
continues to describe availability independently from execution.

## 2. Workspace and manager behavior

The top device selection provides the editing context across the existing three manager screens.
Switching devices changes their projected rows, activation switches, relevant settings and telemetry.
Their lists contain the current workspace's private definitions and available shared definitions.
An enabled switch always means enabled for the displayed workspace. A Shared badge describes availability.
An inactive shared rule remains discoverable without becoming an effective runtime rule.
Keep the workspace name visible in each manager. Rename the current Global profile caption to Device
profile/Network profile as appropriate. The Traffic Conditions shortcut uses the same selected-workspace
settings and resolver as the full screen; navigation must not expose a second inconsistent control path.

Create/editor presentation:

```text
Add breakpoint

Available to
  All devices
  Only this device — Samsung SM-A505F · Mc0w-Y

Enabled only for Samsung SM-A505F · Mc0w-Y after saving.
Other devices can enable it separately.

Existing rule fields ...

Cancel                                        Save and enable
```

Default new definitions to Only this device. All devices is an explicit reusable-definition choice.
Creating in either location saves and enables the rule for the captured workspace in one transaction.
Do not place a contradictory disabled toggle in the create popup. Existing-rule activation remains editable.
Keep existing KNet components, spacing, keyboard behavior, motion and reduced-motion support.

An open editor retains its original target when the top tab changes. Its target name stays visible; an
asynchronous save cannot move to the new tab. Editing retains the definition's existing availability.
If its owning device was forgotten before commit, reject the stale action without a partial save.

Editing a shared definition changes the definition for all workspaces currently using it. Show its usage
count/device names before saving, and offer Duplicate for this device for an independent variation.
Editing an inactive shared rule does not activate it. Creating a duplicate is a create-and-enable action.
Disabling a shared rule in one workspace changes only that workspace's activation.

Deleting an in-use shared definition presents its affected workspaces and removes the definition and all
its activations atomically after the existing destructive-action confirmation pattern. Offer Disable for
this device separately. Private deletion affects only its owner. Changing Shared to private cannot strand
other activations: use duplication for the current workspace rather than silently changing ownership.
Promoting a private definition to Shared preserves the owner's activation and leaves others inactive.

Traffic quick actions and the interception drawer use the captured record's verified workspace, even if
selection changes before the action completes. Their create popups expose the same availability choice.
Existing Protected Traffic quick actions default to device-private rules for the originating workspace;
the shared rule editor provides the explicit shared-save path. Current handling and historical admission
evidence remain distinguishable when revisiting an older TLS row.

## 3. Scope and boundaries

- One desktop application, proxy engine, database, control listener and shared definition library.
- Existing profile definitions, built-in and custom, remain reusable assets; selecting a profile and
  enabling Network Conditions are workspace settings. Rule availability does not duplicate a profile.
- Built-in profiles remain immutable. Custom profile edits show their rule/workspace impact; duplicate
  a custom profile when an independent variation is needed. Do not mutate a referenced shared profile
  as an incidental side effect of duplicating a rule.
- Network Conditions' baseline profile, enabled state and quick-add selection become per workspace.
  Preserve existing protocol/domain precedence after selecting that workspace's active rule set.
- Protected Traffic default TLS handling and automatic-fallback preference become per workspace.
  Temporary learned fallback retains its existing expiry and client/session boundary.
- Breakpoint activation becomes per workspace. No new master interception switch is required.
- Other traffic remains an explicit workspace for existing desktop/manual/unattributed ingress. Its
  private rules belong to Other traffic; its shared activations never imply paired-device activation.
  API Studio remains desktop-origin traffic and keeps its existing explicit simulator-routing behavior.
- Traffic Capture Start/Pause is source-specific in the subsequent [capture increment](device_capture_qualification.md).
  The shared writer remains process-owned; existing scoped Clear, tab, unread and scroll behavior is preserved.
- No additional hardware identity scheme, duplicated proxy ports, background reconnect framework,
  new protocol support, chart library or iOS/desktop packet provider is introduced.

## 4. Existing architecture and extension points

Current code already carries authenticated ingress identity, source-scoped Traffic and presence. Rule
repositories/configurations still expose shared snapshots, while the simulator uses aggregate rule budgets.
The Companion control gateway currently publishes one packet-policy flow to all authenticated sessions.
These are implementation changes, not capabilities already delivered by the device tabs.

| Layer | Work |
|---|---|
| Core models | Typed policy workspace, definition availability/ownership, activation and revision models. |
| Application | Scoped queries and mutations, atomic create-and-enable, usage/impact lookup and effective-policy resolution. |
| Data/Room | Durable definitions, activation bindings, per-workspace settings, indexed queries, migration and atomic publication. |
| Proxy/simulator | Select policies using authenticated ingress and separate device bandwidth budgets. |
| Control gateway | Resolve effective packet configuration for the authenticated device on WSS and HTTPS fallback. |
| Presentation | Observe the selected workspace, render library/activation separately, capture editor targets and fence stale results. |
| Product composition | Wire contracts, repositories, resolvers and observers through existing Koin composition roots. |

Keep UI -> ViewModel -> use case -> repository/adapter dependency direction. Put reusable policy models
in a suitable inward core module; never introduce a dependency on UI `TrafficSource` into domain models.
Map source selection to typed policy workspace at the application boundary. Administrative All traffic is
an aggregate query scope, not an identity or a valid private-rule owner.

Most importantly, the selected tab controls editing context only. Runtime matching reads the request's
verified ingress identity. It must work while another tab is selected and while these screens are closed.

## 5. Durable model and transactions

Store independently:

1. Definition: stable rule ID, typed rule kind, Shared/private ownership, current matching/action payload,
   and a monotonic edit revision. Existing protocol payload codecs remain owned by their extensions.
2. Activation: unique definition/workspace association with enabled state. A private definition can have
   an activation only for its owning workspace. Shared availability alone creates no runtime membership.
3. Workspace settings: Network Conditions baseline/enabled state and Protected Traffic defaults. Profiles
   remain referenced by ID, not copied into every workspace.

Prefer feature-specific definition and activation entities with actual foreign keys and shared typed
contracts, rather than a polymorphic JSON table without referential integrity. Separate authored content
from the old globally interpreted `enabled` fields. Add indexes for workspace activation, ownership,
definition usage and normalized selectors. Enforce uniqueness and ownership inside repository transactions.

Create + activate, promote, duplicate + activate, replace an activation, delete and Forget device must be
atomic. Failure or cancellation cannot leave an orphan definition, unexpected activation or partially
published policy. Publish complete immutable snapshots only after commit. Add revision checks so a stale
shared editor cannot silently overwrite another edit. Resolve concurrent profile deletion/Forget/save races.

### Matching conflicts

Shared/private availability must not itself change runtime priority. First select active definitions for
the authenticated workspace, then apply each feature's existing priority/specificity rules.

Saved alternatives may have identical selectors within either availability choice. Save automatically
switches exact matching activations only for the captured workspace, retaining previous definitions and
all sibling activations. There is no replacement checkbox or extra confirmation. The transaction returns
the replaced IDs after commit/publication; the UI shows a four-second confirmation naming the original
device. An inactive edit remains inactive, and editing the same ID does not report a replacement.
Traffic quick-add reopens the active Network Conditions equivalent before any retained inactive
alternative, while an inactive-only definition remains editable without being enabled automatically.

Other overlapping patterns, ports, methods, phases, applications and operations retain the existing
priority/specificity behavior. Editing a definition used by siblings still checks disclosed usage and
revisions; a collision on another device is rejected rather than changing that device's activation.
Concurrent creates serialize so the last committed save is the active choice, with all definitions retained.
An explicit strict mode remains available to low-level import/validation callers; it is not a UI option.

### Migration from the current schema

Use the next available Room version after current v38, with exported schema and preservation tests.

- Preserve existing rule/profile IDs, payloads, priorities and actions; convert existing shared rule
  definitions to Shared library entries.
- For every registered device at migration, including offline devices, create activation associations
  reflecting each legacy rule's enabled state. Also migrate the explicit Other traffic workspace.
- Copy the current Network Conditions and Protected Traffic settings to those workspace settings;
  retain references to the same profiles. The resulting behavior for existing workspaces is preserved.
- Devices paired after migration start with no rule activations, Network Conditions disabled with
  No throttling, and the normal product Protected Traffic baseline: Inspect with automatic fallback.
  They can opt into shared rules. Do not maintain a legacy wildcard that silently enables rules on them.
- Keep capture records, device registration, pairing credentials and certificate identity unchanged.
- Migration must be transactional and reproducible from supported prior schemas. Avoid loading bodies
  or rewriting capture history. Validate invalid/orphan legacy references with existing safe decode rules.

## 6. Runtime behavior by feature

### Intercepts

Propagate verified workspace through HTTP and protocol-message candidates. Only active rules for that
workspace participate, across currently supported HTTP, GraphQL, WebSocket/GraphQL WS, gRPC and SSE
boundaries. Preserve existing support limits and protocol semantics.

Pending requests retain their original device, matched-rule revision and native connection. Switching
tabs, disabling a rule or editing a definition cannot transfer, auto-resume or replay an existing pause.
Keep pending counts discoverable per device and existing timeout/resource limits in force. One busy
device must not exhaust every other device's usable interception capacity; retain a desktop safety cap
with bounded per-device admission and deterministic rejection behavior.

### Network Conditions

Resolve the workspace before baseline, destination or semantic rule matching. Key bandwidth budgets by
workspace plus the existing effective-rule/default grouping and direction. A shared 100 kbps rule enabled
on two devices gives each device its own 100 kbps budget; requests inside one budget still share its limit.
Also partition fault/random state, queue accounting and effective-policy evidence where currently shared.

Keep effective profiles and counters device-specific, reuse existing source telemetry/rate calculation,
and retain explicit aggregate monitoring only where labelled. Editing Device 2's profile or activation
must not flush Device 1's queue, reset its rate or emit its graph samples. Apply live shaping changes at
the existing transport boundaries; prevent double shaping on proxy-routed versus packet-handled traffic.

WSS policy subscriptions and the authenticated HTTPS fallback resolve by their verified principal, never
by a device ID supplied in the body. Send only the device's effective packet configuration, retaining
per-channel revisions, ACK results, lease expiry, reconnect and bounded buffering. Only affected devices
receive effective policy changes. Keep control/pairing traffic outside the simulator. Existing Android
packet capability limits and unsupported iOS shaping acknowledgements remain explicit.

### Protected Traffic

Resolve default action and active manual rules using ingress identity before TLS interception/tunneling.
The same host/port can be Tunnel on one device, Inspect on another, and Block on a third. Application
selectors continue to require verified application identity where available.

Keep automatic fallback temporary and client/session isolated. Saving/resetting a manual rule forgets
only affected fallback entries, not another device's learned decisions. The Traffic action result and
current-handling display must observe the committed scoped policy before reporting success. Saved rule
changes apply to future connections; historical capture evidence is not rewritten or retroactively repaired.

## 7. Performance and lifecycle

Compile/cache immutable effective snapshots by workspace and revision. Do not query Room, parse protocol
criteria or scan every device/rule on each packet/chunk. Reuse one engine/listener; bound caches, metrics,
queues and subscriptions. Offline devices retain lightweight persisted configuration without an active
timer, socket or separate engine. Release transient runtime budgets on the existing lifecycle boundary.

Use one authoritative selected-workspace observation for the managers. Freeze mutation targets in commands;
cancel/fence old asynchronous queries so Device 1 cannot display or mutate Device 2's state. Preserve
existing reconnect behavior and prevent late frames from an older VPN generation affecting a newer one.

## 8. Delivery phases

| Phase | Status | Completion requirement |
|---|---|---|
| P00 Product/architecture plan | COMPLETED | Creation, availability, activation, migration and boundaries documented. |
| P01 Core/application contracts | COMPLETED | Typed models, scoped use cases, policy resolution and ownership validation with unit cases. |
| P02 Persistence and migration | COMPLETED | Indexed entities/DAOs, atomic mutation/publication and prior-schema migration cases. |
| P03 Runtime workspace routing | COMPLETED | Authenticated attribution, cached effective snapshots, Other traffic and safe default behavior. |
| P04 Network Conditions | COMPLETED | Independent budgets/settings/telemetry and authenticated per-device packet-policy delivery. |
| P05 Intercepts | COMPLETED | Scoped matching/activation, pending ownership, fairness and protocol regressions. |
| P06 Protected Traffic | COMPLETED | Scoped defaults/manual rules, isolated fallback resets and Traffic action consistency. |
| P07 Manager/editor integration | COMPLETED | Shared/private creation, per-device toggles, usage impact, duplication and fixed target semantics. |
| P08 Automated qualification | COMPLETED | Matrix below, product builds, architecture gates and evidence report. |
| P09 Physical verification | PARTIAL | Two Android phones passed authenticated proxy upload/download and breakpoint probes; desktop UI, actual TUN packet path and Apple hardware remain separate. |

Implementation phases P01-P07 are complete. Real Room, manager composition, authenticated proxy and two-phone probes have passed. The strict gate, refreshed after automatic exact replacement and Traffic shortcut qualification, passed 1,674 tests across 33 complete task outputs with no failures/skips; Android/desktop packaging, Apple compilation/linking and 13 native tests also passed. Qualification evidence and additional hardware boundaries are recorded in [the evidence report](device_policy_libraries_qualification.md).

## 9. Test and acceptance matrix

Map each requirement to named automated cases and record pass/fail evidence. All automatable cases are
required before declaring implementation complete; physical cases remain explicitly separate. This is
scenario coverage, not a claim of 100% line/branch coverage or all possible inputs.

| Area | Required cases |
|---|---|
| Creation | Shared/private create enables only the captured workspace; other existing and newly registered devices stay inactive; offline owner works; invalid owner rejected. |
| Activation | Shared enable/disable independent for A/B/Other; idempotent calls; private activation by another workspace rejected; no implicit activation after edit. |
| Shared editing | Usage impact; concurrent revision conflict; device-private duplicate; promotion; no silent shared-to-private conversion; profile-reference safety. |
| Conflicts/deletion | Automatic exact activation replacement only in target and same-library alternatives; deterministic overlaps/priorities; shared in-use deletion; private deletion; rollback/cancellation and stale edits. |
| Migration | v38 and supported predecessors; online/offline/no registered devices; enabled/disabled rules; protocol payloads; settings/profile preservation; new device remains inactive; FK/index validity. |
| Lifecycle | Desktop restart, device offline/reconnect, Clear/Close retaining policy, Forget target only, revoke without reassignment, reinstall/new identity, hidden offline tab and stale editor target. |
| Workspace UI | A/B switching across all three managers; correct badges/toggles/settings; frozen open popup; stale query/save results; keyboard, overflow and reduced motion; global-definition impact text. |
| Network runtime | A=100 kbps/B=4G/C=unlimited; same shared definition with independent budgets; same-device aggregate cap; upload/download, Offline, latency/jitter/faults, live changes and queue cancellation isolation. |
| Protocol paths | Supported HTTP/1.x, HTTPS/H2, H2C, GraphQL GET/POST/persisted queries, WS/GraphQL WS, native gRPC cardinalities and SSE; opaque TLS must use only available metadata. |
| Control delivery | Two authenticated WSS peers get different policies/revisions; HTTPS fallback uses same resolver; unauthorized/cross-device attempts; ACK/lease/restart behavior; no unrelated broadcasts; control remains alive under Offline. |
| Interception ownership | Same URL/operation on two devices; only enabled device pauses; pending remains actionable after tab switch; request/message edits, timeout, disconnect, shared edits and per-device/global admission bounds. |
| Protected Traffic | Same host Inspect/Tunnel/Block across devices; source app selectors; explicit rejection learns only that client; reset/forget isolation; saved action survives row revisit/restart; later connections versus historical evidence. |
| Persistence concurrency | Parallel create/toggle/edit/delete/Forget from independent repositories; no partial policy snapshots, duplicated bindings, orphan definitions or device mix-up. |
| Resources | Representative 1/2/10-device loads, idle devices, large shared library, long streaming transfers, bounded cache/queue/subscriber growth, rapid switching and repeated reconnect cleanup. |

Qualification must include unit tests, real Room migration/repository tests, headless rendered UI cases,
real authenticated multi-client socket integration and existing protocol/architecture gates. Record exact
commands and per-task counts; assemble Android and compile/link supported Apple targets. Use isolated
fixtures and disposable databases without launching an extra desktop or altering the user's active capture.

Physical acceptance with at least two connected devices:

1. Create Shared from Device 2; only Device 2 responds to it. Enable it on Device 1 independently.
2. Create private from Device 2; Device 1 cannot discover or enable it.
3. Exercise distinct real upload/download limits concurrently against the same origin, including two
   devices activating the same 100 kbps definition and receiving separate budgets.
4. Pause the same endpoint on one device while the other continues; switch tabs and resolve the correct
   pending request. Verify protected destinations can use different manual handling simultaneously.
5. Restart desktop with VPNs active; settings and ownership survive. Clear/close a device's history and
   reconnect it without losing policies. Newly paired identity receives no shared activations automatically.
6. Verify Android packet-policy delivery on hardware and the signed iOS control/unsupported-shaping path
   where available. Record platform limitations without promoting unverified protocol support.
