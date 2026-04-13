---
afad: "3.5"
version: "0.8.0"
domain: APPLICATION
updated: "2026-04-13"
route:
  keywords: [fingrind, application, open-book, declare-account, list-accounts, post-entry, preflight, rejection, committed, uuid-v7]
  questions: ["how does the application boundary work in fingrind", "what results can posting return in fingrind", "how are posting ids generated in fingrind"]
---

# Application API Reference

## `BookAdministrationService`

`BookAdministrationService` owns explicit book initialization and account-registry commands.

```java
public final class BookAdministrationService
```

- Constructor: requires `BookSession` and `Clock`
- Surface: `openBook()`, `declareAccount(DeclareAccountCommand)`, `listAccounts()`
- Policy: stamps `initializedAt` and `declaredAt` from the application clock instead of trusting caller input

## `DeclareAccountCommand`

`DeclareAccountCommand` is the application-layer request to declare or reactivate one account.

```java
public record DeclareAccountCommand(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance)
```

- Purpose: keep account-registry writes typed at the application boundary
- Validation: rejects `null` fields

## `DeclaredAccount`

`DeclaredAccount` is the durable account-registry projection returned by Phase 2 surfaces.

```java
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance,
    boolean active,
    Instant declaredAt)
```

- Purpose: represent one declared account independently of CLI or SQLite concerns
- Validation: rejects `null` value fields

## `OpenBookResult`

`OpenBookResult` is the closed result family for explicit book initialization.

```java
public sealed interface OpenBookResult
```

- Variants: `Opened`, `Rejected`
- Purpose: keep book lifecycle outcomes explicit instead of throwing for ordinary rejections

## `DeclareAccountResult`

`DeclareAccountResult` is the closed result family for `declare-account`.

```java
public sealed interface DeclareAccountResult
```

- Variants: `Declared`, `Rejected`
- Purpose: return the current declared-account shape or a deterministic refusal

## `ListAccountsResult`

`ListAccountsResult` is the closed result family for `list-accounts`.

```java
public sealed interface ListAccountsResult
```

- Variants: `Listed`, `Rejected`
- Purpose: keep the account-registry read surface explicit at the application layer

## `BookAdministrationRejection`

`BookAdministrationRejection` is the closed family of deterministic book-lifecycle refusals.

```java
public sealed interface BookAdministrationRejection
```

- Variants: `BookAlreadyInitialized`, `BookNotInitialized`, `BookContainsSchema`, `NormalBalanceConflict`
- Purpose: distinguish lifecycle and registry-state refusals from malformed requests or runtime failure

## `MachineContract`

`MachineContract` owns the canonical typed machine descriptors for the CLI discovery surface.

```java
public final class MachineContract
```

- Purpose: keep `help`, `version`, `capabilities`, and `print-request-template` sourced from one
  application-owned contract instead of CLI-local map assembly
- Surface: `help(...)`, `capabilities(...)`, `version(...)`, and `requestTemplate()`
- Shared constants: exports canonical request-field names so `CliRequestReader` and the
  `capabilities` payload stay aligned
- Live vocabularies: derives enum vocabularies from `JournalLine.EntrySide`, `ActorType`, and
  `NormalBalance`
- Live rejections: derives rejection descriptors from the sealed `BookAdministrationRejection` and
  `PostingRejection` families instead of a hand-maintained CLI list
- Explicit policy: publishes `preflightSemantics = advisory` plus
  `currencyModel.scope = single-currency-per-entry`

## `PostEntryCommand`

`PostEntryCommand` is the application-layer request to preflight or commit one journal entry.

```java
public record PostEntryCommand(
    JournalEntry journalEntry,
    Optional<ReversalReference> reversalReference,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel)
```

- Purpose: carry the balanced journal body, reversal linkage, accepted request provenance, and ingress channel into the write boundary
- Normalization: `null` reversal becomes `Optional.empty()`
- Validation: rejects `null` journal entry, request provenance, and source channel

## `PostEntryResult`

`PostEntryResult` is the closed result family returned by the application write boundary.

```java
public sealed interface PostEntryResult
```

- Variants: `PreflightAccepted`, `Committed`, `Rejected`
- Purpose: keep preflight success, commit success, and deterministic rejection explicit

## `PostEntryResult.PreflightAccepted`

`PreflightAccepted` is the success variant for a validated request that has not been committed yet.

```java
public record PreflightAccepted(
    IdempotencyKey idempotencyKey,
    LocalDate effectiveDate)
    implements PostEntryResult
```

- Purpose: confirm that a later commit attempt is admissible against current state
- Validation: rejects `null` fields

## `PostEntryResult.Committed`

`Committed` is the success variant for one durably stored posting fact.

```java
public record Committed(
    PostingId postingId,
    IdempotencyKey idempotencyKey,
    LocalDate effectiveDate,
    Instant recordedAt)
    implements PostEntryResult
```

- Purpose: report durable write success with posting identity and commit timestamp
- Validation: rejects `null` fields

## `PostEntryResult.Rejected`

`Rejected` is the variant for a request that did not cross the durable write boundary.

```java
public record Rejected(
    IdempotencyKey idempotencyKey,
    PostingRejection rejection)
    implements PostEntryResult
```

- Purpose: return a deterministic domain rejection without throwing
- Validation: rejects `null` idempotency key and rejection

## `PostingApplicationService`

`PostingApplicationService` owns preflight and commit behavior for posting entries.

```java
public final class PostingApplicationService
```

- Constructor: requires `BookSession`, `PostingIdGenerator`, and `Clock`
- Surface: exposes `preflight(PostEntryCommand)` and `commit(PostEntryCommand)`
- Policy: rejects unopened books, unknown accounts, inactive accounts, duplicate idempotency, missing reversal targets, duplicate reversals, and non-negating reversals deterministically
- Commit path: creates one committed `PostingFact`, stamps `CommittedProvenance`, generates a fresh `PostingId`, and maps `BookSession` commit outcomes into `PostEntryResult`

## `PostingIdGenerator`

`PostingIdGenerator` supplies the next posting identity during commit.

```java
public interface PostingIdGenerator {
  PostingId nextPostingId();
}
```

- Purpose: keep posting-id generation explicit and injectable at the application boundary

## `UuidV7PostingIdGenerator`

`UuidV7PostingIdGenerator` is FinGrind's project-owned production posting-id generator.

```java
public final class UuidV7PostingIdGenerator implements PostingIdGenerator
```

- Purpose: generate time-ordered UUID v7 posting identities without adding an external dependency
- Timestamp source: uses millisecond wall-clock time for the 48-bit UUID v7 timestamp prefix
- Randomness: fills the remaining UUID bits from a cryptographically secure random source in production
- Production default: the CLI's default SQLite workflow uses this generator for committed `postingId` values

## `PostingRejection`

`PostingRejection` is the closed family of deterministic domain refusals for posting requests.

```java
public sealed interface PostingRejection
```

- Variants: `BookNotInitialized`, `UnknownAccount`, `InactiveAccount`, `DuplicateIdempotencyKey`, `ReversalReasonRequired`, `ReversalReasonForbidden`, `ReversalTargetNotFound`, `ReversalAlreadyExists`, `ReversalDoesNotNegateTarget`
- Purpose: keep validly parsed but inadmissible requests machine-distinguishable
