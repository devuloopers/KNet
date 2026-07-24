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
