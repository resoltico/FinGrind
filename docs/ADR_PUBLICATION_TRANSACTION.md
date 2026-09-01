---
afad: "5.0.1"
version: "0.64.0"
domain: PUBLICATION_TRANSACTION
updated: "2026-09-01"
route:
  keywords: [fingrind, publication transaction, protected-book pair, journal, staged secret, recovery, owner context, residue deletion, lease order, cleanup outcome]
  questions: ["how does FinGrind recover a protected-book publication", "when may FinGrind delete a staged secret", "what makes an artifact publication successful", "how does a protected-book operation find its journal"]
---

# Publication Transaction Journal ADR

**Status**: Accepted for the v0.63 implementation candidate.

## Decision

FinGrind uses one authenticated private publication transaction for each v0.63 protected-book
backup, restore, and rekey pair. The transaction journal, rather than a filename, sidecar, or
final artifact, is the only authority that can publish, recover, or automatically clean a private
stage.

Legacy protected-book pair sidecars are not migrated into this authority. They are read only to
distinguish a clean destination from contaminated evidence; any such evidence blocks the operation
as `protected-book-pair-publication-evidence-blocked`. FinGrind never adopts, publishes from,
deletes, or repairs a legacy sidecar or its staged files.

## Transaction Identity And Store

- A transaction ID is 32 lower-case hexadecimal characters generated from 128 bits of
  `SecureRandom` entropy. It is an opaque lookup key, never a path prefix or deletion token.
- The canonical store is the deterministic owner-private per-user state root. POSIX uses
  `${XDG_STATE_HOME:-$HOME/.local/state}/fingrind/publication-transactions`; Windows uses
  `%LOCALAPPDATA%\\FinGrind\\publication-transactions`. Every component is created as owner-only or
  the operation fails before staging.
- A journal records schema `4`, its ID, nonce, owner-key fingerprint, creation time, ordered
  members, physical directory identities, private stage and final paths, file identities,
  SHA-256 digests, publication mode, and durable transitions. Its optional `ownerContext` is a
  64-character SHA-256 digest of the exact non-secret protected-book operation identity; it is
  authenticated journal metadata, not an external recovery capability.
- Canonical UTF-8 JSON bytes excluding `integrity` are authenticated with HMAC-SHA-256. A missing,
  malformed, stale, or unauthenticated journal is fail-closed and is never adopted or deleted
  automatically.
- Terminal journals retain their non-secret metadata for 90 days. Expiry is an explicit
  owner-authorized pruning operation; it is never an implicit excuse to delete a live stage.

## State And Outcome

The journal owns a closed transition sequence:

`PREPARED → STAGED → COMMITTING → COMMITTED → CLEANING → COMPLETE`.

`BLOCKED`, `COMMIT_UNCERTAIN`, `CLEANUP_INCOMPLETE`, and `CLEANUP_UNCERTAIN` are terminal or
recovery-required states. Every transition is written and force-confirmed before its dependent
filesystem mutation. A transaction exposes two independent outcomes:

| Axis | Values |
|:--|:--|
| Commit | `NONE_COMMITTED`, `ALL_COMMITTED`, `PARTIALLY_COMMITTED`, `COMMIT_UNCERTAIN` |
| Cleanup | `COMPLETE`, `INCOMPLETE`, `UNCERTAIN` |

Only `ALL_COMMITTED` plus `COMPLETE` is success. A visible final member, a completed link, or a
directory force alone is not success. No result may report success while a secret-bearing stage
remains materialized.

## Member, Lease, And Recovery Rules

Each member has an explicit role, final path, stage path, SHA-256 digest, physical identity,
publication mode (`NO_REPLACE_LINK` or `REPLACE`), and progress state. Protected-book pair members
are one transaction; they are never separately retried by copying a stage.

Before a stage, journal, final member, recovery artifact, or lease-control artifact is mutated,
the owner resolves all participating directories and acquires physical-directory leases in globally
sorted identity order. It releases them in exact reverse order. The lease coordinates cooperating
FinGrind processes only; hostile same-UID mutation is outside the supported threat boundary because
the JDK lacks a conditional unlink primitive.

Interactive and external recovery accepts only a transaction ID and resolves it from the canonical
store. A supplied path is diagnostic only: a stage name and a matching final file never grant
cleanup authority. A protected-book adapter may additionally look up one authenticated journal by
its owner context only after it holds the exact final-target leases; it verifies that the receipt
contains precisely the two expected roles and final paths before it projects a recovered result.
Ambiguous, incomplete, or mismatched receipts fail closed and never start a second transaction.

Immediately before every automatic unlink, the owner revalidates all of the following through
no-follow access:

1. the stage is a regular non-symlink file;
2. its current physical identity equals the journaled identity;
3. its SHA-256 digest equals the journaled digest;
4. its relationship to the final member still matches the journaled publication mode; and
5. the journal is authenticated and names this transaction as owner.

Failure of any check preserves residue and produces a non-success cleanup outcome. A final member
that is the same file as a stage but lacks an authenticated owner journal requires independent
investigation; a filename prefix is never proof of ownership.

## Public Contract

Successful protected-book maintenance projects only the two final member paths and the completed
`PublicationTransactionResult` (`id`, `state`, `commitOutcome`, and `cleanupOutcome`). It never
projects a stage path, stage digest, cleanup capability, or owner context. An interrupted matching
journal reports `publication-transaction-incomplete` with the final candidate and ID-only result;
legacy or malformed same-directory evidence reports the separate evidence-blocked outcome without
turning it into a retry instruction.

## Verification

The implementation has deterministic fault injection after each journal transition, every unlink,
and every affected directory force. Required proofs cover one member, a two-member pair,
interrupted recovery, stale and corrupt journals, repeated replay, concurrent transactions sharing
a directory domain, owner-context ambiguity, exact final-role receipt matching, and the rule that
a secret stage prevents success.

## Consequences

- v0.63 protected-book pair recovery is intentionally incompatible with v0.62.x sidecar recovery.
- Existing sidecars remain preserved evidence only; they do not form a compatibility path.
- The journal’s private root is operational state, not protected-book accounting evidence; it must
  not become an accounting source of truth.
- Adding hostile same-UID protection requires a separately designed native conditional-unlink
  capability and is out of scope for v0.63.
