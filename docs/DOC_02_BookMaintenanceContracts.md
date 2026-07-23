---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_MAINTENANCE_CONTRACT
updated: "2026-07-23"
route:
  keywords: [fingrind, maintenance, backup, restore, rekey, rollback, protected book, artifact, path, rejection, public path hint]
  questions: ["where are protected-book maintenance rejections documented", "how does fingrind report maintenance paths", "what is PublicPathHint", "which contract owns backup and restore path failures"]
---

# Book Maintenance Contract Reference

This file documents the public maintenance-artifact and rejection vocabulary shared by the
protected-book maintenance workflows and their CLI projections.

## `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, And `BookMaintenanceRejection`

These public maintenance-contract types keep verification-driven maintenance outcomes typed at the
published-language edge.

```java
public enum BookMaintenanceArtifactRole implements WireValue
public enum BookMaintenancePathFailure implements WireValue
public enum BookMaintenanceVerificationFailure implements WireValue
public sealed interface BookMaintenanceRejection
```

- `BookMaintenanceArtifactRole`: keeps maintenance failures precise about whether the rejected artifact was the live book, backup source, backup target, backup-key target, rollback artifact, or restored target.
- `BookMaintenancePathFailure`: keeps maintenance path-contract refusals typed as missing parent directory, parent path collision, missing owner traversal/write access, missing owner-only protection, non-regular target path, or unsupported secure filesystem.
- `BookMaintenanceVerificationFailure`: keeps deterministic maintenance verification failures typed as missing, blank SQLite, foreign SQLite, unsupported format version, incomplete FinGrind book, or protected-book verification failure.
- Boundary: `BookMaintenanceRejection.ArtifactPathInvalid`, `BookMaintenanceRejection.ArtifactBusy`, and `BookMaintenanceRejection.ArtifactVerificationFailed` retain artifact role, path failure or verification class, and canonical absolute paths as first-class machine facts instead of collapsing maintenance verification into generic runtime failure text.

## `PublicPathHint`

`PublicPathHint` is the text-and-PDF presentation value for a filesystem path.

```java
public record PublicPathHint(String value)
```

- It redacts one path to `<redacted>` plus the minimum trailing context useful to a human reader.
- It is excluded from JSON success payloads, JSON artifacts, and JSON failure details, whose machine fields carry canonical absolute paths.

## `BookMaintenanceRejection`

`BookMaintenanceRejection` is the closed family of deterministic maintenance-workflow refusals.

```java
public sealed interface BookMaintenanceRejection
```

- Variants: `BookHasBlockingArtifacts`, `BackupSourceHasBlockingArtifacts`, `ArtifactPathInvalid`, `ArtifactBusy`, `BackupDestinationAlreadyExists`, `BackupKeyFileAlreadyExists`, `ArtifactVerificationFailed`, `NoRollbackArtifactsFound`, `RollbackArtifactSelectionRequired`, `RollbackArtifactNotFound`, and `RollbackArtifactNotForBook`.
- Purpose: preserve closed-copy and rollback-artifact safety as first-class rejection language instead of leaking maintenance mistakes as ad hoc storage exceptions.

## `RejectionNarrative`

`RejectionNarrative` owns user-facing rejection prose for public rejection contracts.

```java
public final class RejectionNarrative
```

- Purpose: prevent CLI rendering and other public rejection surfaces from leaking Java class names as rejection text.

## `AttestationRegistryMutationResult`

`AttestationRegistryMutationResult` is the public result family for a credential enrollment,
rollover, revocation, or policy mutation.

```java
public sealed interface AttestationRegistryMutationResult
```

- `Mutated` carries the canonical book path, closed operation token, and exact
  `AttestationCommit` for the appended registry mutation.
- `Rejected` retains a deterministic `BookMaintenanceRejection` before admission.
- `AuthorizationRejected` retains the exact closed `AttestationVerificationFailure` when the
  live historical signer, quorum, capability, or credential-purpose rule refuses admission.
