---
afad: "5.0.1"
version: "0.62.2"
domain: BOOK_MAINTENANCE_CONTRACT
updated: "2026-08-09"
route:
  keywords: [fingrind, maintenance, backup, restore, rekey, recovery, protected book, artifact, path, canonical parent, source-artifact-identity-duplicated, source-artifact-identity-changed, pair-targets-conflict, target-owner-only-required, protected-book-pair-publication-evidence-blocked, rejection, public path hint]
  questions: ["where are protected-book maintenance rejections documented", "how does fingrind report maintenance paths", "how does a maintenance path resolve to a canonical parent", "what does source-artifact-identity-duplicated mean", "what does source-artifact-identity-changed mean", "what does pair-targets-conflict mean", "what is PublicPathHint", "which contract owns backup and restore path failures"]
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

- `BookMaintenanceArtifactRole`: its complete wire vocabulary is `live-book`,
  `live-book-key-source`, `backup-source`, `backup-key-source`, `backup-target`,
  `backup-key-target`, `restored-target`, and `new-book-key-target`. It keeps maintenance failures
  precise about whether the rejected artifact was the live book, its key-file source, a backup
  source or backup-key source, a backup target or backup-key target, a restored-book target, or
  the shared new-book-key target used by restore and rekey.
- `BookMaintenancePathFailure`: its closed wire vocabulary is `missing-parent-directory`,
  `parent-path-collision`, `parent-owner-access-required`, `parent-owner-only-required`,
  `artifact-must-be-regular-non-symlink-file`, `target-owner-only-required`,
  `target-identity-unestablished`,
  `source-artifact-identity-duplicated`, `source-artifact-identity-changed`,
  `unsupported-secure-filesystem`, `atomic-owner-only-protocol-file-creation-unsupported`,
  `atomic-secret-publication-unsupported`, `atomic-book-publication-unsupported`, and
  `atomic-book-replacement-unsupported`. It keeps maintenance path-contract refusals typed rather
  than inferring safety from a generic filesystem failure.
  `source-artifact-identity-duplicated` means a later role-tagged source resolves to the same
  physical artifact as an earlier source; callers must select independent source files.
  `source-artifact-identity-changed` means post-lock revalidation found that a selected source no
  longer has the locked physical identity; callers must restore the trustworthy intended source,
  keep every source stable, and rerun the complete operation.
  `target-owner-only-required` means an existing protected-book source or FinGrind recovery
  artifact that must be inspected is not owner-only; correct it outside FinGrind before retrying
  rather than asking FinGrind to repair it. A caller-owned ordinary no-clobber output leaf is not
  inspected as a FinGrind artifact and receives its operation's exact occupied-target rejection.
  Completed pair-publication records retain immutable stage-owner evidence without reserving
  unrelated later output targets.
- `BookMaintenanceVerificationFailure`: keeps deterministic maintenance verification failures typed as
  missing, blank SQLite, foreign SQLite, incomplete FinGrind book, or protected-book verification
  failure. A non-current physical format is instead the separate top-level
  `unsupported-book-format-version` contract failure; it is not a maintenance-verification token.
- Boundary: `BookMaintenanceRejection.ArtifactPathInvalid`, `BookMaintenanceRejection.ArtifactBusy`,
  and `BookMaintenanceRejection.ArtifactVerificationFailed` retain artifact role, path failure or
  verification class, and machine paths as first-class facts instead of collapsing maintenance
  verification into generic runtime failure text. An unreadable selected backup-key source is an
  `ArtifactVerificationFailed` result with role `backup-key-source` and that key's canonical path;
  ambiguous failures while opening an otherwise readable encrypted snapshot remain attributed to
  `backup-source`. `PairTargetsConflict` separately preserves each normalized absolute submitted
  spelling, because a physical alias need not be lexically equal.

## `PublicPathHint`

`PublicPathHint` is the text-and-PDF presentation value for a filesystem path.

```java
public record PublicPathHint(String value)
```

- It redacts one path to `<redacted>` plus the minimum trailing context useful to a human reader.
- It is excluded from JSON success payloads, JSON artifacts, and JSON failure details, whose machine fields carry canonical absolute paths.

## Protected-Book Maintenance Artifact Path Admission

`backup-book`, `restore-book`, and `rekey-book` admit every caller-selected protected-book,
book-key source, and generated-key target through one hard-break path contract before lifecycle
work begins.

- Every existing selected direct parent is validation-only: it must already be a no-follow real
  directory with complete private owner-only, non-mutable ancestry, and FinGrind never
  permission- or ACL-repairs it.
- Only an absent final-target parent may be created: FinGrind preflights its creation ancestry,
  atomically creates it with POSIX `0700`, then resolves and validates the resulting canonical
  parent and complete private ancestry. A lifecycle source parent must already exist. ACL-only
  final-target creation fails closed as `artifact-path-invalid` with
  `atomic-owner-only-protocol-file-creation-unsupported`; FinGrind never creates a readable
  parent and repairs its ACL.
- Before canonicalization, FinGrind scans every lexical component from the root through the
  selected parent without following links. Any symbolic-link or non-directory component,
  including a direct-parent alias, is refused. A lifecycle mutation source leaf must already be a
  regular non-symlink file before FinGrind prepares any final-target parent. A final target leaf
  may be absent; when it exists, a symlink or non-regular type is refused, while a regular file is
  admitted or rejected by that operation's no-replace or replacement rule. `inspect-book`
  deliberately keeps an absent live book as a typed missing-book state rather than reusing the
  lifecycle-source rule; attestation-verification commands report their own verification failure
  after admitting the same path. Normalization never changes an operation-specific final-name
  policy.
- This makes canonical machine paths, held leases, pair-recovery records, and final publication
  refer to the same physical target rather than to a caller's alternate spelling.
- Each physical maintenance directory has one retained owner-only v4 directory-reservation control
  file and an exclusive lock; it coordinates exact target-directory admission, including absent
  targets. Existing source artifacts additionally use a private per-user v4 object-control
  namespace named from explicit physical identity, so hard-link spellings in different directories
  converge on the same active-access and maintenance exclusion. A held lock is the sole liveness
  fact; an unlocked valid control file is inert after a crash. FinGrind never reclaims, deletes,
  or rewrites this protocol state. v2/v3 controls and other retired namespaces are not interpreted
  or adopted: their residue, malformed controls, unavailable locks, and overlapping in-process
  locks block safely. The incompatible v4 cutover requires an independently verified outage and
  archival—not deletion—of every old per-user root and affected v2/v3 directory control; old and
  new controls must never be merged or co-run.
- The complete source set must contain distinct physical artifacts. If two selected source roles
  resolve to one object, the later role receives `artifact-path-invalid` with
  `pathFailure: "source-artifact-identity-duplicated"` before target admission, staging, or
  publication begins.
- After all source exclusions are acquired, SQLite revalidates every source against its locked
  physical identity and repeats the uniqueness check before target admission. A source replacement
  or substitution receives `artifact-path-invalid` with
  `pathFailure: "source-artifact-identity-changed"`; the caller must restore the trustworthy
  intended source, keep every source stable, and rerun the complete operation.

## Protected-Book Pair Final-Target Identity

Final pair-target identity is established after lifecycle source validation and final-parent
admission. An eligible missing final parent may therefore remain as a freshly created private
POSIX-`0700` directory after this refusal.

## Protected-Book Pair Publication SPI

`ProtectedBookPairPublicationAdmission` holds the decision reached while both final-target leases
are owned: prepare a new pair, reconcile an exact earlier request, acknowledge a complete backup,
require prepublication recovery, block unsafe evidence, or preserve a completion uncertainty.
`ProtectedBookPairPublicationBinding` persists the exact backup, restore, or rekey facts that own
that recovery. `ProtectedBookPairPublicationRecoveryRequest` is the caller's complete requested
identity, while `ProtectedBookPairPublicationSourceIdentity` and its `Kind` record only the
non-secret rekey source path and transport. `StagedPairPublicationCommitOutcome` preserves the
same published, recoverable-before-final, evidence-blocked, and completion-uncertain distinction.
`ProtectedBookPairPublicationFailureOutcome` is the shared closed non-success outcome: its
`PrepublicationRecoveryRequired` variant proves no final-member primitive ran and carries a
durable recovery record, `EvidenceBlocked` retains two unestablished members without claiming
authoritative retained-stage evidence, and `CompletionUncertain` retains the strongest established
member facts plus any matching retained-stage evidence.
This is one closed SPI: no implementation may reinterpret unestablished evidence as retryable or
reuse another operation's retained stages.

- Existing-target rule: when both final targets exist, SQLite uses `Files.isSameFile`; a proven
  single physical object is `BookMaintenanceRejection.PairTargetsConflict`. For two absent leaves
  in one physical parent, exact raw leaf equality or a collision after canonical Unicode
  decomposition plus root-locale case mapping is the same conflict. Its public wire code is
  `pair-targets-conflict`, its category is `precondition`, and its exit code is `2`. Other distinct
  leaves remain valid when the filesystem admits them. An inability to establish target identity
  during admission is the separate `target-identity-unestablished` path failure.
- Mutation boundary: initial pair-target admission occurs before any final target, retained
  lease-control file, stage, capability witness, reservation, claim, or pair-recovery-evidence
  artifact is created. The lock-protected revalidation keeps an already-held lease control after
  an external same-owner race changes a target identity; it still never publishes either target.

## `BackupAcknowledgementState`

`BackupAcknowledgementState` is the exact public disposition of a backup's source-book
acknowledgement attempt.

```java
public enum BackupAcknowledgementState implements WireValue
```

- `acknowledged`: this invocation appended the backup-created attestation operation.
- `resumed`: an explicit resume completed acknowledgement; the exact operation may have been
  appended now or already have been present.
- `already-present`: the exact acknowledgement operation already existed, so this invocation
  appended no operation.

## `ProtectedBookPairPublicationCompletion`, `ProtectedBookPairPublicationRetention`, `ProtectedBookPairPublicationMemberState`, And `ProtectedBookPairPublicationRecoveryRecordState`

These closed wire vocabularies distinguish a completed protected-book pair from an invocation that
must preserve and reconcile a completion-uncertain pair.

```java
public enum ProtectedBookPairPublicationCompletion implements WireValue
public record ProtectedBookPairPublicationRetention(
    ArtifactPublicationResult bookPublication,
    ArtifactPublicationResult generatedSecretPublication)
public enum ProtectedBookPairPublicationMemberState implements WireValue
public enum ProtectedBookPairPublicationRecoveryRecordState implements WireValue
```

- `ProtectedBookPairPublicationCompletion` is carried by every published maintenance result:
  `published` means this invocation durably published the final book-and-generated-secret pair;
  `recovered` means this invocation reconciled an earlier completion-uncertain pair without a new
  maintenance mutation; `already-published` means an acknowledgement retry verified an already
  complete backup pair without publishing it again.
- `ProtectedBookPairPublicationRetention` is required for `published` and `recovered`. Its exact
  JSON projection is `pairPublicationRetention.{bookPublication,generatedSecretPublication}`;
  each member is exactly `{path,retainedStage}`. The two final paths and the two retained stages
  are four distinct immutable evidence locations. The field is null only for the
  `already-published` backup acknowledgement, which has no captured FinGrind retained-stage
  evidence. Restore and rekey cannot emit `already-published`.
- `ProtectedBookPairPublicationMemberState` is the strongest fact about each final member in a
  `protected-book-pair-publication-uncertain` error: `not-attempted`, `outcome-uncertain`,
  `published-durability-unconfirmed`, or `published-durable`.
- `ProtectedBookPairPublicationRecoveryRecordState` has `durably-retained`, when the complete
  recovery record was force-confirmed before a final-member precondition stopped publication, and
  `durability-unconfirmed`, when forcing the record's parent directory did not complete before a
  final-member primitive began.
- Boundary: a pair error always names distinct canonical `bookTarget` and `generatedSecretTarget`
  paths. Its top-level `argument` is explicitly `null`; `path` is the book target and
  `relatedPaths` includes the generated-secret target plus both retained stages whenever the
  latter are established. Its JSON `recoveryRecordState` is non-null exactly when both member
  states are `not-attempted`; otherwise the field is explicitly `null`. Its JSON
  `pairPublicationRetention` is always present and nullable. When non-null, its
  `bookPublication.{path,retainedStage}` and
  `generatedSecretPublication.{path,retainedStage}` facts must bind exactly to their respective
  final targets. `null` means no authoritative pair-stage fact was established; it never permits
  cleanup, replacement, or a fresh retry.
  For verified operation-bound evidence, callers preserve the entire named pair and rerun the exact
  same operation with its complete original source, target, and secret inputs. When
  `recoveryRecordState` is non-null, they preserve FinGrind's recovery material too. FinGrind only
  resumes derived stages recorded by the owner record. Callers never rename,
  overwrite, delete, replace, substitute, recreate, or otherwise manually clean pair evidence or
  either final member, and they do not start a fresh pair.
- `protected-book-pair-publication-evidence-blocked` is the distinct exit-4 precondition where
  retained evidence exists but cannot establish a safe final-member state. Its details require both
  member states to be `unestablished` and `recoveryRecordState: null`; its always-present nullable
  `pairPublicationRetention` is `null` when no authoritative pair-stage fact is safe to report.
  It is not a recovery command or a partial-workflow retry instruction. Preserve the evidence and
  investigate independently.
- Admission hard break: before any stage, probe, reservation, or final mutation, FinGrind acquires
  and scans the full source-and-target workflow scope for an operation-owned record that binds the
  exact source, target pair, secret identity, and derived stages. A pending owner record blocks with
  `maintenance-recovery-pending`; its visible target details are diagnostic only and do not
  authorize reconstructing a workflow from partial inputs. Malformed, legacy, incomplete, or
  inconsistent evidence fails closed as `protected-book-pair-publication-evidence-blocked`; it is
  neither adopted nor manually cleaned.
- Rekey replacement boundary: while its maintenance lease is held, `rekey-book` revalidates the
  selected live-book digest immediately before generated-secret publication and again immediately
  before book replacement. The lease coordinates FinGrind maintenance work, but cannot prevent a
  same-owner external filesystem write in the interval after validation and before the operating
  system publication call. That interference is a completion-uncertain
  `protected-book-pair-publication-uncertain` outcome, never an atomic-replacement guarantee.

## `BookMaintenanceRejection`

`BookMaintenanceRejection` is the closed family of deterministic maintenance-workflow refusals.

```java
public sealed interface BookMaintenanceRejection
```

- Variants: `BookHasBlockingArtifacts`, `BackupSourceHasBlockingArtifacts`, `BackupSourceMatchesLiveBook`,
  `PairTargetsConflict`, `ArtifactPathInvalid`, `ArtifactBusy`, `BackupAcknowledgementConflict`,
  `BackupDestinationAlreadyExists`, `SecretTargetOccupied`, `BookDestinationOccupied`,
  `RecoveryPending`, and `ArtifactVerificationFailed`.
- `PairTargetsConflict`: a `rejected`, `precondition`, exit-`2` refusal for one established
  final filesystem identity. Its `bookTarget` and `generatedSecretTarget` fields retain normalized
  absolute submitted spellings; they are not a claim that either string is the canonical physical
  path. When the strings differ, the envelope retains the generated-secret spelling in
  `relatedPaths` as well as the book spelling in `path`.
- `ArtifactPathInvalid`: a `rejected`, `precondition`, exit-`6` refusal that preserves the
  declared `artifactRole`, `artifactPath`, and closed `pathFailure` token.
- `RecoveryPending`: a `rejected`, `precondition`, exit-`7` maintenance-state conflict. Its
  non-null JSON `details` are `recoveryOperation`, `bookTarget`, and
  `generatedSecretTarget`; the operation is the canonical wire value that must resume the retained
  pair, and both targets are canonical absolute paths. Text labels are `Recovery operation`,
  `Book target`, and `Generated secret target`. Its top-level `argument` is explicitly `null`;
  `path` is the book target and `relatedPaths` contains the generated-secret target.
  Resume that operation with its complete original source, target, and secret inputs; the three
  details do not reconstruct a backup source, backup ID, credentials, or secret material. No caller
  may rename, overwrite, delete, recreate, or otherwise manually clean recovery evidence.
- Purpose: preserve closed-copy, exact-pair-publication, evidence-admission, and artifact
  path/verification safety as first-class rejection language instead of leaking maintenance
  mistakes as ad hoc storage exceptions.

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
  reconstructed current-head signer, quorum, capability, or credential-purpose rule refuses
  admission.
