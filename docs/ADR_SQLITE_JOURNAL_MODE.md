---
afad: "4.0"
version: "0.51.0"
domain: ADR_SQLITE_JOURNAL_MODE
updated: "2026-06-03"
route:
  keywords: [fingrind, adr, sqlite, journal mode, delete, wal, protected book, sidecar, concurrency]
  questions: ["why does fingrind use journal_mode=DELETE", "does fingrind support wal mode", "what is the sqlite concurrency posture in fingrind"]
---

# ADR: SQLite Journal Mode

**Status**: Accepted

## Decision

FinGrind pins opened protected-book sessions to `journal_mode=DELETE`.

It does not support `journal_mode=WAL` on the current public line.

## Context

FinGrind's current storage model is:
- one protected SQLite file is one book for one accounting entity
- the book is opened through one explicit session surface
- write transactions are authoritative bookkeeping commits, not high-concurrency shared-cache
  collaboration
- the same directory also carries security-relevant sidecar artifacts such as rollback journals,
  stale rekey rollback copies, and checksum sidecars for managed runtime verification workflows

SQLite WAL is an excellent default for many multi-reader application shapes. FinGrind is not
optimizing for that shape today.

## Why `DELETE`

`DELETE` is the best current fit because it keeps the storage theory simple and aligned with the
protected-book boundary:

- rollback-journal lifecycle is short-lived and does not leave a long-lived `-wal` plus `-shm`
  pair behind after ordinary close paths
- owner-only permission hardening can reason about one book file plus present same-directory
  sidecars without promising long-lived shared-memory coordination artifacts
- write semantics match FinGrind's current single-book, explicit-session, immediate-consistency
  posture
- the product does not currently promise multi-process concurrent readers during long-lived write
  activity
- the product does not currently need WAL's main benefit: read concurrency that continues while a
  writer keeps changes in a separate append log

## Supported Concurrency Stance

Current FinGrind stance:
- one SQLite session is thread-confined
- one selected book may be opened by separate processes, but FinGrind does not promise a
  collaborative multi-process concurrency model
- authoritative bookkeeping writes are serialized by SQLite's normal locking plus FinGrind's own
  transactional validation and append-only constraints
- if future product meaning requires long-lived concurrent readers across active writers, that is
  a new storage decision and must be introduced deliberately

## Security And Operational Consequences

- protected-book encryption covers rollback-journal bytes written for the book
- permission hardening applies to the book plus present same-directory SQLite sidecars
- operators do not need to manage long-lived `-shm` metadata as part of the ordinary current
  storage contract
- the current smoke, runtime, and schema tests can assert one stable journal-mode posture instead
  of tolerating multiple modes

## Revisit Trigger

Revisit this ADR only if FinGrind gains a real product promise that needs WAL, such as:
- a supported multi-process read-concurrency model during active writes
- a deployment topology where rollback-journal behavior becomes operationally worse than WAL for
  measured reasons
- a new protected-book or replication architecture whose invariants differ from the current
  single-book session model

Until one of those conditions is true, `journal_mode=DELETE` remains the intended current-state
contract rather than an incidental SQLite default.
