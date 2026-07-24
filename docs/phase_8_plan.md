# KNet Phase 8 Plan [IN PROGRESS]: Pixel-Perfect HTML Replica Integration

We will refactor KNet's presentation layer to replicate the provided HTML structure and options with 100% fidelity.

---

## 1. Theme Color Alignment (`Color.kt` & `Theme.kt`)
We map the Tailwind hex colors directly into KNet's color palette:
* `knet-dark`: `#0d1117` (Main backgrounds and headers)
* `knet-panel`: `#161b22` (Inner widget card backgrounds)
* `knet-border`: `#30363d` (Borders and dividers)
* `knet-blue`: `#2f81f7` (Primary actions and active selectors)
* `knet-green`: `#3fb950` (Success tags, GET methods, OK statuses)
* `knet-red`: `#f85149` (Errors, Drops, POST methods)
* `knet-text`: `#c9d1d9` (Primary texts)
* `knet-text-dim`: `#8b949e` (Secondary labels and details)

---

## 2. Component Refactoring & Upgrades

### A. Main Header (`TopHeader.kt`)
* **Branding**: Logo box + `KNet` (white) + `Network Debugging Proxy` (dim, 10.sp).
* **Navigation Links**: Tab bar featuring `Dashboard`, `Live Traffic` (active), `Sessions`, `Collections` $\rightarrow$ separator `|` $\rightarrow$ `Breakpoints`, `Rewrite Rules`, `Map Local`, `Map Remote`, `WebSocket`, `HTTP/2`, `gRPC`, `Certificates`, `Settings`.
* **Toolbar Tools**:
  * Status Badge: `Proxy: 127.0.0.1:8888 Running`.
  * Search Bar: Integrated `Search (Ctrl + K)` field with a static `⌘K` badge.
  * Right Action Buttons: Alert Bell, Help Question, Settings Gear, Theme Moon, and Profile avatar `K` (Indigo background).

### B. Left Sidebar (`TrafficFeedWidget.kt`)
* **Toolbar**: Title `Live Traffic` + `312` count badge. Refresh icon, Clear trash icon, and settings menu options.
* **Search Filters**: Full-width filter text input with inline prefix `🔍` and suffix `▽`.
* **Filter Row**: Chips (`All`, `HTTP`, `HTTPS`, `WebSocket`, `HTTP/2`, `gRPC`).
* **Table List**:
  * Group title: `▼ Today — May 23, 2025` + `120` count.
  * Selection: Left 2.dp border highlight (`knet-blue`) on active item.
  * Columns: `#` (w-6), `Method` (w-12), `Host` (w-1/3), `Path` (weight-1), `Status` (w-10, right-aligned), `Time` (w-14, right-aligned), `Size` (w-12, right-aligned).
  * Method coloring: `POST` (Red), `GET` (Green), `WS` (Purple).
  * Footer status: `Total: 312 requests` | `12.4 MB  3m 42s`.

### C. Middle View Inspector (`App.kt` & SubFrames)
* **Overview Headers**:
  * Action Buttons: `Forward` (Blue), `Drop` (Red), `Edit` (Orange), `Replay` (Split button + drop arrow).
  * Connection Row: Scheme, IP:Port, Start Time, Duration, Size.
* **Primary Tab Strip**: `Overview`, `Request` (active), `Response`, `Timeline`, `Headers (12)`, `Cookies (2)`, `Auth`, `WebSocket`, `HTTP/2`, `gRPC`.
* **Request Tree Panel**:
  * Toolbar: `Tree` (selected), `Table`, `JSON`, `Raw` chips + Parameter Search Box + `+ Add Parameter` button + `Smart View` Toggle.
  * Tree Nodes: Checkboxes next to each node, variable text colors, edit/delete hover icons.
* **JSON Viewers (Request & Response Bodies)**:
  * Line Numbers: Dark line count column on left.
  * Syntax Highlight Colors:
    * Key (`#79c0ff`)
    * String Value (`#a5d6ff`)
    * Number Value (`#d2a8ff`)
    * Boolean Value (`#ffab70`)
  * Validation Footer: `JSON | UTF-8 | LF | Size` + `✓ Valid JSON` status.

### D. Right Sidebar (`Inspector.kt`)
* Refactor `TimingsWidget.kt` and `NotesTagsWidget.kt` into a single collapsible section list:
  * Request Details (Scheme, Host, Port with lock icon).
  * Timings (DNS, TCP, TLS, TTFB, Total).
  * Collapsible items: Cookies (2), Request Headers (12), Response Headers (10), TLS (TLS 1.3), Applied Rules (3).
  * Notes area + Tags chip list with cross remove button.

### E. Bottom Panel (`App.kt` Layout)
* Refactor bottom area to match the Split Layout:
  * Left 3/4: Tab bar (`Breakpoints (3)` with badge, `Replay Queue (2)`, `Rewrite Rules (8)`, `Throttle Profiles (3)`, `Sessions (5)`, `Collections`, `Diff`, `Console`). Table showing Breakpoint Rules list.
  * Right 1/4: Import/Export buttons + Replay control dashboard (Count, Concurrency spinner, Replay & Schedule buttons).

---

## 3. Verification Plan
* Recompile JVM code.
* Verify layout dimensions and visual alignment.
