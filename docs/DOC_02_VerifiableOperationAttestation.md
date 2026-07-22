---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION
updated: "2026-07-22"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestedOperation", "AttestationAuthorizationLimits", "AttestationEnvelope", "AttestationAccountMutationIntent", "AttestationCapability", "AttestationCredentialPurpose", "AttestationEvidence", "AttestationGenesis", "AttestationGrantState", "AttestationKeyFiles", "AttestationOperationKind", "AttestationOperationSigner", "AttestationPublicCredential", "AttestationRegistryMutation", "AttestationSigningCredential", "AttestationSystemWorkflowKind"]
route:
  keywords: [verifiable-operation-attestation, operation-head, attestation-envelope, principal-quorum, credential-purpose, autonomous-workflow, semantic-profile, ed25519, immutable-preimage, operation-kind]
  questions: ["what does FinGrind book-operation attestation prove", "how is an attested operation encoded", "which credential may authorize a system operation", "which semantic profile governs a typed operation"]
stage: "Current public protocol 32 and protected-book format 51 contract"
---

# Verifiable Operation Attestation Protocol

This is the normative contract for FinGrind protocol 32 and protected-book format 51. It is the
current public behavior. Earlier protected-book formats are rejected: there is no mode, migration,
alias, or compatibility path.

## Ownership And Scope

Book-operation attestation is not GitHub or release-artifact attestation. Release provenance proves
who published a FinGrind distribution; this protocol proves the authorized mutations retained in one
protected accounting book.

The protected book owns the accepted accounting facts for one entity. The attestation protocol owns
the immutable evidence that links those facts to an ordered authorization decision. FinGrind owns
the accounting meaning and admission of a command; SQLite owns transactionality, recovery, and
storage mechanics; CLI and bundle surfaces only project the result. A verifier never treats a
mutable SQLite business row, a report, an audit row, an output path, or a log as attestation truth.

| Term | Meaning |
|:--|:--|
| attested operation | One committed accounting or protected-book mutation with signed request and effect preimages. |
| principal | A book-recognized controller of one or more enrolled credentials, not a verified human or legal identity. |
| credential | One Ed25519 public key, identified by SHA-256 of its DER SubjectPublicKeyInfo encoding. |
| eligible principal | A principal with an active credential and an active grant for the resolving capability. |
| operation head | SHA-256 of one canonical operation envelope. |
| request preimage | Immutable, normalized semantic facts supplied or selected for one command. |
| effect preimage | Immutable, normalized semantic facts actually committed by that command. |
| manifest | An off-chain, quorum-signed blessing embedded in a backup artifact. |
| receipt | An off-chain, quorum-signed anchor for an existing operation head. |

## Guarantees And Limits

For every mutation, an attested book proves that the exact number of eligible, distinct
book-recognized principals signed the exact normalized request and committed effect at the
applicable historical position. The operation-head chain commits to both the semantic payload and
the authorization proof.

This protocol does not prove a real-world identity, human intent, complete external-world event
coverage, or wall-clock truth. A retained independent receipt detects rollback, truncation, or
alteration at or before its pinned head. An external witness can expose equivocation only if it
observes both branches. No mechanism detects a fork that is never revealed.

## `AttestationEvidence`

The exported `AttestationEvidence` value owns exactly one raw operation envelope, request
preimage, and effect preimage. It defensively copies all byte input and output so a storage adapter
cannot alter evidence after handing it to the verifier. The canonical verifier contract and its
result surfaces are owned by
[DOC_02_VerifiableOperationAttestationVerification.md](./DOC_02_VerifiableOperationAttestationVerification.md).

## `AttestationAccountMutationIntent`

`AttestationAccountMutationIntent` is the core semantic classification for a declaration,
amendment, or retirement effect. The adapter supplies the public catalog operation identifier while
the core validates that the persisted effect is compatible with this intent, keeping transport
operation names out of the accounting kernel.

## `AttestationGenesis`

`AttestationGenesis.create` accepts one through five distinct founder credentials, declares the
complete initial registry and policy in immutable preimages, and produces the unanimous order-zero
evidence.

`AttestationFounderInput` is the public open-book input that binds one founder principal to one
credential and purpose. `AttestationGenesisFactory` translates those inputs into the canonical
core genesis; it never accepts an implicit founder or a caller-provided genesis envelope.

## `AttestationAuthorizationLimits`

`AttestationAuthorizationLimits` owns the cardinality range shared by post-genesis policy and
public signing boundaries.

### Definition

```java
public final class AttestationAuthorizationLimits {
  public static final int MINIMUM_QUORUM = 1;
  public static final int MAXIMUM_QUORUM = 64;
}
```

### Constraints

- Genesis: one through five founders; this bootstrap boundary remains separate.
- Post-genesis: one through 64 distinct credential triples, the complete exact-quorum range
  admitted by `AttestationPolicyRule` and `AttestationRegistryMutation.PolicyRule`.
- Reachability: an accepted policy always remains usable through public operation, manifest,
  receipt, restore, rekey, and policy-mutation signing boundaries.
- Compatibility: public protected-book format-51 / protocol-32 contract.

---

## `AttestationKeyFiles`

`AttestationKeyFiles.create` is the sole format-51 creation path for a new encrypted file-backed
Ed25519 credential. It publishes a no-clobber key file and returns `AttestationPublicCredential`.
`AttestationKeyFiles.loadPublicCredential` reads the public credential published with an existing
encrypted key without decrypting its private material. A credential contains only canonical public
DER-SPKI bytes and its SHA-256 key identifier.

The public CLI makes those two safe custody operations available as
`generate-attestation-key-file` and `inspect-attestation-key-file`: the generator emits the
canonical base64url SPKI required by `enroll-key` or `rollover-key`, while inspection makes an
existing credential's SPKI recoverable for revocation or predecessor selection without exposing
private material.

## `AttestationKeyFileMetadata`

`AttestationKeyFileMetadata` is the public non-secret response value for standalone credential
custody. It carries the selected credential-file path, canonical base64url `credentialSpki`, and
the derived lowercase-hex SHA-256 `keyId`; the CLI publishes the key file itself only as the
generator's artifact and never publishes private-key or passphrase material.

## `AttestationOperationSigner`

`AttestationOperationSigner` builds an ordered operation envelope only from canonical request and
effect preimages; persistence performs historical authorization and compare-and-swap admission.

## `Attestation Signing Sessions And Authorization`

`AttestationSigningSession` owns one short-lived private signing capability. Its executor factory,
`AttestationSigningSessionFactory`, resolves a selected credential source without exposing private
key bytes to the caller. `AttestationCredentialSource` names the accepted credential source,
`AttestationCredentialException` reports an executor-facing credential-resolution failure, while
`AttestationCredentialUseException` reports that one explicitly selected encrypted credential
could not be read or verified and retains only its path plus a non-secret cause.
`AttestationMutationAuthorization` couples an authorized mutation to the operation evidence it
must append. These types are adapter seams: application code supplies a credential source and
canonical mutation facts, while the session owns signing and zeroization.

## `Attestation Mutation Projections`

`AttestationOperationKind` is the closed canonical token vocabulary for signed operation
preimages. `AttestationOperationRequest`, `AttestationOperationPreimages`, and
`AttestationOperationAuthorizer` carry one normalized request/effect pair into the signer;
`AttestationPlanOperationAuthorizer` collects child mutations and permits exactly one final
aggregate plan authorization.

`AttestationAccountMutationProjection`, `AttestationLifecycleMutationProjection`,
`AttestationPostingMutationProjection`, `AttestationPeriodCloseMutationProjection`, and
`AttestationTaxRegistrationMutationProjection` derive those preimages from the corresponding
committed state transition. Their immutable input records — `AttestationAccountSnapshot`,
`AttestationLifecycleState`, `AttestationPostingRequestSnapshot`,
`AttestationPostingEffectSnapshot`, `AttestationPostingEvidenceDocument`,
`AttestationPostingLine`, `AttestationClosePostingSnapshot`, `AttestationTaxCodeSnapshot`,
`AttestationTaxRegistrationSnapshot`, and `AttestationEffectMutation` — prevent the signing
boundary from reading mutable storage rows after a mutation has been selected. Projection callers
must retain the exact request and committed effect; they must not synthesize a semantic proxy.

## `AttestationPublicCredential`

`AttestationPublicCredential` identifies a public key by the SHA-256 digest of its DER
SubjectPublicKeyInfo encoding.

## `AttestationSigningCredential`

`AttestationSigningCredential` binds a public credential to its book principal, encrypted key
file, and short-lived caller-owned signing secret without disclosing private-key bytes.

## Profile Constants And Canonical Primitives

The current value of every version byte is exactly 01. An implementation must reject any other
value with attestation-unsupported-version; it must not guess a newer grammar.

| Structure | Domain tag | Version field | Current value |
|:--|:--|:--|:--:|
| operation payload | FGATTOP1 | payloadVersion | 01 |
| backup manifest | FGATTBM1 | manifestVersion | 01 |
| receipt | FGATTRC1 | receiptVersion | 01 |
| backup artifact trailer | FGATBMF1 | containerVersion | 01 |

All integer values are big-endian. All timestamps are exactly 24 ASCII bytes in the form
YYYY-MM-DDThh:mm:ss.sssZ and must denote a valid UTC instant. All dates are exactly 10 ASCII bytes
in the form YYYY-MM-DD and must denote a valid Gregorian calendar date.

| Type | Canonical bytes | Validation |
|:--|:--|:--|
| u8, u16, u32, u64 | unsigned fixed-width integer | big-endian |
| i64, i128 | signed two's-complement fixed-width integer | big-endian |
| uuid | 16 RFC-4122 network-order bytes | textual UUIDs are never signed |
| hash | 32 raw SHA-256 bytes | hexadecimal text is never signed |
| spki | u16 byte length then DER SubjectPublicKeyInfo | length 1 through 4096; Ed25519 only |
| bytes | u32 byte length then raw bytes | length at most 1,048,576; never secret material |
| token | u8 byte length then lowercase ASCII kebab token | length 1 through 64 |
| algorithm-id | u8 byte length then lowercase ASCII kebab token | length 1 through 32; version 1 value is ed25519 |
| text | u32 byte length then NFC UTF-8 | length at most 1,048,576; no NUL |
| currency | three uppercase ASCII letters | ISO-4217 code when a currency is used |
| date | ten ASCII bytes | valid Gregorian date |
| instant | 24 ASCII bytes | valid millisecond UTC instant |
| money | currency, sign u8, minorUnits u128 | sign 00 is plus and 01 is minus; zero must use 00 |
| scaled | scale u8, sign u8, units u128 | scale 0 through 18; sign rules equal money |
| bool | one byte 00 or 01 | no other value |

No signed payload contains passphrases, private keys, custodian handles, environment values, local
paths, encrypted-key bytes, or presentation-only path hints. A preimage has at most 1,000,000
records and 16 MiB of encoded bytes. An authorization quorum is in the inclusive range 1 through
64. The post-operation fold of every policy, binding, revocation, grant, or system-workflow-policy
mutation must leave each configured quorum no greater than its eligible-principal count. It must
also leave each capability's quorum no greater than its operator-purpose eligible-principal count
whenever that capability admits a cli operation. If an active system-workflow policy exists, the
CLOSE_PERIOD quorum must additionally be no greater than its system-purpose eligible-principal
count. A system workflow is therefore never activated into an impossible all-system quorum.

## Principal Registry, Policy, And Operation Kinds

Registry and policy facts are append-only. For operation order N greater than zero, policy and key
eligibility resolve from the fold through N minus one. A binding or revocation committed at N first
affects N plus one. No record may set a retrospective or future-effective credential interval.

For every capability, its effective policy rule at a resolving position is the
policy.capability-rule fact for that capability with the greatest accepted operation order at or
before that position. Genesis supplies exactly one rule for every closed capability. A later
alter-policy operation replaces only the effective quorum of each capability it names; it does not
alter another capability or rewrite its predecessor. There can be at most one policy-change record
for a capability in one operation because a duplicate complete per-type sort key is invalid.

An active credential is an enrolled or rolled-over Ed25519 key that has not been revoked. Every
binding also has one immutable credentialPurpose: operator or system. A cli operation has only
operator-purpose envelope credentials; a system operation has only system-purpose envelope
credentials. Purpose does not constrain a manifest or receipt because neither carries a source
channel. Purpose belongs to the credential binding, not to an inferred principal name, external
key-store label, or caller claim. A new binding is the only way to change a credential's purpose;
history retains the purpose that was effective at its operation position.

A principal is eligible for one capability exactly when it owns at least one active credential and
its latest principal.capability-grant fact for that capability is GRANT. A purpose-eligible
principal additionally owns an active credential with the required operator or system purpose.
Capability grants are append-only facts; ALTER_POLICY may add a GRANT or REVOKE fact but may not
rewrite prior authority. There is no hidden role or scope table.

Every admitted principal.key-binding, including every genesis founder binding, has a keyId exactly
equal to SHA-256 of its canonical Ed25519 DER-SPKI. A keyId may occur in exactly one binding fact in
the entire book history and therefore belongs permanently to one principal. An enroll binding has
an absent predecessorKeyId. A rollover binding has a predecessorKeyId that is different from the
new keyId and identifies an active credential of the same principal at the preceding resolving
position. Rollover adds the new credential; it never implicitly revokes or changes the predecessor.
Only an explicit credential.revocation of that principal's active binding retires a credential, and
revocation is final: no later binding may reuse its keyId. A binding or revocation that violates
these rules is an invalid request/effect profile.

Every policy rule is capability plus concrete quorum M. Distinct principals and distinct keys are
unconditional envelope invariants, so there is no persisted require-distinct-principals switch.
Genesis declares one through five founders, enrolls their keys, grants every founder every
capability, and sets every policy rule. It requires one envelope signature from every founder. The
genesis defaults are:

| Capability | Default M |
|:--|:--:|
| POST, APPROVE, CLOSE_PERIOD, BACKUP, ANCHOR | 1 |
| RESTORE, REKEY, ENROLL_KEY, REVOKE_KEY, ALTER_POLICY | min(2, founderCount) |

The uppercase labels above are prose labels. Their signed capability tokens are post, approve,
close-period, backup, anchor, restore, rekey, enroll-key, revoke-key, and alter-policy,
respectively. The following tokens are likewise closed:

| Field | Exact permitted token values |
|:--|:--|
| bindingAction | enroll, rollover |
| credentialPurpose | operator, system |
| grantState | grant, revoke |
| sourceChannel | cli, system |
| systemWorkflowKind | interim-result-sweep, fiscal-year-close |
| closeKind | interim-result-sweep, fiscal-year-close |
| journal side | debit, credit |
| taxDirection | payable, receivable |
| mutation | encoded u8 values defined in Immutable Preimages; never a token |

Other token fields in the record catalog are the already-owned accounting vocabularies documented
by their bounded contexts. Their values are normalized to their published lowercase wire token
before preimage encoding; a display label, enum name, or localized text is never signed.

## `AttestationRegistryMutation`, `AttestationCapability`, `AttestationCredentialPurpose`, `AttestationGrantState`, And `AttestationSystemWorkflowKind`

`AttestationRegistryMutation` is the closed public command-domain owner for future credential and
policy changes. Its `EnrollKey`, `RolloverKey`, `RevokeKey`, and `AlterPolicy` variants project
the exact request and effect preimages defined in the record catalogue; no caller passes encoded
preimage bytes or a mutable registry object. `PolicyRule`, `CapabilityGrant`, and
`SystemWorkflowPolicy` are the closed nested facts of an `AlterPolicy` mutation.

`AttestationCapability` owns the signed lower-case capability tokens. `AttestationCredentialPurpose`
owns `operator` and `system`; `AttestationGrantState` owns `grant` and `revoke`; and
`AttestationSystemWorkflowKind` owns `interim-result-sweep` and `fiscal-year-close`. A public
lifecycle request carries a canonical base64url Ed25519 SPKI, from which FinGrind derives the
keyId. Local credential paths and private custody material are not request or effect facts.

The sourceChannel provenance rule and autonomous system-close derivation are owned by
[DOC_02_VerifiableOperationAttestationProfiles.md](./DOC_02_VerifiableOperationAttestationProfiles.md).
Before ordinary operation authorization, FinGrind recomputes requestDigest from the retained
request preimage, requires its one request.command operationKind to equal the payload, and derives
sourceChannel from that record. sourceChannel is never an authorization-call argument. A system
request additionally carries exactly one request.system-workflow-run; the historical resolver
matches that exact workflowId and operation kind, not merely any active workflow of the same kind.

The operation-kind catalog is closed. A token not listed here is attestation-unknown-operation-kind.

| Operation kinds | Capability |
|:--|:--|
| book-genesis | special genesis rule |
| declare-account, amend-account, retire-account, declare-tax-registration | POST |
| post-entry, execute-plan, record-sale-settled, record-sale-on-credit, record-purchase-settled, record-purchase-on-credit | POST |
| record-inventory-capitalization-settled, record-inventory-capitalization-on-credit, record-inventory-write-down, record-inventory-shrinkage, record-inventory-count-increase | POST |
| record-prepayment, record-deferred-revenue, record-accrued-expense, record-accrual-cutoff-recognition, record-accrued-expense-settlement | POST |
| record-latvian-monthly-payroll, record-latvian-payroll-net-wage-settlement, record-latvian-payroll-state-remittance | POST |
| record-fixed-asset-capitalization, record-fixed-asset-depreciation, record-fixed-asset-disposal | POST |
| record-financing-borrowing, record-financing-principal-repayment, record-financing-interest-accrual, record-financing-interest-payment | POST |
| record-foreign-currency-obligation, record-realized-foreign-exchange-settlement | POST |
| record-expense-settled, record-expense-on-credit, record-receipt, record-payment, record-owner-contribution, record-owner-withdrawal, record-opening-position, record-reversal | POST |
| interim-result-sweep, fiscal-year-close | CLOSE_PERIOD |
| backup-created | BACKUP |
| restore-book | RESTORE |
| rekey-book | REKEY |
| enroll-key, rollover-key | ENROLL_KEY |
| revoke-key | REVOKE_KEY |
| alter-policy | ALTER_POLICY |
| attach-posting-approval | APPROVE |

The off-chain backup manifest uses BACKUP at sourceOrder. The off-chain receipt uses ANCHOR at
operationOrder. Neither is an operation-kind and neither mutates the book. Every policy, key, or
grant mutation validates that every configured M remains no greater than the number of eligible
principals for its capability after the mutation.

## Common Authorization Envelope

Every operation, manifest, and receipt uses one common envelope:

~~~text
envelope = canonicalPayload
         || sigCount(u16)
         || sigCount * [ principalId(uuid) || keyId(hash) || signature(64) ]
~~~

Entries are strictly ascending by raw keyId bytes. Each signature is the raw 64-byte Ed25519
signature over canonicalPayload. Except for genesis, an envelope is valid at its resolving
position if and only if all of these conditions hold:

1. sigCount equals the applicable policy quorum M exactly;
2. every principalId is distinct and every keyId is distinct;
3. every keyId is SHA-256 of its Ed25519 SPKI, belongs to the stated principal, and is active;
4. every stated principal is eligible for the resolved capability;
5. every signature verifies over canonicalPayload; and
6. for an operation, the sourceChannel credential-purpose rule holds; and
7. entries are strictly keyId ascending.

Extra signatures never add authority. A request for unanimous authorization changes M explicitly;
it never appends redundant signatures. This list states envelope invariants; the deterministic
failure-precedence section defines their evaluation order.

## Key Custody And Operation Atomicity

Version 1 ships exactly one signing custodian: an encrypted file-backed PKCS#8 private key. The
JDK Ed25519 seam decrypts that key only transiently while signing. PKCS#11, HSM, OS-keychain, and
non-exportable custodians are unshipped; selecting one returns custodian-not-supported and never
falls back to file custody. Private-key material cannot enter a book, report, telemetry payload,
log, CLI DTO, contract DTO, or generic accounting object. The custodian enforces a 1 KiB key-file
maximum with one additional probe byte, so an oversized artifact is rejected before decryption and
after a bounded read. Creation writes and forces an owner-only stage, creates the final name by
no-replace hard link, and reports success only after the parent directory's native durability
barrier succeeds; a post-link durability failure is reported without pretending that the key was
not published.

| Class | Applies to | Required boundary |
|:--|:--|:--|
| one atomic operation | business entries, execute-plan, account/tax/registry/policy mutation, close, and rekey | one SQLite transaction contains domain rows, immutable preimages, envelope, and head |
| sequence of signed operations | a command genuinely unable to be one transaction | each step is independently signed, has exact preimages, and has its own chain position |
| staged external artifact | backup-book and restore-book | the filesystem boundary uses the ordered manifest protocol in [the artifact reference](./DOC_02_VerifiableOperationAttestationArtifacts.md); it is never claimed to be one SQLite transaction |

## Version 1 Exclusions And Future Format Breaks

Version 1 is Ed25519-only. It does not support hybrid signatures, ML-DSA, mixed-algorithm quorums,
or an algorithmId per signature entry. Although the JDK supports ML-DSA key conversion, including
X.509 and PKCS#8 encodings, through [JEP 497](https://openjdk.org/jeps/497), adding it requires a
new payload and envelope version rather than a compatibility branch.

algorithmId has its own bounded wire grammar: its length prefix and one-through-32-byte lowercase
ASCII kebab token are decoded as part of the payload before signature entries are framed. The
Ed25519-only semantic rule is then enforced at shared envelope check 12, after the quorum,
duplicate-principal, duplicate-key, and signature-entry-order checks. A malformed algorithm-id is
a payload-grammar failure; a well-formed value other than ed25519 is
attestation-key-algorithm-invalid.

Version 1 also excludes PKCS#11, HSM, and OS-keychain custodians; RFC 3161 or other trusted
timestamp countersigning; a live transparency log or witness service; cross-book or group
attestation; automatic invalidation of historically valid work after a key compromise; and an
external current recovery authority for restore. A receipt timestamp remains signer-asserted, a
witness remains an optional external verifier input, and compromise remains a non-persisted review
finding. None of these exclusions may be emulated by a hidden mode, a fallback, or a new meaning
for an existing version-1 field.

## Operation Payload, Chain, And Genesis

~~~text
operationPayload =
  "FGATTOP1"
  || payloadVersion(u8 = 01)
  || bookId(uuid)
  || operationOrder(u64)
  || operationKind(token)
  || algorithmId(algorithm-id = "ed25519")
  || previousHead(hash)
  || recordedAt(instant)
  || requestDigest(hash)
  || effectDigest(hash)

operationHead = SHA-256(envelope(operationPayload))
~~~

The operation payload contains no free-form Unicode. Quorum resolves from the operation-kind
catalog at operationOrder minus one. An initiator signs the observed head. Commit compares that
head to the current book head in the same SQLite transaction as all effect rows and immutable
preimages. A mismatch is stale-head with exit 2 and fields observedHead, currentHead, and
currentOrder; it is never persisted. An operation order is never reserved while a signing or
co-signing exchange is pending.

Genesis is the only self-authorizing envelope. It has operationOrder zero, a 32-byte all-zero
previousHead, and operationKind book-genesis. Every founder credential has credentialPurpose
operator. Its effect preimage must contain exactly one book.identity record, one
principal.key-binding enrollment record per founder, one
principal.capability-grant GRANT record for each founder-capability pair, and one
policy.capability-rule record per listed capability. It has one through five founders. Every
envelope signer must match a declared founder principal and declared SPKI-derived keyId, and every
declared founder must sign exactly once. Genesis is trusted out of band. Every later structure
uses the ordinary as-of rule.

Genesis cannot declare a system-workflow policy: its founders have operator-purpose credentials
only. Enroll the required system-purpose credential first, then declare the active workflow in a
later attested operation; the post-operation registry fold rejects an unreachable system quorum.

Genesis authorization constructs no caller-supplied founder or policy list. It recomputes both
preimage digests, verifies the order-zero book-genesis payload and zero previousHead, then proves
that each declared identity, founder, policy, and grant maps exactly to its immutable CREATE effect
before deriving the founder bindings and checking unanimous signatures.

## Immutable Preimages

Request and effect preimages are immutable append-only facts stored with the operation. A verifier
recomputes their digests from those bytes only, never from mutable domain rows. SQLite rejects
update and delete of a persisted preimage; that database backstop does not replace verifier checks.

~~~text
preimage = recordCount(u32) || records
records  = records sorted ascending by (recordTypeTag, encodedPerTypeSortKey)
record   = recordTypeTag(u16) || fieldCount(u16) || fields
field    = presence(u8) || value
~~~

Every field, required or optional, starts with presence. Required fields must use presence 01;
optional fields use 00 for absent and have no following value, or 01 followed by their listed
value. fieldCount must equal the catalog field count for that record type exactly. A field order,
field type, absent required field, unknown tag, duplicate complete sort key, or trailing byte is
attestation-preimage-invalid. The sort key is encoded using the field encodings in the listed order,
including required presence bytes.

In the tables below, an exclamation mark means required and a question mark means optional. The
schema column is the complete wire-field order, not a prose summary. Every effect record begins
with mutation:u8!. Mutation values are 00 CREATE, 01 AMEND, 02 RETIRE, 03 REACTIVATE, 04 REVERSE,
05 DERIVE, and 06 ACKNOWLEDGE. Request records do not carry mutation.

### Request Record Catalog

Every request has exactly one request.command record. Repeated facts use repeated records with a
distinct complete sort key. An operation profile below determines which records are required,
allowed, or forbidden; a record not allowed by that profile is attestation-request-profile-invalid.

| Tag | Record | Complete schema field order | Per-type sort key |
|:--|:--|:--|:--|
| 0100 | request.command | operationKind:token!, idempotencyKey:text?, causationId:text?, sourceChannel:token! | operationKind |
| 0101 | request.book-identity | bookId:uuid!, entityName:text!, kernelProfile:token!, accountingBasis:token!, frameworkPosition:token!, entityForm:token!, template:token!, costingDoctrine:token?, functionalCurrency:currency!, fiscalYearStartMonth:u8!, fiscalYearStartDay:u8!, bookStartDate:date! | bookId |
| 0102 | request.founder | principalId:uuid!, keyId:hash!, spki:spki!, credentialPurpose:token! | principalId, keyId |
| 0103 | request.policy-rule | capability:token!, quorum:u16! | capability |
| 0110 | request.account | accountCode:text!, accountName:text!, accountType:token!, nodeKind:token!, parentAccountCode:text?, unitOfMeasure:text? | accountCode |
| 0111 | request.account-classification | accountCode:text!, classificationFamily:token!, classification:token! | accountCode, classificationFamily |
| 0112 | request.account-relationship | accountCode:text!, relationshipKind:token!, targetAccountCode:text? | accountCode, relationshipKind |
| 0113 | request.tax-registration | registrationId:text!, registrationName:text!, jurisdiction:text!, registrationCode:text?, payableAccountCode:text!, receivableAccountCode:text!, obligationFrequency:token!, dueDaysAfterPeriodEnd:u16!, active:bool! | registrationId |
| 0114 | request.tax-registration-code | registrationId:text!, taxCode:text!, taxCodeName:text!, rate:scaled!, inclusionMode:token!, applicationKind:token! | registrationId, taxCode |
| 0120 | request.posting | stepOrder:u32!, operationKind:token!, effectiveDate:date!, postingKind:token!, priorPostingId:uuid?, reversalReason:text? | stepOrder |
| 0121 | request.account-role | stepOrder:u32!, role:token!, accountCode:text! | stepOrder, role |
| 0122 | request.money | stepOrder:u32!, role:token!, amount:money! | stepOrder, role |
| 0123 | request.quantity | stepOrder:u32!, role:token!, quantity:scaled! | stepOrder, role |
| 0124 | request.evidence-document | stepOrder:u32!, sourceDocumentId:text!, sourceDocumentType:text!, documentDate:date! | stepOrder, sourceDocumentId |
| 0125 | request.posting-approval | postingId:uuid!, approvalId:text!, approverReference:text!, approverType:token!, decision:token!, approvedAt:instant! | postingId, approvalId |
| 0126 | request.tax-selection | stepOrder:u32!, registrationId:text!, taxCode:text! | stepOrder, registrationId, taxCode |
| 0127 | request.foreign-exchange | stepOrder:u32!, foreignCurrency:currency!, foreignAmount:money!, functionalAmount:money!, exchangeRate:scaled! | stepOrder |
| 0128 | request.inventory-movement | stepOrder:u32!, inventoryAccountCode:text!, counterAccountCode:text!, movementKind:token!, quantity:scaled! | stepOrder |
| 0129 | request.settlement-adjunct | stepOrder:u32!, accountCode:text!, amount:money! | stepOrder, accountCode |
| 012A | request.journal-line | stepOrder:u32!, lineOrder:u32!, accountCode:text!, side:token!, amount:money!, quantity:scaled? | stepOrder, lineOrder |
| 0130 | request.accrual-cutoff | stepOrder:u32!, cutoffId:uuid!, cutoffKind:token!, recognitionStart:date?, recognitionEnd:date? | stepOrder, cutoffId |
| 0131 | request.fixed-asset | stepOrder:u32!, assetId:uuid!, assetClass:token?, usefulLifeMonths:u32? | stepOrder, assetId |
| 0132 | request.financing | stepOrder:u32!, arrangementId:uuid! | stepOrder, arrangementId |
| 0133 | request.foreign-currency-obligation | stepOrder:u32!, obligationId:uuid!, settlementId:uuid? | stepOrder, obligationId, settlementId |
| 0134 | request.payroll | stepOrder:u32!, payrollRunId:uuid!, employeeReference:text?, payrollMonth:text?, withholdingProfile:token?, taxBookHeldAtEmployer:bool?, dependantCount:u8? | stepOrder, payrollRunId |
| 0140 | request.period-close | closeKind:token!, effectiveFrom:date?, effectiveTo:date?, fiscalYear:u32?, resultHoldingAccountCode:text?, capitalAccountCode:text?, retainedResultAccountCode:text? | closeKind, effectiveTo |
| 0141 | request.system-workflow-run | workflowId:uuid! | workflowId |
| 0150 | request.backup-acknowledgement | backupId:uuid!, backupArtifactDigest:hash!, sourceOrder:u64!, sourceHead:hash! | backupId |
| 0160 | request.restore | backupId:uuid!, backupArtifactDigest:hash!, sourceOrder:u64!, sourceHead:hash! | backupId |
| 0170 | request.rekey | keyEpoch:u64!, reason:text? | keyEpoch |
| 0180 | request.credential-binding | principalId:uuid!, keyId:hash!, bindingAction:token!, spki:spki!, credentialPurpose:token!, predecessorKeyId:hash? | principalId, keyId |
| 0181 | request.credential-revocation | keyId:hash!, principalId:uuid!, reason:text? | keyId |
| 0182 | request.policy-change | capability:token!, quorum:u16! | capability |
| 0183 | request.principal-capability-grant | principalId:uuid!, capability:token!, grantState:token! | principalId, capability |
| 0184 | request.system-workflow-policy | workflowId:uuid!, workflowKind:token!, resultHoldingAccountCode:text!, capitalAccountCode:text?, retainedResultAccountCode:text?, active:bool! | workflowId |

### Effect Record Catalog

| Tag | Record | Complete schema field order | Per-type sort key |
|:--|:--|:--|:--|
| 0001 | book.identity | mutation:u8!, bookId:uuid!, entityName:text!, kernelProfile:token!, accountingBasis:token!, frameworkPosition:token!, entityForm:token!, template:token!, costingDoctrine:token?, functionalCurrency:currency!, fiscalYearStartMonth:u8!, fiscalYearStartDay:u8!, bookStartDate:date! | bookId |
| 0002 | principal.key-binding | mutation:u8!, principalId:uuid!, keyId:hash!, bindingAction:token!, spki:spki!, credentialPurpose:token!, predecessorKeyId:hash? | principalId, keyId |
| 0003 | principal.capability-grant | mutation:u8!, principalId:uuid!, capability:token!, grantState:token! | principalId, capability |
| 0004 | credential.revocation | mutation:u8!, keyId:hash!, principalId:uuid!, reason:text? | keyId |
| 0005 | policy.capability-rule | mutation:u8!, capability:token!, quorum:u16! | capability |
| 0006 | backup.acknowledgement | mutation:u8!, backupId:uuid!, backupArtifactDigest:hash!, sourceOrder:u64!, sourceHead:hash! | backupId |
| 0007 | book.key-epoch | mutation:u8!, keyEpoch:u64!, rekeyedAt:instant! | keyEpoch |
| 0008 | system.workflow-policy | mutation:u8!, workflowId:uuid!, workflowKind:token!, resultHoldingAccountCode:text!, capitalAccountCode:text?, retainedResultAccountCode:text?, active:bool! | workflowId |
| 0010 | account.state | mutation:u8!, accountCode:text!, accountName:text!, accountType:token!, nodeKind:token!, parentAccountCode:text?, unitOfMeasure:text?, active:bool! | accountCode |
| 0011 | account.classification | mutation:u8!, accountCode:text!, classificationFamily:token!, classification:token! | accountCode, classificationFamily |
| 0012 | account.relationship | mutation:u8!, accountCode:text!, relationshipKind:token!, targetAccountCode:text? | accountCode, relationshipKind |
| 0013 | tax.registration | mutation:u8!, registrationId:text!, registrationName:text!, jurisdiction:text!, registrationCode:text?, payableAccountCode:text!, receivableAccountCode:text!, obligationFrequency:token!, dueDaysAfterPeriodEnd:u16!, active:bool! | registrationId |
| 0014 | tax.registration-code | mutation:u8!, registrationId:text!, taxCode:text!, taxCodeName:text!, rate:scaled!, inclusionMode:token!, applicationKind:token! | registrationId, taxCode |
| 0020 | posting.fact | mutation:u8!, postingId:uuid!, operationStepOrder:u32!, operationKind:token!, postingKind:token!, originKind:token!, effectiveDate:date!, recordedAt:instant!, priorPostingId:uuid?, commandId:uuid!, idempotencyKey:text?, causationId:text?, sourceChannel:token! | postingId |
| 0021 | posting.source-document | mutation:u8!, postingId:uuid!, sourceDocumentId:text!, sourceDocumentType:text!, documentDate:date! | postingId, sourceDocumentId |
| 0022 | posting.approval | mutation:u8!, postingId:uuid!, approvalId:text!, approverReference:text!, approverType:token!, decision:token!, approvedAt:instant! | postingId, approvalId |
| 0023 | posting.applied-tax | mutation:u8!, postingId:uuid!, registrationId:text!, taxCode:text!, taxableAmount:money!, taxAmount:money!, taxDirection:token! | postingId, registrationId, taxCode |
| 0024 | posting.foreign-exchange | mutation:u8!, postingId:uuid!, foreignCurrency:currency!, foreignAmount:money!, functionalAmount:money!, exchangeRate:scaled! | postingId |
| 0025 | journal.line | mutation:u8!, postingId:uuid!, lineOrder:u32!, accountCode:text!, side:token!, amount:money!, quantity:scaled? | postingId, lineOrder |
| 0030 | inventory.movement | mutation:u8!, movementOrder:u64!, postingId:uuid!, accountCode:text!, movementKind:token!, quantity:scaled!, unitCost:money!, inventoryCost:money! | movementOrder |
| 0031 | inventory.on-hand | mutation:u8!, accountCode:text!, quantity:scaled!, costPool:money!, valuationThrough:date! | accountCode |
| 0040 | interim-result-sweep | mutation:u8!, sweepOrder:u64!, effectiveFrom:date!, effectiveTo:date!, resultHoldingAccountCode:text! | sweepOrder |
| 0041 | interim-result-sweep-total | mutation:u8!, sweepOrder:u64!, currency:currency!, total:money! | sweepOrder, currency |
| 0042 | interim-result-sweep-posting | mutation:u8!, sweepOrder:u64!, postingId:uuid! | sweepOrder, postingId |
| 0043 | fiscal-year-close | mutation:u8!, closeOrder:u64!, effectiveFrom:date!, effectiveTo:date!, capitalAccountCode:text!, resultHoldingAccountCode:text!, retainedResultAccountCode:text! | closeOrder |
| 0044 | fiscal-year-close-posting | mutation:u8!, closeOrder:u64!, postingId:uuid! | closeOrder, postingId |
| 0050 | accrual-cutoff | mutation:u8!, cutoffId:uuid!, cutoffKind:token!, originPostingId:uuid!, balanceAccountCode:text!, recognitionAccountCode:text!, recognitionStart:date?, recognitionEnd:date?, deferredOrAccruedAmount:money! | cutoffId |
| 0051 | accrual-cutoff-application | mutation:u8!, cutoffId:uuid!, applicationOrder:u64!, postingId:uuid!, applicationKind:token!, recognizedAmount:money!, reversalPostingId:uuid? | cutoffId, applicationOrder |
| 0060 | fixed-asset | mutation:u8!, assetId:uuid!, originPostingId:uuid!, fixedAssetAccountCode:text!, accumulatedDepreciationAccountCode:text!, depreciationExpenseAccountCode:text!, gainOnDisposalAccountCode:text!, lossOnDisposalAccountCode:text!, assetClass:token!, capitalizationAmount:money!, serviceDate:date!, usefulLifeMonths:u32! | assetId |
| 0061 | fixed-asset-application | mutation:u8!, assetId:uuid!, applicationOrder:u64!, postingId:uuid!, applicationKind:token!, amount:money!, period:date! | assetId, applicationOrder |
| 0062 | fixed-asset-reversal | mutation:u8!, reversalPostingId:uuid!, assetId:uuid!, reversedApplicationOrOriginId:uuid! | reversalPostingId |
| 0070 | financing-arrangement | mutation:u8!, arrangementId:uuid!, originPostingId:uuid!, principalAccountCode:text!, interestPayableAccountCode:text!, principal:money!, commencementDate:date! | arrangementId |
| 0071 | financing-application | mutation:u8!, arrangementId:uuid!, applicationOrder:u64!, postingId:uuid!, principalAmount:money!, interestAmount:money!, effectiveDate:date! | arrangementId, applicationOrder |
| 0072 | financing-reversal | mutation:u8!, reversalPostingId:uuid!, arrangementId:uuid!, reversedApplicationOrOriginId:uuid! | reversalPostingId |
| 0080 | foreign-currency-obligation | mutation:u8!, obligationId:uuid!, originPostingId:uuid!, receivableAccountCode:text!, revenueAccountCode:text!, foreignExchangeGainAccountCode:text!, foreignExchangeLossAccountCode:text!, currency:currency!, foreignAmount:money!, functionalAmount:money! | obligationId |
| 0081 | foreign-currency-settlement | mutation:u8!, obligationId:uuid!, settlementId:uuid!, postingId:uuid!, settlementAmount:money!, realizedGainLoss:money! | obligationId, settlementId |
| 0082 | foreign-currency-reversal | mutation:u8!, reversalPostingId:uuid!, obligationOrSettlementId:uuid! | reversalPostingId |
| 0090 | latvian-payroll-run | mutation:u8!, payrollRunId:uuid!, employeeReference:text!, payrollMonth:text!, withholdingProfile:token!, wageExpenseAccountCode:text!, employerSocialContributionExpenseAccountCode:text!, netWagesPayableAccountCode:text!, employeeSocialContributionPayableAccountCode:text!, employerSocialContributionPayableAccountCode:text!, personalIncomeTaxPayableAccountCode:text!, grossAmount:money!, netAmount:money!, taxContributionAmount:money! | payrollRunId |
| 0091 | latvian-payroll-run-reversal | mutation:u8!, reversalPostingId:uuid!, payrollRunId:uuid! | reversalPostingId |
| 0092 | latvian-payroll-settlement | mutation:u8!, payrollRunId:uuid!, settlementKind:token!, postingId:uuid!, settledAmount:money! | payrollRunId, settlementKind |
| 0093 | latvian-payroll-settlement-reversal | mutation:u8!, reversalPostingId:uuid!, payrollRunId:uuid!, settlementKind:token! | reversalPostingId |
| 00A0 | restore.provenance | mutation:u8!, backupId:uuid!, backupArtifactDigest:hash!, restoredFromOrder:u64!, historicalSnapshotAuthorization:bool! | backupId |

The catalog is semantic rather than a mirror of SQLite tables. Physical indexes, caches, triggers,
audit events, report projections, and paths are not records. Adding a tag, changing a field type,
or changing a field order requires a new attestation payload version and a new hard format.

### Effect Closure And Derived Facts

The effect preimage is exhaustive: it contains every semantic domain-state mutation, generated
identifier, lifecycle change, and relationship update committed by its operation, and it contains
no attestation metadata. A generated identifier occurs only in an effect record. A verifier does
not recover any of those facts from mutable business rows, SQLite triggers, caches, reports, or
adapter output.

Derived does not create an extension point. Every effect record must either carry a fact admitted
in the matching request record or be one of the exhaustive derivations below. A derivation may use
only the matching request, the prior accepted book state, the operation's assigned position and
recordedAt value, or a verified manifest. It may not introduce a record type, field, relationship,
or semantic mutation outside the effect catalog and operation profile.

| Operation profile | Exhaustive permitted derivations |
|:--|:--|
| book-genesis | canonical initial active lifecycle state implied by the declared identity, founder bindings, grants, and policy rules; no autonomous workflow may be active at Genesis |
| account lifecycle and tax registration | resulting active state and normalized relationship rows implied by the requested lifecycle mutation |
| typed posting | generated postingId and commandId; recordedAt; resolved posting origin; journal lines; and only the tax, foreign-exchange, inventory, accrual, fixed-asset, financing, foreign-currency, or payroll facts admitted by its exact operation-kind row below |
| attach-posting-approval | the approved posting relationship named by the request |
| interim-result-sweep and fiscal-year-close | generated posting IDs, journal lines, currency totals, and close relationships calculated from the admitted period request and prior accepted balances |
| backup-created | no derivation beyond the acknowledged verified artifact tuple |
| restore-book | restoration provenance derived from the verified manifest and the new restoration-derived chain position |
| rekey-book | the resulting key epoch and recorded rekey instant |
| enroll-key, rollover-key, revoke-key, alter-policy | only the binding, revocation, capability-grant, capability-policy, and system-workflow-policy facts explicitly requested |

An operation with an effect fact that is neither request-supported nor in this table is
attestation-request-profile-invalid. A profile may be narrowed by its operation kind, but it may
never be widened by an adapter, a database trigger, or a future implementation convention.

### Request And Effect Profiles

| Operation profile | Required request tags | Allowed effect tags |
|:--|:--|:--|
| book-genesis | 0100, 0101, 0102, 0103, 0183 | 0001, 0002, 0003, 0005 |
| account lifecycle | 0100, 0110; 0111 and 0112 as applicable | 0010, 0011, 0012 |
| tax registration | 0100, 0113; 0114 as applicable | 0013, 0014 |
| typed posting | 0100 plus the exact request groups in the closed per-kind matrix | the exact effect groups in the closed per-kind matrix |
| attach-posting-approval | 0100, 0125 | 0022 |
| interim-result-sweep | 0100, 0120, 0140; 0141 when sourceChannel is system | 0020, 0025, 0040, 0041, 0042 |
| fiscal-year-close | 0100, 0120, 0140; 0141 when sourceChannel is system | 0020, 0025, 0043, 0044 |
| backup-created | 0100, 0150 | 0006 |
| restore-book | 0100, 0160 | 00A0 |
| rekey-book | 0100, 0170 | 0007 |
| enroll-key or rollover-key | 0100, 0180 | 0002 |
| revoke-key | 0100, 0181 | 0004 |
| alter-policy | 0100; one or more of 0182, 0183, 0184 | matching 0003, 0005, 0008 only |

For alter-policy, each request record maps to exactly one effect record of its paired kind: 0182 to
0005, 0183 to 0003, and 0184 to 0008. All non-mutation fields are byte-for-value equal; no effect
record may lack its paired request or share it with another effect. The effect mutation is the only
derived field and expresses the resulting append-only lifecycle transition. The post-operation
quorum and purpose-eligible capacity rules apply before this operation can be accepted.

Every effect posting.fact operationStepOrder must match one request.posting stepOrder in the same
operation, and every effect record referring to a postingId, account, lifecycle ID, credential,
policy, or backup must be justified by the corresponding admitted request record or the exhaustive
derivation rule above. A generated identifier may occur only in an effect record.

### Closed Per-Kind Posting Matrix

The following groups are the canonical owner of Version-1 typed-posting admissibility. A group
names every request and effect tag it contributes; a row permits no tag outside its listed groups.
`B` is required for every typed posting. `D` is the raw-journal profile. `T` and `X` are optional
paired record groups; `S` admits one settlement adjunct and its exact B-journal projection where
the selected row permits it. `R` is the total compensating-projection relation for the narrowly admitted
non-lifecycle reversal origins; it contributes no caller-selected fact beyond B. A row that lists a
group as required requires every one of that group's request tags and at least the corresponding
effect facts; the effect's generated IDs remain derivations.
Tag admission is only the outer grammar: field multiplicity, role admission, account linkage,
amount use, journal balance, and the exact request-to-effect relation are closed by the semantic
profiles in
[DOC_02_VerifiableOperationAttestationProfiles.md](./DOC_02_VerifiableOperationAttestationProfiles.md).

| Group | Request tags | Effect tags |
|:--|:--|:--|
| B: typed-posting base | 0100, 0120, 0124 | 0020, 0021, 0025 |
| D: direct journal | 0100, 0120, 0124, 012A | 0020, 0021, 0025 |
| T: tax selection | 0126 | 0023 |
| X: quoted foreign exchange | 0127 | 0024 |
| S: settlement adjunct | 0129 | none beyond B's 0025 journal lines |
| I: inventory | 0123, 0128 | 0030, 0031 |
| A1: accrual or cut-off origin | 0130 | 0050 |
| A2: accrual or cut-off application | 0130 | 0051 |
| L1: Latvian payroll run | 0134 | 0090 |
| L2: Latvian payroll settlement | 0134 | 0092, 0093 |
| F1: fixed-asset capitalization | 0131 | 0060 |
| F2: fixed-asset application | 0131 | 0061 |
| F3: fixed-asset disposal or reversal | 0131 | 0062 |
| N1: financing origination | 0132 | 0070 |
| N2: financing application | 0132 | 0071 |
| N3: financing reversal | 0132 | 0072 |
| O1: foreign-currency obligation | 0133, 0127 | 0024, 0080 |
| O2: foreign-currency settlement | 0133, 0127 | 0024, 0081 |
| O3: foreign-currency reversal | 0133 | 0082 |
| R: compensating reversal | none beyond B | exact inverse B lines and, only when present on the admitted original, 0023 and 0024 |

| Operation kinds | Required groups | Optional groups |
|:--|:--|:--|
| post-entry | D | X |
| execute-plan | one or more canonical child mutation bundles | none beyond the selected child bundles |
| record-sale-settled, record-sale-on-credit, record-purchase-settled, record-purchase-on-credit, record-expense-settled, record-expense-on-credit | B | T |
| record-receipt, record-payment | B | S |
| record-owner-contribution, record-owner-withdrawal | B | none |
| record-opening-position | D | none |
| record-inventory-capitalization-settled, record-inventory-capitalization-on-credit, record-inventory-write-down, record-inventory-shrinkage, record-inventory-count-increase | B, I | none |
| record-prepayment, record-deferred-revenue, record-accrued-expense | B, A1 | none |
| record-accrual-cutoff-recognition, record-accrued-expense-settlement | B, A2 | none |
| record-latvian-monthly-payroll | B, L1 | none |
| record-latvian-payroll-net-wage-settlement, record-latvian-payroll-state-remittance | B, L2 | none |
| record-fixed-asset-capitalization | B, F1 | none |
| record-fixed-asset-depreciation | B, F2 | none |
| record-fixed-asset-disposal | B, F3 | none |
| record-financing-borrowing | B, N1 | none |
| record-financing-principal-repayment, record-financing-interest-accrual, record-financing-interest-payment | B, N2 | none |
| record-foreign-currency-obligation | B, O1 | none |
| record-realized-foreign-exchange-settlement | B, O2 | none |
| record-reversal | B, R | none |

`execute-plan` is one attested operation with exactly one 0100 request.command. It retains each
successful child mutation's canonical immutable request and effect bundle, rather than emitting a
separate chain operation for each child. Every request.posting stepOrder names one posting child
operation kind and must independently satisfy that child's exact row after removing 0100 from its
B group. Its effect records carry the same stepOrder or the generated postingId linked to that
step; account and tax-registration children retain their own canonical identity records. The
aggregate cannot use the union of unrelated rows as a discretionary extension point.
The aggregate and every mutating child share the one authenticated head observed before the
plan's first write admission. After the children succeed, FinGrind compares that same head before
the aggregate signer runs. A concurrent advance is a `stale-head` refusal, not a plan journal
entry: the full plan transaction rolls back and the caller re-signs against the reported current
head.

`record-reversal` names the admitted original posting in
request.posting.priorPostingId and may reverse it only under the closed reversal relation in the
semantic profiles.

`request.account-role.role` is closed to cash-account, receivable-account, payable-account,
revenue-account, inventory-account, expense-account, equity-account, settlement-adjunct-account,
prepayment-account, deferred-revenue-account, accrued-expense-account, fixed-asset-account,
accumulated-depreciation-account, depreciation-expense-account, financing-principal-account,
financing-interest-account,
foreign-exchange-gain-account, foreign-exchange-loss-account, tax-payable-account,
tax-receivable-account, result-holding-account, capital-account, retained-result-account,
write-down-loss-account, shrinkage-loss-account, count-gain-account, prepayment-asset-account,
accrued-expense-liability-account, gain-on-disposal-account, loss-on-disposal-account,
loan-payable-account, interest-expense-account, interest-payable-account, wage-expense-account,
employer-social-contribution-expense-account, net-wages-payable-account,
employee-social-contribution-payable-account, employer-social-contribution-payable-account, and
personal-income-tax-payable-account.
`request.money.role` is closed to gross-amount, net-amount, tax-amount, foreign-amount,
functional-amount, principal-amount, interest-amount, carrying-amount, settlement-amount, and
depreciation-amount, recognition-amount, proceeds-amount, unit-cost, and opening-balance-amount.
`request.quantity.role` is exactly inventory-quantity or opening-quantity.
The matrix and the profile document determine which of these closed facts is semantically required.
An unlisted role, duplicate role, wrong multiplicity, unconsumed request fact, or tag outside the
selected row is attestation-request-profile-invalid.

## Backup Artifacts And Receipts

[DOC_02_VerifiableOperationAttestationArtifacts.md](./DOC_02_VerifiableOperationAttestationArtifacts.md)
is the canonical owner of backup-manifest, artifact-publication, restore, and receipt/anchor
contracts, including the artifact golden vectors. This protocol retains the shared envelope grammar,
historical authorization rules, and record facts consumed by the verifier.

## Verification, Compromise Review, And Failure Taxonomy

[DOC_02_VerifiableOperationAttestationVerification.md](./DOC_02_VerifiableOperationAttestationVerification.md)
is the canonical owner of verifier procedure, compromise review, the closed structural-rejection
vocabulary, and deterministic failure precedence. It consumes this protocol's immutable envelope,
preimage, historical-authorization, and semantic-profile facts without redefining them.

## Operation Envelope Golden Vectors

[DOC_02_VerifiableOperationAttestationVectors.md](./DOC_02_VerifiableOperationAttestationVectors.md)
is the canonical owner of the byte-for-byte single- and two-principal operation-envelope vectors.
The backup-manifest, receipt, and parser vectors remain owned by
[DOC_02_VerifiableOperationAttestationArtifacts.md](./DOC_02_VerifiableOperationAttestationArtifacts.md).

## Static Corpus

The normative static-book and artifact source ledger, including positive sources, exact negative
mutations, and live-CAS admission attempts, is
[DOC_02_VerifiableOperationAttestationCorpus.md](./DOC_02_VerifiableOperationAttestationCorpus.md).

## Implementation Boundary

The protocol is implemented as one atomic public format break. Every book mutation reaches the
same immutable append boundary with an operation kind owned by the public command catalog; the
SQLite transaction commits attestation evidence and bookkeeping effects together. No legacy public
mutation path, unauthenticated recovery path, legacy format reader, or compatibility adapter
remains.
