---
afad: "5.0.1"
version: "0.61.0"
domain: BOOK_OPERATION_ATTESTATION
updated: "2026-07-19"
scope:
  paths: ["contract", "core", "executor", "sqlite", "cli", "docs"]
  symbols: ["AttestedOperation", "AttestationEnvelope", "BackupManifest", "AttestationReceipt"]
route:
  keywords: [verifiable-operation-attestation, operation-head, attestation-envelope, backup-manifest, receipt-anchor, principal-quorum, credential-purpose, per-kind-profile, failure-precedence, ed25519, stale-head]
  questions: ["what does FinGrind book-operation attestation prove", "how is an attested operation encoded", "which credential may authorize a system operation", "which effect records may a typed operation create", "how does FinGrind verify an attestation receipt"]
stage: "Slice 0 feature-branch specification; not released behavior"
---

# Verifiable Operation Attestation Protocol

This is the normative contract for FinGrind's next accepted protected-book format. It is a
feature-branch specification, not a claim about a released command, accepted book, or persisted
schema. Slice 5 makes this one hard format break: earlier books are rejected, with no mode,
migration, alias, or compatibility path.

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
64; a policy, key, or grant mutation is invalid if its post-operation state would leave any
configured quorum greater than its eligible-principal count for that capability.

## Principal Registry, Policy, And Operation Kinds

Registry and policy facts are append-only. For operation order N greater than zero, policy and key
eligibility resolve from the fold through N minus one. A binding or revocation committed at N first
affects N plus one. No record may set a retrospective or future-effective credential interval.

An active credential is an enrolled or rolled-over Ed25519 key that has not been revoked. Every
binding also has one immutable credentialPurpose: operator or system. An operator credential may
sign an operation only when sourceChannel is cli. A system request must contain at least one active
system credential; its other quorum signatures may be operator co-signatures. Purpose does not
constrain a manifest or receipt because neither carries a source channel. Purpose belongs to the
credential binding, not to an inferred principal name, external key-store label, or caller claim.
A new binding is the only way to change a credential's purpose; history retains the purpose that
was effective at its operation position.

A principal is eligible for one capability exactly when it owns at least one active credential and
its latest principal.capability-grant fact for that capability is GRANT. Capability grants are
append-only facts; ALTER_POLICY may add a GRANT or REVOKE fact but may not rewrite prior authority.
There is no hidden role or scope table.

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
| closeKind | interim-result-sweep, fiscal-year-close |
| journal side | debit, credit |
| taxDirection | payable, receivable |
| mutation | encoded u8 values defined in Immutable Preimages; never a token |

Other token fields in the record catalog are the already-owned accounting vocabularies documented
by their bounded contexts. Their values are normalized to their published lowercase wire token
before preimage encoding; a display label, enum name, or localized text is never signed.

### Autonomous System Initiation

The sourceChannel field records the origin of a signed request; it is not authority. cli means the
request was initiated through an operator-facing surface. system means FinGrind derived the request
from already committed book facts while executing an autonomous accounting workflow, such as the
posting steps required by an interim-result sweep or fiscal-year close. A human-requested command,
including one submitted by automation on that human's behalf, is cli.

A system-initiated operation has at least one system-purpose enrolled credential in its envelope.
That credential has no ambient or implicit authority: it needs the same active key, capability
grant, exact quorum, distinct-principal, observed-head, and historical-policy checks as every
other signer. Its private key remains outside the book and is subject to the same custody rule.
system is valid only for an operation whose request and effect are mechanically derived from the
stated autonomous workflow and already committed facts; it is invalid for a caller-supplied
request, a caller-authored journal entry, or a discretionary policy decision. A cli request has
only operator-purpose envelope credentials. The signed request therefore proves the credential
purpose that authorized its asserted provenance, but cannot prove an external human did or did not
press a button.

Version 1 permits sourceChannel=system only for interim-result-sweep, fiscal-year-close,
record-accrual-cutoff-recognition, record-fixed-asset-depreciation, and
record-financing-interest-accrual. Those kinds remain subject to their normal capability and
request/effect profiles; every other operation kind requires sourceChannel=cli. A system channel
outside this closed set is attestation-request-profile-invalid.

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
log, CLI DTO, contract DTO, or generic accounting object.

| Class | Applies to | Required boundary |
|:--|:--|:--|
| one atomic operation | business entries, execute-plan, account/tax/registry/policy mutation, close, and rekey | one SQLite transaction contains domain rows, immutable preimages, envelope, and head |
| sequence of signed operations | a command genuinely unable to be one transaction | each step is independently signed, has exact preimages, and has its own chain position |
| staged external artifact | backup-book and restore-book | the filesystem boundary uses the ordered manifest protocol below; it is never claimed to be one SQLite transaction |

## Version 1 Exclusions And Future Format Breaks

Version 1 is Ed25519-only. It does not support hybrid signatures, ML-DSA, mixed-algorithm quorums,
or an algorithmId per signature entry. Although the JDK supports ML-DSA key conversion, including
X.509 and PKCS#8 encodings, through [JEP 497](https://openjdk.org/jeps/497), adding it requires a
new payload and envelope version rather than a compatibility branch.

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
  || algorithmId(token = "ed25519")
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
| 0113 | request.tax-registration | registrationId:text!, jurisdiction:text!, registrationCode:text!, payableAccountCode:text!, receivableAccountCode:text!, active:bool! | registrationId |
| 0114 | request.tax-registration-code | registrationId:text!, taxCode:text!, rate:scaled!, effectiveFrom:date!, effectiveTo:date? | registrationId, taxCode, effectiveFrom |
| 0120 | request.posting | stepOrder:u32!, operationKind:token!, effectiveDate:date!, postingKind:token!, priorPostingId:uuid?, reversalReason:text? | stepOrder |
| 0121 | request.account-role | stepOrder:u32!, role:token!, accountCode:text! | stepOrder, role |
| 0122 | request.money | stepOrder:u32!, role:token!, amount:money! | stepOrder, role |
| 0123 | request.quantity | stepOrder:u32!, role:token!, quantity:scaled! | stepOrder, role |
| 0124 | request.evidence-document | stepOrder:u32!, sourceDocumentId:text!, sourceDocumentType:text!, documentDate:date! | stepOrder, sourceDocumentId |
| 0125 | request.posting-approval | postingId:uuid!, approvalId:text!, approverReference:text!, approverType:token!, decision:token!, approvedAt:instant! | postingId, approvalId |
| 0126 | request.tax-selection | stepOrder:u32!, registrationId:text!, taxCode:text! | stepOrder, registrationId, taxCode |
| 0127 | request.foreign-exchange | stepOrder:u32!, foreignCurrency:currency!, foreignAmount:money!, functionalAmount:money!, exchangeRate:scaled! | stepOrder |
| 0128 | request.inventory-relief | stepOrder:u32!, inventoryAccountCode:text!, costOfSalesAccountCode:text!, quantity:scaled! | stepOrder |
| 0129 | request.settlement-adjunct | stepOrder:u32!, accountCode:text!, amount:money! | stepOrder, accountCode |
| 0130 | request.accrual-cutoff | stepOrder:u32!, cutoffId:uuid!, cutoffKind:token!, recognitionStart:date?, recognitionEnd:date? | stepOrder, cutoffId |
| 0131 | request.fixed-asset | stepOrder:u32!, assetId:uuid!, assetClass:token?, usefulLifeMonths:u32? | stepOrder, assetId |
| 0132 | request.financing | stepOrder:u32!, arrangementId:uuid! | stepOrder, arrangementId |
| 0133 | request.foreign-currency-obligation | stepOrder:u32!, obligationId:uuid!, settlementId:uuid? | stepOrder, obligationId, settlementId |
| 0134 | request.payroll | stepOrder:u32!, payrollRunId:uuid!, employeeReference:text?, payrollMonth:text?, taxBookHeldAtEmployer:bool?, dependantCount:u8? | stepOrder, payrollRunId |
| 0140 | request.period-close | closeKind:token!, effectiveFrom:date?, effectiveTo:date?, fiscalYear:u32?, resultHoldingAccountCode:text?, capitalAccountCode:text?, retainedResultAccountCode:text? | closeKind, effectiveTo |
| 0150 | request.backup-acknowledgement | backupId:uuid!, backupArtifactDigest:hash!, sourceOrder:u64!, sourceHead:hash! | backupId |
| 0160 | request.restore | backupId:uuid!, backupArtifactDigest:hash!, sourceOrder:u64!, sourceHead:hash! | backupId |
| 0170 | request.rekey | keyEpoch:u64!, reason:text? | keyEpoch |
| 0180 | request.credential-binding | principalId:uuid!, keyId:hash!, bindingAction:token!, spki:spki!, credentialPurpose:token!, predecessorKeyId:hash? | principalId, keyId |
| 0181 | request.credential-revocation | keyId:hash!, principalId:uuid!, reason:text? | keyId |
| 0182 | request.policy-change | capability:token!, quorum:u16! | capability |
| 0183 | request.principal-capability-grant | principalId:uuid!, capability:token!, grantState:token! | principalId, capability |

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
| 0010 | account.state | mutation:u8!, accountCode:text!, accountName:text!, accountType:token!, nodeKind:token!, parentAccountCode:text?, unitOfMeasure:text?, active:bool! | accountCode |
| 0011 | account.classification | mutation:u8!, accountCode:text!, classificationFamily:token!, classification:token! | accountCode, classificationFamily |
| 0012 | account.relationship | mutation:u8!, accountCode:text!, relationshipKind:token!, targetAccountCode:text? | accountCode, relationshipKind |
| 0013 | tax.registration | mutation:u8!, registrationId:text!, jurisdiction:text!, registrationCode:text!, payableAccountCode:text!, receivableAccountCode:text!, active:bool! | registrationId |
| 0014 | tax.registration-code | mutation:u8!, registrationId:text!, taxCode:text!, rate:scaled!, effectiveFrom:date!, effectiveTo:date? | registrationId, taxCode, effectiveFrom |
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
| 0050 | accrual-cutoff | mutation:u8!, cutoffId:uuid!, cutoffKind:token!, originPostingId:uuid!, recognitionStart:date?, recognitionEnd:date?, deferredOrAccruedAmount:money! | cutoffId |
| 0051 | accrual-cutoff-application | mutation:u8!, cutoffId:uuid!, applicationOrder:u64!, postingId:uuid!, applicationKind:token!, recognizedAmount:money!, reversalPostingId:uuid? | cutoffId, applicationOrder |
| 0060 | fixed-asset | mutation:u8!, assetId:uuid!, originPostingId:uuid!, assetClass:token!, capitalizationAmount:money!, serviceDate:date!, usefulLifeMonths:u32! | assetId |
| 0061 | fixed-asset-application | mutation:u8!, assetId:uuid!, applicationOrder:u64!, postingId:uuid!, applicationKind:token!, amount:money!, period:date! | assetId, applicationOrder |
| 0062 | fixed-asset-reversal | mutation:u8!, reversalPostingId:uuid!, assetId:uuid!, reversedApplicationOrOriginId:uuid! | reversalPostingId |
| 0070 | financing-arrangement | mutation:u8!, arrangementId:uuid!, originPostingId:uuid!, principal:money!, commencementDate:date! | arrangementId |
| 0071 | financing-application | mutation:u8!, arrangementId:uuid!, applicationOrder:u64!, postingId:uuid!, principalAmount:money!, interestAmount:money!, effectiveDate:date! | arrangementId, applicationOrder |
| 0072 | financing-reversal | mutation:u8!, reversalPostingId:uuid!, arrangementId:uuid!, reversedApplicationOrOriginId:uuid! | reversalPostingId |
| 0080 | foreign-currency-obligation | mutation:u8!, obligationId:uuid!, originPostingId:uuid!, currency:currency!, foreignAmount:money!, functionalAmount:money! | obligationId |
| 0081 | foreign-currency-settlement | mutation:u8!, obligationId:uuid!, settlementId:uuid!, postingId:uuid!, settlementAmount:money!, realizedGainLoss:money! | obligationId, settlementId |
| 0082 | foreign-currency-reversal | mutation:u8!, reversalPostingId:uuid!, obligationOrSettlementId:uuid! | reversalPostingId |
| 0090 | latvian-payroll-run | mutation:u8!, payrollRunId:uuid!, employeeReference:text!, payrollMonth:text!, withholdingProfile:token!, grossAmount:money!, netAmount:money!, taxContributionAmount:money! | payrollRunId |
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
| book-genesis | canonical initial active lifecycle state implied by the declared identity, founder bindings, grants, and policy rules; no other state |
| account lifecycle and tax registration | resulting active state and normalized relationship rows implied by the requested lifecycle mutation |
| typed posting | generated postingId and commandId; recordedAt; resolved posting origin; journal lines; and only the tax, foreign-exchange, inventory, accrual, fixed-asset, financing, foreign-currency, or payroll facts admitted by its exact operation-kind row below |
| attach-posting-approval | the approved posting relationship named by the request |
| interim-result-sweep and fiscal-year-close | generated posting IDs, journal lines, currency totals, and close relationships calculated from the admitted period request and prior accepted balances |
| backup-created | no derivation beyond the acknowledged verified artifact tuple |
| restore-book | restoration provenance derived from the verified manifest and the new restoration-derived chain position |
| rekey-book | the resulting key epoch and recorded rekey instant |
| enroll-key, rollover-key, revoke-key, alter-policy | only the binding, revocation, policy, and capability-grant facts explicitly requested |

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
| interim-result-sweep | 0100, 0140 | 0020, 0025, 0040, 0041, 0042 |
| fiscal-year-close | 0100, 0140 | 0020, 0025, 0043, 0044 |
| backup-created | 0100, 0150 | 0006 |
| restore-book | 0100, 0160 | 00A0 |
| rekey-book | 0100, 0170 | 0007 |
| enroll-key or rollover-key | 0100, 0180 | 0002 |
| revoke-key | 0100, 0181 | 0004 |
| alter-policy | 0100, 0182; 0183 as applicable | 0003, 0005 |

Every effect posting.fact operationStepOrder must match one request.posting stepOrder in the same
operation, and every effect record referring to a postingId, account, lifecycle ID, credential,
policy, or backup must be justified by the corresponding admitted request record or the exhaustive
derivation rule above. A generated identifier may occur only in an effect record.

### Closed Per-Kind Posting Matrix

The following groups are the canonical owner of Version-1 typed-posting admissibility. A group
names every request and effect tag it contributes; a row permits no tag outside its listed groups.
`B` is required for every typed posting. `T` and `X` are optional paired groups: their request and
effect tags occur together. `R` means the effect tags of the verified operation being reversed,
with every lifecycle effect using its catalogued reversal form where one exists. A row that lists a
group as required requires every one of that group's request tags and at least the corresponding
effect facts; the effect's generated IDs remain derivations.

| Group | Request tags | Effect tags |
|:--|:--|:--|
| B: base posting | 0100, 0120, 0121, 0122, 0124 | 0020, 0021, 0025 |
| T: tax selection | 0126 | 0023 |
| X: quoted foreign exchange | 0127 | 0024 |
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

| Operation kinds | Required groups | Optional groups |
|:--|:--|:--|
| post-entry | B | T, X |
| execute-plan | one or more independently ordered child rows from this table | none beyond the selected child rows |
| record-sale-settled, record-sale-on-credit, record-purchase-settled, record-purchase-on-credit, record-expense-settled, record-expense-on-credit, record-receipt, record-payment, record-owner-contribution, record-owner-withdrawal, record-opening-position | B | T |
| record-inventory-capitalization-settled, record-inventory-capitalization-on-credit, record-inventory-write-down, record-inventory-shrinkage, record-inventory-count-increase | B, I | T |
| record-prepayment, record-deferred-revenue, record-accrued-expense | B, A1 | T |
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

`execute-plan` is one attested operation with exactly one 0100 request.command. Every
request.posting stepOrder names one child operation kind and must independently satisfy that
child's exact row after removing 0100 from its B group. Its effect records carry the same stepOrder
or the generated postingId linked to that step; it cannot use the union of unrelated rows as a
discretionary extension point. `record-reversal` names the admitted original posting in
request.posting.priorPostingId and may reverse only the original operation's closed profile.

`request.account-role.role` is closed to cash-account, receivable-account, payable-account,
revenue-account, inventory-account, expense-account, equity-account, settlement-adjunct-account,
prepayment-account, deferred-revenue-account, accrued-expense-account, fixed-asset-account,
accumulated-depreciation-account, financing-principal-account, financing-interest-account,
foreign-exchange-gain-account, foreign-exchange-loss-account, tax-payable-account,
tax-receivable-account, result-holding-account, capital-account, retained-result-account,
write-down-loss-account, shrinkage-loss-account, count-gain-account, prepayment-asset-account,
accrued-expense-liability-account, gain-on-disposal-account, loss-on-disposal-account,
loan-payable-account, interest-expense-account, and interest-payable-account.
`request.money.role` is closed to gross-amount, net-amount, tax-amount, foreign-amount,
functional-amount, principal-amount, interest-amount, carrying-amount, settlement-amount, and
depreciation-amount, unit-cost, and opening-balance-amount. `request.quantity.role` is exactly
inventory-quantity or opening-quantity.
The matrix determines which of these closed facts is semantically required; an unlisted role or a
tag outside the selected row is attestation-request-profile-invalid.

## Backup Manifest, Publication, And Restore

Signer identity belongs only in the envelope:

~~~text
manifestPayload =
  "FGATTBM1"
  || manifestVersion(u8 = 01)
  || bookId(uuid)
  || backupId(uuid)
  || sourceOrder(u64)
  || sourceOperationHead(hash)
  || snapshotDigest(hash)
  || algorithmId(token = "ed25519")

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
  || algorithmId(token = "ed25519")

receiptEnvelope = envelope(receiptPayload)
~~~

The receipt quorum resolves ANCHOR as of operationOrder. Receipt timestamps are signer-asserted
until a future counter-signature format. Export is non-mutating, uses the same atomic no-clobber
publication rule, and warns when its output remains inside the book's trust boundary.

verify-receipt is non-mutating. It verifies the receipt envelope, finds the referenced book and
operation order, requires equal bookId and operationHead, and evaluates the receipt's ANCHOR quorum
as of that order. A valid receipt retained beside the book is reported as valid-but-not-independent.

| Verifier holds | Detects | Does not detect |
|:--|:--|:--|
| book only | interior alteration, reordering, signatures, and authorization failures | truncation, rollback, or fork |
| retained receipt at K | rollback, truncation, or alteration through K | a fork strictly after K |
| latest known head | rollback or truncation through that head | a fork strictly after that head |
| append-only witness with mandatory submission, gossip, and consistency checks | equivocation among submitted and observed heads when both branches are revealed | a never-revealed branch |

## Verification, Compromise Review, And Failure Taxonomy

verify-book folds registry and policy, validates genesis, walks the chain, recomputes preimage
digests and operation heads, applies every historical envelope rule, checks chain linkage, and
reports the first structural break. A verifier checks a manifest independently from any
backup-created acknowledgement. It never reports a structural attestation defect as a generic
storage-runtime failure.

### Deterministic Failure Precedence

Every verifier applies the following phases in order and returns the first failure from the first
failing phase. It does not substitute a later cryptographic or storage failure for an earlier
structural classification. For an operation, phase 1 checks the payload version and grammar,
preimage field grammar and digests, the closed operation kind, and the exact per-kind profile;
phase 2 checks chain position and previous head; phase 3 resolves the historical policy; phase 4
checks sigCount against M; phase 5 rejects duplicate principal IDs; phase 6 rejects duplicate key
IDs; phase 7 rejects a strictly descending adjacent key ID; phase 8 rejects a non-Ed25519
credential; phase 9 checks enrollment, revocation, and key-to-principal binding; phase 10 verifies
signatures; phase 11 checks capability eligibility; and phase 12 checks credential purpose and the
sourceChannel rule. Equal key IDs are phase-6 duplicate keys, never an envelope-order failure.

Manifest and receipt verification applies the same common-envelope phases after its own version,
tuple, length, digest, and source-position checks. Genesis uses phases 1 and 4 through 10, then
checks its founder declaration, operator-purpose requirement, and unanimity rule as
attestation-genesis-invalid before any normal historical-policy or capability phase. A malformed
backup container that is not a valid manifest artifact is attestation-manifest-invalid; a receipt
whose tuple does not bind the resolved book head is attestation-receipt-invalid.

Compromise review is verifier input, never mutable book state. A review declaration is the tuple
credential keyId, firstAffectedOrder, and optional lastAffectedOrder; an omitted end means the
verified head. Its interval is inclusive. A valid operation signed by that credential in the
interval receives a reviewRequired finding containing that tuple and the operation order.
verify-book remains valid; require-clean-attestation changes any reviewRequired finding to exit 2.
attestation-review is the same non-persisted, full finding report.

The following are valid-result findings rather than structural failures: reviewRequired contains
the compromise-review tuple and affected operation order; receipt-not-independent reports a receipt
retained within the book's trust boundary. Both have exit 0 unless require-clean-attestation turns
reviewRequired into exit 2.

| Exact result | Meaning | Exit |
|:--|:--|:--:|
| attestation-unsupported-version | unknown profile version | 2 |
| attestation-preimage-invalid | malformed preimage, unknown record, field-count, presence, ordering, or digest failure | 2 |
| attestation-request-profile-invalid | request records do not match the operation profile | 2 |
| attestation-unknown-operation-kind | operation kind is outside the closed catalog | 2 |
| attestation-previous-head-invalid | previous head does not link | 2 |
| attestation-envelope-order-invalid | envelope entries are not strictly keyId ascending | 2 |
| attestation-quorum-below | sigCount is smaller than M | 2 |
| attestation-quorum-excess | sigCount is larger than M | 2 |
| attestation-duplicate-principal | a principal occurs more than once | 2 |
| attestation-duplicate-key | a key occurs more than once | 2 |
| attestation-key-not-enrolled | key was not active at the resolving position | 2 |
| attestation-key-revoked | key was revoked at the resolving position | 2 |
| attestation-key-principal-mismatch | key does not belong to the stated principal | 2 |
| attestation-key-algorithm-invalid | non-Ed25519 key or algorithmId | 2 |
| attestation-signature-invalid | signature does not verify | 2 |
| attestation-capability-invalid | signer is not eligible or policy quorum is impossible | 2 |
| attestation-credential-purpose-invalid | sourceChannel conflicts with enrolled credential purposes | 2 |
| attestation-genesis-invalid | genesis order, founders, policy, declared key, or unanimity rule fails | 2 |
| attestation-manifest-invalid | container, digest, source head, book identity, or BACKUP rule fails | 2 |
| attestation-receipt-invalid | receipt does not match the book, head, or ANCHOR rule | 2 |
| stale-head | live head changed before CAS admission | 2 |
| backup-acknowledgement-conflict | backupId was reused with different facts | 2 |
| artifact-already-exists | no-clobber target already exists | 7 |
| custodian-not-supported | caller selected an unshipped key custodian | 2 |

## Golden Vectors

All vector private seeds are public fixtures only, never production credentials. An encoder must
reproduce every declared payload, envelope, length, and digest byte-for-byte. A verifier must
return the listed exact result without falling through to a generic failure.

### V-OP-01: Single-Signer Operation Envelope

~~~text
privateSeed = 000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f
spki        = 302a300506032b657003210003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8
keyId       = a050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a5
principalId = 102132435465768798a9babcbddceeff
payload     = 46474154544f50310100112233445566778899aabbccddeeff000000000000002a137265636f72642d73616c652d736574746c65640765643235353139000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f323032362d30372d31375430333a33343a30302e3438355a202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f
signature   = 8f09b867c26f97cf7887d76fe87035b1ecf96ba078f816463e439d2d035e882288a6b4ec50951ba6e2bc7f28b954c1579e1fc37328a405b869644ff15f877d0e
head        = d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213
~~~

payload is 181 bytes. The envelope is payload, 0001, principalId, keyId, and signature; it is 295
bytes and SHA-256 equals head.

### V-OP-02: Complete Two-Principal Posting Quorum

This uses the V-OP-01 payload, principal A, and principal B. Under an M=2 POST resolver, both
principals are active and granted POST. keyB sorts before keyA, so B's entry precedes A's.

~~~text
signatureB = aa1ed4763cf3b2712c1826c25d43ff3d4cef5fb11ebd840ae97e57036f7003b2408a59ec16c9d3754ab1467d27488a96b455bb178182a4d56fd2d96d2b4be601
envelope   = 46474154544f50310100112233445566778899aabbccddeeff000000000000002a137265636f72642d73616c652d736574746c65640765643235353139000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f323032362d30372d31375430333a33343a30302e3438355a202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f0002112233445566778899aabbccddeeff00824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ecee6aa1ed4763cf3b2712c1826c25d43ff3d4cef5fb11ebd840ae97e57036f7003b2408a59ec16c9d3754ab1467d27488a96b455bb178182a4d56fd2d96d2b4be601102132435465768798a9babcbddceeffa050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a58f09b867c26f97cf7887d76fe87035b1ecf96ba078f816463e439d2d035e882288a6b4ec50951ba6e2bc7f28b954c1579e1fc37328a405b869644ff15f877d0e
head       = 1340639b39f477bde0427c9e347b9096e18ef19551ff288f88aa597f1347d45a
~~~

The envelope is 407 bytes and SHA-256 equals head. V-OP-01's payload and A signature are unchanged.

### V-MANIFEST-02: Complete Two-Principal Backup Quorum

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

### V-RECEIPT-02: Complete Two-Principal Anchor Quorum

~~~text
payload  = 46474154545243310100112233445566778899aabbccddeeff000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213323032362d30372d31375430343a30303a30302e3030305a0765643235353139
envelope = 46474154545243310100112233445566778899aabbccddeeff000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213323032362d30372d31375430343a30303a30302e3030305a07656432353531390002112233445566778899aabbccddeeff00824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ecee68f69835573aa8fe7afb8456eca706eb32700b4a19faf7fb544e8f9e55e49393bafa0316be4dd0a01362c2650df94e37ca857a994aac46a869f33c5d8a788320b102132435465768798a9babcbddceeffa050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a556cf223436c6e05b65040e26eb5674686e575846c4f4b78ff7645a7bfb2d5dddfeb0c7b76e67d4b2557a45c5499a1c890192d4daa2840b6b682da7be5cdff20e
head     = 42549e39bdb60205d16082d6e557c4c9d12e000a87b40f0974b2d82f62f3d0dc
~~~

The principals, keys, and seeds are exactly V-MANIFEST-02. payload is 97 bytes and envelope is
323 bytes.

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
00112233445566778899aabbccddeeff. Its full container is snapshot followed by the following
347-byte manifest envelope and the listed trailer:

~~~text
4647415454424d310100112233445566778899aabbccddeeff00112233445566778899aabbccddeeff000000000000002ad7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213be45cb2605bf36bebde684841a28f0fd43c69850a3dce5fedba69928ee3a899107656432353531390002112233445566778899aabbccddeeff00824c89aa8efb95ef93629b4519599129cace4adac9a6180daba31ceed41ecee67653ae182cf8e3eb9cfbfb479a11ac87effa34ea3b7deafbec65ca7a29fd4993a93f66ef8cd42fac7d2f3cef70f54cbe3f8a359c89ee3ebaa5e5397efce88406102132435465768798a9babcbddceeffa050837d85070582ccf7394b0988847cc312cb88259b894899f6f239cf1791a5555760252105dfdd5f3a45358581f7ede854f5c8ed7e156ee80a488a67c0da8c28a5c85a16d12d8d415448f8cfe6ee4558566a157ec51f97af4f22b4d5d45c0d
~~~

## Static Corpus

The normative static-book and artifact source ledger, including positive sources, exact negative
mutations, and live-CAS admission attempts, is
[DOC_02_VerifiableOperationAttestationCorpus.md](./DOC_02_VerifiableOperationAttestationCorpus.md).

## Implementation Boundary

Slice 1 owns the canonical preimage and envelope encoders and proves V-OP-01, V-OP-02,
V-MANIFEST-02, V-RECEIPT-02, and V-CONTAINER-01 byte-for-byte. Slice 2 owns the JCA file-PKCS#8
custody seam and rejects unshipped custodians. Slice 3 owns registry/policy folding and all
historical authorization outcomes. Slice 4 owns verification, review, static-book resources, and
the exact negative corpus. Slice 5 performs the only public hard format break and ships user, CLI,
response, schema, security, index, example, and changelog documentation together.
