# Inspector reference cases for KNet network protection

Reviewed: 2026-11-10. Scope: primary public tests/docs relevant to Network Conditions and Protected Traffic.

HTTP Toolkit's Mockttp source was inspected at commit
[`af2b0ce6371f020c531f3e518009e8f9c5fc0d73`](https://github.com/httptoolkit/mockttp/tree/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration).
Only behavior is used as a reference; KNet does not vendor these tests, add Mockttp as a runtime dependency or
claim a full product equivalence. Its public suite is not evidence that every possible network case is covered.

| ID | Independent KNet acceptance case | Primary reference | Work package |
| --- | --- | --- | --- |
| CMP-001 | Protected connections create metadata-only flow events; intercepted connections do not duplicate them as passthrough | [TLS flow events](https://github.com/httptoolkit/mockttp/blob/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration/subscriptions/tls-passthrough-events.spec.ts) | C05/C08 |
| CMP-002 | Raw tunnels preserve both directions and incremental delivery, including large replies | [Raw transport events](https://github.com/httptoolkit/mockttp/blob/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration/subscriptions/raw-passthrough-events.spec.ts) | C08/C11 |
| CMP-003 | Closing a downstream owner releases its upstream sockets, queued data and delayed work | [Connection ownership](https://github.com/httptoolkit/mockttp/blob/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration/proxying/upstream-connection-isolation.spec.ts) | C01/C08 |
| CMP-004 | Connection pooling cannot cross trusted device/application/TLS identity boundaries; permitted reuse stays scoped | [Connection reuse/isolation](https://github.com/httptoolkit/mockttp/blob/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration/proxying/upstream-connection-isolation.spec.ts) | C05/C08 |
| CMP-005 | Stream errors/cancellation abort once and release ownership without buffering a complete response | [Streamed responses](https://github.com/httptoolkit/mockttp/blob/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration/handlers/stream-response.spec.ts) | C08/C11 |
| CMP-006 | Initial certificate rejection, upstream trust failure, immediate reset, late reset and successful TLS have distinct evidence; only eligible downstream rejection may be learned | [TLS failure observations](https://github.com/httptoolkit/mockttp/blob/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration/subscriptions/tls-error-events.spec.ts) | C02/C08 |
| CMP-007 | Malformed/incomplete HTTP, oversized headers and broken HTTP/2 prefaces produce bounded, exactly-once diagnostics and never Smart learning | [Client failure observations](https://github.com/httptoolkit/mockttp/blob/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration/subscriptions/client-error-events.spec.ts) | C02/C08 |
| CMP-008 | Restart/reset invalidates old observations/listeners and keeps new capture working without duplicated notifications | [TLS observer lifecycle](https://github.com/httptoolkit/mockttp/blob/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration/subscriptions/tls-error-events.spec.ts) | C02/C10 |
| CMP-009 | WebSocket handshake rejection, binary/text fragmentation, close/control frames and upstream failure retain protocol ordering and ownership | [WebSocket integration](https://github.com/httptoolkit/mockttp/blob/af2b0ce6371f020c531f3e518009e8f9c5fc0d73/test/integration/websockets.spec.ts) | C08 |
| CMP-010 | CONNECT/SNI/IP scope is determined before HTTP headers; mixed HTTP destinations on reused connections cannot inherit an unrelated bypass | [mitmproxy ignore-host scope](https://docs.mitmproxy.org/stable/howto-ignoredomains/) | C03/C08 |
| CMP-011 | Ignored/protected flow visibility remains bounded and payload-free, especially under large slow streams | [mitmproxy visibility/streaming options](https://docs.mitmproxy.org/stable/concepts/options/) | C05/C11 |
| CMP-012 | DNS, TLS and socket failures remain distinct from HTTP error responses; simulated failures cannot create automatic exemptions | [Mockttp passthrough error contract](https://httptoolkit.github.io/mockttp/interfaces/PassThroughStepConnectionOptions.html) | C02/C08 |

KNet-specific additional assertions remain mandatory: shared rate budgets and no double shaping, actual 100 kbps
delivery and graph sampling, protocol-aware GraphQL/gRPC/WS selection, authenticated native metadata, original tuple
attribution, UDP loss/duplication/reordering, exact migration/evidence persistence and unavailable-provider states.
These are independently qualified; a comparator's different pooling or visibility design is not copied blindly.
