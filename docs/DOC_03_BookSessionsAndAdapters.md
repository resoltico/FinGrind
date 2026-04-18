---
afad: "3.5"
version: "0.17.0"
domain: ADAPTERS
updated: "2026-04-17"
route:
  keywords: [fingrind, sqlite, sqlite3mc, adapter, posting-fact, in-memory, cli, ffm, account-registry, query-session, posting-session, book-access, ledger-plan-session]
  questions: ["how is a committed posting stored in fingrind", "what are the book seams in fingrind now", "what does the sqlite adapter do in fingrind", "what seam does execute-plan use"]
---

# Book Session And Adapter API Reference

## `PostingFact`

`PostingFact` is the canonical committed fact carried across FinGrind's adapter seam.

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

## `BookAccess`

`BookAccess` is the explicit protected-book access tuple carried into the SQLite adapter.

```java
public record BookAccess(Path bookFilePath, PassphraseSource passphraseSource)
```

- Purpose: keep the durable book path and exactly one passphrase-source selection coupled as one
  value
- Access contract: `passphraseSource` is one of key file, standard input, or interactive prompt

## `BookAdministrationSession`

`BookAdministrationSession` is the executor-owned lifecycle and account-registry write seam.

```java
public interface BookAdministrationSession extends AutoCloseable
```

- Surface: `openBook(...)`, `declareAccount(...)`, `close()`
- Purpose: keep book initialization and account declaration separate from posting and query work

## `PostingValidationBook`

`PostingValidationBook` is the minimal lookup seam shared by preflight and transactional commit validation.

```java
public interface PostingValidationBook
```

- Surface: `isInitialized()`, `findAccount(...)`, `findExistingPosting(...)`, `findPosting(...)`,
  `findReversalFor(...)`
- Purpose: keep domain validation shared between the executor layer and the SQLite write transaction

## `PostingBookSession`

`PostingBookSession` is the executor-owned posting seam.

```java
public interface PostingBookSession extends PostingValidationBook, AutoCloseable
```

- Surface: `commit(PostingDraft, PostingIdGenerator)`, fixture-oriented `commit(PostingFact)`, `close()`
- Purpose: keep durable commit explicit and allow the store to allocate `postingId` only after acceptance

## `BookQuerySession`

`BookQuerySession` is the executor-owned read seam for lifecycle inspection, listings, and balances.

```java
public interface BookQuerySession extends AutoCloseable
```

- Surface: `inspectBook()`, `isInitialized()`, `listAccounts(...)`, `findAccount(...)`,
  `findPosting(...)`, `listPostings(...)`, `accountBalance(...)`, `close()`
- Purpose: expose query capabilities without widening the write seam

## `LedgerPlanSession`

`LedgerPlanSession` is the atomic transaction seam used by `LedgerPlanService`.

```java
public interface LedgerPlanSession
```

- Views: exposes `administrationSession()`, `postingSession()`, and `querySession()` as narrow
  operation seams bound to the same transaction boundary
- Surface: `beginLedgerPlanTransaction()`, `commitLedgerPlanTransaction()`,
  `rollbackLedgerPlanTransaction()`
- Purpose: let one ledger plan reuse the ordinary administration, query, and posting seams inside
  one explicit durable transaction

## `PostingCommitResult`

`PostingCommitResult` is the closed family of ordinary posting-session commit outcomes.

```java
public sealed interface PostingCommitResult
```

- Variants: `Committed`, `Rejected`
- Purpose: distinguish accepted durable writes from ordinary domain rejections without throwing

## `InMemoryBookSession`

`InMemoryBookSession` is the non-durable in-memory implementation of the administration, posting,
query, and ledger-plan seams.

```java
public final class InMemoryBookSession implements LedgerPlanSession, BookAdministrationSession, PostingBookSession, BookQuerySession
```

- Classpath: lives in application test fixtures rather than the production runtime surface
- Purpose: provide a fast in-memory book for application tests and fuzz harnesses
- Storage: maps by account code, idempotency key, posting id, and reversal target

## `SqliteBookPassphrase`

`SqliteBookPassphrase` is the resolved zeroizable UTF-8 passphrase payload used by the SQLite adapter.

```java
public final class SqliteBookPassphrase implements AutoCloseable
```

- Purpose: hold normalized UTF-8 passphrase bytes only after the CLI has resolved a safe source
- Lifecycle: copied into native memory for `sqlite3_key()` or `sqlite3_rekey()` and then zeroized

## `SqlitePostingFactStore`

`SqlitePostingFactStore` is the durable SQLite-backed implementation of FinGrind's administration,
posting, query, and ledger-plan seams, with focused collaborators handling the lower-level SQLite
read/write/configuration details.

```java
public final class SqlitePostingFactStore implements LedgerPlanSession, BookAdministrationSession, PostingBookSession, BookQuerySession
```

- Purpose: persist one protected entity book into one selected SQLite file
- Access modes: supports read-only query sessions plus writable administration and commit sessions
- Inspection: exposes lifecycle, application id, detected book-format version, supported version,
  compatibility, and migration policy through `inspectBook()`
- Queries: supports paged account listing, posting lookup, filtered posting history, and grouped
  per-currency balances
- Plans: supports outer ledger-plan transactions so `execute-plan` can open, declare, post, query,
  and assert atomically
- Validation: reuses the same posting validation rules during application preflight and
  transactional SQLite commit
- Rekey: also exposes `rekeyBook(...)` so one existing protected book can rotate onto replacement
  passphrase material
- Helper split:
  `SqliteConnectionSupport`, `SqliteBookStateReader`, `SqliteStatementQuerySupport`,
  `SqlitePostingReadSupport`, and `SqliteMutationWriter`

## `App`

`App` is the public process entrypoint for the FinGrind CLI adapter.

```java
public final class App
```

- Surface: `main(String[] args)`
- Purpose: run the JSON CLI and exit with its process status code
- Commands: fronts discovery, administration, query, ledger-plan, preflight, and commit commands
  over contract DTOs assembled from contract-owned protocol metadata
