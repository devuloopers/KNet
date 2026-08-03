# KNet Custom Rules

This document specifies instructions and style guidelines that must be adhered to in every development session for KNet.

## Documentation Rule

Every code element and project component must be thoroughly documented.

### Guidelines:
1. **Public API Documentation**:
   * Every class, interface, object, public function, and public property must include a descriptive KDoc block.
   * KDoc comments must explain the purpose, design intent, parameter inputs, return values, and any exceptions thrown.
2. **Implementation Documentation**:
   * Complex algorithms or non-obvious code paths within functions must be documented using inline comments.
   * Multi-threaded operations and asynchronous Netty event handler coordination must include sequence details in the comments.
3. **Project Documentation**:
   * All configuration files, build scripts, and modules must have corresponding documentation in the `docs/` directory.
   * The `docs/` folder is the single source of truth for architectural layouts, schemas, and usage guides.

---

## Phase Status Tracking Rule

The implementation plan must serve as a live project board to track development progress.

### Guidelines:
1. **Immediate Updates**:
   * As soon as any development phase is completed, or partially completed (started/in progress), the developer agent must immediately update the main implementation plan document (`docs/implementation_plan.md`).
2. **Status Annotations**:
   * Use clear annotations next to each phase heading (e.g., `[COMPLETED]`, `[IN PROGRESS]`, `[PENDING]`) so that the current status of the project is visible at a glance.

---

## Variable Naming Rule

Strict naming style guidelines must be followed for variable declarations.

### Guidelines:
1. **Context Variables**:
   * Do not use the shorthand variable name `ctx` for context parameters (such as Netty's `ChannelHandlerContext`).
   * Always write the full word `context` (e.g., `context: ChannelHandlerContext`) to ensure code readability and clean architecture.

---

## Discussion Protocol Rule

Whenever the user includes "Discuss" or asks to discuss a feature/change, do NOT start editing code directly.

### Guidelines:
1. **Plan & Propose First**:
   * Present a clear implementation plan or design proposal outlining proposed changes, architectural impact, and UI/code layout.
2. **User Approval Required**:
   * Stop and wait for explicit user feedback/approval on the proposed plan before modifying any files or making code edits.

---

## Composable API Guideline

Composable APIs should remain simple, cohesive, and easy to understand.

### Guidelines:
1. **Cohesive Parameter Objects**:
   * Prefer cohesive parameter objects over long parameter lists.
   * Group related values into State (immutable values, free of callbacks), Actions (interaction callbacks only), Configuration, or Style objects.
2. **Clarity First**:
   * Prioritize readability over arbitrary parameter counts.
   * Do not create wrapper objects for simple APIs unnecessarily.

---

## Desktop Responsive Behaviour Rule

Every desktop feature must inherit responsive behaviour from the Design System (`:ui:core`).

### Guidelines:
1. **Centralized Ownership**:
   * Minimum dimension tokens, window constraints, overflow strategies, and text rendering defaults are owned centrally by `:ui:core`.
2. **Prohibited Overrides**:
   * Feature modules must NOT hardcode resize behaviour, hardcode minimum dimensions, invent custom overflow strategies, or implement custom text wrapping rules.
3. **Text Stability Guarantee**:
   * Horizontal desktop text must NEVER wrap vertically during window resizing (`maxLines = 1`, `softWrap = false`). Truncation must use `Ellipsis` or `Clip`.

---

## Clipboard API Rule

KNet uses the modern Compose Multiplatform Clipboard API.

### Guidelines:
1. **Approved APIs**:
   * `LocalClipboard`, `Clipboard`, `ClipEntry` (`ClipEntry.withPlainText(text)`).
2. **Prohibited Deprecated APIs**:
   * Deprecated APIs (`LocalClipboardManager`, `ClipboardManager`, Java AWT Toolkit) are strictly prohibited inside feature modules.
3. **Asynchronous Execution**:
   * Clipboard writes are asynchronous and must execute within an appropriate coroutine scope (`rememberCoroutineScope().launch`).

---

## Strongly-Typed Contracts Rule

Do not use primitive strings or generic fallback types when the closed set or explicit domain type of the output is known.

### Guidelines:
1. **Avoid Hardcoded Primitive Types**:
   * Whenever a domain property, strategy interface return type, status code, protocol, or encoding token represents a known set of outputs, model it as a strongly-typed `enum` or `sealed interface` rather than a raw `String`.
2. **Extensible Sealed Hierarchies**:
   * If an API supports unknown or custom extensions alongside standard values, use a sealed hierarchy containing standard enum values and a typed custom fallback variant.
3. **No Magic Strings Across Boundaries**:
   * Do not pass primitive strings across module boundaries, strategy interfaces, or viewmodel UI states when a strongly-typed contract can be used.
