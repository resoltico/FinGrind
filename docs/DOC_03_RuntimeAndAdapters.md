---
afad: "3.5"
version: "0.3.0"
domain: RUNTIME
updated: "2026-04-10"
route:
  keywords: [fingrind, runtime, sqlite, adapter, posting-fact, store, in-memory, cli, ffm]
  questions: ["how is a posting fact stored in fingrind", "what runtime stores does fingrind expose", "what does the sqlite adapter do in fingrind"]
---

# Runtime And Adapter API Reference

## `PostingFact`

`PostingFact` is the canonical committed fact carried across runtime persistence seams.

```java
public record PostingFact(
    PostingId postingId,
    JournalEntry journalEntry,
    Optional<CorrectionReference> correctionReference,
    CommittedProvenance provenance)
```

- Purpose: represent one committed posting independently of any concrete storage adapter
- Normalization: `null` correction becomes `Optional.empty()`
- Validation: rejects `null` posting id, journal entry, and provenance

## `PostingFactStore`

`PostingFactStore` is the narrow runtime persistence seam for committed posting facts.

```java
public interface PostingFactStore
```

- Surface: `findByIdempotency(...)`, `findByPostingId(...)`, `findReversalFor(...)`, `commit(...)`
- Purpose: keep the application boundary honest about the exact state and commit operations it needs

## `PostingCommitResult`

`PostingCommitResult` is the closed family of ordinary runtime commit outcomes.

```java
public sealed interface PostingCommitResult
```

- Variants: `Committed`, `DuplicateIdempotency`, `DuplicateReversalTarget`
- Purpose: distinguish expected duplicate outcomes from exceptional runtime failure

## `PostingCommitResult.Committed`

`PostingCommitResult.Committed` is the runtime success variant carrying the stored fact.

```java
public record Committed(PostingFact postingFact)
    implements PostingCommitResult
```

- Purpose: return the committed fact explicitly from the runtime seam
- Validation: rejects `null` `postingFact`

## `PostingCommitResult.DuplicateIdempotency`

`PostingCommitResult.DuplicateIdempotency` reports that the book already contains the submitted idempotency key.

```java
public record DuplicateIdempotency(IdempotencyKey idempotencyKey)
    implements PostingCommitResult
```

- Purpose: keep duplicate idempotency as an ordinary runtime outcome
- Validation: rejects `null` `idempotencyKey`

## `PostingCommitResult.DuplicateReversalTarget`

`PostingCommitResult.DuplicateReversalTarget` reports that the target posting already has a full reversal.

```java
public record DuplicateReversalTarget(PostingId priorPostingId)
    implements PostingCommitResult
```

- Purpose: keep duplicate reversal targeting as an ordinary runtime outcome
- Validation: rejects `null` `priorPostingId`

## `InMemoryPostingFactStore`

`InMemoryPostingFactStore` is the non-durable runtime implementation used by tests and fuzz harnesses.

```java
public final class InMemoryPostingFactStore implements PostingFactStore
```

- Purpose: provide a fast in-memory store for application-layer verification
- Storage: maps by idempotency key, posting id, and reversal target
- Duplicate behavior: returns typed `PostingCommitResult` variants instead of throwing for ordinary duplicates

## `SqlitePostingFactStore`

`SqlitePostingFactStore` is the durable SQLite-backed store for one explicit book file.

```java
public final class SqlitePostingFactStore
    implements PostingFactStore, AutoCloseable
```

- Purpose: persist one book into one selected SQLite file
- Book identity: constructor path is the durable book boundary
- Concurrency: thread-confined to one owning CLI command
- Reads: return empty for a missing file and do not initialize storage eagerly
- Commit: creates parent directories, applies the canonical schema bootstrap once per opened
  handle through `sqlite3_exec`, then maps ordinary duplicate conditions into typed commit
  outcomes before insert
- Process model: opens one in-process SQLite handle per store instance and closes it through `AutoCloseable`

## `App`

`App` is the public process entrypoint for the FinGrind CLI adapter.

```java
public final class App
```

- Surface: exposes `main(String[] args)`
- Purpose: run the JSON CLI and exit with its process status code
- Commands: fronts `help`, `version`, `capabilities`, `print-request-template`, `preflight-entry`, and `post-entry`
