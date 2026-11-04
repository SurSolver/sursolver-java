# Changelog

## 0.1.0 — unreleased

First release. Tested against the SDK specification revision in `SPEC_VERSION` (catalog of
2026-11-04).

- `SurSolverClient` with `createTask`, `getTaskResult`, `getBalance`, `listTaskTypes`,
  `listServices` and `solve` (3 s polling, 210 s deadline, `onTaskCreated`, cancellation by
  thread interruption).
- Typed exceptions: `AuthenticationException`, `InvalidTaskException` (with
  `supportedUserAgents()`), `InsufficientBalanceException`, `NoSlotAvailableException`,
  `TaskNotFoundException`, `TaskFailedException`, `ApiException`, `TransportException` (with
  `mayHaveCreatedTask()`), `ProtocolException`, `SolveTimeoutException`, `ConfigurationException`.
- `TaskTypes` and `ErrorCodes` constants generated from the public catalog.
- Java 17 bytecode; no runtime dependencies (`java.net.http` and a built-in JSON codec).
