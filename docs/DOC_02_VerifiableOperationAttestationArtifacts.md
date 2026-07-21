---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION_ARTIFACTS
updated: "2026-07-21"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["BackupManifest", "AttestationReceipt", "AttestationArtifactContainer"]
route:
  keywords: [verifiable-operation-attestation, backup-manifest, attestation-receipt, artifact-container, restore-book, backup-acknowledgement, receipt-anchor, no-clobber]
  questions: ["how is an attested backup artifact encoded", "how does FinGrind restore an attested snapshot", "what does an attestation receipt anchor", "which vectors prove backup and receipt envelopes"]
stage: "Slice 0 feature-branch specification; not released behavior"
---

# Verifiable Operation Attestation Artifacts

This document is the canonical artifact subprotocol for the next protected-book attestation format:
backup manifests, artifact publication and restore, off-chain receipts, and artifact golden vectors.
[DOC_02_VerifiableOperationAttestation.md](./DOC_02_VerifiableOperationAttestation.md) owns the
shared envelope grammar, historical authorization, and failure vocabulary; the corpus owns complete
protected-book and artifact fixture sources. This document does not describe released behavior.

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
   and re-signs the acknowledgement while retaining source H.

backup-created is idempotent only for the identical tuple bookId, backupId, backupArtifactDigest,
and sourceHead. Exact replay is a no-op success. Any other reuse of backupId is
backup-acknowledgement-conflict with exit 2.

| Crash point | Residual | Recovery |
|:--|:--|:--|
| after snapshot or blessing, before publication | staged local temporary file | discard locally; nothing was published |
| after publication, before acknowledgement | manifest-attested artifact and an understated source-book backup index | resume the identical acknowledgement |

A manifest-attested artifact is never unattested or orphaned. Explicit discard is confirmed local
off-chain deletion; no operation claims a published file disappeared.

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

The receipt quorum resolves ANCHOR as of operationOrder. Receipt timestamps are signer-asserted
until a future counter-signature format. Export is non-mutating, uses the same atomic no-clobber
publication rule, and warns when its output remains inside the book's trust boundary.

verify-receipt is non-mutating. It verifies the receipt envelope, finds the referenced book and
operation order, requires equal bookId and operationHead, and evaluates the receipt's ANCHOR quorum
as of that order. A valid receipt retained beside the book reports the valid-result finding
receipt-not-independent.

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
complete B-05 artifact owns the N-14 manifest-binding negatives. Slice 4 materializes those
complete sources without inventing additional semantic facts.
