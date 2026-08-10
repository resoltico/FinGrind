---
afad: "5.0.1"
version: "0.62.2"
domain: OPERATOR_RESPONSES
updated: "2026-08-10"
route:
  keywords: [fingrind, response-json, payload, attestation-diagnostics, inspect-book, list-postings, account-balance, trial-balance, account-ledger, period-summary, fixed-asset-register, output-mode, capabilities, execute-plan, tax-setup, amend-account, retire-account, report-output, source-artifact-identity-duplicated, source-artifact-identity-changed, pair-targets-conflict, target-owner-only-required, protected-book-pair-publication-evidence-blocked]
  questions: ["what response envelopes does fingrind return", "what does inspect-book return", "how does list-accounts pagination work in fingrind", "what execute-plan response does fingrind return", "what do amend-account and retire-account return", "what does fixed asset register return", "what report payloads does fingrind return", "where does capabilities publish exact attestation diagnostics", "what JSON does protected-book pair target admission return", "what does source-artifact-identity-duplicated mean", "what does source-artifact-identity-changed mean"]
---

# Response And Output Guide

**Purpose**: Show the CLI's output documents, shared response envelopes, and payload families.
**Prerequisites**: Familiarity with [USER_CLI.md](./USER_CLI.md) and the request shapes in
[USER_REQUESTS.md](./USER_REQUESTS.md).

## CLI Output Shapes

| Output | Returned By | Fields |
|:-------|:------------|:-------|
| success envelope | `help`, `version`, `capabilities`, `environment`, `generate-book-key-file`, `generate-attestation-key-file`, `inspect-attestation-key-file`, `open-book`, `rekey-book`, `backup-book`, `restore-book`, `enroll-key`, `rollover-key`, `revoke-key`, `alter-policy`, `verify-book`, `attestation-review`, `export-attestation-receipt`, `verify-receipt`, `declare-account`, `amend-account`, `retire-account`, `declare-tax-registration`, `inspect-book`, `list-accounts`, `get-posting`, `list-postings`, `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `inventory-valuation`, `accrual-cutoff-schedule`, `fixed-asset-register`, `financing-register`, `realized-foreign-exchange-register`, `latvian-payroll-register`, `income-statement`, `cash-flow-statement`, `changes-in-equity`, `tax-obligation` | `status`, `payload`, optional `artifacts[]` |
| raw request document | `print-request-template`, `print-plan-template` | canonical posting-request, declare-account-request, declare-tax-registration-request, or AI-agent ledger-plan scaffold JSON |
| `ok` | successful `preflight-entry` | `status`, `payload.idempotencyKey`, `payload.effectiveDate`, `payload.resolvedJournal` |
| `ok` | successful typed `record-*` command, `post-entry`, or `record-reversal` | `status`, `payload.postingId`, `payload.idempotencyKey`, `payload.effectiveDate`, `payload.recordedAt`, `payload.idempotentReplay`, `payload.resolvedJournal` |
| `ok` | successful `execute-plan` | `status`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, `payload.attestationDisposition`, `payload.attestationCommit`, and optional `payload.journal` |
| `rejected` | deterministically rejected `execute-plan` | `status`, `category`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, `payload.attestationDisposition: null`, `payload.attestationCommit: null`, optional `payload.journal`, plus top-level `code` and `message` |
| `error` | assertion-failed `execute-plan` | `status`, `category`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, `payload.attestationDisposition: null`, `payload.attestationCommit: null`, optional `payload.journal`, plus top-level `code` and `message` |
| `error` | `stale-head` during `execute-plan` admission | standard error fields plus `details.observedHead`, `details.currentHead`, and `details.currentOrder`; no plan payload |
| `error` | `attestation-review-window-exceeds-head` | standard error fields plus `details.credentialKeyId`, `details.firstAffectedOrder`, always-present nullable `details.lastAffectedOrder`, and `details.verifiedHeadOrder`; no verification payload |
| `error` | `unsupported-book-format-version` | standard error fields plus `details.detectedBookFormatVersion` and `details.supportedBookFormatVersion`; the selected protected book opened successfully but its FinGrind format is not this binary's exact current format |
| `rejected` | deterministic single-command business rejection | `status`, `category`, `code`, `message`, optional `hint`, optional `idempotencyKey`, optional `details`, optional `path`, optional `relatedPaths` |

For an open-ended compromise-review declaration, the error keeps the field rather than implying a
bound: in concise field notation this is `lastAffectedOrder: null`; the actual JSON member is
`"lastAffectedOrder": null`.
| `error` | malformed input or runtime failure | `status`, `category`, `code`, `message`, optional `hint`, optional `argument`, optional `path`, optional `relatedPaths` |

Every non-success JSON envelope carries `category` with exactly one of `structural-invalid`, `domain-semantic`, `precondition`, `unsupported-selection`, or `internal`. `internal` means FinGrind detected or encountered a software failure rather than a caller, request, book-state, or supported-selection refusal. Success envelopes do not carry `category`.

For protected-book pair target admission, `pair-targets-conflict` is a `rejected`,
`precondition` envelope with exit code `2` and
`details.{bookTarget,generatedSecretTarget}`. Those fields retain normalized absolute submitted
spellings rather than claiming a canonical physical path; if the spellings differ, the top-level
`path` is the book spelling and `relatedPaths` retains the generated-secret spelling. The
conflict is either a `Files.isSameFile`-established one-object pair of existing targets, exact raw
leaf equality for two absent targets in one physical parent, or a collision after canonical Unicode
decomposition plus root-locale case mapping. These initial admission refusals may leave a previously
admitted eligible missing parent, but create no final target, retained lease-control file, stage,
capability witness, reservation, claim, or pair-recovery-evidence artifact. See
[USER_REJECTIONS.md](./USER_REJECTIONS.md#protected-book-pair-target-admission) for
the exact rule and repair action.
`pathFailure: "target-owner-only-required"` instead identifies an existing protected-book source
or FinGrind recovery artifact that must be inspected but is not owner-only; correct the identified
`details.artifactPath` outside FinGrind before rerunning. A caller-owned ordinary no-clobber output
leaf is not inspected as a FinGrind artifact and receives that operation's exact occupied-target
rejection instead.
`pathFailure: "source-artifact-identity-duplicated"` instead identifies a later selected
maintenance source role whose artifact is the same physical file as an earlier selected source.
It names the later source at both `path` and `details.artifactPath`, keeps `relatedPaths` empty,
and is emitted before destination admission, staging, or book mutation. Select independent source
artifacts before retrying.
`pathFailure: "source-artifact-identity-changed"` instead identifies a source that post-lock
revalidation found no longer has the physical identity FinGrind locked. It uses the same
`artifactRole`, `artifactPath`, top-level `path`, and empty `relatedPaths` structure, and is also
emitted before destination admission. Keep every selected source stable, restore the trustworthy
intended source if it changed, then rerun the complete maintenance command.

Dynamic fields:
- `capabilities.payload` is stable unless the public command contract or runtime surface changes
- discovery JSON payloads from `help`, `capabilities`, and `version` publish
  `payload.protocolVersion`, and the current hard-break line is `"58"`
- `docs/examples/request-template.json` and `docs/examples/ledger-plan-template.json` are
  checked-in source-copy companions for `print-request-template` and `print-plan-template`; they
  publish the minimal settled-sale request scaffold and the placeholder-first general ledger-plan
  scaffold respectively; named plan topics emit the tax, fixed-asset, and financing setup scaffolds
- `generate-book-key-file --new-book-key-file` publishes its result through `artifacts[]`, the
  canonical successful artifact publication surface;
  each JSON entry carries `format`, one canonical `path`, and the mandatory immutable
  `retainedStage` publication fact;
  generated key files currently publish `format: "book-key-file"`
- `generate-attestation-key-file` requires `--attestation-custodian file-pkcs8` and
  `--new-attestation-key-file`, and publishes the created encrypted credential through `artifacts[]` as
  `format: "attestation-key-file"`; its payload exposes only canonical `credentialSpki` and
  derived lowercase-hex `keyId`. `inspect-attestation-key-file` also requires that explicit
  custodian selection, returns the same two public fields, and never emits an artifact, private
  key, or passphrase.
- `generate-book-key-file` succeeds only when the selected parent directory already exists and is
  owner-only; it validates but never creates, follows, weakens, or permission-repairs that secret
  parent directory
- `generate-attestation-key-file` likewise requires an existing non-symbolic-link parent directory
  and never creates one while publishing an encrypted credential
- `open-book` validates every caller-selected existing live-book or key-file parent and its
  resolved ancestry as real, owner-only, and non-mutable without changing permissions or ACLs. A
  missing live-book parent is created only by the atomic POSIX `0700` path and is otherwise
  refused on ACL-only filesystems.
- `open-book.payload.initializedAt` is stamped from the FinGrind clock
- `open-book.payload.bookIdentity.entityName`, `.accountingKernelProfile`,
  `.accountingBasis`, `.accountingFrameworkPosition`, `.entityForm`, `.bookTemplateId`,
  `.functionalCurrency`, `.fiscalYearStart`, and `.bookStartEffectiveDate` echo the persisted
  initialized-book identity
- `open-book.payload.bookIdentity.bookTemplateId` currently publishes either
  `OWNER_MANAGED_SERVICE` or `OWNER_MANAGED_TRADING`, while `.accountingBasis` distinguishes
  the cash-basis and accrual charts within the selected template family
- `declare-account.payload.outcome` is one of `declared`, `reactivated`, `renamed`, or
  `unchanged`
- `declare-account.payload.account.declaredAt` is stamped from the FinGrind clock on first
  declaration and preserved on later reactivations, renames, and unchanged replays
- `amend-account.payload.outcome` is `amended` or `unchanged`; a successful amendment preserves
  the account identity and original `declaredAt`
- `retire-account.payload.outcome` is `retired` or `unchanged`; a retired account remains visible
  in history and can appear in a later historical reversal
- `inspect-book.payload.bookFile` is the canonical absolute path for the selected book
- `list-accounts`, `list-postings`, `list-tax-registrations`, and `get-posting` use the shared
  query-result spine: `family`, `bookIdentity`, `resolvedQuery`, `generatedAt`, and their
  family-specific facts; every paginated collection keeps the accepted cursor in
  `resolvedQuery.cursor` and emits a top-level `nextCursor` only when a further page exists
- `account-ledger.payload.resolvedQuery.pagination` always publishes the accepted `limit` and
  `cursor` (`null` for the first page); `account-ledger.payload.nextCursor` is present only when a
  further page exists
- `account-balance.payload.resolvedQuery` and `account-ledger.payload.resolvedQuery` always
  publish their own `effectiveDateFrom` and `effectiveDateTo` fields; an omitted bound is `null`
  rather than an absent or unrelated query field
Mutation, plan, attestation, receipt, and typed payroll response facts are owned by
[USER_MUTATION_RESPONSES.md](./USER_MUTATION_RESPONSES.md).

Discovery output also has two intentionally different JSON scopes:
- `--detail minimal|compact|full` is accepted only when the resolved discovery output mode is JSON
- `help --output json` defaults to the compact command index with usage, getting-started hints,
  exit codes, and `protocolVersion`
- `help --output json --detail compact` returns the concise stable discovery payload with usage,
  getting-started hints, exit codes, and `protocolVersion`
- `help <command> --output json` returns one narrow command-local payload with canonical syntax,
  usage, options, examples, operator notes, and request-file guidance when that command accepts
  `--request-file`
- `help <command> --output json --detail compact` returns the stable command-local descriptor with
  canonical syntax, usage, options, examples, operator notes, and request-file guidance
- `help --output json --detail full` and `help <command> --output json --detail full` include the
  extended discovery body such as embedded templates, enum vocabularies, and request-shape details
- `capabilities --output json` defaults to the compact command, storage, and request-entry
  discovery contract with `protocolVersion`, while
- `capabilities --output json --detail compact` expands to the stable command, storage, and
  request-entry discovery contract plus the same `protocolVersion`
- `capabilities --output json --detail full` expands to the full doctrine, command grammar,
  request shapes, cross-command facts, and the canonical `fullContract.capabilityCatalog` scope
  list; `capabilities --output json --focus capability-catalog` returns that same list alone
- `environment --output json` is the live runtime contract for distribution, runtime provenance,
  loaded SQLite facts, and launcher-local storage paths

## Capabilities Discovery Shape

`capabilities` is the canonical machine contract and exposes typed descriptors instead of raw string lists for the drift-prone parts of the surface. Every discovery JSON payload also carries one `payload.protocolVersion` field so callers can detect hard contract breaks directly. Operation ids, display labels, aliases, output modes, summaries, command groups, shared query limits, hard book-model facts, preflight facts, and currency facts are sourced from the contract protocol catalog before this response is rendered:

- Every compact command surface and every full `CommandDescriptor` carries `name` and
  `displayLabel`. `displayLabel` is the exact `ProtocolCatalog` label for that operation; report
  renderers use the same owner for their document titles, so a caller must not maintain a parallel
  command-to-title table.

- `fullContract.capabilityCatalog` is the canonical ordered capability-scope list; each row carries an `id`, `scopeStatement`, `status`, and an `operativeBoundary` only when status is `PARTIAL`. `capabilities --output json --focus capability-catalog` publishes the same list as a focused slice.

- `requestShapes.bookkeepingEntry.topLevelFields`, `lineFields`, `provenanceFields`, and `reversalFields` are arrays of `{ "name", "presence", "description" }`
- `requestShapes.bookkeepingEntry.entryKindSemantics` is an array of `{ "entryKind", "requiredTopLevelFields", "forbiddenTopLevelFields", "requiredSourceDocumentFields", "sourceDocumentTypeMode", "acceptedSourceDocumentTypes", "sourceDocumentTypeSemantics", "semantics" }` sourced from the live request-surface owner
- command-scoped `requestShapes.bookkeepingEntry.sourceDocumentFields[]` rows and the embedded
  executable schema publish the selected `sourceDocumentType` policy directly, so callers do not
  have to inspect `entryKindSemantics[]` to validate one narrowed request shape
- `requestShapes.bookkeepingEntry.reachabilityMatrix` is an array of `{ "classificationFamily", "accountType", "classification", "declarable", "openingReachable", "operationalJournalReachable", "reversalReachable" }` generated from the live account-classification reachability doctrine
- `requestShapes.bookkeepingEntry.evidenceRequirement` is one machine object with `description` and `minimumSourceDocuments`
- `presence` is a live enum-backed machine value and is currently one of `required`, `conditional`, `optional`, or `forbidden`
- `requestShapes.schemaDialect` is the JSON Schema dialect URI used by the embedded executable schemas
- `requestShapes.bookkeepingEntry.schema`, `declareAccount.schema`, and `ledgerPlan.schema` are executable JSON Schema objects sourced from the live contract, not hand-maintained prose
- `requestShapes.*.enumVocabularies` are arrays of `{ "name", "values" }` sourced from the live enum constants
- `payload.fullContract.responseModel.rejections` is an array of deterministic business
  rejections rendered from the administration, query, and posting rejection families. Every row
  carries its exact `code`, `category`, non-negative `exitCode`, description, and nested contract
  descriptors, so automation can derive the process outcome without inferring it from a category.
  Its per-code `detailFields` descriptors publish the stable nested fields and closed enum
  vocabularies: read `rejections[code="artifact-path-invalid"].detailFields[name="pathFailure"]`
  for every maintenance path failure, including `source-artifact-identity-duplicated` and
  `source-artifact-identity-changed`, and
  `rejections[code="artifact-verification-failed"].detailFields[name="verificationFailure"]`
  for every maintenance artifact-verification failure.
- `payload.fullContract.responseModel.attestationAdmissionDiagnostics` is an array of
  `{ "context", "diagnostics" }` rows. Each `diagnostics` value is an array of exact
  `{ "code", "message", "hint" }` triplets. Its closed `context` vocabulary is
  `ordinary-live-admission`, `registry-mutation`, and `backup-acknowledgement`; use the selected
  context rather than assuming one attestation failure has one context-free operator message.
  Registry and backup-acknowledgement rows deliberately omit manifest and receipt failures, which
  belong to artifact verification rather than those two live append boundaries.
- `payload.fullContract.responseModel.attestationVerificationDiagnostics` is an array of
  `{ "surface", "diagnostics" }` rows using the same exact triplet shape. Its `surface` values
  are `verify-book`, `attestation-review`, `export-attestation-receipt`, and `verify-receipt`.
  `receipt-artifact-invalid` is emitted only by the `verify-receipt` row. Agents that need exact
  diagnostic text must read these two discovery arrays rather than embedding message or hint
  literals.
- `responseModel.rejectionFields` publishes the shared top-level rejection envelope fields; its
  optional `payload` descriptor exists because `execute-plan` may still publish one rejected plan
  outcome body while lifting `code` and `message` to the top level
- `responseModel.postEntryRejectionFields` is the narrower posting-write rejection shape and
  intentionally omits that optional `payload`
- `responseModel.rejections[].detailRejections` publishes the nested stable rejection catalog for
  structured detail families such as `account-state-violations` and `entry-semantics-violations`
- `responseModel.errorDescriptors` is an array of deterministic CLI invocation, execution, and
  recovery error
  descriptors such as `invalid-page-cursor`, `protected-book-verification-failed`,
  `unsupported-book-format-version`,
  `internal-defect`, `internal-error`, `managed-runtime-failure`, `storage-runtime-failure`,
  `pdf-export-failure`, `artifact-publication-outcome-uncertain`,
  `artifact-publication-durability-uncertain`, `publication-transaction-incomplete`,
  `open-book-publication-progress`, `open-book-preparation-artifacts-retained`,
  `protected-book-pair-publication-uncertain`,
  `protected-book-pair-publication-evidence-blocked`, and
  `interactive-prompt-unavailable`; each descriptor includes its
  published `exitCode`
- `responseModel.errorFields` publishes the shared top-level error envelope fields; its optional
  `payload` descriptor exists because `execute-plan` assertion failures still return the plan
  outcome body beside the lifted machine error diagnostics
- `preflight.semantics` carries the short machine hint and `preflight.commitGuarantee`
  carries the advisory-versus-guaranteed commit relationship
- `currencyModel` declares the current single-currency scope and the explicit
  `multiCurrencyStatus: "owned-foreign-exchange-only"`
- `bookkeepingKernel.scope` identifies the current executable kernel scope as one
  internal-management owner-managed-service bookkeeping kernel
- `bookkeepingKernel.builtInStatements` lists the shipped statement ids
- `bookkeepingKernel.reportCapabilities[]` describes each built-in statement id, the published
  `comparativeModes[]`, the `comparativeDefault`, and the live text description published by the
  contract
- `bookkeepingKernel.description` carries the current machine-published summary of that live
  bookkeeping kernel
- `requestInput.bookPassphraseOptions` advertises the supported protected-book passphrase routes
- `requestInput.bookPassphraseMaxUtf8Bytes` publishes the canonical UTF-8 byte ceiling shared by
  key-file, stdin, and prompt-backed passphrase sources
- `requestInput.requestDocumentMaxUtf8Bytes` publishes the canonical UTF-8 byte ceiling for one
  request JSON document
- `requestInput.requestDocumentSemantics` advertises the strict JSON-object, duplicate-key, and
  unknown-field rules
- `environment` reports runtime distribution, protected-book requirements, and managed SQLite
  metadata, including `requiredCompileOptions`, `forbiddenCompileOptions`,
  `requiresSecureMemorySupport`, `requiredSqliteSourceId`,
  `runtime.compileOptionsVerification`, `runtime.runtimeProvenance`,
  `runtime.runtimeTrustBasis`, `runtime.loadedLibraryPath` as a canonical absolute path, and
  `runtime.loadedSqliteSourceId`
- `commands` also lists `print-plan-template` and `execute-plan`, both rendered from the contract
  protocol catalog

## Shared Response Payloads

Every JSON response-side filesystem path field carries its canonical absolute path, including
success artifacts, environment diagnostics, and structured failure details. Failures expose their
primary offending path at top-level `path` and any companion locations at top-level
`relatedPaths[]`; their human `message` does not carry a path. Text and PDF renderers redact paths
for operator-facing display. This applies equally to generic deterministic failures such as an
invalid book or book-key path, an occupied generated-secret target, and a protected-book
maintenance lease, not only to maintenance rejection details. Receipt-success `receiptFile` values
are stronger: they identify the resolved canonical physical artifact location FinGrind published or
read, rather than only the normalized spelling supplied by the caller.

Shared initialized-book identity payload:
- `entityName`
- `accountingKernelProfile`
- `accountingBasis`
- `accountingFrameworkPosition`
- `entityForm`
- `bookTemplateId`
- `functionalCurrency`
- `fiscalYearStart`
- `bookStartEffectiveDate`, the immutable earliest effective date for accepted postings

Shared declared-account payload:
- `accountCode`
- `accountName`
- `accountType`
- `accountNodeKind`
- optional `parentAccountCode`
- optional `contraOfAccountCode`, identifying the active account whose statement line this account reduces
- optional `financialPositionLineClassification`
- optional `cashFlowAssetClassification`
- optional `profitAndLossLineClassification`
- optional `unitOfMeasure`, where the nested object carries `token` and `quantityScale`; this is
  present exactly for inventory accounts
- `normalBalance`
- `active`
- `declaredAt`

Shared resolved-journal payload:
- `expandedLines.effectiveDate`
- `expandedLines.lines[]`, where each line carries `accountCode`, `side`, and typed `amount`
- optional `appliedTax`
- optional `foreignExchangeDetails`
- `classification.eventClass`
- `classification.anchorSignature[]`, where each entry carries `accountRole` and `side`
- `classification.containedTypedEvents[]`
  This lists only the typed business-event pair classes contained in the resolved anchor signature.
  It is empty for structural outcomes such as `OPENING` and `REVERSAL`, and for genuine
  `ADJUSTMENT` journals. JSON publishes the full classifier set; text-mode posting output omits
  the line when that set is only the same singleton class already shown in `eventClass`.
- `classification.hasCashLine`
- `classification.evidenceClass`
- `classification.structural.adoptionOpeningEntry`
- optional `classification.structural.reversesPriorPostingId`

Shared posting payload:
- `postingId`
- `postingKind`
- `reversalState`
- `effectiveDate`
- `recordedAt`
- `commandId`
- `idempotencyKey`
- `causationId`
- optional `correlationId`
- `sourceChannel`
- `evidence.sourceDocuments[]`, where each entry carries `sourceDocumentId`, `sourceDocumentType`,
  and `documentDate`; every posting retains at least one such source-document entry
- `evidence.approvals[]`, where each entry carries `approvalId`, `approvalType`, `approverReference`,
  `approverType`, `decision`, and `approvedAt`
- optional `reversal.priorPostingId` and `reversal.reason`
- `lines[]`, where each line carries `accountCode`, `side`, and typed `amount`

Shared posting summary payload:
- `postingId`
- `postingKind`
- `postingOriginKind`
- `reversalState`
- optional `reversesPostingId`
- optional `reversedByPostingId`
- `effectiveDate`
- `recordedAt`
- `debitTotal`
- `creditTotal`
- `accountCodes[]`
- `sourceDocumentIds[]`
- `approvalIds[]`

Every response-side money object reuses the same exact money shape with `currencyCode` and
`minorUnits`.

Fixed-asset-register row payload:

- `cost`, `accumulatedDepreciation`, and `carryingAmount`, where `carryingAmount` is current and is zero after disposal
- optional `carryingAmountAtDisposal`, present exactly for a disposed row and preserving its immutable pre-disposal amount
- `capitalizedOn`, schedule fields, lifecycle dates, and optional `disposedOn`

## Book Initialization Responses

`open-book` success returns:
- `payload.bookFile`
- `payload.initializedAt`
- `payload.bookIdentity`, using the shared initialized-book identity payload
- `payload.attestationBookId` and `payload.attestationCommit`, whose order and head identify the
  genesis operation exactly
- `payload.attestationTrustRoot`, the genesis registry snapshot using the same credential,
  effective-capability-policy, principal-capability, and system-workflow-policy shapes returned by
  `verify-book`

## Account Declaration Responses

`declare-account` success returns:
- `payload.outcome`, one of `declared`, `reactivated`, `renamed`, or `unchanged`
- `payload.account`, using the shared declared-account payload
- `payload.attestationCommit`, or `null` exactly for `unchanged`

## Book Inspection And Query Responses

`inspect-book` success returns:
- `payload.bookFile`
- `payload.state`
- `payload.compatibleWithCurrentBinary`
- `payload.canInitializeWithOpenBook`
- optional `payload.applicationId`
- optional `payload.detectedBookFormatVersion`
- `payload.supportedBookFormatVersion`
- `payload.migrationPolicy`
- optional `payload.initializedAt`
- optional `payload.bookIdentity`, using the shared initialized-book identity payload

`payload.state` uses the stable lower-case vocabulary `missing`, `blank-sqlite`, `initialized`,
`foreign-sqlite`, `unsupported-format-version`, or `incomplete-fingrind`.
That `state` field is the canonical lifecycle discriminator; the JSON payload does not duplicate it
with a separate `initialized` flag.
`payload.canInitializeWithOpenBook` is true exactly when `open-book` may initialize the selected
path directly. The current public line reports `true` for `missing` and `blank-sqlite`, and
`false` for every other inspection state.
`payload.migrationPolicy.mode` is currently
`hard-break-reject-noncurrent-formats`, and the remaining migration-policy booleans are all `false`
for the current hard-break line.

`backup-book` success returns:
- `payload.bookFile`
- `payload.backupId`, the immutable acknowledgement tuple identifier
- `payload.pairPublicationCompletion`: `published` when this invocation durably published the
  backup/key pair, `recovered` when it reconciled the exact earlier completion-uncertain pair, or
  `already-published` when an acknowledgement retry verified the complete existing pair without
  publishing it again
- `payload.pairPublicationRetention`: required for `published` and `recovered`, with exactly
  `bookPublication.{path,retainedStage}` and
  `generatedSecretPublication.{path,retainedStage}`; `null` only for the `already-published`
  acknowledgement that has no FinGrind retained-stage evidence
- `payload.acknowledgementState`: `acknowledged` when this invocation appended the acknowledgement,
  `resumed` when it completed or observed an exact-tuple resume, or `already-present` when the
  acknowledgement was already in the live chain
- `payload.attestationCommit`, which is non-null exactly when this invocation appended the
  acknowledgement. It is always present for `acknowledged`, always absent for `already-present`,
  and may be either for `resumed`.
- `artifacts[]`, where the current entries are `backup-file` and `backup-key-file`

When the backup pair has been published but its live-book acknowledgement is refused by
current-head authorization reconstructed from immutable evidence, `backup-book` instead returns a
`structural-invalid` rejected envelope with the exact `attestation-*` code and exit code `2`. Its
message confirms that the pair remains published and its hint directs the caller to retain the pair
and rerun the same `--backup-id` tuple after correcting the credential set or policy. This is
distinct from a published
`acknowledgementState: pending` response with exit code `4`, which represents an operational
interruption rather than an authorization refusal. The rejected envelope's `details` retains
`bookFile`, `backupFile`, `backupKeyFile`, `backupId`, `pairPublicationCompletion`, and the same
nullable `pairPublicationRetention`, so the already published or recovered pair remains
machine-visible without a success payload.

`pairPublicationCompletion` and `acknowledgementState` answer separate questions: the former
describes the final pair's publication disposition, while the latter and nullable
`attestationCommit` describe the source-book acknowledgement. A recovered or already-published
pair therefore does not by itself say whether this invocation appended an acknowledgement.

`restore-book` success returns:
- `payload.bookFile`
- `payload.bookKeyFilePath`
- `payload.pairPublicationCompletion`: `published` when this invocation durably published the
  absent destination pair, or `recovered` when it reconciled the exact earlier
  completion-uncertain pair without another restore mutation
- `payload.pairPublicationRetention`, always non-null for restore, with exact
  `bookPublication.{path,retainedStage}` and
  `generatedSecretPublication.{path,retainedStage}` facts
- `payload.attestationCommit`
- `artifacts[]`, where the current entries use `format: "book-file"` and
  `format: "book-key-file"`

`payload.bookKeyFilePath` and the published `book-key-file` artifact identify the destination key
file required to reopen the restored live `payload.bookFile`. The backup key remains source-only
and is not the restored live-book secret.

Restore provenance is established by the destination's verified chain, not by duplicated source
facts in the restore response. If source `verify-book` recorded `bookId: B` and
`verifiedAttestationHead: { operationOrder: O, operationHead: H }` for the backup snapshot,
destination `verify-book` must report `B`,
`verifiedAttestationHead.operationOrder: O + 1`, and `previousHead: H`; its reported
`verifiedAttestationHead.operationHead` equals the restore response's
`payload.attestationCommit.operationHead`. A later source `backup-created` acknowledgement is not
the restored operation's predecessor.

`rekey-book` success returns `payload.bookFile`, `payload.newBookKeyFile`,
`payload.pairPublicationCompletion`, required `payload.pairPublicationRetention`,
`payload.attestationCommit`, and one `book-key-file` artifact. `pairPublicationCompletion` is
`published` when this invocation durably published the final pair or `recovered` when it
reconciled the exact earlier completion-uncertain pair without a new rotation mutation. The key
file is newly generated at the requested absent target.

`maintenance-recovery-pending` is a `rejected`, `precondition`, exit-`7` maintenance response,
not an exit-`4` completion-uncertain error. Before any stage, probe, reservation, or final
mutation, `backup-book`, `restore-book`, and `rekey-book` acquire and scan the full
source-and-target workflow scope for an owner record that binds the full exact workflow: source,
target pair, secret identity, and derived stages. JSON always supplies non-null
`details.recoveryOperation`, `details.bookTarget`,
and `details.generatedSecretTarget`; the operation is a canonical wire value and both targets are
canonical absolute paths. Text labels are `Recovery operation`, `Book target`, and `Generated
secret target`. Restart the named operation with complete original source, target, and secret
inputs. The three details do not reconstruct a backup source, backup ID, credential, or secret
material, and they cannot authorize a partial retry. Never rename, overwrite, delete, recreate,
or otherwise manually clean recovery evidence.

Malformed, legacy, incomplete, or internally inconsistent evidence cannot establish a safe
operation. It fails closed as the exit-`4`
`protected-book-pair-publication-evidence-blocked` error, not
`maintenance-recovery-pending`; it is never adopted, deleted, or manually repaired.

`protected-book-pair-publication-uncertain` is an exit-`4`, `precondition` error for
`backup-book`, `restore-book`, and `rekey-book`; it is not a successful result with a retained-stage
warning. Its top-level `argument` is explicitly `null`; `path` is the canonical book target and
`relatedPaths` includes the canonical generated-secret target and both retained stages when those
facts are established. Its `details.operation` names the maintenance operation that reported the
uncertainty; only verified pair evidence makes it a retained original-operation recovery
instruction. `details.pairPublication` contains distinct canonical `bookTarget.{path,state}` and
`generatedSecretTarget.{path,state}` objects. A member `state` is one of `not-attempted`,
`outcome-uncertain`, `published-durability-unconfirmed`, or `published-durable`. JSON always
includes nullable `details.pairPublication.recoveryRecordState`: it is `durably-retained` or
`durability-unconfirmed` exactly when both members are `not-attempted`, otherwise `null`. JSON
also always includes nullable `details.pairPublication.pairPublicationRetention`. When non-null,
its `bookPublication.{path,retainedStage}` and
`generatedSecretPublication.{path,retainedStage}` paths bind exactly to the two final targets.
`null` means no authoritative pair-stage fact was established, never that any evidence may be
cleaned.
Preserve FinGrind pair evidence and both final
paths. A verified completion-uncertain pair may be rerun only with the exact same operation and
complete original source, target, and secret inputs; FinGrind resumes only owner-recorded derived
stages. Never rename, overwrite, delete, recreate, or manually clean pair evidence or either final
member; do not start a fresh pair. When `recoveryRecordState` is non-null, preserve FinGrind's
recovery material too. A recovered rekey verifies the generated-key pair before
accessing the prior key.

`protected-book-pair-publication-evidence-blocked` is separate from completion uncertainty. Its
two pair-member states are `unestablished` and its `recoveryRecordState` is `null`: the evidence
cannot establish a safe final-member state or a recoverable operation. Its always-present nullable
`pairPublicationRetention` is `null` when no authoritative pair-stage fact is safe to report; that
does not permit cleanup. Preserve every reported path and investigate independently; do not rerun
or reconstruct a workflow from that error.
Those final-path values are the canonical physical targets admitted from each selected real private
parent, not necessarily the path spelling supplied by the caller.

`enroll-key`, `rollover-key`, `revoke-key`, and `alter-policy` success returns
`payload.bookFile`, `payload.operationKind`, and `payload.attestationCommit`. Each confirms that
the named immutable authorization mutation was
appended; JSON publishes the canonical absolute book path, while text redacts the local path. It
never exposes credential paths, passphrases, encrypted key contents, or a private signing result.
An authorization refusal is a `structural-invalid` rejected envelope with the exact
`attestation-*` code and exit code 2. A change that would leave a live policy with insufficient
eligible principals is the distinct `attestation-policy-capacity-invalid` refusal. An `enroll-key`
request for an already represented principal is refused before signing as
`attestation-duplicate-principal`; reusing an enrolled credential for another principal is
`attestation-duplicate-key`.

`verify-book` returns the verified book identity, immutable current head, its signed predecessor,
and complete chain-derived credential and policy registry snapshot, or the first typed structural
failure.
`attestation-review` returns non-persisted compromise-review findings for a structurally valid
chain. `export-attestation-receipt` and `verify-receipt` success each publish the complete receipt
anchor: `bookId` and
`payload.receiptAttestationAnchor.{operationOrder,operationHead}`. Receipt export also returns
the no-clobber receipt artifact's resolved canonical physical path and one
`attestation-receipt-v1` entry in `artifacts[]`; receipt verification returns the exact resolved
receipt path it read in JSON and its redacted receipt-file hint in text. See
[USER_BOOK_ATTESTATION.md](./USER_BOOK_ATTESTATION.md) for their distinct trust boundaries.

If source-book verification fails for `verify-book`, `attestation-review`, or receipt export, the
command returns a `structural-invalid` rejected envelope with exit code `2`, the exact failure
code, a surface-specific historical cause, and an evidence-preserving recovery hint. It never
misstates a historical failure as a live-head admission refusal. `verify-receipt` returns the same
exact-code shape for a decoded receipt or chain failure, but its hint first preserves the selected
receipt and directs comparison with a verified protected book.

`list-accounts` success returns:
- `payload.family` as `list-accounts`
- `payload.bookIdentity`, using the shared initialized-book identity payload
- `payload.resolvedQuery.limit` and the accepted opaque `payload.resolvedQuery.cursor`
- optional top-level `payload.nextCursor` only when another page exists
- `payload.generatedAt`
- `payload.accounts[]`, where each entry uses the shared declared-account payload

`list-tax-registrations` success returns:
- `payload.family` as `list-tax-registrations`
- `payload.bookIdentity`, using the shared initialized-book identity payload
- `payload.resolvedQuery.limit` and the accepted opaque `payload.resolvedQuery.cursor`
- optional top-level `payload.nextCursor` only when another page exists
- `payload.generatedAt`
- `payload.registrations[]`, where each entry uses the shared declared-tax-registration payload

`get-posting` success returns:
- `payload.family` as `get-posting`
- `payload.bookIdentity`, using the shared initialized-book identity payload
- `payload.resolvedQuery.postingId`
- `payload.generatedAt`
- `payload.posting`, using the shared posting payload

`list-postings` success returns:
- `payload.family` as `list-postings`
- `payload.bookIdentity`, using the shared initialized-book identity payload
- `payload.resolvedQuery.accountCodeFilter`, date bounds, `limit`, and accepted opaque `cursor`
- optional top-level `payload.nextCursor` only when another page exists
- `payload.generatedAt`
- `payload.postings[]`, where each entry uses the shared posting summary payload

## Report Responses

The report payload spine, query semantics, and output-mode contract are owned by
[USER_REPORT_RESPONSES.md](./USER_REPORT_RESPONSES.md).

## Execute-Plan Responses

`execute-plan` success returns:
- `payload.planId`
- `payload.status`
- `payload.resultDetail`
- `payload.attestationDisposition`, one of `appended`, `read-only`, or
  `no-durable-child-mutation`
- `payload.attestationCommit`, non-null exactly when `payload.attestationDisposition` is
  `appended`
- `payload.summary.startedAt`
- `payload.summary.finishedAt`
- `payload.summary.stepCount`
- `payload.summary.succeededStepCount`
- `payload.summary.failedStepCount`
- optional `payload.summary.failedStepId`
- optional `payload.journal`, present when `--result-detail full` is selected

Rejected and assertion-failed plan payloads retain both `payload.attestationDisposition` and
`payload.attestationCommit` as explicit `null` fields. A successful `read-only` plan proves it ran
through the dedicated credential-free read-only boundary; a successful
`no-durable-child-mutation` plan instead proves it used the mutation-capable boundary but committed
no durable child, for example because every applicable step replayed an already durable effect.

`payload.journal` carries:
- `startedAt`
- `finishedAt`
- `steps[]`, where each entry includes `stepId`, `kind`, `status`, `startedAt`, `finishedAt`,
  typed `data`, optional `detailKind`, and optional `failure`

When a mutating plan loses the attestation compare-and-swap race at either a child write or its
final aggregate operation, FinGrind rolls back the entire plan and returns the standard
`stale-head` error envelope with exit code `2`. It does not publish a rejected plan journal, since
none of that plan's mutations was admitted; reload the book state, re-sign against `currentHead`,
and retry.

Commands that advertise `--output` default successful stdout to text; a per-command `--output ...`
flag selects a supported alternative. Discovery, administration, write, and read/report commands
can also render operator-facing `--output text`, and the tabular
read/report commands support `--output csv` for spreadsheet import. Successful primary results own
stdout. Every non-plan deterministic failure or single-command business rejection uses one
canonical renderer on stderr. A valid explicit `--output json` selects the JSON diagnostics envelope
even when the command token is unknown. An absent, missing, duplicate, or invalid `--output`
selection uses the text diagnostics renderer; explicit `--output text` always stays text. CSV has
no error CSV grammar, so its failures also use the text diagnostics renderer. Unknown-command and
unsupported-output failures therefore use one deterministic rendering rule.
`tax-obligation`, `account-balance`, `trial-balance`, `account-ledger`, `period-summary`,
`financial-position`, `inventory-valuation`, `accrual-cutoff-schedule`, `fixed-asset-register`,
`financing-register`, `realized-foreign-exchange-register`, `latvian-payroll-register`,
`income-statement`, `cash-flow-statement`, and `changes-in-equity` can additionally write one PDF
artifact through `--pdf-out <path>`. Its selected parent must already exist as a real owner-only
directory whose resolved ancestry resists non-owner substitution, and the final target must be
absent; FinGrind does not create or weaken that caller-owned parent. The report result itself
remains unchanged, while successful JSON envelopes
publish one `artifacts[]` entry with `format: "pdf"` and the canonical physical final artifact
`path`. Before canonicalization, FinGrind scans every lexical component from the root through the
selected parent without following links and refuses any symbolic-link or non-directory component,
including a direct-parent alias. The entry also has mandatory `retainedStage`. The stage is
immutable evidence, not a cleanup handle. If the final-link
parent-directory force cannot confirm the published final path, the command returns
`artifact-publication-durability-uncertain` instead of a successful report payload. Its JSON
contains top-level `retainedStage` and
`details.publishedArtifact.{path,retainedStage}`. Preserve and inspect the final path and stage
before relying on the artifact, and do not retry that no-clobber target. When `--pdf-out` is
selected together with `--output text`, stdout renders one artifact confirmation block instead of
the full report body. `--output csv` cannot be combined with `--pdf-out`. If a no-replace
final-link attempt throws without establishing whether it created its canonical candidate path,
FinGrind returns `artifact-publication-outcome-uncertain` with
`details.{candidateArtifact,retainedStage}` and the same top-level stage when one exists. Preserve
the candidate and evidence and use a fresh destination for a new attempt. A pre-final PDF export
failure remains `pdf-export-failure` and exposes top-level `retainedStage` whenever applicable.
Deterministic failures and single-command business rejections follow that same selected-output
rule, so text output stays readable without making machine callers parse prose.

When comparative selection is requested, each statement report's `resolvedQuery` records the derived reference window from the selected book's fiscal-year anchor. Trial balance then carries `comparativeRows[]`; financial position carries `comparativeSections[]`; income statement carries `comparativeSections[]`, `grossProfitTotals[]`, `comparativeGrossProfitTotals[]`, and `comparativeNetIncomeTotals[]`; `cash-flow-statement` carries `comparativeOpeningCashTotals[]`, `comparativeSections[]`, `comparativeMovementTotals[]`, and `comparativeClosingCashTotals[]`; and changes in equity carries `comparativeRows[]`, `comparativeOpeningTotals[]`, `comparativeMovementTotals[]`, and `comparativeClosingTotals[]`.

Checked-in examples for the read/report surface:
- [examples/inspect-book-response.json](./examples/inspect-book-response.json)
- [examples/list-accounts-response.json](./examples/list-accounts-response.json)
- [examples/get-posting-response.json](./examples/get-posting-response.json)
- [examples/list-postings-response.json](./examples/list-postings-response.json)
- [examples/account-balance-response.json](./examples/account-balance-response.json)
- [examples/trial-balance-response.json](./examples/trial-balance-response.json)
- [examples/account-ledger-response.json](./examples/account-ledger-response.json)
- [examples/period-summary-response.json](./examples/period-summary-response.json)
- [examples/trial-balance-text.txt](./examples/trial-balance-text.txt)
- [examples/account-ledger.csv](./examples/account-ledger.csv)
- [examples/period-summary-text.txt](./examples/period-summary-text.txt)

FinGrind does not check PDF binaries into `docs/examples`; PDF export is verified through CLI,
bundle, and Docker smoke flows instead.

Checked-in template and ledger-plan examples:
- [examples/request-template.json](./examples/request-template.json)
- [examples/ledger-plan-template.json](./examples/ledger-plan-template.json)
- [examples/ledger-plan-request.json](./examples/ledger-plan-request.json)
- [examples/ledger-plan-query-request.json](./examples/ledger-plan-query-request.json)
- [examples/execute-plan-committed-response.json](./examples/execute-plan-committed-response.json)
- [examples/execute-plan-assertion-failed-response.json](./examples/execute-plan-assertion-failed-response.json)
- [examples/execute-plan-query-response.json](./examples/execute-plan-query-response.json)

## Deterministic Rejections

The canonical catalog, repair facts, and checked-in examples are in
[USER_REJECTIONS.md](./USER_REJECTIONS.md). It owns deterministic rejections and associated
error diagnostics; this guide continues to own the shared envelope and success-payload contract.
