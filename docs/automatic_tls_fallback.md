# Automatic TLS certificate-rejection fallback

Started: 2026-11-11. Status: implemented and automated qualification passed on 2026-11-12; physical acceptance pending.

## Product behavior

The existing Protected Traffic default dropdown offers Inspect with automatic fallback (default), Inspect only,
Tunnel and Block. These are global TLS choices. Manual rule actions remain Inspect, Tunnel, Block and retained
legacy Bypass; automatic fallback is never a persistent rule action or service preset.

After KNet submits an inspection certificate, an explicit downstream unknown_ca, certificate_unknown or
bad_certificate alert can authorize temporary passthrough for a later connection. It does not prove certificate
pinning. Missing CA trust can produce the same rejection. A generic handshake error, silent close, cancellation,
DNS failure, timeout, HTTP error, unknown certificate presentation, unsupported provider or accepted resumed
handshake cannot teach a decision. KNet's root authority must be valid, and the desktop certificate provider validates the root and
leaf before building its TLS context. No proof of client/system CA trust is required to permit fallback.

The first rejected handshake remains failed. KNet does not replay an HTTP operation, duplicate a TLS session or
repair an already-rejected connection. A client must open another connection, automatically or through a user
retry. That connection uses the original server certificate through a raw encrypted tunnel. KNet retains opaque
metadata and actual network shaping; it cannot read encrypted HTTP results or claim a purchase succeeded.

Selection order is saved manual/imported rule, then eligible temporary decision under the global automatic Inspect
mode, then global default. Explicit Inspect never silently falls back, even after another rejection. Explicit Tunnel
and Block also remain authoritative. Existing saved matcher precedence is unchanged.

## Scope and lifetime

Every temporary key contains the canonical capture session, admitted client scope, normalized exact hostname/IP,
exact port and OS-verified application identity when available. Unknown application identity is a separate scope.
Automatic selection occurs only after bounded ClientHello classification, including the no-SNI/IP case; a CONNECT
host alone cannot apply a learned SNI decision to a different hello. This path handles TLS over TCP, not UDP/QUIC.

Authorized bridge client identities distinguish devices behind a shared loopback ingress. Otherwise the admitted
numeric source address and ingress isolate network clients; local desktop clients share an explicit local scope.
An unidentified loopback bridge is ineligible. A source address is not proof of physical-device identity, and apps
without verified attribution on the same client cannot be distinguished. No HTTP headers supply client identity.

Decisions expire 30 minutes after the first qualifying failure, or earlier on capture pause, clear/rotation, stop,
restart, authority change, automatic-mode change or a relevant explicit choice. Reads/repeated failures do not
extend expiry. New sessions never inherit old decisions. A generation token fences late handshake callbacks after
session, authority, policy or manual-choice changes. Rule edits forget affected destinations while preserving
unrelated learned traffic. Deleted/disabled overrides do not resurrect an earlier automatic decision.

The in-memory cache is capped at 1,024 entries and evicts the oldest admission first. One product-owned coroutine
schedules expiry; there is no per-flow job or payload retention. Short synchronized operations are safe from Netty
callbacks. Expiry publishes removals to the selected Traffic inspector even without another network connection.
No temporary decision is stored in Room.

## Certificate-flight evidence and session resumption

A resumption offer does not establish whether the server actually resumes. For audited SunJSSE engines, KNet keeps
the existing drained-flight/local-certificate checks and requires every observed handshake write to succeed. If the
client offers resumption, independent outgoing metadata must establish a full certificate handshake: a complete
plaintext TLS 1.2 Certificate message header, or a real TLS 1.3 ServerHello selecting version 1.3 without pre_shared_key.
Accepted PSKs/abbreviated handshakes cannot turn cached session certificates into new transmission evidence.

The metadata reader uses bounded, zero-copy record/header reads. It does not read certificate DER, copy/retain
buffers or parse encrypted application data. Partial/unsupported metadata remains inconclusive. HelloRetryRequest
and compatibility CCS do not establish a certificate; TLS 1.2 ciphertext after CCS cannot become a fake Certificate
message. An earlier failed transport promise prevents a later successful write from manufacturing presentation.

This permits relearning after an origin session/ticket is offered to KNet and declined in favor of a full handshake,
including after a temporary decision expires or is cleared. Genuine resumed TLS 1.2/1.3 sessions are tested separately
and remain ineligible. Protocol basis: [TLS 1.3 ServerHello](https://www.rfc-editor.org/rfc/rfc8446.html#section-4.1.3)
and [PSK selection](https://www.rfc-editor.org/rfc/rfc8446.html#section-4.2.11).

## User control and Traffic

Traffic shows the original rejection unchanged and separately observes current future-connection handling. A new
automatic tunnel records AUTOMATIC provenance and the earlier certificate-rejection trigger. This is distinct from
retired historical SMART records and does not create a legacy smartEntryId. Transport lifecycle and close attribution
retain their existing meanings: Active/Closed does not imply an encrypted HTTP operation's outcome.

The inspector offers Always tunnel, Inspect next time and Block. These save one equivalent destination rule;
its selected state survives navigation and database restart. Use default removes only that exact destination
user override and forgets corresponding temporary decisions. Other application/domain rules continue to apply.
Changes govern new connections, not an established socket. Actions await authoritative committed publication
before reporting success; failures retain actionable feedback and late results cannot replace another selection.

The Protected Traffic default dropdown provides a shared KNet-themed explanation after 500ms of hovering,
both for its selected mode and each menu choice. The descriptions explain that rejected
connections are not replayed, tunneling preserves encrypted HTTP data, saved rules take precedence, and changes
govern new connections. The same copy is exposed to accessibility services. Help does not change the selection
or control dimensions; narrow-window placement keeps the complete menu available for selection.
The popup dismisses immediately when the pointer leaves the control or menu row and never reopens from retained
focus. Keyboard navigation exposes the same accessible description without opening a hover popup.

## Persistence and composition

Room schema 36 adds automaticTlsFallbackEnabled with a default of true. Migration 35 to 36 preserves existing
actions, manual/imported rules, opaque history and the retired smartCompatibilityEnabled column unchanged. A fresh
configuration defaults to automatic Inspect; choosing Inspect only durably disables fallback. Old Block/Tunnel
settings keep their action. Temporary cache entries remain process/session-local.

The application owns typed cache/workflow contracts and live current-handling observation. The data layer implements
the bounded cache and adapts domain saved rules to TLS. The engine forwards trusted admission/session context,
retains the original decision epoch, observes downstream evidence and invokes the optional non-blocking outcome
callback before connection close. It does not access Room or match service catalogues. The product injects one cache,
its owned scope, the current authority identity/validity, session lifecycle and application use cases. UI modules
still depend only on application/domain/UI-core APIs.

## Automated evidence

| Area | Cases |
| --- | --- |
| Evidence | Explicit rejection plus presented flight; setup/unknown alerts, missing/unknown presentation, invalid authority and unavailable capture excluded; full versus genuine resumed TLS, HRR/CCS, encrypted/partial/malformed metadata and mixed write outcomes |
| Exact scope | Client, ingress, application, hostname normalization, port, capture session, SNI versus CONNECT IP and no-SNI TLS |
| Manual authority | Inspect/Tunnel/Block/legacy Bypass and imported exceptions; stale callbacks cannot re-learn after manual inspection |
| Lifetime | Monotonic expiry without new traffic, no lifetime extension, pause/session/authority/mode changes, bounded concurrent callbacks and eviction |
| Rule updates | Matching invalidation preserves unrelated learned traffic; pending epochs are fenced |
| Persistence | Fresh default, durable Inspect only/reopen, all retained migrations including version 35, preserved retired preferences/actions/rules |
| Real transport | TLS 1.2/1.3 rejection then automatic reconnect, original leaf certificate, mTLS, no application replay, Room provenance/counters, retained 100 kbps shaping and repeated explicit Inspect rejection; relearning with cached origin TLS sessions/tickets |
| Traffic workflows | Live temporary/default/manual observation, immutable history, other-client/port/application/session/UDP exclusions, Inspect/Tunnel/Block/Use default, navigation persistence and publication guards |
| UI | Four global choices, automatic explanation, explicit alternatives and inline reset, plus existing narrow/larger-text inspector and manager regressions |

The final combined run passed in 6m 25s: **1,064 tests across 26 distinct task outputs**, with zero failures, errors or
skipped tests. Both feature gates, breakpoint-manager regressions and desktop compilation passed. All six architecture
checks, ledger validation and `git diff --check` passed. Required new suites were independently confirmed in XML;
counts exclude the separate canonical storage-scale workload, which was not rerun for this change.

Dedicated evidence includes 10 cache/policy tests, five real-socket tests, four application handling tests, two admitted
client-scope tests, seven certificate-flight observer tests, five bounded metadata-reader tests and two genuine JSSE
resumption tests. Existing suites additionally cover Room persistence/migration, capture lifecycle, manual-choice
navigation, four default modes and actual inspector controls. Automatic/manual inspector and wide/compact manager
renders were inspected. Physical Android/Apple service acceptance is separate from these local-server tests; this
feature does not qualify unavailable native packet providers or diagnose every opaque TLS failure. The broader
repository ledger still has 93 open software requirements; ATF-001 through ATF-005 are the verified scope of this change.

Reproduction command (the init script redirects module build directories outside the running app's outputs):

```shell
./gradlew --init-script /tmp/knet-traffic-inspector-20261110.init.gradle.kts \
  protectedTrafficQualification networkConditionsQualification \
  :ui:desktop:breakpointManager:jvmTest :products:desktop:compileKotlin \
  --max-workers=3 --no-daemon --no-configuration-cache --console=plain
```

Session evidence: `/tmp/knet-auto-tls-final-qualification.log` and
`/tmp/knet-auto-tls-final-qualification-summary.json`. The independent XML summary checks all 26 outputs and the
new mandatory suites. The earlier targeted resumption/transport verification also passed in 7m 40s; the first
complete regression run before that follow-up passed in 6m 37s. Verification did not launch the desktop app,
replace its running JARs or mutate its user database.

## Physical acceptance procedure

1. Rebuild/relaunch KNet, choose Inspect with automatic fallback, and start a fresh capture.
2. Remove any matching Always tunnel rule with Use default or the manager; retained manual rules deliberately win.
3. Trigger the target operation. An eligible first rejection stays visible; retry the operation if the app does not.
4. Verify a new connection is automatically tunnelled and current handling says Automatic fallback, without a saved
   rule appearing in the manager. Check the app's result separately.
5. Select Inspect next time. Reconnect repeatedly: inspection remains selected and rejection cannot re-enable fallback.
6. Select Tunnel or Block and verify future connections follow that explicit rule. Use default to restore inheritance.
7. Verify a different client/port is unaffected, and pause/resume or clear capture resets temporary learning.

Verification must use isolated Gradle outputs. No desktop app or user database is opened by the automated workflow.
