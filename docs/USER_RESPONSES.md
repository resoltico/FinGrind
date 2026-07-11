---
afad: "4.0"
version: "0.60.0"
domain: OPERATOR_RESPONSES
updated: "2026-07-11"
route:
  keywords: [fingrind, response-json, payload, rejection, inspect-book, list-postings, account-balance, trial-balance, account-ledger, period-summary, output-mode, capabilities, execute-plan, report-output]
  questions: ["what response envelopes does fingrind return", "what does inspect-book return", "how does list-accounts pagination work in fingrind", "what execute-plan response does fingrind return", "what report payloads does fingrind return"]
---

# Response And Output Guide

**Purpose**: Show the output documents, response envelopes, and deterministic rejection or error
payloads returned by the CLI.
**Prerequisites**: Familiarity with [USER_CLI.md](./USER_CLI.md) and the request shapes in
[USER_REQUESTS.md](./USER_REQUESTS.md).

## CLI Output Shapes

| Output | Returned By | Fields |
|:-------|:------------|:-------|
| success envelope | `help`, `version`, `capabilities`, `environment`, `generate-book-key-file`, `open-book`, `rekey-book`, `backup-book`, `restore-book`, `inspect-rekey-rollback`, `restore-rekey-rollback`, `delete-rekey-rollback`, `declare-account`, `inspect-book`, `list-accounts`, `get-posting`, `list-postings`, `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `inventory-valuation`, `income-statement`, `cash-flow-statement`, `changes-in-equity` | `status`, `payload`, optional `artifacts[]` |
| raw request document | `print-request-template`, `print-plan-template` | canonical posting-request, declare-account-request, declare-tax-registration-request, or AI-agent ledger-plan scaffold JSON |
| `ok` | successful `preflight-entry` | `status`, `payload.idempotencyKey`, `payload.effectiveDate`, `payload.resolvedJournal` |
| `ok` | successful `record-sale-settled`, `record-sale-on-credit`, `record-expense-settled`, `record-expense-on-credit`, `record-receipt`, `record-payment`, `record-owner-contribution`, `record-owner-withdrawal`, `record-opening-position`, `record-reversal`, or `post-entry` | `status`, `payload.postingId`, `payload.idempotencyKey`, `payload.effectiveDate`, `payload.recordedAt`, `payload.idempotentReplay`, `payload.resolvedJournal` |
| `ok` | successful `execute-plan` | `status`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, and optional `payload.journal` |
| `rejected` | deterministically rejected `execute-plan` | `status`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, optional `payload.journal`, plus top-level `code` and `message` |
| `error` | assertion-failed `execute-plan` | `status`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, optional `payload.journal`, plus top-level `code` and `message` |
| `rejected` | deterministic single-command business rejection | `status`, `code`, `message`, optional `idempotencyKey`, optional `details` |
| `error` | malformed input or runtime failure | `status`, `code`, `message`, optional `hint`, optional `argument` |

Dynamic fields:
- `capabilities.payload` is stable unless the public command contract or runtime surface changes
- discovery JSON payloads from `help`, `capabilities`, and `version` now publish
  `payload.protocolVersion`, and the current hard-break line is `"21"`
- `docs/examples/request-template.json` and `docs/examples/ledger-plan-template.json` are
  checked-in source-copy companions for `print-request-template` and `print-plan-template`; both
  intentionally publish placeholder-first sample documents whose evidence and provenance values
  should be replaced before real-world use, and they expose the minimal settled-sale request
  scaffold and the minimal settled-sale plan scaffold respectively
- `generate-book-key-file.artifacts[]` is the canonical successful artifact publication surface;
  each entry carries `format` plus one redacted public `path`, and generated key files currently
  publish `format: "book-key-file"`
- `generate-book-key-file` succeeds only when the selected parent directory is already owner-only
  or can be created as one missing private directory
- `open-book.payload.initializedAt` is stamped from the FinGrind clock
- `open-book.payload.bookIdentity.entityName`, `.accountingKernelProfile`,
  `.accountingBasis`, `.accountingFrameworkPosition`, `.entityForm`, `.bookTemplateId`,
  `.functionalCurrency`, and `.fiscalYearStart` echo the persisted initialized-book identity
- `open-book.payload.bookIdentity.bookTemplateId` currently publishes either
  `OWNER_MANAGED_SERVICE` or `OWNER_MANAGED_TRADING`, while `.accountingBasis` distinguishes
  the cash-basis and accrual charts within the selected template family
- `declare-account.payload.outcome` is one of `declared`, `reactivated`, `renamed`, or
  `unchanged`
- `declare-account.payload.account.declaredAt` is stamped from the FinGrind clock on first
  declaration and preserved on later reactivations, renames, and unchanged replays
- `inspect-book.payload.bookFile` is a redacted public path hint for the selected book
- `list-accounts` exposes `limit` plus an optional opaque `nextCursor`
- `list-postings` exposes `limit` plus an optional opaque `nextCursor`
- `preflight-entry.payload.resolvedJournal` publishes the exact expanded journal plus semantic classification that passed the current advisory validation pass
- `committed.payload.postingId` is generated per successful commit as a UUID v7 value
- `committed.payload.recordedAt` is stamped from the FinGrind commit clock, not caller input
- `committed.payload.idempotentReplay` is true exactly when the submitted normalized request matched one already committed posting
- `committed.payload.resolvedJournal` publishes the exact expanded journal plus semantic classification attached to the committed posting result
- `payload.resultDetail` echoes whether the caller requested `summary` or `full`
- `payload.summary.startedAt`, `finishedAt`, aggregate step counts, and optional failure
  details are stamped from the FinGrind execution clock
- `payload.journal.startedAt`, `finishedAt`, and step timestamps are stamped from the
  FinGrind execution clock when `--result-detail full` is selected
- plan-journal steps carry typed `data` records rather than generic fact arrays
- successful `ensure-book` plan steps emit `initializedAt`, `entityName`, `functionalCurrency`,
  and `fiscalYearStart`; the persisted initialized-book identity also carries
  `accountingKernelProfile`, `accountingBasis`, `accountingFrameworkPosition`, `entityForm`, and
  `bookTemplateId`
- successful `declare-account` plan steps emit `outcome` plus `account`, using the shared
  declared-account payload
- successful committed posting steps, raw `post-entry`, and `get-posting` plan steps emit typed
  `evidence` data with source document and approval entries
- successful `assert-account-balance` plan steps emit typed `account` data plus repeated
  `balances[]`
- `execute-plan` accepts at most 100 steps, so returned plan summaries and optional full journals
  are complete but bounded

Successful `preflight-entry` output is advisory. It confirms that the current request passed
validation against the current book state, but it is not a durable commit guarantee:
the matching committing write command performs its authoritative transactional checks before
committing.

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
  request shapes, and cross-command facts
- `environment --output json` is the live runtime contract for distribution, runtime provenance,
  loaded SQLite facts, and launcher-local storage paths

## Capabilities Discovery Shape

`capabilities` is the canonical machine contract and exposes typed descriptors instead of raw string lists for the drift-prone parts of the surface. Every discovery JSON payload also carries one `payload.protocolVersion` field so callers can detect hard contract breaks directly. Operation ids, display labels, aliases, output modes, summaries, command groups, shared query limits, hard book-model facts, preflight facts, and currency facts are sourced from the contract protocol catalog before this response is rendered:

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
- `responseModel.rejections` is an array of deterministic business rejections rendered from the
  administration, query, and posting rejection families
- `responseModel.rejectionFields` publishes the shared top-level rejection envelope fields; its
  optional `payload` descriptor exists because `execute-plan` may still publish one rejected plan
  outcome body while lifting `code` and `message` to the top level
- `responseModel.postEntryRejectionFields` is the narrower posting-write rejection shape and
  intentionally omits that optional `payload`
- `responseModel.rejections[].detailRejections` publishes the nested stable rejection catalog for
  structured detail families such as `account-state-violations` and `entry-semantics-violations`
- `responseModel.errorDescriptors` is an array of deterministic CLI invocation/runtime error
  descriptors such as `invalid-page-cursor`, `protected-book-verification-failed`,
  `internal-defect`, `internal-error`, `managed-runtime-failure`, `storage-runtime-failure`,
  `pdf-export-failure`, and `interactive-prompt-unavailable`; each descriptor includes its
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
  `runtime.runtimeTrustBasis`, `runtime.loadedLibraryPath` as a redacted public path hint, and
  `runtime.loadedSqliteSourceId`
- `commands` also lists `print-plan-template` and `execute-plan`, both rendered from the contract
  protocol catalog

## Shared Response Payloads

Every response-side filesystem path field is a redacted public path hint that preserves only the
smallest trailing directory context needed for that response, for example
`<redacted>/books/acme.sqlite` or `<redacted>/backup/books/acme.sqlite`.

Shared initialized-book identity payload:
- `entityName`
- `accountingKernelProfile`
- `accountingBasis`
- `accountingFrameworkPosition`
- `entityForm`
- `bookTemplateId`
- `functionalCurrency`
- `fiscalYearStart`

Shared declared-account payload:
- `accountCode`
- `accountName`
- `accountType`
- `accountNodeKind`
- optional `parentAccountCode`
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
- `actorId`
- `actorType`
- `commandId`
- `idempotencyKey`
- `causationId`
- optional `correlationId`
- `sourceChannel`
- `evidence.sourceDocuments[]`, where each entry carries `sourceDocumentId`, `sourceDocumentType`,
  and `documentDate`; every posting retains at least one such source-document entry
- `evidence.approvals[]`, where each entry carries `approvalId`, `approvalType`, `approverId`,
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

## Book Initialization Responses

`open-book` success returns:
- `payload.bookFile`
- `payload.initializedAt`
- `payload.bookIdentity`, using the shared initialized-book identity payload

## Account Declaration Responses

`declare-account` success returns:
- `payload.outcome`, one of `declared`, `reactivated`, `renamed`, or `unchanged`
- `payload.account`, using the shared declared-account payload

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
`hard-break-reject-older-formats`, and the remaining migration-policy booleans are all `false`
for the current hard-break line.

`backup-book` success returns:
- `payload.bookFile`
- `artifacts[]`, where the current entries are `backup-file` and `backup-key-file`

`restore-book` success returns:
- `payload.bookFile`
- `payload.bookKeyFilePath`
- `artifacts[]`, where the current entries use `format: "book-file"` and
  `format: "book-key-file"`

`payload.bookKeyFilePath` and the published `book-key-file` artifact identify the destination key
file required to reopen the restored live `payload.bookFile`. The backup key remains source-only
and is not the restored live-book secret.

`inspect-rekey-rollback` success returns:
- `payload.bookFile`
- optional `artifacts[]`, where each current entry uses `format: "rollback-book-file"`

`restore-rekey-rollback` success returns:
- `payload.bookFile`
- `artifacts[]`, where the current entry uses `format: "rollback-book-file"`

`delete-rekey-rollback` success returns:
- `payload.bookFile`
- `artifacts[]`, where the current entry uses `format: "rollback-book-file"`

`list-accounts` success returns:
- `payload.context.bookIdentity`, using the shared initialized-book identity payload
- `payload.limit`
- optional `payload.nextCursor`
- `payload.accounts[]`, where each entry uses the shared declared-account payload

`get-posting` success returns:
- `payload.context.bookIdentity`, using the shared initialized-book identity payload
- `payload.posting`, using the shared posting payload

`list-postings` success returns:
- `payload.context.bookIdentity`, using the shared initialized-book identity payload
- optional `payload.context.accountCodeFilter`
- optional `payload.context.effectiveDateFrom`
- optional `payload.context.effectiveDateFromMeaning`
- optional `payload.context.effectiveDateTo`
- optional `payload.context.effectiveDateToMeaning`
- `payload.limit`
- optional `payload.nextCursor`
- `payload.postings[]`, where each entry uses the shared posting summary payload

`account-balance` success returns:
- `payload.context.bookIdentity`, using the shared initialized-book identity payload
- `payload.context.postingCoverage`
- `payload.accountCode`
- `payload.accountName`
- `payload.accountType`
- `payload.normalBalance`
- `payload.active`
- `payload.declaredAt`
- optional `payload.effectiveDateFrom`
- optional `payload.effectiveDateTo`
- `payload.balances[]`, where each bucket includes typed `debitTotal`, `creditTotal`,
  `netAmount`, and `balanceSide`

## Report Responses

Shared report context payload:
- `bookIdentity`, using the shared initialized-book identity payload
- `postingCoverage`
- optional `comparativeReferenceEffectiveDateFrom`
- optional `comparativeReferenceEffectiveDateTo`

Comparative report selection:
- `trial-balance`, `financial-position`, `income-statement`, `cash-flow-statement`, and `changes-in-equity` accept opt-in `--comparative` selection
- as-of reports accept `none`, prior period, or one explicit `..YYYY-MM-DD` comparison bound
- bounded-period reports accept `none`, prior period, or one explicit `YYYY-MM-DD..YYYY-MM-DD` comparison range
- when `--comparative none` is used or the option is omitted, the comparative reference dates and comparative row or section arrays are empty

`trial-balance` success returns:
- optional `payload.effectiveDateAsOf`
- `payload.context`, using the shared report context payload
- `payload.rows[]`, where each row includes `accountCode`, `accountName`, `accountType`, `normalBalance`, `active`, `declaredAt`, typed `debitTotal`, `creditTotal`, `netAmount`, and `balanceSide`
- `payload.comparativeRows[]`, using the same row shape when one comparative as-of selection is requested and resolved

`account-ledger` success returns:
- `payload.context`, using the shared report context payload
- `payload.accountCode`
- `payload.accountName`
- `payload.accountType`
- `payload.normalBalance`
- `payload.active`
- `payload.declaredAt`
- optional `payload.effectiveDateFrom`
- optional `payload.effectiveDateTo`
- `payload.openingBalances[]` and `payload.closingBalances[]`, where each bucket includes typed `debitTotal`, `creditTotal`, `netAmount`, and `balanceSide`
- `payload.entries[]`, where each row includes `postingId`, `postingKind`, `reversalState`, optional `reversalTarget`, optional `reversalReason`, `effectiveDate`, `recordedAt`, typed `debitAmount`, `creditAmount`, `runningBalance`, `runningBalanceSide`, `evidence.sourceDocuments[]`, optional `evidence.approvals[]`, and `counterpartAccounts[]`

`period-summary` success returns:
- `payload.context`, using the shared report context payload
- `payload.effectiveDateFrom`
- `payload.effectiveDateTo`
- `payload.postingCount`
- `payload.postingLineCount`
- `payload.accountsTouched`
- `payload.currencyTotals[]`, where each row includes typed `debitTotal`, `creditTotal`, `netAmount`, and `balanceSide`
- `payload.accountActivity[]`, where each row includes `accountCode`, `accountName`, `accountType`, `normalBalance`, `active`, `declaredAt`, typed `debitTotal`, `creditTotal`, `netAmount`, and `balanceSide`

`financial-position` success returns:
- optional `payload.effectiveDateAsOf`
- `payload.context`, using the shared report context payload
- `payload.sections[]`, where each section includes `accountType`, `rows[]`, and `totals[]`
- `payload.comparativeSections[]`, using the same section shape when one comparative as-of selection is requested and resolved

`inventory-valuation` success returns one shared report-model payload with an optional inclusive
`effectiveDateAsOf` context, one account table containing the inventory account, owned unit of
measure, exact quantity on hand, informational `roundedMovingAverageUnitCostProjection`, and exact
carrying value, plus ordered durable movement sections only when `--movements` is selected. The
carrying value is the exact inventory cost pool, never quantity multiplied by the rounded display
projection. `get-posting` likewise includes a costed sale's executor-derived cost of sales, relieved
quantity, and informational rounded moving-average unit-cost projection when those facts exist.

`income-statement` success returns:
- `payload.effectiveDateFrom`
- `payload.effectiveDateTo`
- `payload.context`, using the shared report context payload
- `payload.sections[]`
- `payload.netIncomeTotals[]`
- `payload.comparativeSections[]` and `payload.comparativeNetIncomeTotals[]` when one comparative period selection is requested and resolved

`cash-flow-statement` success returns:
- `payload.effectiveDateFrom`
- `payload.effectiveDateTo`
- `payload.context`, using the shared report context payload
- `payload.openingCashTotals[]`
- `payload.sections[]`, where each section includes `sectionKind`, `rows[]`, and `totals[]`
- `payload.movementTotals[]`
- `payload.closingCashTotals[]`
- `payload.comparativeOpeningCashTotals[]`, `payload.comparativeSections[]`, `payload.comparativeMovementTotals[]`, and `payload.comparativeClosingCashTotals[]` when one comparative period selection is requested and resolved

`changes-in-equity` success returns:
- `payload.effectiveDateFrom`
- `payload.effectiveDateTo`
- `payload.context`, using the shared report context payload
- `payload.rows[]`
- `payload.openingTotals[]`
- `payload.movementTotals[]`
- `payload.closingTotals[]`
- `payload.comparativeRows[]`, `payload.comparativeOpeningTotals[]`, `payload.comparativeMovementTotals[]`, and `payload.comparativeClosingTotals[]` when one comparative period selection is requested and resolved

## Execute-Plan Responses

`execute-plan` success returns:
- `payload.planId`
- `payload.status`
- `payload.resultDetail`
- `payload.summary.startedAt`
- `payload.summary.finishedAt`
- `payload.summary.stepCount`
- `payload.summary.succeededStepCount`
- `payload.summary.failedStepCount`
- optional `payload.summary.failedStepId`
- optional `payload.journal`, present when `--result-detail full` is selected

`payload.journal` carries:
- `startedAt`
- `finishedAt`
- `steps[]`, where each entry includes `stepId`, `kind`, `status`, `startedAt`, `finishedAt`,
  typed `data`, optional `detailKind`, and optional `failure`

Commands that advertise `--output` default successful stdout to text unless the current session
sets `FINGRIND_DEFAULT_OUTPUT=json`; `FINGRIND_DEFAULT_OUTPUT=text` restores the text default
explicitly, and a per-command `--output ...` flag always wins. Discovery, administration, write,
and read/report commands can also render operator-facing `--output text`, and the tabular
read/report commands support `--output csv` for spreadsheet import. Successful primary results own
stdout. Every non-plan deterministic failure or single-command business rejection uses one
canonical JSON diagnostics envelope on stderr instead, regardless of the selected success output
mode. Invalid invocation failures use that same diagnostics shape.
`tax-obligation`, `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `inventory-valuation`, `income-statement`, `cash-flow-statement`, and `changes-in-equity` can additionally write one PDF artifact through `--pdf-out <path>`. That PDF export reuses the same canonical result model; it does not change the JSON report payload itself, but successful JSON success envelopes now also publish one `artifacts[]` entry with `format: "pdf"` and one redacted artifact `path` hint. When `--pdf-out` is selected together with `--output text`, stdout renders one artifact confirmation block instead of the full report body. `--output csv` cannot be combined with `--pdf-out`. If the requested PDF artifact cannot be written, the command returns one deterministic `pdf-export-failure` error instead of a successful report payload.
Deterministic failures and single-command business rejections for commands that accept
`--output text` keep the same JSON diagnostics envelope rather than switching to a separate text
failure grammar.

Statement-report context also includes one comparative reference window derived from the selected book's fiscal-year anchor when one comparative selection is requested and resolved. Trial balance then carries `comparativeRows[]`; financial position carries `comparativeSections[]`; income statement carries `comparativeSections[]` and `comparativeNetIncomeTotals[]`; `cash-flow-statement` carries `comparativeOpeningCashTotals[]`, `comparativeSections[]`, `comparativeMovementTotals[]`, and `comparativeClosingCashTotals[]`; and changes in equity carries `comparativeRows[]`, `comparativeOpeningTotals[]`, `comparativeMovementTotals[]`, and `comparativeClosingTotals[]`.

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

| Code | Meaning | Extra `details` |
|:-----|:--------|:----------------|
| `book-already-initialized` | `open-book` targeted a book that is already initialized | none |
| `book-contains-schema` | `open-book` targeted a pre-existing SQLite file that already has schema objects | none |
| `administration-book-not-initialized` | an administration command targeted a book that does not exist or has not been opened yet | none |
| `query-book-not-initialized` | a query command targeted a book that does not exist or has not been opened yet | none |
| `posting-book-not-initialized` | a posting command targeted a book that does not exist or has not been opened yet | none |
| `account-type-conflict` | `declare-account` attempted to amend an existing account's immutable classification | `accountCode`, `existingAccountType`, `requestedAccountType` |
| `unknown-account` | a query named an undeclared account | `accountCode` |
| `posting-not-found` | `get-posting` targeted a posting id that does not exist in the selected book | `postingId` |
| `account-state-violations` | `preflight-entry`, one of the typed `record-*` commit commands, or raw `post-entry` found one or more undeclared, inactive, or non-postable accounts, one inventory movement that would backdate before an account horizon, one inventory quantity decrease that would drive on-hand quantity below zero, or one carrying-cost decrease that would drive an inventory pool below zero | `violations[]`, where each item includes `code`, `field`, `message`, `category`, `repair`, `accountCode`, and optional `accountNodeKind` |
| `inactive-account` | one item inside `account-state-violations.violations[]` named an inactive account | `code`, `field`, `message`, `category`, `repair`, `accountCode` |
| `non-postable-account` | one item inside `account-state-violations.violations[]` named a declared header account that cannot accept direct postings | `code`, `field`, `message`, `category`, `repair`, `accountCode`, `accountNodeKind` |
| `inventory-movement-precedes-account-horizon` | one item inside `account-state-violations.violations[]` named one inventory movement whose effective date would backdate before the selected inventory account's accepted horizon | `code`, `field`, `message`, `category`, `repair`, `accountCode` |
| `inventory-quantity-below-zero` | one item inside `account-state-violations.violations[]` named one inventory decrease that would drive exact quantity on hand below zero | `code`, `field`, `message`, `category`, `repair`, `accountCode` |
| `inventory-write-down-exceeds-carrying-cost` | one item inside `account-state-violations.violations[]` named one carrying-cost decrease that would drive an inventory pool below zero | `code`, `field`, `message`, `category`, `repair`, `accountCode` |
| `entry-semantics-violations` | `preflight-entry`, one of the typed `record-*` commit commands, or raw `post-entry` found one or more ordered entry-semantics conflicts in the selected posting request | `violations[]`, where each item includes `code`, `field`, `message`, `category`, and `repair` |
| `economic-null-journal` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` lines net every referenced account to zero | `code`, `field`, `message`, `category`, `repair` |
| `raw-journal-requires-cash-line` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` adjustment omits every declared cash account line on a cash-basis book | `code`, `field`, `message`, `category`, `repair` |
| `raw-journal-touches-inventory` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` contains one line whose declared account resolves to the inventory role, even though raw journals do not own exact inventory quantity truth | `code`, `field`, `message`, `category`, `repair` |
| `distinct-role-accounts-required` | one item inside `entry-semantics-violations.violations[]` found that two semantic role fields point to the same account even though the entry kind requires distinct accounts | `code`, `field`, `message`, `category`, `repair` |
| `account-type-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one referenced account uses the wrong declared `accountType` for the selected entry kind | `code`, `field`, `message`, `category`, `repair` |
| `cash-flow-asset-classification-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one referenced account uses the wrong declared `cashFlowAssetClassification` for the selected entry kind | `code`, `field`, `message`, `category`, `repair` |
| `financial-position-classification-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one referenced account uses the wrong declared `financialPositionLineClassification` for the selected entry kind | `code`, `field`, `message`, `category`, `repair` |
| `account-role-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one referenced account resolves to the wrong semantic `accountRole` for the selected entry kind | `code`, `field`, `message`, `category`, `repair` |
| `source-document-type-not-accepted` | one item inside `entry-semantics-violations.violations[]` found that the selected evidence uses one unsupported `sourceDocumentType` | `code`, `field`, `message`, `category`, `repair` |
| `unknown-tax-registration` | one item inside `entry-semantics-violations.violations[]` found that one tax selector references an undeclared `taxRegistrationId` | `code`, `field`, `message`, `category`, `repair` |
| `unknown-tax-code` | one item inside `entry-semantics-violations.violations[]` found that one tax selector references a `taxCode` not declared on the selected tax registration | `code`, `field`, `message`, `category`, `repair` |
| `tax-application-kind-mismatch` | one item inside `entry-semantics-violations.violations[]` found that one selected tax code resolves to an unsupported tax `applicationKind` for the entry kind | `code`, `field`, `message`, `category`, `repair` |
| `verb-requires-receivable-role` | one item inside `entry-semantics-violations.violations[]` found that the selected typed entry requires trade-receivable semantics that the current cash-basis book does not admit | `code`, `field`, `message`, `category`, `repair` |
| `verb-requires-payable-role` | one item inside `entry-semantics-violations.violations[]` found that the selected typed entry requires trade-payable semantics that the current cash-basis book does not admit | `code`, `field`, `message`, `category`, `repair` |
| `verb-requires-trading-template` | one item inside `entry-semantics-violations.violations[]` found that the selected inventory-purchase verb is admitted only on trading-template books | `code`, `field`, `message`, `category`, `repair` |
| `trading-sale-requires-inventory-relief` | one item inside `entry-semantics-violations.violations[]` found that a trading-template sale omitted the required `inventoryRelief` object | `code`, `field`, `message`, `category`, `repair` |
| `inventory-relief-requires-trading-book` | one item inside `entry-semantics-violations.violations[]` found that `inventoryRelief` appeared on a non-trading sale request | `code`, `field`, `message`, `category`, `repair` |
| `inventory-quantity-incompatible-with-unit-of-measure` | one item inside `entry-semantics-violations.violations[]` found that one inventory quantity field contradicts the selected inventory account's declared `unitOfMeasure` scale | `code`, `field`, `message`, `category`, `repair` |
| `inventory-acquisition-cost-not-exact` | one item inside `entry-semantics-violations.violations[]` found that one inventory acquisition's `quantity` and `unitCost` cannot compose one exact carrying-cost amount at the currency minor-unit boundary | `code`, `field`, `message`, `category`, `repair` |
| `inventory-acquisition-breaches-minor-unit-floor` | one item inside `entry-semantics-violations.violations[]` found that one inventory acquisition would leave a positive carrying-cost pool below the minimum minor-unit floor required to preserve zero-to-zero disposal truth | `code`, `field`, `message`, `category`, `repair` |
| `inventory-acquisition-foreign-exchange-functional-amount-mismatch` | one item inside `entry-semantics-violations.violations[]` found that a foreign-exchange inventory acquisition's retained functional amount differs from the exact pre-tax acquisition cost resolved from `quantity × unitCost` | `code`, `field`, `message`, `category`, `repair` |
| `inventory-capitalization-requires-quantity-on-hand` | one item inside `entry-semantics-violations.violations[]` found that a cost-only capitalization attempted to create a zero-quantity inventory pool | `code`, `field`, `message`, `category`, `repair` |
| `evidence-class-conflict` | one item inside `entry-semantics-violations.violations[]` found that the retained evidence class contradicts the event class resolved from the request | `code`, `field`, `message`, `category`, `repair` |
| `raw-journal-shadows-typed-event` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` resolves to one published typed business event and therefore must not be admitted through the raw path | `code`, `field`, `message`, `category`, `repair` |
| `raw-journal-bundles-operational-events` | one item inside `entry-semantics-violations.violations[]` found that the supplied `DIRECT_JOURNAL` bundles multiple operational business events into one posting | `code`, `field`, `message`, `category`, `repair` |
| `opening-window-account-not-permitted` | one item inside `entry-semantics-violations.violations[]` found that an `OPENING_POSITION` request referenced one account that the adoption opening window does not permit | `code`, `field`, `message`, `category`, `repair` |
| `opening-inventory-requires-quantity` | one item inside `entry-semantics-violations.violations[]` found that an `OPENING_POSITION` request omitted `openingBalances[].quantity` for an inventory account and therefore cannot establish exact inventory on hand | `code`, `field`, `message`, `category`, `repair` |
| `opening-quantity-requires-inventory` | one item inside `entry-semantics-violations.violations[]` found that a non-inventory opening balance carried `openingBalances[].quantity` | `code`, `field`, `message`, `category`, `repair` |
| `inventory-opening-must-be-first-movement` | one item inside `entry-semantics-violations.violations[]` found that an inventory opening followed an existing durable movement for that account | `code`, `field`, `message`, `category`, `repair` |
| `inventory-opening-carrying-cost-invalid` | one item inside `entry-semantics-violations.violations[]` found that an inventory opening did not supply a positive carrying cost consistent with its quantity | `code`, `field`, `message`, `category`, `repair` |
| `idempotency-key-conflict` | the selected book already contains the same `idempotencyKey`, but it is bound to a different committed posting request | none |
| `posting-effective-date-in-future` | the selected posting request uses an `effectiveDate` later than the current UTC date | `attemptedEffectiveDate`, `currentUtcDate` |
| `book-functional-currency-mismatch` | the selected posting request uses one journal-line or typed-entry currency that does not match the selected book functional currency | `functionalCurrency`, `attemptedCurrency` |
| `closed-period-violation` | the selected posting request uses an effective date that falls inside one transferred reporting period | `transferredThroughEffectiveDate`, `attemptedEffectiveDate` |
| `opening-position-window-closed` | `OPENING_POSITION` was submitted after the book already contains its first committed posting | `firstBlockingPostingKind`, `firstBlockingEffectiveDate` |
| `opening-position-touches-nominal-account` | `OPENING_POSITION` touched a revenue or expense account | `accountCode`, `accountType` |
| `reserved-result-classification` | the selected posting request touched one account whose `financialPositionLineClassification` is reserved for reporting-period closes | `accountCode`, `financialPositionLineClassification` |
| `reversal-target-not-found` | `reversal.priorPostingId` does not exist in the selected book | `priorPostingId` |
| `reversal-target-is-reversal` | `reversal.priorPostingId` already identifies one reversal posting | `priorPostingId` |
| `reversal-already-exists` | the target posting already has a full reversal | `priorPostingId` |
| `reversal-does-not-negate-target` | a reversal request does not negate the target posting exactly | `priorPostingId` |

`unknown-account` and `posting-not-found` are query-side rejections. `account-state-violations` is the posting-side rejection for account-registry failures, and its ordered `details.violations[]` payload owns the machine-readable per-issue repair guidance while the top-level machine `message` stays a stable count-summary and no top-level repair `hint` is emitted.
`entry-semantics-violations` follows the same rule for semantic contradictions inside one accepted request shape. The paired `--output text` surface renders one `Summary` header plus one `Issue N | <code>` section per violation for both nested repairable posting families.
Checked-in machine and operator examples live at [examples/account-state-violations-response.json](./examples/account-state-violations-response.json), [examples/account-state-violations-text.txt](./examples/account-state-violations-text.txt), [examples/entry-semantics-violations-response.json](./examples/entry-semantics-violations-response.json), and [examples/entry-semantics-violations-text.txt](./examples/entry-semantics-violations-text.txt).

Malformed JSON, wrong field types, missing required fields, invalid date/time text, and domain-validation failures return `status: "error"` with code `invalid-request`.
Argument and parsing failures may also carry a `hint` and `argument` field so a caller can correct the invocation mechanically.
Journal-entry validation now reports every detected journal grammar violation in one deterministic `invalid-request` response and publishes the full ordered set under `details.violations[]`, so callers can repair the whole request before retrying without scraping prose.

Deterministic CLI-side `status: "error"` examples are also checked in:
- [examples/invalid-page-cursor-error.json](./examples/invalid-page-cursor-error.json)
- [examples/protected-book-verification-failed-error.json](./examples/protected-book-verification-failed-error.json)
- [examples/interactive-prompt-unavailable-error.txt](./examples/interactive-prompt-unavailable-error.txt)

When you want those malformed-input or deterministic-error examples from the live CLI, rerun the
same command: the diagnostics envelope is JSON even when the selected success mode is text.
