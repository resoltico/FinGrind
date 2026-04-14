---
afad: "3.5"
version: "0.14.0"
domain: ADAPTERS
updated: "2026-04-14"
route:
  keywords: [fingrind, book-session, sqlite, sqlite3mc, adapter, posting-fact, in-memory, cli, ffm, account-registry, book-key-file, book-passphrase-stdin, book-passphrase-prompt]
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

`BookSession` is the application-owned persistence session for one selected book.

```java
public interface BookSession extends AutoCloseable
```

- Surface: `isInitialized()`, `openBook(...)`, `findAccount(...)`, `declareAccount(...)`, `listAccounts()`, `findByIdempotency(...)`, `findByPostingId(...)`, `findReversalFor(...)`, `commit(...)`, `close()`
- Purpose: keep both lifecycle and posting persistence explicit at the application boundary
- Lifecycle: one opened session owns one concrete adapter lifecycle and is explicitly closeable

## `BookAccess`

`BookAccess` is the explicit protected-book access tuple carried across the application boundary.

```java
public record BookAccess(
    Path bookFilePath,
    PassphraseSource passphraseSource)
```

- Purpose: keep the durable book path and the selected passphrase-source contract coupled as one
  value
- Book identity: `bookFilePath` remains the durable book identity
- Access contract: `passphraseSource` must be exactly one of key file, standard input, or
  interactive prompt

## `SqliteBookPassphrase`

`SqliteBookPassphrase` is the resolved zeroizable UTF-8 passphrase payload used by the SQLite
adapter.

```java
public final class SqliteBookPassphrase
    implements AutoCloseable
```

- Purpose: hold normalized UTF-8 passphrase bytes only after the CLI has resolved a safe source
- Lifecycle: copied into native memory for `sqlite3_key()` or `sqlite3_rekey()` and then zeroized
- Safety: avoids keeping CLI source transport concerns inside the low-level SQLite bridge

## `PostingCommitResult`

`PostingCommitResult` is the closed family of ordinary book-session commit outcomes.

```java
public sealed interface PostingCommitResult
```

- Variants: `Committed`, `BookNotInitialized`, `UnknownAccount`, `InactiveAccount`, `DuplicateIdempotency`, `DuplicateReversalTarget`
- Purpose: distinguish expected lifecycle/account/duplicate outcomes from exceptional adapter failure

## `InMemoryBookSession`

`InMemoryBookSession` is the non-durable in-memory book-session implementation used by tests and fuzz harnesses.

```java
public final class InMemoryBookSession implements BookSession
```

- Classpath: lives in application test fixtures rather than the production runtime surface
- Purpose: provide a fast in-memory session for application-layer verification
- Storage: maps by account code, idempotency key, posting id, and reversal target
- Lifecycle: starts unopened, supports explicit account declaration, and can deactivate accounts for tests

## `SqlitePostingFactStore`

`SqlitePostingFactStore` is the durable SQLite-backed `BookSession` implementation for one explicit book file.

```java
public final class SqlitePostingFactStore
    implements BookSession
```

- Purpose: persist one protected book into one selected SQLite file
- Book identity: constructor `Path bookPath` is the durable book boundary
- Access secret: constructor `SqliteBookPassphrase` supplies the resolved protected-book
  passphrase bytes
- Concurrency: thread-confined to one owning CLI command
- Reads: return empty for a missing file and do not initialize storage eagerly
- Access modes: query-style operations reopen books through an explicit read-only SQLite session
  that also enforces `pragma query_only = on`
- Open configuration: applies `sqlite3_key()` immediately after open, validates the key, enables
  `foreign_keys`, and disables `trusted_schema` on the opened handle
- Initialization: `openBook(...)` creates parent directories when needed, applies the canonical
  schema, inserts `book_meta.initialized_at`, and stamps the canonical FinGrind `application_id`
  plus `user_version`
- Account registry: stores declared accounts in the `account` table and enforces the `journal_line.account_code -> account.account_code` foreign key
- Commit: rejects unopened books plus unknown or inactive accounts before ordinary duplicate checks
- SQLite-specific administration: also exposes `rekeyBook(...)` outside the generic `BookSession`
  surface so one existing protected book can rotate onto replacement passphrase material
- Process model: opens one in-process SQLite3 Multiple Ciphers handle per session instance and
  closes it through the `BookSession` lifecycle

## `App`

`App` is the public process entrypoint for the FinGrind CLI adapter.

```java
public final class App
```

- Surface: exposes `main(String[] args)`
- Purpose: run the JSON CLI and exit with its process status code
- Commands: fronts `help`, `version`, `capabilities`, `print-request-template`, `open-book`,
  `rekey-book`, `declare-account`, `list-accounts`, `preflight-entry`, and `post-entry`
- Discovery contract: serializes application-owned `MachineContract` descriptors instead of
  assembling discovery payload maps inside the CLI layer
