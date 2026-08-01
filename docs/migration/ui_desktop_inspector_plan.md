# UI Desktop Inspector Module Plan — `:ui:desktop:inspector` (Phase 4)

**Target Module:** `ui/desktop/inspector/`  
**Gradle Module:** `:ui:desktop:inspector`  
**Package Namespace:** `com.devuloopers.knet.ui.desktop.inspector`  
**Platform:** Compose Multiplatform (Desktop JVM)  
**Status:** Approved for Creation

---

# 📌 Vision

`:ui:desktop:inspector` is KNet's read-only transaction analysis module.

It provides deep inspection of captured HTTP, HTTPS, WebSocket, gRPC, and future protocol traffic, allowing developers to inspect every aspect of a network transaction without modifying it.

The Inspector is responsible only for presentation and analysis.

It never executes requests, modifies traffic, or communicates directly with proxy internals.

---

# 🎯 Responsibilities

The Inspector owns:
- Transaction overview (`OverviewPanel`, `SummaryCard`, `MetadataCard`)
- Request inspection (`RequestInspector`, `RequestHeadersView`, `QueryParametersView`, `RequestBodyView`, `RequestCookiesView`)
- Response inspection (`ResponseInspector`, `ResponseHeadersView`, `ResponseBodyView`, `ResponseCookiesView`, `ResponseTrailersView`)
- Timing & waterfall inspection (`TimingInspector`, `WaterfallChart`, `TimingBreakdown`)
- TLS certificate inspection (`CertificateInspector`, `CertificateChainView`, `HandshakeView`, `CipherSuiteView`)
- Protocol inspection (`ProtocolInspector`, `WebSocketView`, `GrpcView`, `Http2View`, `Http3View`)
- Payload body viewers (`JsonViewer`, `XmlViewer`, `HtmlViewer`, `TextViewer`, `RawViewer`, `HexViewer`, `ImageViewer`)
- Inspector toolbar & search (`InspectorToolbar`, `SearchBar`, `CopyActions`, `BodyModeSelector`, `StatusSummary`)
- UDF ViewModel (`InspectorViewModel`) & Koin DI (`InspectorModule`)
- Desktop Shell Contribution (`InspectorContribution`)

---

# 🚫 Explicitly Out of Scope

This module MUST NOT contain:
- Request execution or editing (belongs to `:ui:desktop:apistudio`)
- Traffic replay
- Proxy server logic or Netty handlers
- Database implementation or SQL
- Workspace navigation or Collection explorer (belongs to `:ui:desktop:workspace`)

---

# 📂 Directory Structure

```text
ui/
└── desktop/
    └── inspector/
        ├── build.gradle.kts
        │
        └── src/
            ├── jvmMain/
            │   └── kotlin/
            │       └── com/devuloopers/knet/ui/desktop/inspector/
            │
            │           ├── model/
            │           │     ├── InspectorState.kt
            │           │     ├── InspectorIntent.kt
            │           │     ├── InspectorTab.kt
            │           │     ├── TransactionOverview.kt
            │           │     ├── RequestPresentation.kt
            │           │     └── ResponsePresentation.kt
            │           │
            │           ├── overview/
            │           │     ├── OverviewPanel.kt
            │           │     ├── SummaryCard.kt
            │           │     └── MetadataCard.kt
            │           │
            │           ├── request/
            │           │     ├── RequestInspector.kt
            │           │     ├── RequestHeadersView.kt
            │           │     ├── QueryParametersView.kt
            │           │     ├── RequestBodyView.kt
            │           │     └── RequestCookiesView.kt
            │           │
            │           ├── response/
            │           │     ├── ResponseInspector.kt
            │           │     ├── ResponseHeadersView.kt
            │           │     ├── ResponseBodyView.kt
            │           │     ├── ResponseCookiesView.kt
            │           │     └── ResponseTrailersView.kt
            │           │
            │           ├── timing/
            │           │     ├── TimingInspector.kt
            │           │     ├── WaterfallChart.kt
            │           │     └── TimingBreakdown.kt
            │           │
            │           ├── tls/
            │           │     ├── CertificateInspector.kt
            │           │     ├── CertificateChainView.kt
            │           │     ├── HandshakeView.kt
            │           │     └── CipherSuiteView.kt
            │           │
            │           ├── protocol/
            │           │     ├── ProtocolInspector.kt
            │           │     ├── WebSocketView.kt
            │           │     ├── GrpcView.kt
            │           │     ├── Http2View.kt
            │           │     └── Http3View.kt
            │           │
            │           ├── viewer/
            │           │     ├── JsonViewer.kt
            │           │     ├── XmlViewer.kt
            │           │     ├── HtmlViewer.kt
            │           │     ├── TextViewer.kt
            │           │     ├── RawViewer.kt
            │           │     ├── HexViewer.kt
            │           │     └── ImageViewer.kt
            │           │
            │           ├── component/
            │           │     ├── InspectorToolbar.kt
            │           │     ├── SearchBar.kt
            │           │     ├── CopyActions.kt
            │           │     ├── BodyModeSelector.kt
            │           │     └── StatusSummary.kt
            │           │
            │           ├── viewmodel/
            │           │     └── InspectorViewModel.kt
            │           │
            │           ├── contribution/
            │           │     └── InspectorContribution.kt
            │           │
            │           └── di/
            │                 └── InspectorModule.kt
            │
            └── jvmTest/
                └── kotlin/
                    └── com/devuloopers/knet/ui/desktop/inspector/
                        ├── InspectorViewModelTest.kt
                        ├── OverviewPanelTest.kt
                        ├── RequestInspectorTest.kt
                        ├── ResponseInspectorTest.kt
                        ├── TimingInspectorTest.kt
                        ├── CertificateInspectorTest.kt
                        ├── ProtocolInspectorTest.kt
                        ├── ViewerTest.kt
                        ├── SearchTest.kt
                        ├── CopyActionsTest.kt
                        ├── InspectorContributionTest.kt
                        └── MigrationRegressionTest.kt
```

---

# 🏗 Component Specifications

### 1. State & Data Models (`model/`)
- `InspectorState`: Selected transaction, active inspector tab, search query, selected body mode, expanded JSON/XML nodes, protocol view.
- `TransactionOverview`: Summary DTO (URL, host, IP, port, method, status, protocol, TLS version, cipher suite, request/response size, duration).
- `RequestPresentation` & `ResponsePresentation`: Read-only headers, query params, cookies, body string, trailers.
- `InspectorTab`: Enum (`OVERVIEW`, `REQUEST`, `RESPONSE`, `TIMING`, `TLS`, `PROTOCOL`).

### 2. Overview & Inspection (`overview/`, `request/`, `response/`)
- `OverviewPanel`: Displays summary metrics card and host metadata card.
- `RequestInspector`: Headers, query parameters, cookies, and request body viewers.
- `ResponseInspector`: Headers, cookies, body, and trailers viewers.

### 3. Timing & TLS (`timing/`, `tls/`)
- `TimingInspector`: DNS, TCP connect, TLS handshake, request send, TTFB, download timing waterfall breakdown.
- `CertificateInspector`: Certificate chain, SAN, validity, fingerprints, public key, and cipher suite view.

### 4. Protocol & Body Viewers (`protocol/`, `viewer/`)
- `ProtocolInspector`: HTTP/1.1, HTTP/2, HTTP/3, WebSocket, and gRPC frame inspection.
- Viewers: `JsonViewer`, `XmlViewer`, `HtmlViewer`, `TextViewer`, `RawViewer`, `HexViewer`, `ImageViewer`.

### 5. Contribution API (`contribution/`)
- `InspectorContribution`: Registers Inspector navigation items and toolbar actions into `:ui:desktop:shell`.

---

# 📦 Dependencies

Depends on:
- `:ui:core`
- `:ui:desktop:shell`
- `:core:domain`
- `:core:logger`
- Compose Multiplatform (Desktop)
- Koin

Must NOT depend on:
- `:ui:desktop:workspace`
- `:ui:desktop:apistudio`
- `:engine:proxy`
- SQL / Netty

---

# 🧪 Test Architecture (`jvmTest/`)

- `InspectorViewModelTest`: Transaction selection, tab switching, search, state updates.
- `OverviewPanelTest`: Metadata, status, and protocol rendering.
- `RequestInspectorTest` & `ResponseInspectorTest`: Header, cookie, query, and body rendering.
- `TimingInspectorTest`: Timeline, waterfall, duration calculations.
- `CertificateInspectorTest`: Certificate chain, fingerprints, TLS metadata.
- `ProtocolInspectorTest`: Protocol-specific frame rendering.
- `ViewerTest`: Pretty, Raw, Hex, and Image body viewers.
- `SearchTest`: Payload search & filtering.
- `CopyActionsTest`: Copy headers, cURL, JSON, raw payload actions.
- `InspectorContributionTest`: Navigation and toolbar contributions.
- `MigrationRegressionTest`: Model and public API stability.
