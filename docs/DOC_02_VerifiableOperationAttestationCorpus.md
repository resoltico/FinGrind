---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION_CORPUS
updated: "2026-07-19"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestationStaticCorpus", "AttestationVectors"]
route:
  keywords: [verifiable-operation-attestation, static-corpus, golden-vectors, fixture-ledger, verifier-negative-cases, backup-artifact, live-cas]
  questions: ["which static fixtures verify FinGrind operation attestation", "how is the attestation corpus constructed", "which negative attestation vectors are required", "which artifact fixtures cover backup and restore"]
stage: "Slice 0 feature-branch specification; not released behavior"
---

# Verifiable Operation Attestation Corpus

This is the normative fixture source for the next protected-book attestation format. It extends
[DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md), which owns
the wire grammar, closed profiles, authorization rules, error taxonomy, and verifier precedence.

## Required Static Book And Artifact Corpus

This is a fixture-source ledger, not a list of test ideas. A fixture contains the listed immutable
preimages, folded registry and policy facts, exact envelope bytes, and expected first result. Slice
4 materializes each source into a protected-book or artifact resource without choosing new semantic
data, keys, operation positions, or expected results. Envelope bytes, heads, snapshot bytes, and
artifact digests are derived outputs of these literal sources; the resource records their complete
bytes and any mutation as a byte offset plus replacement bytes. The single-structure octets are
V-OP-01, V-OP-02, V-MANIFEST-02, V-RECEIPT-02, and V-CONTAINER-01 in the core protocol.

## Static Corpus Common Facts

All corpus fixtures use the following literal facts unless their construction row overrides one.
The values make every key, principal, time, identifier, and policy decision reproducible rather
than implementation-selected.

| Name | Exact value or construction |
|:--|:--|
| book A | bookId 00112233445566778899aabbccddeeff; identity is Acme Attestation Fixture, internal-management-bookkeeping-kernel, cash, non-statutory-internal-management, owner-managed-single-entity, owner-managed-service, functional EUR, fiscal start 01-01, book start 2026-01-01 |
| principal A | principalId, seed, SPKI, and keyId from V-OP-01 |
| principal B | principalId, seed, SPKI, and keyId from V-MANIFEST-02 |
| principal C | principalId 2233445566778899aabbccddeeff0011; seed 404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f; SPKI and keyId are the canonical Ed25519 DER-SPKI and SHA-256 thereof |
| initial capability policy | post, approve, close-period, backup, and anchor have M=1; restore, rekey, enroll-key, revoke-key, and alter-policy have M=min(2, founderCount) |
| initial grants | every founder has GRANT for every listed capability; no other principal has a grant until its explicit grant record |
| fixture instants | genesis is 2026-12-31T03:00:00.000Z; subsequent operation n is exactly that instant plus n milliseconds |
| fixture IDs | account cash = 1000, revenue = 4000, result holding = 3000, capital = 3100, retained result = 3200; common posting = 30000000000070008000000000000001 and command = 30000000000070008000000000000002; sweep posting = 30000000000070008000000000000003 and command = 30000000000070008000000000000004; close posting = 30000000000070008000000000000005 and command = 30000000000070008000000000000006; B-03 second posting = 30000000000070008000000000000007 and command = 30000000000070008000000000000008; backup = ffeeddccbbaa99887766554433221100; rekey epoch = 2 |
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
| B-03 | B-02 through 3; at 4 A and B enroll C with credentialPurpose operator, at 5 A and B retain POST M=2 and grant C POST, at 6 A and B roll A to the key derived from seed 606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f, at 7 B and C append a second ordinary sale posting using IDs ending 07/08, idempotencyKey fixture-sale-2, source document fixture-receipt-2, effectiveDate 2026-12-31, and the same account roles and EUR 100.00 journal lines as the common posting, and at 8 A and B revoke C | valid |
| B-04 | B-02 through 3; at 4 A and B enroll C with credentialPurpose system, at 5 A and B retain CLOSE_PERIOD M=1 and grant C CLOSE_PERIOD, and at 6, 7, and 8 A and B declare accounts 3000, 3100, and 3200. At 9 C alone appends sourceChannel system interim-result-sweep for 2026-01-01 through 2026-12-30 into result holding 3000: posting/command IDs ending 03/04; journal lines debit 4000 and credit 3000 EUR 100.00; sweepOrder 1; EUR total 100.00; and its posting link. At 10 C alone appends sourceChannel system fiscal-year-close for 2026-01-01 through 2026-12-31 with capital 3100, result holding 3000, and retained result 3200: posting/command IDs ending 05/06; journal lines debit 3000 and credit 3200 EUR 100.00; closeOrder 1; and its posting link. | valid |
| B-05 | B-02 through the common posting at sourceOrder 3. `B-05.snapshot` is the deterministic consistent SQLite online-backup copy of exactly that committed source book: no rekey, VACUUM, page-size change, or intervening operation. Its manifest is signed by A under BACKUP M=1. The artifact's source head is the order-3 head and its whole-container digest is the SHA-256 of that named derived resource. A appends backup-created at order 4 with the fixture backup ID and that exact tuple. | valid |
| B-06 | B-05 snapshot source, not its order-4 acknowledgement. The staged destination is exactly B-02 through order 3 and preserves book A. A and B append restore-book at order 4 with the B-05 backup ID, its derived artifact digest, sourceOrder 3, source head 3, and historicalSnapshotAuthorization true; publication uses the no-replacement protocol. | valid |
| B-07 | B-02 through order 3 with the B-05 snapshot and an A-signed BACKUP M=1 manifest, but without any backup-created operation. A and B perform the same order-4 restore as B-06. | valid |
| B-08 | an explicit two-founder resolver at sourceOrder 42 with BACKUP M=2, both A and B active and granted BACKUP, plus V-MANIFEST-02 | valid |
| B-09 | an explicit two-founder resolver at operationOrder 42 with ANCHOR M=2, both A and B active and granted ANCHOR, plus V-RECEIPT-02 | valid |
| B-10 | B-02 through order 3; A and B as the REKEY quorum append rekey-book at order 4 with keyEpoch 2, absent reason, and book.key-epoch DERIVE with rekeyedAt 2026-12-31T03:00:00.004Z | valid |

## Negative Fixture Sources

For N-01 through N-10, the operation, manifest, and receipt forms are separate fixtures. Each
starts from the listed valid base bytes and applies exactly one mutation; the verifier must return
the stated result before considering any later condition. This makes the three-structure coverage
explicit rather than inferred from a fixture name.

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
| N-13 | B-08: replace one BACKUP grant for A or B with REVOKE at or before sourceOrder | attestation-capability-invalid |
| N-14 | V-CONTAINER-01 independently: in four named resources XOR byte 0 of snapshotDigest, sourceOperationHead, bookId, and trailer snapshotLength with 01 | attestation-manifest-invalid |
| N-15 | B-09: replace one ANCHOR grant for A or B with REVOKE at or before operationOrder | attestation-capability-invalid |
| N-16 | B-04 at order 9: change C's order-4 credentialPurpose from system to operator while retaining its key, grant, request, envelope, and every other registry fact | attestation-credential-purpose-invalid |
| N-17 | B-02 common posting: add a fixed-asset 0060 effect record, recompute the effect digest, operation payload, and A/B signatures, but add no 0131 request record | attestation-request-profile-invalid |

The corpus resource records the raw source bytes, mutation offset, replacement bytes, policy fold,
and expected result for every row above. A later slice may generate the resource from this ledger,
but may not replace it with prose-only scenario tests or choose a different first failure.

## Command-Admission Corpus

The separate command-admission corpus is live-CAS only. It has three exact attempts: a request
signed over head H committed after another operation advances the head returns stale-head with exit
2 and observedHead/currentHead/currentOrder; an acknowledgement repeating the B-05 tuple succeeds
without a new operation; and the same backup ID with any differing digest or source head returns
backup-acknowledgement-conflict with exit 2. Each live below-quorum or unauthorized attempt returns
the matching taxonomy refusal. These are not verify-book fixture failures.
