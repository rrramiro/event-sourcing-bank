# Code Duplication Reduction Specification

## Objective

Reduce repeated code while preserving the bank's event-sourcing behavior, HTTP API, event schemas, and projection semantics. The refactor should make shared infrastructure behavior live in one place while keeping account- and client-specific business rules explicit.

The audit found these priorities:

1. `AccountService` and `ClientService` duplicate the aggregate load, event-store write, topic publication, snapshot scheduling, and result/error plumbing. This is the primary production-code target.
2. HTTP integration tests repeatedly build the same enroll-client, open-account, deposit, and withdrawal requests. This is the primary test-code target.
3. `Listeners` duplicates projection stream fan-out between live subscription and event-log replay, and repeats projection construction for deposit/withdrawal events.
4. `BankRoutes` repeats aggregate-to-DTO response mapping. This is a small, local cleanup target.

## Requirements

### 1. Shared event-sourced service lifecycle

Introduce a generic service component in `bank.services` parameterized by effect type, aggregate state, and aggregate type. It must own the infrastructure workflow common to account and client services:

- Define the common `EitherT[F, AggregateError, *]` result type.
- Load the latest optional snapshot.
- Load only events after the snapshot version, or the full stream when no snapshot exists.
- Rehydrate the aggregate through its `AggregateCompanion`.
- Store all newly emitted events using the aggregate's current `AggregateId`.
- Publish each newly stored event exactly once, in aggregate order, through the shared `Topic` without closing it.
- Write a snapshot when the committed version is evenly divisible by `snapshotEvery`.
- Return the committed aggregate unchanged after publication and snapshot handling.
- Provide a protected load-transform-commit operation for commands that update an existing aggregate.

`AccountService` and `ClientService` must delegate to or extend this component. Their concrete `process` methods must retain only command dispatch and domain-specific aggregate operations. Public service behavior and public `load` behavior must remain source-compatible for existing route callers.

### 2. Preserve domain-specific aggregate logic

Keep the following explicit in their existing aggregate companions:

- Account opening, deposits, withdrawals, and insufficient-funds validation.
- Client enrollment and updates.
- Exhaustive per-aggregate event folding.

Add `AggregateId.nextEventId(timestamp: ZonedDateTime)` to centralize creation of an update event's ID. Account deposits, account withdrawals, and client updates must call it with `ZonedDateTime.now()`. Initial version-one events remain explicit because they are created before an aggregate has existing identity/version state.

Do not combine `AccountEvent` and `ClientEvent`, or their command ADTs, merely because their dispatch syntax is similar. Their separation provides compile-time exhaustiveness guarantees.

### 3. Consolidate projection execution

Extract a private `Listeners` helper that accepts the account-event stream and transaction-event stream, applies the two existing listeners, and joins them in parallel. Both live subscription and projection rebuild must use it.

The implementation must preserve a critical distinction:

- Live processing uses two separately registered `Topic.subscribeAwait` streams so both listeners receive every event.
- Rebuild processing creates two streams over the same loaded event list.

Within `transactionsListener`, use a local helper for constructing a `TransactionProjection` from a deposit or withdrawal event so common event metadata is mapped once. Within `accountsListener`, use a local adjustment helper to map the shared aggregate ID and version fields while each match branch continues to supply the correct signed amount.

### 4. Consolidate HTTP response mapping

Replace route-local implicit extension classes with explicit aggregate-to-DTO conversion functions, located either in DTO companions or as private route helpers. Add small private response helpers so account and client routes do not repeat `Ok(...toDto.asJson)`.

Keep route patterns and request DTO decoding explicit. In particular, do not introduce a generic route builder that obscures HTTP methods, paths, or command construction.

### 5. Consolidate integration-test setup

Add typed request helpers to `BankFixture` for the workflows repeated across `AccountTest` and `ClientTest`:

- Enroll a client.
- Open an account for a client.
- Deposit into an account.
- Withdraw from an account, with support for both decoded success responses and status-only error assertions.

The helpers must accept test-specific inputs and return decoded DTOs or status codes needed by assertions. Less-repeated operations such as get/update client and projection reads remain inline. Tests must continue to state the scenario and expected behavior clearly; helpers must not hide assertions.

Application wiring remains explicit in `MainApp` and `BankFixture`; two composition roots with different ownership/lifetime concerns do not justify a dependency-injection abstraction.

## Constraints and Invariants

- Planning and implementation must not change any HTTP method, route path, success/error status, or JSON field currently used by the tests.
- Do not change persisted event case classes, event versioning, event ordering, aggregate IDs, timestamps, or snapshot contents.
- Preserve the write sequence: event-store commit, then event publication, then optional snapshot, then aggregate result.
- Event-store version conflicts must still produce `Left(AggregateVersionError)` rather than a raised exception.
- Snapshot absence must continue to fall back to full event replay; snapshots remain an optimization, not a correctness requirement.
- Preserve the existing positive `snapshotEvery` configuration and cadence; configuration validation is outside this behavior-preserving refactor.
- Projection listeners must remain asynchronous and must continue ignoring event families they do not consume.
- Maintain Scala 2.13 compatibility, `-Xfatal-warnings`, and the existing WartRemover rules.
- Add no new runtime or test dependency solely for this refactor.
- Keep the current in-memory repositories separate. Their `TrieMap` update blocks look similar but implement different indexing, ordering, idempotency, and concurrency rules; a generic repository abstraction is out of scope.
- Keep DTOs with different domain meanings separate even when their fields currently coincide; do not collapse `DepositDto` and `WithdrawalDto` or change their public JSON contracts.
- Preserve current comments that explain non-obvious topic, snapshot, and replay semantics, moving them to the shared implementation where appropriate.

## Architecture

The intended dependency flow is:

```text
AccountService ----\
                    > shared EventSourcedService lifecycle
ClientService -----/       | load snapshots/events
                           | commit new events
                           | publish to Topic
                           | save snapshots
                           v
             EventStore + SnapshotStore + Topic
```

The shared lifecycle should depend on the existing `Aggregate`, `AggregateCompanion`, `EventStore`, and typed `SnapshotStore` interfaces. It must not know about account/client commands, DTOs, routes, or projection repositories.

Concrete services remain responsible for translating commands into calls such as `Account.open`, `Account.deposit`, `Account.withdrawn`, `Client.enroll`, and `Client.update`. This keeps the abstraction boundary at infrastructure orchestration rather than business behavior.

## Implementation Steps

1. Add or strengthen characterization tests for the shared lifecycle invariants: loading with and without a snapshot, committing new events, ordered publication, snapshot cadence, and version-conflict propagation. Reuse the existing snapshot, publication, projection rebuild, and event-store tests where they already cover an invariant.
2. Implement the generic event-sourced service lifecycle with a single load path, commit/publish/snapshot path, and load-transform-commit helper. Move the explanatory topic and snapshot comments into this component.
3. Refactor `AccountService` and `ClientService` to use the shared lifecycle. Keep only their command pattern matches and aggregate-specific calls in each concrete service.
4. Add and adopt `AggregateId.nextEventId(timestamp)` for update events while leaving initial event IDs explicit.
5. Extract the listener fan-out helper and local transaction-projection mapping helper. Verify separately registered live subscriptions are retained.
6. Replace route implicit conversion classes with explicit DTO conversion/response helpers while leaving route declarations readable and unchanged externally.
7. Add typed enroll/open/deposit/withdraw helpers to `BankFixture`, then rewrite repeated integration-test setup to use them while retaining scenario-specific values and assertions.
8. Run formatting if the repository supplies a formatter, compile with the existing warning settings, and run the complete test suite.

## Verification

- Run `sbt test`; all existing suites must pass. The pre-refactor baseline is 14 passing tests across 6 suites.
- Verify tests cover both account and client services through the shared lifecycle, rather than proving the generic implementation only through one aggregate type.
- Verify publication tests observe every new event once and in order, and that completing one command's finite publication stream does not close the shared topic.
- Verify snapshot tests cover the configured boundary and loading after a snapshot.
- Verify stale aggregate writes still return `AggregateVersionError` through the typed error channel.
- Exercise all existing HTTP workflows: client enroll/get/update/accounts; account open/get/deposit/withdraw; insufficient funds; transactions.
- Compile with fatal warnings and WartRemover enabled.
- Review the final diff to ensure service infrastructure behavior exists in only one implementation and test request construction is materially reduced.

## Success Criteria

- `AccountService` and `ClientService` contain no duplicate implementations of snapshot loading, event loading, event storage, event publication, or snapshot cadence.
- Adding a third aggregate service would require supplying its aggregate companion, typed snapshot store, and command dispatch, without copying the lifecycle workflow.
- Live and replay projection paths share one fan-out implementation while retaining correct subscription semantics.
- Repeated client/account setup and money-operation request boilerplate is removed from integration test cases, and each test remains easy to read.
- External HTTP and JSON behavior, event history, version-conflict behavior, projection results, and snapshot behavior remain unchanged.
- The full test suite passes with no new compiler or WartRemover warnings.
- No abstraction is introduced for domain operations or repositories whose behavior only appears syntactically similar.

## Out of Scope

- Changing the event-store atomic-update implementation or its internal exception sentinel.
- Replacing in-memory storage with durable infrastructure.
- Changing eventual-consistency behavior or making projection writes synchronous with commands.
- Redesigning API request/response models, event schemas, or error responses.
- Introducing a dependency-injection framework, macro-based derivation framework, or generic CRUD/route framework.
- Abstracting the two application composition roots or one-off HTTP test operations.
