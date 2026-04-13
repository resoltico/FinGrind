---
afad: "3.5"
version: "0.6.0"
domain: ADAPTERS
updated: "2026-04-13"
route:
  keywords: [fingrind, book-session, sqlite, adapter, posting-fact, in-memory, cli, ffm]
  questions: ["how is a committed posting stored in fingrind", "what is BookSession in fingrind", "what does the sqlite adapter do in fingrind"]
---

# Book Session And Adapter API Reference

## `PostingFact`

`PostingFact` is the canonical committed fact carried across FinGrind's book-session seam.

```java
public record PostingFact(
    PostingId postingId,
    JournalEntry journalEntry,
    Optional<ReversalReference> reversalReference,
    CommittedProvenance provenance)
```

- Purpose: represent one committed posting independently of any concrete storage adapter
- Normalization: `null` reversal becomes `Optional.empty()`
- Validation: rejects `null` posting id, journal entry, and provenance

## `BookSession`

`BookSession` is the narrow application-owned persistence session for committed posting facts.

```java
public interface BookSession extends AutoCloseable
```

- Surface: `findByIdempotency(...)`, `findByPostingId(...)`, `findReversalFor(...)`, `commit(...)`, `close()`
- Purpose: keep the application boundary honest about the exact state and commit operations it needs
- Lifecycle: one opened session owns one concrete adapter lifecycle and is explicitly closeable

## `PostingCommitResult`

`PostingCommitResult` is the closed family of ordinary book-session commit outcomes.

```java
public sealed interface PostingCommitResult
```

- Variants: `Committed`, `DuplicateIdempotency`, `DuplicateReversalTarget`
- Purpose: distinguish expected duplicate outcomes from exceptional adapter failure

## `PostingCommitResult.Committed`

`PostingCommitResult.Committed` is the success variant carrying the stored fact.

```java
public record Committed(PostingFact postingFact)
    implements PostingCommitResult
```

- Purpose: return the committed fact explicitly from the book-session seam
- Validation: rejects `null` `postingFact`

## `PostingCommitResult.DuplicateIdempotency`

`PostingCommitResult.DuplicateIdempotency` reports that the book already contains the submitted idempotency key.

```java
public record DuplicateIdempotency(IdempotencyKey idempotencyKey)
    implements PostingCommitResult
```

- Purpose: keep duplicate idempotency as an ordinary session outcome
- Validation: rejects `null` `idempotencyKey`

## `PostingCommitResult.DuplicateReversalTarget`

`PostingCommitResult.DuplicateReversalTarget` reports that the target posting already has a full reversal.

```java
public record DuplicateReversalTarget(PostingId priorPostingId)
    implements PostingCommitResult
```

- Purpose: keep duplicate reversal targeting as an ordinary session outcome
- Validation: rejects `null` `priorPostingId`

## `InMemoryBookSession`

`InMemoryBookSession` is the non-durable in-memory book-session implementation used by tests and fuzz harnesses.

```java
public final class InMemoryBookSession implements BookSession
```

- Classpath: lives in application test fixtures rather than the production runtime surface
- Purpose: provide a fast in-memory session for application-layer verification
- Storage: maps by idempotency key, posting id, and reversal target
- Duplicate behavior: returns typed `PostingCommitResult` variants instead of throwing for ordinary duplicates

## `SqlitePostingFactStore`

`SqlitePostingFactStore` is the durable SQLite-backed `BookSession` implementation for one explicit book file.

```java
public final class SqlitePostingFactStore
    implements BookSession
```

- Purpose: persist one book into one selected SQLite file
- Book identity: constructor path is the durable book boundary
- Concurrency: thread-confined to one owning CLI command
- Reads: return empty for a missing file and do not initialize storage eagerly
- Open configuration: enables `foreign_keys` and disables `trusted_schema` on the opened handle
- Commit: creates parent directories, applies the canonical strict-table schema bootstrap once per
  opened handle through `sqlite3_exec`, then maps ordinary duplicate conditions into typed commit
  outcomes before insert
- Process model: opens one in-process SQLite handle per session instance and closes it through the
  `BookSession` lifecycle

## `App`

`App` is the public process entrypoint for the FinGrind CLI adapter.

```java
public final class App
```

- Surface: exposes `main(String[] args)`
- Purpose: run the JSON CLI and exit with its process status code
- Commands: fronts `help`, `version`, `capabilities`, `print-request-template`, `preflight-entry`, and `post-entry`
