---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION_ARTIFACTS
updated: "2026-07-27"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["ArtifactPublicationStages", "ArtifactPublicationRetention", "ArtifactPublicationResult", "ArtifactPublicationRetainedStageException", "ContractFailureDetails.ArtifactPublicationOutcomeUncertain", "ContractFailureDetails.ArtifactPublicationDurabilityUncertain", "ProtectedBookPairPublicationCompletion", "ProtectedBookPairPublicationRetention", "BackupManifest", "AttestationArtifactContainer", "AttestationArtifactSnapshotReader", "AttestationArtifactSnapshotReaderException", "AttestationBackupArtifact", "AttestationDirectoryDurability", "AttestationReceipt", "PrivateOutputDirectory", "PrivateOutputDirectory.Violation", "PrivateOutputDirectory.Violation.Kind", "VerifyAttestationReceiptResult"]
route:
  keywords: [verifiable-operation-attestation, backup-manifest, attestation-receipt, artifact-container, restore-book, backup-acknowledgement, receipt-anchor, no-clobber]
  questions: ["how is an attested backup artifact encoded", "how does FinGrind restore an attested snapshot", "what does an attestation receipt anchor", "which vectors prove backup and receipt envelopes"]
stage: "Current public protocol 57 and protected-book format 57 contract"
---

# Verifiable Operation Attestation Artifacts

This document is the canonical artifact subprotocol for protected-book format 57: backup manifests,
artifact publication and restore, off-chain receipts, and artifact golden vectors.
[DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md) owns the
shared envelope grammar and immutable authorization evidence; the
[verification protocol](./DOC_02_VerifiableOperationAttestationVerification.md) owns failure
vocabulary and verifier precedence; the corpus owns complete protected-book and artifact fixture
sources. This document describes the current released behavior.

## Backup Manifest, Publication, And Restore

Signer identity belongs only in the shared envelope:

~~~text
manifestPayload =
  "FGATTBM1"
  || manifestVersion(u8 = 01)
  || bookId(uuid)
  || backupId(uuid)
  || sourceOrder(u64)
  || sourceOperationHead(hash)
  || snapshotDigest(hash)
  || algorithmId(algorithm-id = "ed25519")

manifestEnvelope = envelope(manifestPayload)

trailer =
  "FGATBMF1"
  || containerVersion(u8 = 01)
  || snapshotLength(u64)
  || manifestEnvelopeLength(u32)

publishedArtifact = snapshot || manifestEnvelope || trailer
~~~

The 21-byte final trailer permits an unambiguous reverse parse. It must name exactly one snapshot
and one manifest envelope; their declared lengths must consume the complete file with no trailing
or unconsumed byte. snapshotDigest is SHA-256 of exactly snapshot. backupArtifactDigest is SHA-256
of the entire publishedArtifact. A manifest verifies under the BACKUP quorum resolved as of
sourceOrder from the snapshot's own registry and policy.

Backup is a staged external-artifact operation:

1. Create one consistent SQLite snapshot at head H.
2. Compute snapshotDigest, collect the BACKUP quorum as of H, and form the artifact.
3. Fsync the staged artifact; publish only through an atomic create-exclusive or link-based
   no-clobber primitive; fsync the parent directory. Check-then-rename, ATOMIC_MOVE, and POSIX
   rename are forbidden because they can replace an existing target. A final-path collision is
   artifact-already-exists with exit 7.
4. Best-effort append backup-created with backupId, backupArtifactDigest, sourceOrder H, and
   source head H. It compares-and-swaps the current live head; if that head advanced, it rebuilds
   and re-signs the acknowledgement while retaining source H. An authorization refusal after the
   pair is published returns its exact `attestation-*` rejection with exit code 2 and preserves
   that pair for an exact-tuple retry. It is not acknowledgement-pending; that exit-4 outcome is
   reserved for an operational interruption whose authorization result is not known.

backup-created is idempotent only for the identical tuple bookId, backupId, backupArtifactDigest,
and sourceHead. Exact replay is a no-op success. Any other reuse of backupId is
backup-acknowledgement-conflict with exit 2.

## `AttestationDirectoryDurability`

`AttestationDirectoryDurability.force` persists the parent-directory name change immediately
after every no-clobber attestation artifact, companion key, restored-book, or receipt publication.
It requires JVM native access for the core module and supports macOS, Linux, and Windows. If the
first force follows a returned final link, the final name is known but its durability is not, so
single-artifact output reports `artifact-publication-durability-uncertain` rather than success.
The private stage remains immutable evidence of the exact bytes used for the no-clobber final link;
FinGrind never follows publication by deleting, replacing, or reusing that stage. Backup, restore,
and rekey classify their book-and-generated-secret final members together; their two distinct
non-success outcomes are the recoverable `protected-book-pair-publication-uncertain` and the
investigation-only `protected-book-pair-publication-evidence-blocked` contracts below.

## Private Artifact Output Admission And Retained Stage Evidence

Generated attestation keys, exported receipts, and report PDFs share one private-output rule. The
final target must be absent and its direct parent must already be a real private directory; FinGrind
does not create that parent or relax its permissions. Before canonicalization, it scans every
lexical component from the root through that parent without following links and refuses any
symbolic-link or non-directory component, including a direct-parent alias. Every success and
failure reports the canonical physical final path. Every ordinary POSIX or ACL ancestor must be
owned by the final-output owner; a POSIX ancestor owned by uid `0` is also admitted. On POSIX, a
group- or other-writable resolved ancestor is additionally refused except for a sticky ancestor
whose direct child is owned by the final-output owner. ACL entries must independently deny every
other principal mutation. Search-only access for another principal's ancestor is not enough: that
ancestor's owner could still change its mode or replace its child.

`PrivateOutputDirectory.requireExistingOwnerOnly` enforces that admission before staging begins.
Its checked `PrivateOutputDirectory.Violation` identifies a caller-selected directory that cannot
safely host a staged artifact; callers must select a different existing private directory rather
than changing permissions or following the rejected terminal link.
`PrivateOutputDirectory.Violation.Kind` distinguishes an unsafe owner-only/ancestry condition from
a path collision, so callers can preserve a conflicting entry rather than treating it as a
permissions repair request.

`ArtifactPublicationStages` is the sole core owner of a fresh `0600` retained stage. It creates
the stage with no replacement, writes or copies exact bytes through a bound channel, forces that
channel before returning, and refuses a symlink or non-regular copy source. A later failure never
turns the materialized stage into cleanup authority.

Publication atomically creates, writes, and forces a fresh private owner-only stage, creates the
absent final name without replacement, and forces the parent directory. The stage is not a
cleanup handle: it is immutable publication evidence. `ArtifactPublicationResult` binds the
canonical final path to that stage through `ArtifactPublicationRetention`; each successful staged
artifact is represented in JSON as `artifacts[].{format,path,retainedStage}`. FinGrind never
deletes, replaces, or reuses the reported `retainedStage`; a new attempt uses a fresh destination
and a fresh stage.

Failures preserve the same fact at the envelope seam. When a failure has a retained stage, its
top-level `retainedStage` names that canonical stage and `path`/`relatedPaths` include the relevant
final and evidence paths. Detail objects repeat the fact only where they need to bind it to a
specific candidate or published artifact. The deliberately phase-specific exit-4 outcomes are:

| Publication phase | Public code and details | Required operator action |
|:--|:--|:--|
| a primary publication failure occurs after a stage exists, but a final-link outcome is not indeterminate | the primary deterministic code plus top-level `retainedStage`; there is no stage-cleanup error class | Preserve the stage as evidence. Do not reuse it; choose a fresh destination and stage only after addressing the primary failure. |
| a no-replace link throws without establishing whether it created the final name | `artifact-publication-outcome-uncertain` with `details.{candidateArtifact,retainedStage}`; the nested and top-level stage are absent only when no stage exists | Preserve and inspect the candidate and any reported stage. The candidate is not an assertion that publication occurred; use a fresh destination for a new attempt. |
| a link returned but its parent-directory durability cannot be confirmed | `artifact-publication-durability-uncertain` with top-level `retainedStage` and `details.publishedArtifact.{path,retainedStage}` | Preserve and inspect the known final and its stage before relying on the artifact; never retry that no-clobber target. |

There is no cleanup disposition, rollback artifact, deletion retry, or stage-only cleanup failure
in protocol 57. `ArtifactPublicationRetention` and `ArtifactPublicationResult` are the sole
public owner of the stage fact.

## `Backup Artifact Types`

`AttestationArtifactSnapshotReader` supplies the verified immutable snapshot view used to create
or restore an artifact. `AttestationBackupArtifact` is the parsed snapshot/manifest container;
`AttestationBackupArtifactVerification` is the successful verification result; and
`AttestationBackupAcknowledgement` binds the live-chain acknowledgement to one exact backup tuple.
`AttestationBackupAcknowledgementAdmission` is the closed append, identical-replay, or conflict
decision for that tuple. `BackupAcknowledgementConflictException` carries the non-retryable
conflicting backup id after the exact-tuple check fails.

| Crash point | Residual | Recovery |
|:--|:--|:--|
| after snapshot or blessing, before final publication | a retained private stage | preserve it as immutable evidence; never discard, delete, replace, reuse, or treat it as a retry input |
| at or after a final-member primitive, before the pair is durably classified | both named final members and operation-owned recovery evidence | `protected-book-pair-publication-uncertain` only when verified evidence establishes an exact recoverable operation; preserve the evidence and rerun that exact full workflow. If the evidence cannot establish safe final-member state, `protected-book-pair-publication-evidence-blocked` requires independent investigation and is not rerunnable. |
| after publication, before acknowledgement | manifest-attested artifact and an understated source-book backup index | resume the identical acknowledgement |

A manifest-attested artifact is never unattested or orphaned. A retained stage never authorizes
removal of itself or of a final member; no operation claims a published file disappeared.

## `AttestationArtifactSnapshotReaderException`

Runtime exception that preserves an explicitly classified external-source failure while a storage
adapter opens a manifest-authenticated artifact snapshot.

### Signature

```java
public class AttestationArtifactSnapshotReaderException extends RuntimeException
```

### Constraints

- Trigger: The adapter has established a source-specific failure before it can return immutable
  attestation evidence, such as an unreadable selected backup-key file.
- Recovery: Correct or replace the reported selected source and rerun verification; do not infer
  that the artifact is invalid from this exception alone.
- State: Read-only; it never mutates the artifact or a protected book.
- Compatibility: Public core adapter boundary. Ordinary reader runtime failures remain
  `manifest-invalid`; adapters must not use this type for ambiguous snapshot or manifest failures.

---

## Recovery-Pending Admission

Before `backup-book`, `restore-book`, or `rekey-book` performs any stage, probe, reservation, or
final mutation, it acquires and scans the full source-and-target workflow scope for the current
operation-owned pair evidence. The record binds the maintenance kind, its exact source identity,
both exact final targets, generated-secret input identity, both derived stages, and the
operation-specific authorization facts. It is not a generic target tuple or authority to continue
an arbitrary sibling workflow.

A verified unresolved record owned by another full workflow returns
`maintenance-recovery-pending`: a `rejected`, `precondition`, exit-`7` maintenance-state conflict.
Its non-null JSON `details.{recoveryOperation,bookTarget,generatedSecretTarget}` identifies the
canonical operation and target pair; text labels are `Recovery operation`, `Book target`, and
`Generated secret target`. Those diagnostics intentionally do not reconstruct secret material or
all original inputs. Restart the named command with its complete original source, target, and
secret inputs; FinGrind admits recovery only when they match the owner record and only resumes the
record's own derived stages. Its top-level `argument` is `null`; `path` is the book target and
`relatedPaths` contains the generated-secret target. Never rename, overwrite, delete, recreate,
or manually clean recovery evidence.

Malformed, legacy, incomplete, or internally inconsistent evidence is not adopted. If it cannot
establish safe final-member state it fails closed as the exit-`4`
`protected-book-pair-publication-evidence-blocked` error, never as an invitation to infer or
reconstruct a workflow from partial inputs.

## Protected-Book Pair Publication Recovery

`backup-book`, `restore-book`, and `rekey-book` treat the final book and generated-secret paths as
one operation-bound pair. Successful maintenance results carry
`pairPublicationCompletion`: `published` means the current invocation durably published the pair;
`recovered` means it reconciled the exact earlier completion-uncertain pair without another
maintenance mutation; `already-published` applies only to a backup acknowledgement retry that
verified the complete existing pair without publishing it again. Every `published` or `recovered`
result also has a required `pairPublicationRetention` object:

```json
{
  "bookPublication": {"path": "/private/backup.fgb", "retainedStage": "/private/.backup-stage"},
  "generatedSecretPublication": {"path": "/private/backup.key", "retainedStage": "/private/.backup-key-stage"}
}
```

Those four paths are distinct immutable facts. `pairPublicationRetention` is `null` only for the
`already-published` backup-acknowledgement case, which deliberately acknowledges an external or
older completed pair for which FinGrind has no retained-stage fact. Restore and rekey never emit
`already-published`. Text presents the same facts as `Published book file`, `Book retained stage`,
`Published generated-secret file`, and `Generated-secret retained stage`; the null acknowledgement
states that no FinGrind retained-stage evidence exists.

When FinGrind cannot establish the pair's durable disposition, it returns the exit-`4`,
`precondition` error `protected-book-pair-publication-uncertain`, never a successful result with a
retained-stage warning. Its top-level `argument` is explicitly `null`; `path` is the canonical book
target and `relatedPaths` includes the canonical generated-secret target and, when retained-stage
facts are established, both retained stages. `details.operation` is exactly `backup-book`,
`restore-book`, or `rekey-book`. `details.pairPublication` contains distinct `bookTarget` and
`generatedSecretTarget` objects, each with canonical `path` and strongest `state`:
`not-attempted`, `outcome-uncertain`, `published-durability-unconfirmed`, or
`published-durable`. JSON always includes nullable `recoveryRecordState`; it is
`durably-retained` or `durability-unconfirmed` exactly when both member states are
`not-attempted`, otherwise `null`. JSON also always includes nullable
`pairPublicationRetention`. When non-null, its
`bookPublication.{path,retainedStage}` and
`generatedSecretPublication.{path,retainedStage}` facts bind exactly to the corresponding reported
final member paths. `null` means that FinGrind established no authoritative pair-stage fact; it
never authorizes cleanup, replacement, or a fresh retry.

For verified operation-bound pair evidence, the only recovery action is to preserve both reported
final paths and rerun the exact same command with its complete original source, target, and secret
inputs. When `recoveryRecordState` is non-null, preserve FinGrind's recovery material too.
FinGrind resumes only derived stages named by that owner record. Callers must not
rename, overwrite, delete, replace, substitute, recreate, or otherwise manually clean pair
evidence or either final member, and must not start a fresh pair. A recovered rekey verifies the
generated-key pair before it attempts any prior-key access.

`protected-book-pair-publication-evidence-blocked` is deliberately different. It reports both
members with `state: "unestablished"` and `recoveryRecordState: null`: retained evidence exists,
but it cannot establish a safe final-member state or a recoverable operation. Its
always-present nullable `pairPublicationRetention` is `null` when no authoritative pair-stage
fact is safe to report; that absence never authorizes cleanup. Preserve all reported paths and
investigate independently; do not rerun or reconstruct any workflow from the diagnostic.

Restore is also staged external-artifact work:

1. Split the artifact, verify the snapshot's internal chain through sourceOrder, check its head,
   bookId, snapshot digest, and manifest BACKUP quorum. A surviving source-book acknowledgement is
   not required.
2. Resolve RESTORE as of sourceOrder.
3. Decrypt the snapshot under its backup key and re-encrypt a staged destination under the
   destination key. Inside that destination append restore-book at sourceOrder plus one with
   previousHead equal to sourceOperationHead and the restore.provenance effect record.
4. Fsync the staged destination; create it atomically without replacement; fsync the destination
   directory. Collision is artifact-already-exists with exit 7.

Restore preserves bookId but is no longer byte-for-byte the source artifact. It creates a
restoration-derived continuation, not proof of a concurrent original fork. Restore authorization
is historical, as of sourceOrder: a later-revoked credential can create a valid restoration-derived
branch, and version 1 has no external current recovery authority. The no-clobber primitive and
directory durability must be tested on macOS aarch64 and x86_64, Linux x86_64 and aarch64, and
Windows x86_64.

## Receipts And Anchors

~~~text
receiptPayload =
  "FGATTRC1"
  || receiptVersion(u8 = 01)
  || bookId(uuid)
  || operationOrder(u64)
  || operationHead(hash)
  || receiptTimestamp(instant)
  || algorithmId(algorithm-id = "ed25519")

receiptEnvelope = envelope(receiptPayload)
~~~

The version-one receipt accepts at most 64 signature entries and at most 7,267 encoded bytes.
`verify-receipt` resolves and classifies an existing regular receipt location before reading it,
then performs one bounded no-follow read from that same canonical location. A larger selected
artifact is `receipt-artifact-invalid`, never an unbounded input allocation. The receipt quorum
resolves ANCHOR as of operationOrder. Receipt timestamps are signer-asserted until a future
counter-signature format. Export is non-mutating, uses the same atomic no-clobber publication
rule, and warns when its output remains inside the book's trust boundary. Its successful artifact
has `artifacts[].{format,path,retainedStage}`. A failure with stage evidence exposes top-level
`retainedStage`; an indeterminate no-replace link reports its candidate in
`artifact-publication-outcome-uncertain`, while a returned link with unconfirmed directory
durability reports `details.publishedArtifact.{path,retainedStage}`. It never invites an unsafe
replacement retry or a stage-cleanup action.

Both successful receipt commands report the resolved canonical physical receipt path, not the
caller spelling. Export first scans every lexical component from the root through its output parent
without following links and refuses any symbolic-link or non-directory component before it stages
and creates the final link. Verification carries the exact canonical source it read after resolving
the selected regular file. Consequently relative spelling, dot segments, or later retargeting
cannot make JSON, text, or `artifacts[]` identify a different receipt from the one FinGrind
published or verified.

verify-receipt is non-mutating. It verifies the receipt envelope, finds the referenced book and
operation order, requires equal bookId and operationHead, and evaluates the receipt's ANCHOR quorum
as of that order. A valid receipt retained beside the book reports the valid-result finding
receipt-not-independent.

Receipt independence is determined from the resolved selected location, not path spelling.
Relative paths, dot segments, and intermediate directory symlinks cannot relabel a receipt beneath
the book's trust boundary as independent after its contents are read. An absent or non-regular
selected artifact is `receipt-artifact-invalid`; an I/O failure while resolving or reading a
regular selected artifact is `storage-runtime-failure`. If the book-parent location cannot be resolved for
the retention comparison, FinGrind conservatively reports `receipt-not-independent`.

## `Receipt Result Types`

`AttestationReceipt` is the immutable off-chain anchor envelope. `AttestationReceiptFinding` and
`AttestationReceiptRetention` classify its verification and independence status, while
`AttestationReceiptVerificationResult` carries the verified anchor outcome. The public
`ExportAttestationReceiptResult` has three closed outcomes: `Exported` reports no-clobber receipt
publication, `AuthorizationRejected` reports the exact live-head authorization failure before any
receipt staging or publication begins, and `VerificationRejected` reports an exact source-book
verification failure before credential loading, receipt staging, or publication begins.
`VerifyAttestationReceiptResult.Valid` publishes the complete verified anchor tuple: `bookId` and
the nested `receiptAttestationAnchor` object containing `operationOrder` and the
64-lowercase-hex `operationHead` that the selected receipt authenticated against the selected
chain. It also reports the resolved canonical physical receipt path: JSON publishes that absolute
path and text uses its standard redacted hint with canonical `Attestation order` and
`Attestation head` rows. Successful receipt export uses the same resolved path and
`receiptAttestationAnchor` object.
All remain non-mutating: neither command appends evidence or changes a book's head.
An insufficient otherwise-readable signing set, for example, returns the rejected envelope code
`attestation-quorum-below` with exit code `2`, never `internal-error`, and leaves no receipt file.
A structurally invalid receipt publishes the exact closed `AttestationVerificationFailure` code in
a `structural-invalid` rejected envelope. An absent, non-regular, oversized, or raw-malformed
selected receipt artifact uses `receipt-artifact-invalid`; an I/O failure while resolving or
reading a regular selected file is a `storage-runtime-failure`. Once raw bytes decode, receipt version, tuple, signature, quorum, and
underlying-chain failures retain their own exact `attestation-*` code.

| Verifier holds | Detects | Does not detect |
|:--|:--|:--|
| book only | interior alteration, reordering, signatures, and authorization failures | truncation, rollback, or fork |
| retained receipt at K | rollback, truncation, or alteration through K | a fork strictly after K |
| latest known head | rollback or truncation through that head | a fork strictly after that head |
| append-only witness with mandatory submission, gossip, and consistency checks | equivocation among submitted and observed heads when both branches are revealed | a never-revealed branch |

## Artifact Golden Vectors

All vector private seeds are public fixtures only, never production credentials. An encoder must
reproduce every declared payload, envelope, length, and digest byte-for-byte. A verifier must
return the listed exact result without falling through to a generic failure.

### V-MANIFEST-02: Complete Two-Principal Backup Envelope

~~~text
principalA       = 102132435465768798a9babcbddceeff
seedA            = 000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f
keyA             = a050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a5
principalB       = 112233445566778899aabbccddeeff00
seedB            = 202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f
spkiB            = 302a300506032b657003210029acbae141bccaf0b22e1a94d34d0bc7361e526d0bfe12c89794bc9322966dd7
keyB             = 824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ecee6
payload          = 4647415454424d310100112233445566778899aabbccddeeffffeeddccbbaa99887766554433221100000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f0765643235353139
envelope         = 4647415454424d310100112233445566778899aabbccddeeffffeeddccbbaa99887766554433221100000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f07656432353531390002112233445566778899aabbccddeeff00824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ecee6fe5c371ee312e047907cb70c3f7f93d0f187412869138f58287a8ff8662eb69021f9163e470f3230e89109128204088abe5c5520460b514547ed002c12efa004102132435465768798a9babcbddceeffa050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a59a8259fa79252defc53e7bd64215f5b15e63ec4d16ef5cb3377762c2134371d4194ab61e929e87068475a9ad5e2b19829f16c32eb8f2f2be0721c219e6372804
head             = c3a03b2006e080726454b60ace100df0f9e4e78cdf2154b0454503794c830c69
~~~

payload is 121 bytes and envelope is 347 bytes. keyB precedes keyA because raw keyB sorts first.
This vector proves only the standalone BACKUP envelope. It has no snapshot or chain resource and
must not be presented as a successful full-manifest artifact fixture.

### V-RECEIPT-02: Complete Two-Principal Anchor Envelope

~~~text
payload  = 46474154545243310100112233445566778899aabbccddeeff000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213323032362d30372d31375430343a30303a30302e3030305a0765643235353139
envelope = 46474154545243310100112233445566778899aabbccddeeff000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213323032362d30372d31375430343a30303a30302e3030305a07656432353531390002112233445566778899aabbccddeeff00824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ecee68f69835573aa8fe7afb8456eca706eb32700b4a19faf7fb544e8f9e55e49393bafa0316be4dd0a01362c2650df94e37ca857a994aac46a869f33c5d8a788320b102132435465768798a9babcbddceeffa050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a556cf223436c6e05b65040e26eb5674686e575846c4f4b78ff7645a7bfb2d5dddfeb0c7b76e67d4b2557a45c5499a1c890192d4daa2840b6b682da7be5cdff20e
head     = 42549e39bdb60205d16082d6e557c4c9d12e000a87b40f0974b2d82f62f3d0dc
~~~

The principals, keys, and seeds are exactly V-MANIFEST-02. payload is 97 bytes and envelope is
323 bytes. This vector proves only the standalone ANCHOR envelope. It has no protected-book chain
resource and must not be presented as a successful full-receipt fixture.

### V-CONTAINER-01: Complete Parser And Digest Artifact

This is a container-framing vector, not a SQLite-book verifier vector. Its 16-byte synthetic
snapshot is intentionally not a valid FinGrind book.

~~~text
snapshot       = 000102030405060708090a0b0c0d0e0f
snapshotDigest = be45cb2605bf36bebde684841a28f0fd43c69850a3dce5fedba69928ee3a8991
trailer        = 46474154424d46310100000000000000100000015b
containerDigest= 3b0fc99b3916dadebfdfa6babcff83afdac8d23b861a4a4e5c43d9e386d9d6ff
~~~

Its manifest uses V-MANIFEST-02 signers, bookId and source head; backupId is
00112233445566778899aabbccddeeff. Its payload deliberately has the synthetic snapshot digest,
so it is distinct from V-MANIFEST-02 and cannot prove a successful full-manifest verification.
Its full 384-byte container is:

~~~text
container = 000102030405060708090a0b0c0d0e0f4647415454424d310100112233445566778899aabbccddeeff00112233445566778899aabbccddeeff000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213be45cb2605bf36bebde684841a28f0fd43c69850a3dce5fedba69928ee3a899107656432353531390002112233445566778899aabbccddeeff00824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ecee67653ae182cf8e3eb9cfbfb479a11ac87effa34ea3b7deafbec65ca7a29fd4993a93f66ef8cd42fac7d2f3cef70f54cbe3f8a359c89ee3ebaa5e5397efce88406102132435465768798a9babcbddceeffa050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a5555760252105dfdd5f3a45358581f7ede854f5c8ed7e156ee80a488a67c0da8c28a5c85a16d12d8d415448f8cfe6ee4558566a157ec51f97af4f22b4d5d45c0d46474154424d46310100000000000000100000015b
~~~

## Related Fixture Sources

[DOC_02_VerifiableOperationAttestationCorpus.md](./DOC_02_VerifiableOperationAttestationCorpus.md)
distinguishes these standalone envelope and parser vectors from complete B-05 manifest artifacts
and B-11 receipt/book resources. V-CONTAINER-01 remains parser and digest conformance only; the
complete B-05 artifact owns the N-14 manifest-binding negatives. Those complete sources add no
semantic facts beyond their published literals.
