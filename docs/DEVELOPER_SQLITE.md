---
afad: "3.5"
version: "0.2.0"
domain: DEVELOPER_SQLITE
updated: "2026-04-09"
route:
  keywords: [fingrind, sqlite, sqlite3, storage, single-book, filesystem-path, schema, canonical-schema, no-migrations]
  questions: ["why does fingrind use sqlite3 cli instead of jdbc", "how does fingrind map one sqlite file to one book", "how does the sqlite adapter initialize a new book"]
---

# SQLite Developer Reference

**Purpose**: Storage rationale and implementation notes for FinGrind's SQLite adapter.
**Schema references**:
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)

## Hard-Break Storage Stance

FinGrind currently treats one SQLite file as one book for one entity.

That means:
- the selected file path is the book identity
- the file may live anywhere on the operating-system filesystem
- there is no default database location
- `--book-file` is required for both preflight and commit
- duplicate idempotency is enforced within the selected book, not globally across files
- one canonical current schema defines every newly initialized book
- there is no schema migration framework, version table, or compatibility layer

Older book shapes are out of scope for the current foundation.

## Current Adapter Choice

FinGrind's durable adapter is [`SqlitePostingFactStore`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java).

Current implementation choice:
- shell out to a pinned `sqlite3` CLI, defaulting to the repo-managed SQLite 3.51.3 toolchain
- initialize the schema from the canonical embedded SQL resource
- open a fresh `sqlite3` process per operation
- rely on the chosen book path as the durable boundary

Why this is acceptable for the current stage:
- the project is SQLite-first on purpose, so a direct SQLite process is an honest dependency
- the adapter stays transparent: schema and SQL remain visible artifacts
- local development and packaged-JAR runs can share one explicit binary pin through `FINGRIND_SQLITE3_BINARY`
- SQLite CLI invocation is simple enough to test through the real filesystem

## Adapter Composition

The SQLite adapter is split into focused collaborators:
- [`SqlitePostingFactStore`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java): orchestrates lookups and commit outcomes
- [`SqliteSchemaManager`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteSchemaManager.java): loads and applies the canonical schema on first commit
- [`SqlitePostingSql`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingSql.java): generates lookup and commit SQL
- [`SqlitePostingMapper`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingMapper.java): reconstructs domain facts from SQLite rows
- [`SqliteCommandExecutor`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqliteCommandExecutor.java): owns `sqlite3` process execution

## Runtime Behavior

- Reads and preflight against a missing book return empty state and do not create a file.
- The first commit creates parent directories if needed and initializes the canonical schema.
- Book-local uniqueness enforces duplicate idempotency durably.
- SQLite also enforces one reversal per target through a partial unique index.
- Correction linkage is durable and references `posting_fact(posting_id)` through a foreign key.

The runtime seam distinguishes ordinary duplicate outcomes from true runtime failures:
- duplicate idempotency returns `PostingCommitResult.DuplicateIdempotency`
- duplicate reversal target returns `PostingCommitResult.DuplicateReversalTarget`
- failed `sqlite3` execution stays an `IllegalStateException` and becomes CLI `runtime-failure`

## Canonical Schema Policy

- The canonical schema resource is [`book_schema.sql`](/Users/erst/Tools/FinGrind/sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql).
- There are no versioned migration file names such as `V1__...`.
- There is no migration step between old and new book shapes.
- If the schema changes again during this hard-break phase, new books are created from the new canonical file.

## Why CLI-Backed SQLite Instead Of JDBC

Reasons for the current design:
- the durable dependency is SQLite itself, not a Java driver wrapper
- the pinned `sqlite3` binary is explicit and inspectable
- the packaged CLI JAR stays smaller and simpler
- SQL remains first-class rather than hidden behind driver behavior
- the project is not trying to be portable across unrelated databases

This is a design choice, not an accidental omission.
