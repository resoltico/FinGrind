---
afad: "3.5"
version: "0.1.0"
domain: RUNTIME
updated: "2026-04-08"
route:
  keywords: [fingrind, runtime, sqlite, adapter, posting-fact, store, in-memory, cli, sqlite3]
  questions: ["how is a posting fact stored in fingrind", "what runtime stores does fingrind expose", "what does the sqlite adapter do in fingrind"]
---

# Runtime And Adapter API Reference

## `PostingFact`

Record representing one committed posting fact across runtime persistence seams.

### Signature
```java
public record PostingFact(
    PostingId postingId,
    JournalEntry journalEntry,
    ProvenanceEnvelope provenance)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `postingId` | Y | committed posting identity |
| `journalEntry` | Y | committed journal body |
| `provenance` | Y | committed audit envelope |

### Constraints
- Purpose: Canonical durable fact shape carried between application and persistence
- Rejects: `null` fields
- State: Immutable record

---

## `PostingFactStore`

Interface representing the narrow persistence seam for one book's posting facts.

### Signature
```java
public interface PostingFactStore
```

### Constraints
- Surface: `findByIdempotency(...)` and `commit(...)`
- Purpose: Keeps the application layer honest about lookup and commit operations
- Book scope: duplicate detection is book-local, not global
- State: Runtime port, not a generic repository abstraction

---

## `InMemoryPostingFactStore`

Class representing the non-durable runtime store used for tests and simple composition.

### Signature
```java
public final class InMemoryPostingFactStore implements PostingFactStore
```

### Constraints
- Purpose: Provides a fast non-durable store for tests and fuzz harnesses
- Storage: Backed by `ConcurrentHashMap<IdempotencyKey, PostingFact>`
- Duplicate policy: rejects duplicate idempotency by throwing `IllegalStateException`
- Durability: None

---

## `SqlitePostingFactStore`

Class representing the durable SQLite-backed store for one explicit book file.

### Signature
```java
public final class SqlitePostingFactStore
    implements PostingFactStore, AutoCloseable
```

### Constraints
- Purpose: Persists one book into one selected SQLite file
- Book identity: constructor path is the durable book boundary
- Filesystem: creates parent directories for nested paths when needed
- Implementation: shells out to the pinned `sqlite3` CLI surface and bootstraps the embedded schema
- Process model: opens a fresh `sqlite3` process per call; `close()` is effectively a no-op
- Duplicate policy: relies on durable unique idempotency inside the selected book

---

## `App`

Class representing the public CLI process entrypoint for FinGrind.

### Signature
```java
public final class App
```

### Constraints
- Surface: exposes `main(String[] args)`
- Purpose: runs the JSON CLI and exits with the resulting process status
- Commands: fronts `help`, `version`, `capabilities`, `preflight-entry`, and `post-entry`
- Boundary: delegates to package-private CLI machinery rather than owning business rules
