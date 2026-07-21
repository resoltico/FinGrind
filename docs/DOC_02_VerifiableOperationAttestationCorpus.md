---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION_CORPUS
updated: "2026-07-21"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestationStaticCorpus", "AttestationStaticCorpusVectors", "AttestationStaticArtifactCorpusVectors"]
route:
  keywords: [verifiable-operation-attestation, static-corpus, golden-vectors, fixture-ledger, verifier-negative-cases, backup-artifact, live-cas]
  questions: ["which static fixtures verify FinGrind operation attestation", "how is the attestation corpus constructed", "which negative attestation vectors are required", "which artifact fixtures cover backup and restore"]
stage: "Current public protocol 32 and protected-book format 51 contract"
---

# Verifiable Operation Attestation Corpus

This is the normative fixture source for the next protected-book attestation format. It extends the
[core protocol](./DOC_02_VerifiableOperationAttestation.md), which owns the shared operation and
envelope grammar, authorization rules, error taxonomy, and verifier precedence; the
[semantic profiles](./DOC_02_VerifiableOperationAttestationProfiles.md), which own field-level
posting admission; and the [artifact protocol](./DOC_02_VerifiableOperationAttestationArtifacts.md),
which owns manifest, receipt, and container contracts.

## Required Static Book And Artifact Corpus

This is a fixture-source ledger, not a list of test ideas. A fixture contains the listed immutable
preimages, folded registry and policy facts, exact envelope bytes, and expected first result at its
declared verification scope. Every protected-book and artifact source fixes its semantic data,
keys, operation positions, and expected result. Envelope bytes, heads,
snapshot bytes, and artifact digests are derived outputs of these literal sources; the resource
records their complete bytes and any mutation as a byte offset plus replacement bytes. The core
protocol owns V-OP-01 and V-OP-02. The artifact protocol owns V-MANIFEST-02, V-RECEIPT-02, and
V-CONTAINER-01. They are standalone envelope or parser vectors unless a row expressly names a
complete book or artifact.

The complete-book, artifact, and receipt sources are committed under
`core/src/test/resources/dev/erst/fingrind/core/attestation/corpus/`. Each `source/<id>.b64` has
an adjacent SHA-256 commitment and is checked against the independently repeated test constant.
Each complete-book or complete-artifact negative has `negative/<id>.meta` with its base ID, byte
offset, replaced-byte count, and target SHA-256, plus `negative/<id>.delta.b64` with the
replacement bytes. Both source and negative target hashes are independently repeated in the test
code. The verifier tests decode those bytes directly; no encoder, signer, semantic fixture builder,
or mutation derivation constructs a Slice-4 verifier input at test time.

## Static Corpus Common Facts

All corpus fixtures use the following literal facts unless their construction row overrides one.
The values make every key, principal, time, identifier, and policy decision reproducible rather
than implementation-selected. A field not explicitly supplied here or in a construction row has
the following only permitted default: an optional field is absent; a required cli command has
sourceChannel cli; causationId is absent; and idempotencyKey is the ASCII token
fixture-<fixture-id>-<operation-order>. No other value, record, key, account, time, policy fact,
or derivation is implicit.

| Name | Exact value or construction |
|:--|:--|
| book A | bookId 00112233445566778899aabbccddeeff; identity is Acme Attestation Fixture, internal-management-bookkeeping-kernel, cash, non-statutory-internal-management, owner-managed-single-entity, owner-managed-service, functional EUR, fiscal start 01-01, book start 2026-01-01 |
| principal A (A1; “A” elsewhere) | principalId, seed, SPKI, and keyId from V-OP-01 |
| principal A rollover key (A2) | principalId is A1's principalId; seed is 606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f; SPKI and keyId are the canonical Ed25519 DER-SPKI and SHA-256 thereof; its operator-purpose principal.key-binding fact occurs only in B-03's order-6 rollover |
| principal B | principalId, seed, SPKI, and keyId from V-MANIFEST-02 |
| principal C | principalId 2233445566778899aabbccddeeff0011; seed 404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f; SPKI 302a300506032b65700321002543b92ff1095511476adc8369db6ddc933665a11978dda1404ee1066ca9559d; keyId 788de5096f8b530eef97a4015cffb7cfeb260c23795b846bf8112682a93b1101 |
| initial capability policy | post, approve, close-period, backup, and anchor have M=1; restore, rekey, enroll-key, revoke-key, and alter-policy have M=min(2, founderCount) |
| initial grants | every founder has GRANT for every listed capability; no other principal has a grant until its explicit grant record |
| standalone envelope resolvers | The V-OP-02 resolver is as-of order 42 with active, non-revoked operator-purpose A and B. Its complete POST policy ledger has M=1 at order 40 and M=2 at order 41, so the effective POST M is 2; POST GRANT exists only for A and B. It evaluates only operation-envelope checks after payload and preimage validation. B-08 and B-09 use the same shape with active A, B, and C: B-08 has BACKUP M=2 and BACKUP GRANT only for A and B; B-09 has ANCHOR M=2 and ANCHOR GRANT only for A and B. Every other capability rule and grant is absent. These are complete inputs for standalone envelope verification, never claimed operation-chain, manifest-artifact, or receipt-book resources. |
| fixture instants | genesis is 2026-12-31T03:00:00.000Z; subsequent operation n is exactly that instant plus n milliseconds |
| fixture IDs | account cash = 1000, revenue = 4000, result holding = 3000, capital = 3100, retained result = 3200; common posting = 30000000000070008000000000000001 and command = 30000000000070008000000000000002; sweep posting = 30000000000070008000000000000003 and command = 30000000000070008000000000000004; close posting = 30000000000070008000000000000005 and command = 30000000000070008000000000000006; B-03 second posting = 30000000000070008000000000000007 and command = 30000000000070008000000000000008; sweep workflow = 40000000000070008000000000000001; close workflow = 40000000000070008000000000000002; backup = ffeeddccbbaa99887766554433221100; rekey epoch = 2 |
| fixture accounts | 1000 Cash is asset/leaf; 4000 Service revenue is income/leaf; 3000 Current-year result holding, 3100 Owner capital, and 3200 Retained result are equity/leaf. Every account is active and has absent optional parent, unit, classification, and relationship fields. |
| B-02 operation positions | genesis = 0, declare 1000 = 1, declare 4000 = 2, common posting = 3. A and B are operator-purpose founder credentials in every B-02-derived fixture unless a row explicitly binds another purpose. |
| posting source | request.command has operationKind record-sale-settled, idempotencyKey fixture-sale-1, absent causationId, and sourceChannel cli; request.posting has stepOrder 0, effectiveDate 2026-07-17, postingKind standard; request.account-role has stepOrder 0, role cash-account, accountCode 1000 and role revenue-account, accountCode 4000; request.money has stepOrder 0, role gross-amount, EUR 100.00; request.evidence-document is stepOrder 0, fixture-receipt-1, cash-receipt, 2026-07-17 |
| posting effect | posting.fact CREATE uses the listed posting and command IDs, stepOrder 0, record-sale-settled, standard, sale-settled, 2026-07-17, no prior posting, fixture-sale-1, absent causation, cli; it has source-document fixture-receipt-1 and two CREATE journal.line records: lineOrder 0, 1000 debit EUR 100.00; lineOrder 1, 4000 credit EUR 100.00 |

Every genesis in the corpus has one request.command, one request.book-identity, one
request.founder for every founder, one request.policy-rule for every capability, and one
request.principal-capability-grant for every founder-capability pair. Its effect has exactly the
matching book.identity, principal.key-binding, policy.capability-rule, and
principal.capability-grant records. Every later operation uses the core protocol's request and
effect profile; absent optional fields are encoded with their mandatory absent presence byte. A
construction row that names a signer names its complete envelope signer set. All keys derive from
the stated seeds, all heads derive from the preceding exact envelope, and a resource generator may
not supply an unstated record, identifier, time, signer, policy fact, or first failure.

## Positive Fixture Sources

| ID | Exact construction trace | Expected result |
|:--|:--|:--|
| B-01 | book A genesis at order 0 with founder A as an operator-purpose credential; every initial M is 1 because founderCount is 1; envelope contains only A | valid |
| B-02 | book A genesis at 0 with operator-purpose founders A and B; set post M=2; declare accounts 1000 and 4000 at 1 and 2, then append the common posting at 3 signed by A and B | valid |
| B-03 | B-02 through 3; at 4 A1 and B enroll C with credentialPurpose operator, at 5 A1 and B retain POST M=2 and grant C POST, at 6 A1 and B add A2 by rollover from A1 with operator purpose and predecessorKeyId A1, at 7 B and C append record-sale-settled using IDs ending 07/08, idempotencyKey fixture-sale-2, sourceChannel cli, source document fixture-receipt-2, effectiveDate 2026-12-31, and the same postingKind, originKind, account roles, money role, and EUR 100.00 journal lines as the common posting, and at 8 A2 and B revoke C | valid |
| B-04 | B-02 through 3; at 4 A and B enroll C with credentialPurpose system. At 5 A and B retain CLOSE_PERIOD M=1, grant C CLOSE_PERIOD, and create active system workflow policy sweep workflow with kind interim-result-sweep and result holding 3000, plus active close workflow with kind fiscal-year-close and result holding 3000, capital 3100, and retained result 3200. At 6, 7, and 8 A and B declare accounts 3000, 3100, and 3200. At 9 C alone appends sourceChannel system interim-result-sweep naming sweep workflow for 2026-01-01 through 2026-12-30 into result holding 3000. Its derived request.posting has stepOrder 0, operationKind interim-result-sweep, postingKind period-close, and effectiveDate 2026-12-30; its posting/command IDs end 03/04; journal lines debit 4000 and credit 3000 EUR 100.00; sweepOrder 1; EUR total 100.00; and its posting link. At 10 C alone appends sourceChannel system fiscal-year-close naming close workflow for 2026-01-01 through 2026-12-31 with capital 3100, result holding 3000, and retained result 3200. Its derived request.posting has stepOrder 0, operationKind fiscal-year-close, postingKind period-close, and effectiveDate 2026-12-31; its posting/command IDs end 05/06; journal lines debit 3000 and credit 3200 EUR 100.00; closeOrder 1; and its posting link. | valid |
| B-05 | B-02 through the common posting at sourceOrder 3. `B-05.snapshot` is the fixed raw snapshot source carrying exactly that committed evidence; no rekey, VACUUM, page-size change, or intervening operation is represented. The core corpus framing is test-only and is not a persisted snapshot format: the SQLite adapter remains responsible for producing and decoding the required consistent SQLite online-backup copy under the artifact protocol. Its manifest is signed by A under BACKUP M=1. The artifact's source head is the order-3 head and its whole-container digest is the SHA-256 of that named derived resource. A appends backup-created at order 4 with the fixture backup ID and that exact tuple. | valid |
| B-06 | B-05 snapshot source, not its order-4 acknowledgement. The staged destination is exactly B-02 through order 3 and preserves book A. A and B append restore-book at order 4 with the B-05 backup ID, its derived artifact digest, sourceOrder 3, source head 3, and historicalSnapshotAuthorization true; publication uses the no-replacement protocol. | valid |
| B-07 | B-02 through order 3 with the B-05 snapshot and an A-signed BACKUP M=1 manifest, but without any backup-created operation. A and B perform the same order-4 restore as B-06. | valid |
| B-08 | the standalone BACKUP envelope resolver at sourceOrder 42 with active A, B, and C; BACKUP M=2; A and B, but not C, granted BACKUP; plus V-MANIFEST-02 signed by A and B | valid standalone envelope; not a manifest artifact |
| B-09 | the standalone ANCHOR envelope resolver at operationOrder 42 with active A, B, and C; ANCHOR M=2; A and B, but not C, granted ANCHOR; plus V-RECEIPT-02 signed by A and B | valid standalone envelope; not a receipt/book |
| B-10 | B-02 through order 3; A and B as the REKEY quorum append rekey-book at order 4 with keyEpoch 2, absent reason, and book.key-epoch DERIVE with rekeyedAt 2026-12-31T03:00:00.004Z | valid |
| B-11 | B-02 through order 3. A produces an off-chain receipt with book A, operationOrder 3, the derived order-3 operation head, receiptTimestamp 2027-01-01T00:00:00.000Z, and algorithmId ed25519; A is the exact ANCHOR M=1 signer at order 3. The resource contains the complete B-02 book and its derived receipt envelope. | valid receipt/book pair |

## Negative Fixture Sources

For N-01 through N-10, the operation, manifest, and receipt forms are separate standalone-envelope
fixtures. Each starts from the listed valid base bytes and applies exactly one mutation under its
declared complete envelope resolver; the verifier must return the stated result before considering
any later condition. These rows do not claim complete operation-chain, manifest-artifact, or
receipt-book verification. This makes the three-envelope coverage explicit rather than inferred
from a fixture name.

| ID | Base and single exact mutation | Expected result |
|:--|:--|:--|
| N-01 | V-OP-02, V-MANIFEST-02, and V-RECEIPT-02 independently: XOR the final signature byte with 01 | attestation-signature-invalid |
| N-02 | each two-principal signed base under an M=2 registry/policy: replace sigCount 0002 with 0001 and delete its second 112-byte entry | attestation-quorum-below |
| N-03 | V-OP-02, V-MANIFEST-02, and V-RECEIPT-02 independently under an M=1 resolver; no byte mutation is needed because sigCount is already 0002 | attestation-quorum-excess |
| N-04 | each two-signature base: replace the second principalId with the first principalId, leaving key IDs distinct and ascending | attestation-duplicate-principal |
| N-05 | each two-signature base: replace the second keyId with the first keyId, leaving principal IDs distinct | attestation-duplicate-key |
| N-06 | each two-signature base: swap the complete A and B envelope entries without changing sigCount | attestation-envelope-order-invalid |
| N-07 | each signed base: resolve against a registry in which the named signer binding begins at source position plus 1 | attestation-key-not-enrolled |
| N-08 | each signed base: resolve against a registry in which the named signer is revoked at source position minus 1 | attestation-key-revoked |
| N-09 | each two-principal base resolved with A, B, and active C: replace A's principalId with C's while retaining A's keyId and signature, so no duplicate principal occurs | attestation-key-principal-mismatch |
| N-10 | each signed base: use X25519 SPKI 302a300506032b656e032100000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f and keyId 6625748c7ed4ff8a552c6453609a8892c494e624a5ad97854b2161461c098e7f; retain all other position facts | attestation-key-algorithm-invalid |
| N-11 | B-02 common posting: replace previousHead with 32 zero bytes | attestation-previous-head-invalid |
| N-12a | B-01 genesis: replace A's declared SPKI with B's while retaining A's keyId | attestation-genesis-invalid |
| N-12b | B-01 genesis: remove A's sole envelope entry and set sigCount 0000 | attestation-genesis-invalid |
| N-13 | B-08's standalone envelope resolver with active C and a valid policy state: A and B, but not C, have BACKUP GRANT and M remains 2. Rebuild V-MANIFEST-02 as the two-entry raw envelope C then B: C has principalId 2233445566778899aabbccddeeff0011, keyId 788de5096f8b530eef97a4015cffb7cfeb260c23795b846bf8112682a93b1101, and signature e68bc651ab5ee607fe5f5e5a122e58477950dc37b33794c95fc5671df28d6efaf408d460b75231b175b025276fff1e92981c942ab523602d0076a3250f8d5f0e; B's 112-byte entry is unchanged. | attestation-capability-invalid |
| N-14a | B-05's complete manifest-attested artifact: XOR byte 0 of the manifest snapshotDigest with 01. | attestation-manifest-invalid |
| N-14b | B-05's complete manifest-attested artifact: XOR byte 0 of the manifest sourceOperationHead with 01. | attestation-manifest-invalid |
| N-14c | B-05's complete manifest-attested artifact: XOR byte 0 of the manifest bookId with 01. | attestation-manifest-invalid |
| N-14d | B-05's complete manifest-attested artifact: XOR byte 0 of the trailer snapshotLength with 01. | attestation-manifest-invalid |
| N-15 | B-09's standalone envelope resolver with active C and a valid policy state: A and B, but not C, have ANCHOR GRANT and M remains 2. Rebuild V-RECEIPT-02 as the two-entry raw envelope C then B: C has principalId 2233445566778899aabbccddeeff0011, keyId 788de5096f8b530eef97a4015cffb7cfeb260c23795b846bf8112682a93b1101, and signature 31f445e7dda739aa66fb025d965217c83d8df4a602adad8023539df5ac8cff46be999d06b8278439b201099b57519f699718c05dd0f0abdb0ca7a445cd835705; B's 112-byte entry is unchanged. | attestation-capability-invalid |
| N-16 | B-04 at order 9: change C's order-4 credentialPurpose from system to operator while retaining its key, grant, request, envelope, and every other registry fact. The altered order-5 registry state first leaves CLOSE_PERIOD M=1 without any system-purpose eligible principal, so the verifier rejects that state before order 9. | attestation-capability-invalid |
| N-17 | B-02 common posting: add a fixed-asset 0060 effect record, recompute the effect digest, operation payload, and A/B signatures, but add no 0131 request record | attestation-request-profile-invalid |
| N-18 | B-02 through 3; at 4 A and B enroll active operator-purpose C without POST GRANT. At 5 B and C sign an otherwise valid record-sale-settled envelope under the still-valid POST M=2 policy. | attestation-capability-invalid |
| N-19 | B-04 at order 9: retain C's valid system-purpose key and signature but change request.period-close.effectiveTo, the linked request.posting effectiveDate, and posting.fact effectiveDate from 2026-12-30 to 2026-12-29, then recompute every affected digest and signature while leaving the workflow-derived interval and effects unchanged. | attestation-system-derivation-invalid |
| N-20 | B-04's order-5 policy mutation with CLOSE_PERIOD changed from M=1 to M=2 while C remains its only system-purpose CLOSE_PERIOD principal and A/B remain operator-purpose principals. | attestation-capability-invalid |
| N-21 | B-03 order 6: replace A2's keyId in both matching binding records with a different 32-byte hash while retaining A2's SPKI, then recompute the preimage digests, payload, and A1/B signatures. | attestation-request-profile-invalid |
| N-22 | B-03 order 6: omit the rollover predecessorKeyId from both matching binding records, then recompute the preimage digests, payload, and A1/B signatures. | attestation-request-profile-invalid |
| N-23 | B-03 order 6: replace the rollover predecessorKeyId in both matching binding records with B's active keyId, then recompute the preimage digests, payload, and A1/B signatures. | attestation-request-profile-invalid |
| N-24 | B-02 through 3: at order 4 A1 and B enroll C using B's existing founder keyId and SPKI, then recompute the preimage digests, payload, and A1/B signatures. | attestation-request-profile-invalid |
| N-25 | B-02 through 3: at order 4 A1 and B enroll C with a predecessorKeyId set to A1's keyId, then recompute the preimage digests, payload, and A1/B signatures. | attestation-request-profile-invalid |
| N-26 | B-03 order 6: set the rollover predecessorKeyId in both matching binding records to A2's new keyId, then recompute the preimage digests, payload, and A1/B signatures. | attestation-request-profile-invalid |
| N-27 | B-02 through 3: at order 4 A1 and B revoke C's unbound predefined keyId, then recompute the preimage digests, payload, and A1/B signatures. | attestation-request-profile-invalid |

N-01 through N-10 and N-13/N-15 execute from these literal standalone-envelope bytes:
every one-byte mutation, count replacement, entry replacement, entry deletion, or entry swap is
performed against the published envelope before it is decoded. The authorization context is then
derived from that same decoded payload: operation envelopes resolve at operationOrder minus one,
manifests at sourceOrder, and receipts at operationOrder. N-11, N-12, N-14, and N-16 through N-27
depend on complete protected-book, genesis-preimage, or artifact sources. Those sources include
their raw bytes, mutation offsets, replacement bytes, policy fold, verification scope, and expected
first result; they may not be replaced with prose-only scenario tests or a different first failure.

The standalone operation-envelope bases contain requestDigest but not the corresponding request
preimage bytes, so these rows are deliberately provenance-neutral: they test only the shared
envelope rules. Credential-purpose and exact workflowId authorization are exercised only with a
request preimage whose recomputed digest matches the signed operation payload; a standalone vector
must not simulate that proof with a caller-selected source channel.

## Command-Admission Corpus

The separate command-admission corpus is live-CAS only. It has three exact attempts: a request
signed over head H committed after another operation advances the head returns stale-head with exit
2 and observedHead/currentHead/currentOrder; an acknowledgement repeating the B-05 tuple succeeds
without a new operation; and the same backup ID with any differing digest or source head returns
backup-acknowledgement-conflict with exit 2. Each live below-quorum or unauthorized attempt returns
the matching taxonomy refusal. These are not verify-book fixture failures.
