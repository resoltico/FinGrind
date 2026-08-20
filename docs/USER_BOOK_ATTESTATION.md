---
afad: "5.0.1"
version: "0.63.0"
domain: USER_BOOK_ATTESTATION
updated: "2026-08-20"
route:
  keywords: [fingrind, book-attestation, ed25519, founder, enroll-key, rollover-key, revoke-key, alter-policy, verify-book, attestation-review, receipt, backup, restore, rekey, source-artifact-identity-duplicated, source-artifact-identity-changed, pair-targets-conflict, target-owner-only-required, protected-book-pair-publication-evidence-blocked]
  questions: ["how does fingrind attest a book mutation", "how do I manage attestation credentials and policy", "how do I verify a fingrind book", "how do I retain and verify an attestation receipt", "how do protected-book backup and restore targets establish distinct identity", "why does FinGrind reject duplicate maintenance source artifacts"]
---

# Protected-Book Attestation

**Purpose**: Operate the immutable authorization evidence retained with every FinGrind protected-book mutation.
**Prerequisites**: A FinGrind protocol-58 / format-57 binary, one book passphrase source, and an authorized founder or operator credential where a command requires signing.

## What The Attestation Proves

Every accepted mutation is one ordered immutable operation with canonical request and committed-effect
preimages, an Ed25519 authorization envelope, and a SHA-256 operation head. The protected-book
transaction commits the accounting effect and the evidence together. FinGrind verifies the chain
from genesis rather than trusting mutable audit rows, reports, or rendered output.

Operation and receipt timestamps are recorded in UTC at millisecond precision. FinGrind
canonicalizes the runtime clock value before it becomes a durable accounting fact, attestation
preimage, or receipt payload, so every signed artifact carries an exact canonical time.

This proves that the book-recognized quorum authorized the recorded operation at its historical
position. It does not prove a person's real-world identity, an external event's truth, or events
that were never entered. An independently retained receipt can reveal rollback, truncation, or a
changed chain before its recorded head; it cannot reveal a fork that no independent observer sees.

Format 57 is a hard break. Non-current protected-book formats, whether older or newer, are
rejected. There is no reader mode, migration, alias, or compatibility layer.

## Founder And Operator Credentials

`open-book` requires one explicit custody selection and one through five aligned founder credential
triplets. Repeat each founder option in matching order:

```bash
fingrind open-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" \
  --book-template-id OWNER_MANAGED_SERVICE \
  --accounting-basis CASH \
  --functional-currency EUR \
  --fiscal-year-start 01-01 \
  --book-start-effective-date 2026-01-01 \
  --attestation-custodian file-pkcs8 \
  --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-founder-key-file ./secrets/founder.fgatk \
  --attestation-founder-passphrase-file ./secrets/founder.passphrase
```

At genesis, a missing founder key path is created once and never overwritten; an existing path is
opened as the founder credential. Every command that creates or opens private attestation key
material requires an explicit `--attestation-custodian file-pkcs8` selection. Later signing
commands also require an existing enrolled credential: `--attestation-principal-id`,
`--attestation-key-file`, and `--attestation-passphrase-file`. The only shipped custodian is
`file-pkcs8`; an explicit unsupported selection, such as `pkcs11`, is refused as
`custodian-not-supported` with exit code 2. FinGrind never falls back to file custody. Do not
reuse a book key file as an attestation key file, copy an attestation passphrase into a command
line, or store either secret alongside an exported receipt. Every later signing command accepts
one through 64 aligned credential triplets under the selected custody, matching the full exact-quorum range that a reachable
policy may require. A policy therefore never leaves its own public signing, backup, restore,
rekey, receipt, or policy-repair path unreachable.

Before `open-book` creates any missing founder key, it validates every founder key that already
exists. It then creates the missing keys in the caller's declared founder order. A successful
response exposes only newly generated founder keys: JSON places their canonical paths in
`artifacts[]` with format `attestation-key-file`, while text prints `New founder key file` rows.
An existing founder key is never restated as a generated artifact. If a later preparation step
stops after one or more founder-key transactions complete, `open-book` returns exit 4
`open-book-publication-progress`: its ordered
`details.publishedFounderKeyArtifacts[].{format,path,publicationTransaction}` list contains only
final paths and completed transaction evidence, and nullable
`details.incompleteFounderKeyPublication` contains only a final candidate and an ID-only
transaction result. If book-preparation artifacts are retained, the distinct exit-4
`open-book-preparation-artifacts-retained` response lists
`details.retainedArtifacts[].{role,path,retainedStage}`. Preserve every reported path and never
manually alter private output directories. If initialization reported its book facts but SQLite
could not confirm durable completion after initialization COMMIT or session shutdown, it returns exit 4
`open-book-completion-uncertain`, including the reported identity, trust root, genesis commit,
newly published founder keys, and possible book file, journal, WAL, and shared-memory paths. Do
not retry that `--book-file`; inspect and verify it first.

`execute-plan` is deliberately conditional. After FinGrind reads the request, a plan with at least
one mutating step requires one through 64 complete aligned credential triplets under the explicit
custody selection. A query-only or assertion-only plan forbids the same flags and never opens a
credential. Do not send a valid credential tuple speculatively: the command refuses it rather than
silently discarding it, with `attestation-credentials-not-allowed` and exit `1` after it decodes
the request but before plan execution. A partial tuple remains a parser-level `invalid-request`,
before the plan's business steps run.

Every successful `execute-plan` result makes the append outcome explicit. `attestationDisposition`
is `appended` exactly when `attestationCommit` carries the final aggregate operation; it is
`read-only` when the credential-free dedicated read-only execution completed; and it is
`no-durable-child-mutation` when the signed mutation-capable execution completed with no durable
child, such as an all-idempotent replay. The latter two carry `attestationCommit: null`; rejected
and assertion-failed plan payloads carry both fields explicitly as `null`.

Before generating a standalone credential, create its parent directory yourself. The target must
sit beneath an existing real directory, never a symbolic link; FinGrind does not create credential
parent directories or silently redirect secret output. The final credential file is no-clobber and
owner-only, while the passphrase file remains a separately protected local secret. FinGrind creates
an atomic owner-only (`0600` on POSIX) private stage, writes and forces the credential bytes, then
publishes the absent final name without replacing it. The stage remains immutable evidence. A
successful artifact is `artifacts[].{format,path,retainedStage}`; FinGrind never deletes, replaces,
or reuses that stage. If the no-replace link itself throws, it reports
`artifact-publication-outcome-uncertain` with `details.{candidateArtifact,retainedStage}` and the
same top-level `retainedStage` when applicable; that candidate is not a claim that a credential was
created. A returned link whose directory durability cannot be confirmed reports
`artifact-publication-durability-uncertain` with top-level `retainedStage` and
`details.publishedArtifact.{path,retainedStage}`. Preserve the final and evidence and do not retry
that no-clobber target.

The file-backed credential format is public: it stores an Ed25519 PKCS#8 private key encrypted
with PBKDF2-HMAC-SHA-256 (600,000 iterations, a fresh 16-byte salt) and AES-256-GCM (a fresh
12-byte IV and a 128-bit tag). Its exact version-02 `file-pkcs8` byte grammar, including the
plaintext public-SPKI metadata and 1 KiB bound, is in
[the encoding reference](./DOC_02_VerifiableOperationAttestationEncoding.md#file-pkcs8-credential-container).
A passphrase file must be valid UTF-8, nonempty after one optional trailing line ending, and at
most 4,096 bytes. Private-key material, passphrases, and local key paths are not attestation
payloads and must not be put in request JSON, logs, manifests, receipts, or support tickets.

## Credential And Policy Lifecycle

Credential provisioning is an off-book custody step. First prepare a distinct owner-only,
nonempty UTF-8 passphrase file through your local secret-management procedure, then let FinGrind
create the no-clobber encrypted credential and emit the exact public identity that an enrollment
or rollover request needs:

```bash
fingrind generate-attestation-key-file \
  --attestation-custodian file-pkcs8 \
  --new-attestation-key-file ./secrets/operator.fgatk \
  --attestation-passphrase-file ./secrets/operator.passphrase
```

The successful payload includes canonical unpadded base64url `credentialSpki` and its derived
lowercase-hex `keyId`; it never includes the passphrase or private key. To recover the public
identity of an existing founder or operator credential for rollover or revocation, use:

```bash
fingrind inspect-attestation-key-file \
  --attestation-custodian file-pkcs8 \
  --attestation-key-file ./secrets/founder.fgatk
```

Lifecycle request documents carry only that public DER SubjectPublicKeyInfo (SPKI) as canonical
unpadded base64url in `credentialSpki`; they never carry a local credential path, a passphrase, a
private key, a caller-chosen key ID, or an encrypted-key-file payload. FinGrind derives the key ID
as SHA-256 of the SPKI and signs the resulting public binding facts.

All four lifecycle request documents are emitted directly by the binary. Use
`print-request-template enroll-key`, `print-request-template rollover-key`,
`print-request-template revoke-key`, or `print-request-template alter-policy`; the full JSON help
for each command carries that same `attestationTemplate` under `requestFile`. Template
UUIDs are canonical RFC 4122 spellings and must be replaced with the operation's real identities.
`credentialPurpose` is closed to the lowercase values `operator` and `system`; enrollment and
rollover templates publish `operator` before any request is submitted.

`print-request-template attestation-review` emits the distinct non-persisted
`--attestation-review-file` scaffold. Its full-detail JSON help also publishes the scaffold as
`requestFile.attestationTemplate`.

`enroll-key` binds a new credential to a principal. It does not grant a capability: use
`alter-policy` to grant the principal the intended capability after enrollment. For example:

```json
{
  "principalId": "01234567-89ab-4cde-8fab-0123456789ab",
  "credentialSpki": "MCowBQYDK2VwAyEAJYpWgBK4pHaKkIRKs9p8_6B01sG0SuOXLjI69Q5mGlI",
  "credentialPurpose": "operator"
}
```

```bash
fingrind enroll-key \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --request-file ./enroll-key.json \
  --attestation-custodian file-pkcs8 \
  --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase
```

`rollover-key` binds a different new credential to the same principal and names the predecessor's
public SPKI in `predecessorCredentialSpki`. Its one signed operation records both the replacement
binding and a terminal `superseded` retirement of that predecessor, so the predecessor cannot sign
the next operation. Use `revoke-key`, with the credential's `credentialSpki` and an optional
non-blank reason, for a separately authorized security withdrawal. Supersession and revocation
are both final, auditable, and distinct states.

`alter-policy` changes only future policy. Its optional `policyRules`, `capabilityGrants`, and
`systemWorkflowPolicies` arrays must contain at least one item in total. Each rule names a
closed lowercase capability token and a quorum from 1 through 64. Each grant uses `grant` or
`revoke`. A system workflow is active only when its exact account configuration is supplied;
an interim-result sweep omits capital and retained-result accounts, while a fiscal-year close
supplies both. A single policy request may not repeat a capability, principal-capability pair, or
workflow ID. All four commands are ordinary attested mutations: their signer quorum, credential
purpose, and capability are evaluated against authority reconstructed through the preceding live
book head, and a successful change first governs the next operation.

An unauthorized protected-book mutation is a rejected envelope with the exact attestation code,
such as `attestation-key-not-enrolled`, `attestation-quorum-below`, or
`attestation-capability-invalid`, and exit code 2. Its message and repair hint name that exact
cause; FinGrind never substitutes a generic signer explanation. A backup acknowledgement that
fails after publication also preserves the published-artifact fact before naming the exact cause
and retry action. None of these conditions is reported as a storage failure.

## Verify And Review

Use `verify-book` before relying on a book copied from another system or a recovered artifact:

```bash
fingrind verify-book \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --attestation-review-file ./reviews/acme-compromise.json \
  --require-clean-attestation
```

Structural verification returns the first deterministic chain break. `--require-clean-attestation`
also returns a rejected `attestation-review-required` envelope for a structurally valid chain that
has compromise-review findings, with exit code 2 and no success payload. Its rejected `details`
retain `bookId`, `verifiedAttestationHead`, `previousHead`, and `reviewFindings` so an operator can
investigate without treating the rejected outcome as a valid clean result. On success,
`verify-book` publishes the full verified registry snapshot as `registry`: every credential binding
and its active, superseded, or revoked state, each effective capability quorum with eligible
principal counts, principal capability grants, and system-workflow policies. Its success payload
also publishes `bookId`, nested `verifiedAttestationHead` with `operationOrder` and
`operationHead`, and `previousHead`. `previousHead` is the signed predecessor of the reported
current head (64 zero hexadecimal characters at genesis), not a mutable backlink. Text uses
canonical `Attestation order`, `Attestation head`, and `Previous attestation head` rows. This is
read-only verification evidence reconstructed from the immutable chain, not a mutable
administration view. `attestation-review` returns the same non-persisted findings and the same
`bookId` / `verifiedAttestationHead` shape without changing the book:

```bash
fingrind attestation-review \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --attestation-review-file ./reviews/acme-compromise.json
```

The review file is strict JSON and does not alter the book:

```json
{
  "compromiseReviews": [
    {
      "credentialKeyId": "8f0e9c3c96c8188db78dc9de35290a86f8d3a5c0b9e9d1d2a0e3fd48c6b7a901",
      "firstAffectedOrder": "41",
      "lastAffectedOrder": "57"
    }
  ]
}
```

Every attestation order is a canonical unsigned-64 decimal string, and every attestation head or
credential key ID is 64 lowercase hexadecimal characters. The interval is inclusive; a bounded
`lastAffectedOrder` cannot exceed the verified head, while an omitted or `null`
`lastAffectedOrder` runs through it. FinGrind rejects unknown fields, duplicate JSON object keys,
and duplicate or overlapping intervals for one credential as `invalid-request` on
`--attestation-review-file`. A syntactically valid declaration whose `firstAffectedOrder` or
bounded `lastAffectedOrder` is later than the authenticated head instead returns the
`attestation-review-window-exceeds-head` error with its credential key ID, both declared bounds,
and `verifiedHeadOrder`; it is not a verification finding. Both that error and a finding return
the declaration's `lastAffectedOrder` field explicitly: JSON uses `null` for an open-ended
interval rather than omitting the field or using a sentinel string.

Text review output groups findings by the complete review declaration: credential key ID and its
inclusive review window appear once, followed by every affected operation order. Consecutive
orders may render as an exact range, but gaps are never elided. JSON deliberately remains one
flat finding object per affected operation so automation can filter and join it without parsing
the text layout.

When `verify-book`, `attestation-review`, or source-book verification before receipt export finds
invalid immutable evidence, it returns a rejected envelope with exit code `2`, category
`structural-invalid`, and one exact attestation failure code. Its message names the surface and
the historical cause; its hint preserves the invalid evidence and directs recovery from a verified
independently retained backup. `verify-receipt` has the same exact-code behavior, but its hint
first preserves the selected receipt and directs comparison with a verified protected book. These
expected verification refusals never fall back to `internal-error` or describe historical
authority as a live-head admission decision.

## Backups, Restores, And Receipts

`backup-book`, `restore-book`, and `rekey-book` validate every existing caller-selected
protected-book or book-key artifact parent without changing it: the parent and its resolved
ancestry must already be a real, private owner-only, non-mutable directory. Only an absent
final-target parent may be created: FinGrind preflights its creation ancestry, atomically creates
it with POSIX `0700`, and postvalidates the canonical parent and full ancestry. A lifecycle source
parent must already exist. ACL-only final-target creation fails closed as
`artifact-path-invalid` with
`details.pathFailure: "atomic-owner-only-protocol-file-creation-unsupported"`; FinGrind never
creates a readable parent and repairs its ACL. A non-directory component is refused. Before
canonicalization, FinGrind scans every lexical component from the root through the selected parent
without following links and refuses any symbolic-link or non-directory component, including a
direct-parent alias; a leaf symlink is always refused. A lifecycle mutation source leaf must
already be a regular non-symlink file before FinGrind prepares any final-target parent. An
existing protected-book source or FinGrind recovery artifact that must be inspected must be
owner-only; otherwise FinGrind returns `artifact-path-invalid` with
`details.pathFailure: "target-owner-only-required"`. A caller-owned ordinary no-clobber output
leaf is not inspected as a FinGrind artifact and instead receives that operation's exact
occupied-target rejection.

The complete selected source set must contain independent physical files. That includes the live
book or backup artifact and every selected file-backed key source. If a later source role resolves
to a hard link or other physical alias of an earlier source, FinGrind returns exit-`6`
`artifact-path-invalid` with
`details.pathFailure: "source-artifact-identity-duplicated"` before it admits a destination,
stages data, or mutates a book. Select distinct source artifacts rather than relying on different
path spellings.

After FinGrind holds the complete source set, it revalidates every source against the exact
physical identity it locked before it admits a destination. A replacement or substitution returns
exit-`6` `artifact-path-invalid` with
`details.pathFailure: "source-artifact-identity-changed"`. Keep every selected source stable,
restore the trustworthy intended source if it changed, then rerun the complete maintenance
command.

Initial pair final-target identity is admitted after maintenance has admitted every selected parent,
including any permitted missing-parent creation, and before it creates a final target, stage,
reservation, claim, or pair-evidence artifact. When both final targets already exist, FinGrind
uses `Files.isSameFile` to establish identity; one physical object is `pair-targets-conflict`
(exit `2`). For two absent leaves in one physical parent, exact raw leaf equality or a collision
after canonical Unicode decomposition plus root-locale case mapping is the same rejection. Other
distinct leaves, including Unicode, spaces, punctuation, and leading dashes, remain valid targets
when the filesystem admits them.
Lifecycle source validation and final-parent admission precede this identity check, so an eligible
missing private parent may remain. The initial refusal creates no final target, retained
lease-control file, stage, capability witness, reservation, claim, or pair-recovery evidence. See
[USER_REJECTIONS.md](./USER_REJECTIONS.md#protected-book-pair-target-admission) for the typed
diagnostic contract.

Before `backup-book`, `restore-book`, or `rekey-book` stages, probes, reserves, or mutates a
candidate pair, FinGrind acquires and scans the full source-and-target workflow scope for
operation-owned evidence. That record binds the operation, both targets, its stable
operation-specific facts, and only its owner-recorded derived stages. A verified unresolved record for another full workflow returns the exit-`7`,
`rejected`, `precondition` response `maintenance-recovery-pending`. Its non-null JSON
`details.{recoveryOperation,bookTarget,generatedSecretTarget}` names the canonical operation and
canonical absolute target pair; text renders `Recovery operation`, `Book target`, and `Generated
secret target`. Restart that named command with its admitted operation-specific inputs: backup and
restore use their original verified sources, while rekey uses its final pair and proves the final
signed rekey state. Those diagnostics do not reconstruct a backup source, backup ID, credentials, or secret
material and never authorize a partial retry. Never rename, overwrite, delete, recreate, or
manually clean recovery evidence.

Malformed, legacy, incomplete, or internally inconsistent evidence cannot establish a safe
operation. It fails closed as the exit-`4`
`protected-book-pair-publication-evidence-blocked` error, not
`maintenance-recovery-pending`; preserve it for independent investigation.

`backup-book` publishes an encrypted backup pair only to absent destinations, then appends the
matching `backup-created` acknowledgement to the live chain. Supply a stable UUID with `--backup-id`.
If publication succeeds but acknowledgement is interrupted, rerun the exact same command with the
same book, backup paths, credentials, and backup ID; FinGrind resumes only that exact tuple.
If publication succeeds but the live-head authorization check refuses the acknowledgement, the
command returns the exact `attestation-*` rejected code with exit code `2`. The published pair is
preserved and is not reported as acknowledgement-pending; retain it, correct the signing
credentials or policy, and rerun the same exact tuple. Exit code `4` acknowledgement-pending is
reserved for an operational interruption whose authorization result was not determined.
Every published backup response reports `pairPublicationCompletion`: `published` for a newly
durable pair, `recovered` after exact owner-context verification of a completed transaction, or
`already-published` when an acknowledgement retry verified a complete existing pair without
publishing it again. This is independent of the acknowledgement state and whether the retry
appended an acknowledgement commit. Every `published` or `recovered` result has mandatory
final-only `pairPublication` facts: `bookPublication.path`, `generatedSecretPublication.path`,
and one completed ID-only `publicationTransaction`. `already-published` is the only null case,
because FinGrind has no transaction proof for an external or older pair.

If `backup-book`, `restore-book`, or `rekey-book` cannot establish completion of its matching
publication transaction, it returns the exit-`4` `publication-transaction-incomplete` error.
Its details carry the final candidate and ID-only transaction result. Preserve that candidate and
rerun only the exact same operation with its admitted operation-specific inputs.
Never rename, overwrite, delete, recreate, or manually clean any final member; do not start a
fresh pair.

`protected-book-pair-publication-evidence-blocked` is different: both member states are
`unestablished` because legacy, malformed, or internally inconsistent sidecar evidence cannot
establish a safe final state or a recoverable operation. It reports no private stage. Preserve all
reported paths and investigate independently; do not rerun or reconstruct the workflow.

`restore-book` verifies the backup's internal chain and manifest before restoring it to an absent
destination and appending a signed `restore-book` continuation. Restore uses the backup key to
verify the artifact and creates a new live-book key; it does not need an acknowledgement in the
source book. To prove the restored continuation, record the source snapshot's `verify-book`
`bookId` and `verifiedAttestationHead` before backup. The destination's `verify-book` must retain
that `bookId`, report a `verifiedAttestationHead.operationOrder` one greater, and report the source
`verifiedAttestationHead.operationHead` as `previousHead`; its
`verifiedAttestationHead.operationHead` must match the restore response's `attestationCommit`
head.
A later source `backup-created` acknowledgement is a distinct operation and is not the restore
predecessor.

Restore and rekey success likewise report `pairPublicationCompletion`: `published` for a new
durable pair or `recovered` for exact owner-context verification without a second maintenance
mutation. Both always carry non-null final-only `pairPublication` with both final paths and one
completed ID-only transaction. For a recovered rekey, FinGrind first verifies the generated-key
pair before attempting any access through the prior key.

Retain receipts outside the book and its backup storage boundary. Receipt export is no-clobber and
does not mutate the book. Independence uses resolved filesystem locations: a relative spelling,
dot segment, or intermediate directory symlink cannot make a receipt beneath that boundary
independent. If FinGrind cannot establish the resolved boundary, it conservatively reports
`receipt-not-independent`. Its successful JSON envelope publishes the receipt through `artifacts[]`
with `format: "attestation-receipt-v1"` as well as its payload path:

```bash
fingrind export-attestation-receipt \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --receipt-file ./receipts/acme.fgar \
  --attestation-custodian file-pkcs8 \
  --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-key-file ./secrets/founder.fgatk \
  --attestation-passphrase-file ./secrets/founder.passphrase

fingrind verify-receipt \
  --book-file ./books/acme.sqlite \
  --book-key-file ./secrets/acme.book-key \
  --receipt-file ./receipts/acme.fgar
```

Receipt export evaluates the live current-head `anchor` policy reconstructed from immutable
evidence. A valid but insufficient, unenrolled, revoked, or otherwise unauthorized signer set
returns the exact attestation rejection code, such as `attestation-quorum-below`, with exit code
`2`; it never becomes a storage or internal error and no receipt artifact is created.

Both successful receipt surfaces publish the complete receipt anchor in their successful
representations: JSON contains the resolved canonical physical `receiptFile`, `bookId`, and the
nested `receiptAttestationAnchor` object with `operationOrder` and the exact 64-lowercase-hex
`operationHead`; text uses that location's normal redacted receipt-file hint and canonical
`Attestation order` and `Attestation head` rows. Receipt export additionally publishes its warnings
and one
`attestation-receipt-v1` artifact, while receipt verification reports its findings. `verify-receipt`
reports `receipt-artifact-invalid` when the selected artifact is absent, non-regular, oversized,
selected through a symbolic-link component, or its raw bytes cannot be decoded as a receipt artifact. An I/O failure while reading a regular
selected receipt, or while resolving it, is `storage-runtime-failure`. Once decoded, receipt version, tuple,
signature, quorum, and chain failures
retain their exact codes, such as `attestation-unsupported-version`,
`attestation-receipt-invalid`, `attestation-signature-invalid`, or
`attestation-quorum-below`; its text and JSON response preserve the selected receipt before
directing comparison with a verified protected book.

For receipt export, choose a real output directory: before canonicalization, FinGrind scans every
lexical component from the root through the `--receipt-file` parent without following links and
refuses any symbolic-link or non-directory component, including a direct-parent alias. Success
reports the canonical physical receipt location.

For the canonical binary encoding and authorization policy, see
[DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md). For
verification rules and structural failures, see
[DOC_02_VerifiableOperationAttestationVerification.md](./DOC_02_VerifiableOperationAttestationVerification.md).
Artifact invariants are in
[DOC_02_VerifiableOperationAttestationArtifacts.md](./DOC_02_VerifiableOperationAttestationArtifacts.md).
