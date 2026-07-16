---
afad: "4.0"
version: "0.60.0"
domain: OPERATOR_RESPONSES
updated: "2026-07-15"
route:
  keywords: [fingrind, response-json, payload, rejection, inspect-book, list-postings, account-balance, trial-balance, account-ledger, period-summary, output-mode, capabilities, execute-plan, tax-setup, amend-account, retire-account, report-output]
  questions: ["what response envelopes does fingrind return", "what does inspect-book return", "how does list-accounts pagination work in fingrind", "what execute-plan response does fingrind return", "what do amend-account and retire-account return", "what report payloads does fingrind return"]
---

# Response And Output Guide

**Purpose**: Show the output documents, response envelopes, and deterministic rejection or error
payloads returned by the CLI.
**Prerequisites**: Familiarity with [USER_CLI.md](./USER_CLI.md) and the request shapes in
[USER_REQUESTS.md](./USER_REQUESTS.md).

## CLI Output Shapes

| Output | Returned By | Fields |
|:-------|:------------|:-------|
| success envelope | `help`, `version`, `capabilities`, `environment`, `generate-book-key-file`, `open-book`, `rekey-book`, `backup-book`, `restore-book`, `inspect-rekey-rollback`, `restore-rekey-rollback`, `delete-rekey-rollback`, `declare-account`, `amend-account`, `retire-account`, `declare-tax-registration`, `inspect-book`, `list-accounts`, `get-posting`, `list-postings`, `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `inventory-valuation`, `accrual-cutoff-schedule`, `latvian-payroll-register`, `income-statement`, `cash-flow-statement`, `changes-in-equity`, `tax-obligation` | `status`, `payload`, optional `artifacts[]` |
| raw request document | `print-request-template`, `print-plan-template` | canonical posting-request, declare-account-request, declare-tax-registration-request, or AI-agent ledger-plan scaffold JSON |
| `ok` | successful `preflight-entry` | `status`, `payload.idempotencyKey`, `payload.effectiveDate`, `payload.resolvedJournal` |
| `ok` | successful typed `record-*` command, `post-entry`, or `record-reversal` | `status`, `payload.postingId`, `payload.idempotencyKey`, `payload.effectiveDate`, `payload.recordedAt`, `payload.idempotentReplay`, `payload.resolvedJournal` |
| `ok` | successful `execute-plan` | `status`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, and optional `payload.journal` |
| `rejected` | deterministically rejected `execute-plan` | `status`, `category`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, optional `payload.journal`, plus top-level `code` and `message` |
| `error` | assertion-failed `execute-plan` | `status`, `category`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, optional `payload.journal`, plus top-level `code` and `message` |
| `rejected` | deterministic single-command business rejection | `status`, `category`, `code`, `message`, optional `idempotencyKey`, optional `details`, optional `path`, optional `relatedPaths` |
| `error` | malformed input or runtime failure | `status`, `category`, `code`, `message`, optional `hint`, optional `argument`, optional `path`, optional `relatedPaths` |

Every non-success JSON envelope carries `category` with exactly one of `structural-invalid`, `domain-semantic`, `precondition`, `unsupported-selection`, or `internal`. `internal` means FinGrind detected or encountered a software failure rather than a caller, request, book-state, or supported-selection refusal. Success envelopes do not carry `category`.

Dynamic fields:
- `capabilities.payload` is stable unless the public command contract or runtime surface changes
- discovery JSON payloads from `help`, `capabilities`, and `version` publish
  `payload.protocolVersion`, and the current hard-break line is `"28"`
- `docs/examples/request-template.json` and `docs/examples/ledger-plan-template.json` are
  checked-in source-copy companions for `print-request-template` and `print-plan-template`; they
  publish the minimal settled-sale request scaffold and the placeholder-first atomic tax-setup
  plan scaffold respectively
- `generate-book-key-file --new-book-key-file` publishes its result through `artifacts[]`, the
  canonical successful artifact publication surface;
  each JSON entry carries `format` plus one canonical absolute `path`, and generated key files currently
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
- `amend-account.payload.outcome` is `amended` or `unchanged`; a successful amendment preserves
  the account identity and original `declaredAt`
- `retire-account.payload.outcome` is `retired` or `unchanged`; a retired account remains visible
  in history and can appear in a later historical reversal
- `inspect-book.payload.bookFile` is the canonical absolute path for the selected book
- `list-accounts` exposes `limit` plus an optional opaque `nextCursor`
- `list-postings` exposes `limit` plus an optional opaque `nextCursor`
- `account-ledger.payload.resolvedQuery.pagination` always publishes the accepted `limit` and
  `cursor` (`null` for the first page); `account-ledger.payload.nextCursor` is present only when a
  further page exists
- `account-balance.payload.resolvedQuery` and `account-ledger.payload.resolvedQuery` always
  publish their own `effectiveDateFrom` and `effectiveDateTo` fields; an omitted bound is `null`
  rather than an absent or unrelated query field
- `preflight-entry.payload.resolvedJournal` publishes the exact expanded journal plus semantic classification that passed the current advisory validation pass
- `committed.payload.postingId` is generated per successful commit as a UUID v7 value
- `committed.payload.recordedAt` is stamped from the FinGrind commit clock, not caller input
- `committed.payload.idempotentReplay` is true exactly when the submitted normalized request matched one already committed posting
- `committed.payload.resolvedJournal` publishes the exact expanded journal plus semantic classification attached to the committed posting result
- `get-posting.payload.posting.entry.latvianMonthlyPayroll.resolvedCalculation` publishes the exact executor-resolved contribution, tax, and net-wage facts retained with one Latvian payroll run
- `get-posting.payload.posting.entry.latvianPayrollSettlement.resolvedSettlement` publishes the exact executor-resolved liability accounts and payment components retained with one Latvian payroll settlement
- `latvian-payroll-register.payload` publishes every retained payroll run, including an unsettled run or a run and settlement that later received compensating reversals; the register is an operational reconciliation report, not an EDS filing
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
  request shapes, cross-command facts, and the canonical `fullContract.capabilityCatalog` scope
  list; `capabilities --output json --focus capability-catalog` returns that same list alone
- `environment --output json` is the live runtime contract for distribution, runtime provenance,
  loaded SQLite facts, and launcher-local storage paths

## Capabilities Discovery Shape

`capabilities` is the canonical machine contract and exposes typed descriptors instead of raw string lists for the drift-prone parts of the surface. Every discovery JSON payload also carries one `payload.protocolVersion` field so callers can detect hard contract breaks directly. Operation ids, display labels, aliases, output modes, summaries, command groups, shared query limits, hard book-model facts, preflight facts, and currency facts are sourced from the contract protocol catalog before this response is rendered:

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
maintenance lease, not only to maintenance rejection details.

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

`rekey-book` success returns `payload.bookFile`, `payload.newBookKeyFile`, and one
`book-key-file` artifact. The key file is newly generated at the requested absent target.

`inspect-rekey-rollback` success returns:
- `payload.bookFile`
- optional `artifacts[]`, where each current entry uses `format: "rollback-book-file"`

Inspection discovers sibling rollback artifact paths without opening the protected book, so it
accepts no passphrase source. `restore-rekey-rollback` and `delete-rekey-rollback` act on a
selected artifact and therefore require the current book passphrase source.

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

## Report Responses

Every JSON report payload has the following semantic spine:
- `family`, the command's stable report token
- `bookIdentity`, using the shared initialized-book identity payload
- `resolvedQuery`, a family-specific record of the accepted and resolved query inputs
- `generatedAt`, the time this result was produced
- family-specific result facts such as rows, sections, totals, tax obligations, or inventory movements

`resolvedQuery` records the request that FinGrind accepted and how defaults resolved. It is not a
replay guarantee: a later back-dated posting in an open period can change the same report. Exact
replay would require a separate durable book-revision capability.

All report money values use exact objects with `currencyCode` and integer-string `minorUnits`.
Enums are machine tokens. Report JSON deliberately contains no presentation `context`, columns,
cells, labels, alignment, or formatted money strings.

The family-specific query records contain only inputs that the corresponding command accepts:
- `account-balance`: `accountCode`, effective-date bounds, and `postingCoverage`
- `trial-balance` and `financial-position`: optional `asOf`, `postingCoverage`, and optional comparative range
- `account-ledger`: `accountCode`, effective-date bounds, `postingCoverage`, and
  `pagination { limit, cursor }`
- `period-summary`, `income-statement`, `cash-flow-statement`, and `changes-in-equity`: period bounds, `postingCoverage`, and optional comparative range
- `inventory-valuation`: optional `asOf` and whether movements were requested
- `tax-obligation`: `taxRegistrationId` and reporting-period bounds

`account-balance` returns the declared account and per-currency exact balances. `trial-balance`
returns flattened account rows, per-currency totals, balance state, and optional comparative rows.
`account-ledger` returns the account, opening and closing balances, one ascending keyset page of running-balance entries, and an optional top-level opaque `nextCursor`. The accepted cursor and next cursor are only navigation tokens; they do not provide a durable read snapshot or replay guarantee.
`period-summary` returns counts, currency totals, and account activity. `financial-position`,
`income-statement`, and `cash-flow-statement` return typed sections with rows and totals;
`income-statement` also returns net-income totals. `changes-in-equity` returns opening, movement,
and closing facts per equity line. `tax-obligation` returns registration facts, due date, tax-code
rows, and obligation totals.

`inventory-valuation` returns one row per inventory account with its owned unit of measure, exact
quantity on hand, exact carrying value, and informational
`roundedMovingAverageUnitCostProjection`; movement rows appear only when `--movements` is
selected. Carrying value is the exact inventory cost pool, never quantity multiplied by the rounded
projection. `get-posting` likewise includes a costed sale's executor-derived cost of sales, relieved
quantity, and informational rounded moving-average unit-cost projection when those facts exist.

`--output csv` emits a single typed table for each report family. Every monetary column is paired
as `<name>CurrencyCode` and `<name>MinorUnits`; CSV does not mix in report context or query metadata
rows. Use JSON when the complete semantic result and resolved query are needed. `--output text` and
`--pdf-out` remain human projections of the shared report model.

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
`tax-obligation`, `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `inventory-valuation`, `income-statement`, `cash-flow-statement`, and `changes-in-equity` can additionally write one PDF artifact through `--pdf-out <path>`. That PDF export reuses the same canonical result model; it does not change the JSON report payload itself, but successful JSON success envelopes now also publish one `artifacts[]` entry with `format: "pdf"` and its canonical absolute artifact `path`. When `--pdf-out` is selected together with `--output text`, stdout renders one artifact confirmation block instead of the full report body. `--output csv` cannot be combined with `--pdf-out`. If the requested PDF artifact cannot be written, the command returns one deterministic `pdf-export-failure` error instead of a successful report payload.
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

| Code | Meaning | Extra `details` |
|:-----|:--------|:----------------|
| `book-already-initialized` | `open-book` targeted a book that is already initialized | none |
| `book-contains-schema` | `open-book` targeted a pre-existing SQLite file that already has schema objects | none |
| `administration-book-not-initialized` | an administration command targeted a book that does not exist or has not been opened yet | none |
| `query-book-not-initialized` | a query command targeted a book that does not exist or has not been opened yet | none |
| `posting-book-not-initialized` | a posting command targeted a book that does not exist or has not been opened yet | none |
| `account-type-conflict` | `declare-account` attempted to amend an existing account's immutable classification | `accountCode`, `existingAccountType`, `requestedAccountType` |
| `account-not-found` | `amend-account` or `retire-account` named an account that is not declared in the selected book | `accountCode` |
| `account-has-dependents` | an account amendment or retirement is blocked by durable postings, tax registrations, or child accounts | `accountCode`, `dependencies[]` |
| `account-balance-not-zero` | `retire-account` targeted an account whose current balance is not zero | `accountCode` |
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
