# `application` namespace

## Responsibility

Groups application-layer workflows by product boundary. This directory is a Gradle namespace and does not publish a
runtime artifact.

## Children

- `:application:desktop` owns JVM desktop orchestration, use cases, and UI-neutral contracts for proxy, traffic,
  breakpoints, API Studio, certificates, scripting, pairing, and desktop connectivity.
- `:application:companion` owns portable Android/iOS companion pairing, registration, authenticated connection,
  credential, certificate, inspection, recovery, and lifecycle workflows.

## Dependency rule

Products and outer adapters depend on the matching child application module. The sibling modules do not depend on
each other, and neither may depend outward on UI, product, engine, data, storage, or concrete connectivity
implementations.

## Placement rule

Desktop-only workflows go in `:application:desktop`. Workflows genuinely shared by Android and iOS companion
products go in `:application:companion`. Do not add runtime source or a `build.gradle.kts` file to this namespace.
