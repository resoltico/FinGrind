---
afad: "5.0.1"
version: "0.62.2"
domain: PUBLICATION_TRANSACTION
updated: "2026-08-09"
route:
  keywords: [fingrind, publication transaction, journal, staged secret, recovery, residue deletion, lease order, cleanup outcome]
  questions: ["how does FinGrind recover a staged publication", "when may FinGrind delete a staged secret", "what makes an artifact publication successful", "how are publication-directory leases ordered"]
---

# Publication Transaction Journal ADR

**Status**: Accepted for the v0.63 implementation candidate.

## Decision

FinGrind replaces retained-stage publication with one private, durable publication transaction for
every secret-bearing output. The transaction journal, rather than a filename or a final artifact,
is the only authority that can resume or automatically clean a staged artifact.

The first implementation migrates protected-book backup, restore, and rekey pairs; encrypted book
and attestation keys; attestation receipts; PDF reports; and passphrase files. A publication path
that is not under this owner may not create a secret-bearing stage.

## Transaction Identity And Store

- A transaction ID is 32 lower-case hexadecimal characters generated from 128 bits of
  `SecureRandom` entropy. It is an opaque lookup key, never a path prefix or a deletion token.
- The canonical store is the deterministic owner-private per-user state root. POSIX uses
  `${XDG_STATE_HOME:-$HOME/.local/state}/fingrind/publication-transactions`; Windows uses
  `%LOCALAPPDATA%\\FinGrind\\publication-transactions`. Every component is created as owner-only or
  the operation fails before staging.
- Each transaction is one no-replace `txn-<id>.json` journal and one owner-private per-user HMAC
  key. A journal records schema `1`, the ID, a 128-bit nonce, owner-key fingerprint, creation time,
  ordered members, physical directory identities, stage and final paths, file identities, SHA-256
  digests, publication mode, and each durable transition.
- The canonical UTF-8 JSON bytes excluding `integrity` are authenticated with HMAC-SHA-256. A
  missing, malformed, stale, or unauthenticated journal is fail-closed and is never adopted or
  deleted automatically.
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

## Member And Lease Rules

Each member has an explicit role, final path, stage path, SHA-256 digest, physical identity,
publication mode (`NO_REPLACE_LINK` or `REPLACE`), and progress state. Pair members are one
transaction; they are never separately retried by copying a retained path.

Before a stage, journal, final member, recovery artifact, or lease-control artifact is mutated,
the owner resolves all participating directories and acquires their physical-directory leases in
globally sorted identity order. It releases them in exact reverse order. The lease coordinates
cooperating FinGrind processes only; hostile same-UID mutation is outside the supported threat
boundary because the JDK lacks a conditional unlink primitive.

## Recovery And Residue Removal

Recovery accepts a transaction ID and resolves it from the canonical store. A supplied path is
diagnostic only: `--journal <path>`, a stage name, and a matching final file never grant cleanup
authority.

Immediately before every automatic unlink, the owner revalidates all of the following through
no-follow access:

1. the stage is a regular non-symlink file;
2. its current physical identity equals the journaled identity;
3. its SHA-256 digest equals the journaled digest;
4. its relationship to the final member still matches the journaled publication mode; and
5. the journal is authenticated and names this transaction as owner.

Failure of any check preserves residue and produces a non-success cleanup outcome. A final member
that is the same file as a stage but lacks an authenticated owner journal requires explicit
operator confirmation; a filename prefix is never proof of ownership.

## Failure Vocabulary And Migration

`BookMaintenancePathFailure` is replaced, without alias, by artifact-neutral
`PublicationPathFailure`. It describes filesystem and publication-admission facts for every output
kind. The existing public wire values are deliberately migrated in one hard break; no duplicate
book-only vocabulary remains in CLI, discovery, JSON, PDF, or tests.

`ArtifactPublicationRetention`, `ArtifactPublicationResult`, and all retained-stage output fields
are replaced by transaction ID plus commit and cleanup outcomes. Public diagnostics may name a
final path but never expose a stage as a retry, deletion, or reconstruction handle.

## Verification

The implementation has deterministic fault injection after each journal transition, every unlink,
and every affected directory force. Required proofs cover one member, a two-member pair,
interrupted recovery, stale and corrupt journals, repeated replay, and concurrent transactions
sharing a directory domain. Each proof asserts both outcome axes and that a secret stage prevents
success.

## Consequences

- Existing retained-stage paths cease to be immutable evidence and become owner-journal-governed
  cleanup candidates.
- The migration is intentionally incompatible with v0.62.x output and error shapes.
- The journal’s private root is operational state, not protected-book accounting evidence; it must
  not become an accounting source of truth.
- Adding hostile same-UID protection requires a separately designed native conditional-unlink
  capability and is out of scope for v0.63.
