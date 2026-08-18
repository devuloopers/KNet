# `:ui:desktop:apistudio`

## Responsibility

Owns API Studio's desktop editor, collections, environments, tabs, execution presentation, and feature state.

## Owns

- Mutable request drafts and editor-only models.
- Conversion of the draft method string to the canonical extension-safe `HttpMethod` at save and execution boundaries.
- API Studio ViewModels, components, and the `ResponseInspectorState` UI projection for loading,
  failures, console logs, and assertions.
- Conversion of completed direct executions to the shared canonical HTTP heads at the application recording boundary.
- Presentation orchestration of pre-request and response-test scripts through `ScriptExecutionPort`.

## Does not own

- A second canonical HTTP request/response model, HTTP engine internals, persistence implementations, or product DI bindings.

## Dependency rule

Draft strings are validated into shared HTTP semantic types at execution boundaries; results use shared `HttpResponseSnapshot`/`HttpExchangeSnapshot`. Runtime work goes through use cases.

## Current state

Direct executions use canonical `OutboundRequestBody`/`ExecutionResult`, then record through
`RecordHttpExchangeUseCase` using canonical request/response heads and separately owned bodies.
Scripts consume `:core:scripting` values through an application port implemented in desktop data.
Editor drafts and the response-inspector projection remain feature-local; prepared traffic requests
reuse `HttpRequestSnapshot` and refuse silently truncated replay bodies. Assembly for this feature
lives in `:products:desktop` under `di/apistudio`.
