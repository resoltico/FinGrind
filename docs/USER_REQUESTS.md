---
afad: "4.0"
version: "0.44.0"
domain: HUMAN_REQUESTS
updated: "2026-05-22"
route:
  keywords: [fingrind, request-json, response-json, provenance, reversal, idempotency, payload, rejection, inspect-book, list-postings, account-balance, trial-balance, account-ledger, period-summary, output-mode, ledger-plan, execute-plan]
  questions: ["what request json does fingrind accept", "what response envelopes does fingrind return", "how does list-accounts pagination work in fingrind", "what does inspect-book return", "what ledger plan shape does execute-plan accept"]
---

# Request And Response Guide

**Purpose**: Show the accepted JSON request shapes and the output documents returned by the CLI.
**Prerequisites**: Familiarity with the packaged CLI in [USER_CLI.md](./USER_CLI.md).

The checked-in `docs/examples/*.json` fixtures mentioned below exist only in a source checkout.
The public release bundle does not ship those repo paths.

Book-bound commands pair these JSON payloads with `--book-file` plus exactly one passphrase
source. When the selected book parent directory does not exist, `open-book` creates it with
owner-only protection; when it already exists, FinGrind requires it to remain owner-only:
- `--book-key-file` with a UTF-8 passphrase file protected by POSIX owner-only permissions
  (`0400` or `0600`) on macOS/Linux or a Windows owner-only ACL on Windows; its containing
  directory must also remain owner-only, and the public examples keep this file under a separate
  `./secrets/` tree rather than beside the book. `generate-book-key-file` creates a missing
  parent directory with owner-only protection and rejects a pre-existing non-private parent
  directory
- `--book-passphrase-stdin` with one UTF-8 passphrase payload up to 4096 bytes from standard
  input
- `--book-passphrase-prompt` with an interactive non-echo terminal prompt whose normalized UTF-8
  payload must also fit within the same 4096-byte limit

Every request JSON document must fit within FinGrind's `1048576`-byte UTF-8 payload limit whether
it comes from `--request-file <path>` or `--request-file -`.

`rekey-book` reuses those current-book routes and additionally requires exactly one replacement
passphrase source: `--replacement-book-key-file`, `--replacement-book-passphrase-stdin`, or
`--replacement-book-passphrase-prompt`.

## Posting Request Shape

Inspect the canonical posting-request scaffold:

```bash
fingrind print-request-template
```

Or, in a source checkout, inspect the checked-in exact scaffold:

```bash
cat docs/examples/request-template.json
```

The scaffold is intentionally agent-first: `provenance.actorType` is `AGENT`, and the emitted
document is a runnable sample with demo `effectiveDate`, source-document identity, and
provenance values. Replace that sample business context before real-world use. On one book, an
`idempotencyKey` becomes single-use per book after the first committed posting.

The packaged CLI can surface the same request-shape truth without leaving the terminal:
`help post-entry`, `help declare-account`, and `help execute-plan` inline one canonical template
plus the accepted fields and enum vocabularies for their `--request-file` payloads. When you need
the raw scaffold bytes directly, `print-request-template` now accepts the request-bearing topic
`declare-account` in addition to the posting-surface defaults.

Current posting-request rules:
- all top-level date, enum, identifier, and provenance fields are JSON strings
- `entryKind` is required and selects the posting recipe
- `amount` and `lines[].amount` both use one exact money object with `currencyCode` and `minorUnits`
- every money-object `currencyCode` must be one canonical three-letter uppercase ISO 4217 code
  supported by FinGrind's pinned currency registry
- every money-object `minorUnits` must contain ASCII digits only, must not contain redundant leading
  zeroes, must not exceed 19 digits, and must fit inside FinGrind's exact supported minor-unit
  range
- every money object must decode to one strictly positive posted amount
- `effectiveDate`, `evidence`, and `provenance` are required for every entry kind
- `CASH_REVENUE` requires `cashAccountCode`, `revenueAccountCode`, and `amount`
- `CASH_EXPENSE` requires `expenseAccountCode`, `cashAccountCode`, and `amount`
- `OWNER_CONTRIBUTION` requires `cashAccountCode`, `equityAccountCode`, and `amount`
- `OWNER_DRAW` requires `equityAccountCode`, `cashAccountCode`, and `amount`
- `MANUAL_ADJUSTMENT` requires `postingKind` plus `lines`
- `MANUAL_ADJUSTMENT.postingKind` must be `STANDARD` or `OPENING_BALANCE`
- `MANUAL_ADJUSTMENT.lines` must contain at least two journal lines
- `evidence.sourceDocuments` must contain at least one source-document object
- every `evidence.sourceDocuments[]` entry requires `sourceDocumentId`, `sourceDocumentType`,
  `documentDate`, `capturedAt`, `storageLocator`, and `contentSha256`
- `evidence.approvals` is required as an array and may be empty
- every `evidence.approvals[]` entry requires `approvalId`, `approvalType`, `approverId`,
  `approverType`, `decision`, and `approvedAt`
- `lines[].accountCode` must start with an ASCII letter or digit, may then contain only ASCII
  letters, digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 255 characters
- every `MANUAL_ADJUSTMENT` entry must contain at least one `DEBIT` line and at least one `CREDIT` line
- every line inside one `MANUAL_ADJUSTMENT` entry must share the same `lines[].amount.currencyCode`
- every posted money amount must use the selected book's functional currency
- `reversal` is optional only for `MANUAL_ADJUSTMENT`
- required provenance fields are `actorId`, `actorType`, `commandId`, `idempotencyKey`, and `causationId`
- `provenance.idempotencyKey` must start with an ASCII letter or digit, may then contain only
  ASCII letters, digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 128 characters
- optional provenance field is `correlationId`
- `reversal.priorPostingId` and `reversal.reason` are both required when `reversal` is present
- `provenance.recordedAt` and `provenance.sourceChannel` are not accepted
- optional fields may be omitted; `null` is accepted for `reversal` and `correlationId`
- `reversal.priorPostingId` must already exist in the selected book
- a reversal requires one exact line-by-line negation of the target posting and only one reversal is allowed per target
- `OPENING_BALANCE` postings may touch only `ASSET`, `LIABILITY`, or `EQUITY` accounts
- `OPENING_BALANCE` postings are accepted only before the first committed posting exists in the
  selected book, so all opening balances must be seeded as one opening-statement phase
- legacy `correction` and `reversal.kind` fields are rejected
- unknown fields are rejected at every object level
- duplicate JSON object keys are rejected

## Account-Declaration Request Shape

`declare-account` accepts one book-local account-definition document:

```json
{
  "accountCode": "1000",
  "accountName": "Cash",
  "accountType": "ASSET",
  "accountRole": "ORDINARY",
  "accountNodeKind": "POSTABLE",
  "financialPositionLineClassification": "CURRENT_ASSET"
}
```

Current account-declaration rules:
- `accountCode`, `accountName`, `accountType`, `accountRole`, and `accountNodeKind` are required
- `parentAccountCode` is optional and declares one explicit chart parent when this account belongs
  under another declared account
- `accountCode` must start with an ASCII letter or digit, may then contain only ASCII letters,
  digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 255 characters
- `accountCode` is an opaque book-local identifier today; FinGrind does not infer account class or
  hierarchy from numeric ranges or prefixes
- `accountName` must be a non-blank string
- `accountType` must be one of the canonical chart classifications supported by FinGrind
- `accountRole` must be one of `ORDINARY` or `CONTRA`
- `accountNodeKind` must be one of `POSTABLE` or `HEADER`
- `ASSET`, `LIABILITY`, and `EQUITY` accounts must declare
  `financialPositionLineClassification` and must not declare
  `profitAndLossLineClassification`
- `CURRENT_PERIOD_RESULT` is reserved for derived statement rows and is not accepted in
  `financialPositionLineClassification` when declaring accounts
- `REVENUE` and `EXPENSE` accounts must declare `profitAndLossLineClassification` and must not
  declare `financialPositionLineClassification`
- redeclaring an existing account may update the display name and reactivate the account
- redeclaring an existing account with a different `accountType` is rejected
- redeclaring an existing account with a different `accountRole` is rejected
- redeclaring an existing account with a different chart parent or statement-line taxonomy is
  rejected

## Ledger-Plan Request Shape

Inspect the canonical AI-agent scaffold:

```bash
fingrind print-plan-template
```

Or, in a source checkout, inspect the checked-in runnable example:

```bash
cat docs/examples/ledger-plan-request.json
```

Current ledger-plan rules:
- top-level fields are `planId` and `steps`
- `planId` must be a non-blank string
- `steps` must contain at least one object and every `stepId` must be unique
- `open-book` is allowed only as the first step when a plan initializes a book
- every step requires `stepId` and `kind`
- `open-book` uses nested `openBook`, which requires `entityName`, `entityForm`, `ownerModel`,
  `businessActivityTags`, `functionalCurrency`, `fiscalYearStart`, and `policyProfile`
- `declare-account` uses nested `declareAccount`
- `preflight-entry` and `post-entry` use nested `posting`, which has the same shape as the normal
  posting request, including required `evidence.sourceDocuments[]` and `evidence.approvals[]`
- `list-accounts`, `list-postings`, and `account-balance` use nested `query`
- `list-accounts.query` is optional; when present it accepts optional `limit` plus optional opaque
  `cursor`, and omitted `limit` defaults to the standard page size
- `list-postings.query` is optional; when present it accepts optional `accountCode`, optional
  effective-date bounds, optional `limit`, and optional opaque `cursor`, and omitted `limit`
  defaults to the standard page size
- `account-balance.query` accepts `accountCode` plus optional effective-date bounds
- `get-posting` uses `postingId`
- assertion steps use `kind: "assert"` plus a nested `assertion` object
- supported assertion kinds are `assert-account-declared`, `assert-account-active`,
  `assert-posting-exists`, and `assert-account-balance`
- `assert-account-balance` assertions accept `accountCode`, optional `effectiveDateFrom`,
  optional `effectiveDateTo`, typed `netAmount`, and `balanceSide`
- unknown fields are rejected at every object level
- `print-plan-template` emits the canonical `execute-plan` scaffold shape as one runnable demo
  workflow; replace the sample evidence and provenance values before real-world use
- execution semantics are not request knobs: plans are atomic, halt on first failed step, return
  one bounded aggregate summary by default, and return one complete journal when
  `--result-detail full` is selected; ordinary business steps keep their canonical `kind`,
  assertion entries optionally add `detailKind`, and unexpected begin, initialization-check,
  commit, or rollback failures end the journal with `kind: "plan-boundary"` plus
  `boundaryPhase`
- unexpected transaction-boundary failures such as begin, commit, or rollback problems are mapped
  into the terminal rejected journal step instead of escaping as an untyped plan exception
- plan-journal steps now carry typed `data` records instead of generic fact bags
- money-bearing plan-journal `data` fields use objects carrying `currencyCode` and `minorUnits`
- successful `list-accounts` journal steps emit `count`, `pageLimit`, optional `nextCursor`,
  `hasMore`, and repeated typed `accounts[]`
- successful `list-postings` journal steps emit `count`, `pageLimit`, optional `nextCursor`,
  `hasMore`, and repeated typed `postings[]` with nested `provenance`, `evidence`, `lines`, and
  optional `reversal`

Human rejections and JSON rejection envelopes now stay aligned. Both surfaces carry the same
top-level `message`, optional `hint`, and any typed rejection details that identify the failing
posting id, closing-equity classification mismatch, account-state violation set, or related
deterministic repair data.

## Accepted Values

| Field | Accepted Values |
|:------|:----------------|
| `lines[].side` | `DEBIT`, `CREDIT` |
| `provenance.actorType` | `HUMAN`, `SYSTEM`, `AGENT` |
| `accountType` | `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE` |
| `accountRole` | `ORDINARY`, `CONTRA` |
| `accountNodeKind` | `POSTABLE`, `HEADER` |
| `financialPositionLineClassification` | `CURRENT_ASSET`, `NONCURRENT_ASSET`, `CURRENT_LIABILITY`, `NONCURRENT_LIABILITY`, `OWNER_CAPITAL`, `OWNER_DRAWINGS`, `PARTNER_CAPITAL`, `PARTNER_CURRENT`, `SHARE_CAPITAL`, `RETAINED_EARNINGS`, `ACCUMULATED_SURPLUS`, `RESERVE`, `OTHER_EQUITY` |
| `profitAndLossLineClassification` | `OPERATING_REVENUE`, `OTHER_REVENUE`, `FINANCE_INCOME`, `COST_OF_SALES`, `OPERATING_EXPENSE`, `DEPRECIATION_AND_AMORTIZATION`, `FINANCE_EXPENSE`, `TAX_EXPENSE` |

## CLI Output Shapes

| Output | Returned By | Fields |
|:-------|:------------|:-------|
| success envelope | `help`, `version`, `capabilities`, `environment`, `generate-book-key-file`, `open-book`, `rekey-book`, `backup-book`, `restore-book`, `inspect-rekey-rollback`, `restore-rekey-rollback`, `delete-rekey-rollback`, `declare-account`, `inspect-book`, `list-accounts`, `get-posting`, `list-postings`, `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `income-statement`, `changes-in-equity` | `status`, `payload`, optional `artifacts[]` |
| raw request document | `print-request-template`, `print-plan-template` | canonical posting-request, declare-account-request, or AI-agent ledger-plan scaffold JSON |
| `ok` | successful `preflight-entry` | `status`, `payload.idempotencyKey`, `payload.effectiveDate` |
| `ok` | successful `post-entry` | `status`, `payload.postingId`, `payload.idempotencyKey`, `payload.effectiveDate`, `payload.recordedAt` |
| `ok` | any `execute-plan` outcome | `status`, `payload.planId`, `payload.status`, `payload.resultDetail`, `payload.summary`, and optional `payload.journal` |
| `rejected` | deterministic single-command business rejection | `status`, `code`, `message`, optional `idempotencyKey`, optional `details` |
| `error` | malformed input or runtime failure | `status`, `code`, `message`, optional `hint`, optional `argument` |

Dynamic fields:
- `capabilities.payload` is stable unless the public command contract or runtime surface changes
- `docs/examples/request-template.json` and `docs/examples/ledger-plan-template.json` are exact
  captures of `print-request-template` and `print-plan-template`; both intentionally publish
  runnable sample documents whose demo evidence and provenance values should be replaced before
  real-world use
- `generate-book-key-file.payload.bookKeyFile` is the normalized absolute path of the created key file
- `generate-book-key-file` succeeds only when the selected parent directory is already owner-only
  or can be created as one missing private directory
- `open-book.payload.initializedAt` is stamped from the FinGrind clock
- `open-book.payload.bookIdentity.entityName`, `.entityForm`, `.ownerModel`,
  `.businessActivityTags`, `.functionalCurrency`, `.fiscalYearStart`, and
  `.policyProfile` echo the persisted initialized-book identity
- `declare-account.payload.declaredAt` is stamped from the FinGrind clock on first declaration
- `inspect-book.payload.bookFile` is the normalized absolute path of the selected book
- `list-accounts` exposes `limit` plus an optional opaque `nextCursor`
- `list-postings` exposes `limit` plus an optional opaque `nextCursor`
- `committed.payload.postingId` is generated per successful commit as a UUID v7 value
- `committed.recordedAt` is stamped from the FinGrind commit clock, not caller input
- `ok.payload.resultDetail` echoes whether the caller requested `summary` or `full`
- `ok.payload.summary.startedAt`, `finishedAt`, aggregate step counts, and optional failure
  details are stamped from the FinGrind execution clock
- `ok.payload.journal.startedAt`, `finishedAt`, and step timestamps are stamped from the
  FinGrind execution clock when `--result-detail full` is selected
- plan-journal steps carry typed `data` records rather than generic fact arrays
- successful `open-book` plan steps emit `initializedAt`, `entityName`, `functionalCurrency`, and
  `fiscalYearStart`; the persisted initialized-book identity also carries `entityForm`,
  `ownerModel`, `businessActivityTags`, and `policyProfile`
- successful `declare-account` plan steps emit `accountCode`, `accountName`, `accountType`,
  `accountRole`, `normalBalance`, `active`, and `declaredAt`
- successful `post-entry` and `get-posting` plan steps emit typed `evidence` data with source
  document and approval entries
- successful `assert-account-balance` plan steps emit typed `account` data plus repeated
  `balances[]`
- `execute-plan` accepts at most 100 steps, so returned plan summaries and optional full journals
  are complete but bounded

Successful `preflight-entry` output is advisory. It confirms that the current request passed
validation against the current book state, but it is not a durable commit guarantee:
`post-entry` performs its authoritative transactional checks before committing.

Discovery output also has two intentionally different JSON scopes:
- `--detail compact|full` is accepted only when the resolved discovery output mode is JSON
- `help --output json` returns a concise overview payload with command summaries, getting-started
  hints, and exit codes
- `help <command> --output json` returns one narrow command-local payload with usage, options,
  examples, operator notes, and request-file guidance when that command accepts `--request-file`
- `help --output json --detail full` and `help <command> --output json --detail full` include the
  extended discovery body such as embedded templates, enum vocabularies, and request-shape details
- `capabilities --output json` defaults to the compact machine contract, while
  `capabilities --output json --detail full` expands to the full doctrine, command grammar,
  request shapes, and cross-command facts
- `environment --output json` is the live runtime contract for distribution, runtime provenance,
  loaded SQLite facts, and launcher-local storage paths

## Capabilities Discovery Shape

`capabilities` is the canonical machine contract and exposes typed descriptors instead of raw
string lists for the drift-prone parts of the surface. Operation ids, display labels, aliases,
output modes, summaries, command groups, shared query limits, hard book-model facts, preflight
facts, and currency facts are sourced from the contract protocol catalog before this response is
rendered:

- `requestShapes.postEntry.topLevelFields`, `lineFields`, `provenanceFields`, and `reversalFields`
  are arrays of `{ "name", "presence", "description" }`
- `presence` is a live enum-backed machine value and is currently one of `required`,
  `conditional`, `optional`, or `forbidden`
- `requestShapes.schemaDialect` is the JSON Schema dialect URI used by the embedded executable
  schemas
- `requestShapes.postEntry.schema`, `declareAccount.schema`, and `ledgerPlan.schema` are
  executable JSON Schema objects sourced from the live contract, not hand-maintained prose
- `requestShapes.*.enumVocabularies` are arrays of `{ "name", "values" }` sourced from the live
  enum constants
- `responseModel.rejections` is an array of deterministic business rejections rendered from the
  administration, query, and posting rejection families
- `responseModel.errorDescriptors` is an array of deterministic CLI invocation/runtime error
  descriptors such as `invalid-page-cursor`, `protected-book-verification-failed`,
  `managed-runtime-failure`, `storage-runtime-failure`, `pdf-export-failure`, and
  `interactive-prompt-unavailable`; each descriptor includes its published `exitCode`
- `preflight.semantics` carries the short machine hint and `preflight.commitGuarantee`
  carries the advisory-versus-guaranteed commit relationship
- `currencyModel` declares the current single-currency scope and the explicit
  `multiCurrencyStatus: "not-supported"`
- `accountingBaseline.reportingPosition` states explicitly that the current kernel stops at
  financial position, income statement, and changes in equity
- `accountingBaseline.chartModelPosition` states explicitly that declared accounts carry explicit
  parent-child hierarchy and statement-line taxonomy while account-code text remains opaque
- `accountingBaseline.smallEntityPosition` states explicitly that the current kernel does not yet
  claim IFRS for SMEs parity
- `accountingBaseline.operationalPosition` states explicitly that invoicing, receivables,
  payables, inventory, payroll, and settlement live above the ledger in future adjacent contexts
- `accountingBaseline.taxPosition` states explicitly that tax is not a first-class domain in the
  current kernel and that tax determination/rate/filer policy is not modeled yet
- `accountingBaseline.organizationalPosition` states explicitly that the current kernel does not
  yet claim multi-entity organizational accounting
- `extensionSurface.implementedSeams` lists the live executable policy seams currently owned in
  code
- `extensionSurface.policySeams` lists only live executable seams; adjacent domains stay in ADRs
  and domain docs until they own commands, state, storage, and tests
- `requestInput.bookPassphraseOptions` advertises the supported protected-book passphrase routes
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

Shared initialized-book identity payload:
- `entityName`
- `entityForm`
- `ownerModel`
- `businessActivityTags[]`
- `functionalCurrency`
- `fiscalYearStart`
- `policyProfile`

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
  `documentDate`, `capturedAt`, `storageLocator`, and `contentSha256`
- `evidence.approvals[]`, where each entry carries `approvalId`, `approvalType`, `approverId`,
  `approverType`, `decision`, and `approvedAt`
- optional `reversal.priorPostingId` and `reversal.reason`
- `lines[]`, where each line carries `accountCode`, `side`, and typed `amount`

Every response-side money object reuses the same exact money shape with `currencyCode` and
`minorUnits`.

## Book Initialization Responses

`open-book` success returns:
- `payload.bookFile`
- `payload.initializedAt`
- `payload.bookIdentity`, using the shared initialized-book identity payload

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
- `payload.backupFile`
- `payload.backupBookKeyFile`

`restore-book` success returns:
- `payload.bookFile`
- `payload.backupFile`
- `payload.backupBookKeyFile`

That `payload.backupBookKeyFile` is also the key file required to reopen the restored live
`payload.bookFile`.

`inspect-rekey-rollback` success returns:
- `payload.bookFile`
- `payload.rollbackArtifacts[]`

`restore-rekey-rollback` success returns:
- `payload.bookFile`
- `payload.rollbackArtifact`

`delete-rekey-rollback` success returns:
- `payload.bookFile`
- `payload.rollbackArtifact`

`list-accounts` success returns:
- `payload.context.bookIdentity`, using the shared initialized-book identity payload
- `payload.limit`
- optional `payload.nextCursor`
- `payload.accounts[]`, where each entry includes `accountCode`, `accountName`, `accountType`,
  `accountRole`, optional `parentAccountCode`, optional
  `financialPositionLineClassification`, optional `profitAndLossLineClassification`,
  `normalBalance`, `active`, and `declaredAt`

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
- `payload.postings[]`, where each entry uses the shared posting payload

`account-balance` success returns:
- `payload.context.bookIdentity`, using the shared initialized-book identity payload
- `payload.context.postingCoverage`
- `payload.accountCode`
- `payload.accountName`
- `payload.accountType`
- `payload.accountRole`
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

`trial-balance` success returns:
- optional `payload.effectiveDateAsOf`
- `payload.context`, using the shared report context payload
- `payload.rows[]`, where each row includes `accountCode`, `accountName`, `accountType`,
  `accountRole`, `normalBalance`, `active`, `declaredAt`, typed `debitTotal`, `creditTotal`,
  `netAmount`, and `balanceSide`
- `payload.comparativeRows[]`, using the same row shape for the fiscal-year-anchored comparison
  date when one comparative as-of date exists

`account-ledger` success returns:
- `payload.context`, using the shared report context payload
- `payload.accountCode`
- `payload.accountName`
- `payload.accountType`
- `payload.accountRole`
- `payload.normalBalance`
- `payload.active`
- `payload.declaredAt`
- optional `payload.effectiveDateFrom`
- optional `payload.effectiveDateTo`
- `payload.openingBalances[]` and `payload.closingBalances[]`, where each bucket includes typed
  `debitTotal`, `creditTotal`, `netAmount`, and `balanceSide`
- `payload.entries[]`, where each row includes `postingId`, `postingKind`, `reversalState`,
  optional `reversalTarget`, optional `reversalReason`, `effectiveDate`, `recordedAt`, typed
  `debitAmount`, `creditAmount`, `runningBalance`, `runningBalanceSide`,
  `evidence.sourceDocuments[]`, optional `evidence.approvals[]`, and `counterpartAccounts[]`

`period-summary` success returns:
- `payload.context`, using the shared report context payload
- `payload.effectiveDateFrom`
- `payload.effectiveDateTo`
- `payload.postingCount`
- `payload.postingLineCount`
- `payload.accountsTouched`
- `payload.currencyTotals[]`, where each row includes typed `debitTotal`, `creditTotal`,
  `netAmount`, and `balanceSide`
- `payload.accountActivity[]`, where each row includes `accountCode`, `accountName`,
  `accountType`, `accountRole`, `normalBalance`, `active`, `declaredAt`, typed `debitTotal`,
  `creditTotal`, `netAmount`, and `balanceSide`

`financial-position` success returns:
- optional `payload.effectiveDateAsOf`
- `payload.context`, using the shared report context payload
- `payload.sections[]`, where each section includes `accountType`, `rows[]`, and `totals[]`
- `payload.comparativeSections[]`, using the same section shape for the fiscal-year-anchored
  comparison date when one comparative as-of date exists

`income-statement` success returns:
- `payload.effectiveDateFrom`
- `payload.effectiveDateTo`
- `payload.context`, using the shared report context payload
- `payload.sections[]`
- `payload.netIncomeTotals[]`
- `payload.comparativeSections[]`
- `payload.comparativeNetIncomeTotals[]`

`changes-in-equity` success returns:
- `payload.effectiveDateFrom`
- `payload.effectiveDateTo`
- `payload.context`, using the shared report context payload
- `payload.rows[]`
- `payload.openingTotals[]`
- `payload.movementTotals[]`
- `payload.closingTotals[]`
- `payload.comparativeRows[]`
- `payload.comparativeOpeningTotals[]`
- `payload.comparativeMovementTotals[]`
- `payload.comparativeClosingTotals[]`

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
- optional `payload.summary.failureCode`
- optional `payload.summary.failureMessage`
- optional `payload.journal`, present when `--result-detail full` is selected

`payload.journal` carries:
- `startedAt`
- `finishedAt`
- `steps[]`, where each entry includes `stepId`, `kind`, `status`, `startedAt`, `finishedAt`,
  typed `data`, optional `detailKind`, and optional `failure`

Commands that advertise `--output` default successful stdout to human text on an interactive
terminal and to JSON when stdout is redirected or captured. Discovery, administration, write, and
read/report commands can also render operator-facing `--output human`, and the tabular
read/report commands support `--output csv` for spreadsheet import. Invalid invocation failures
default to human repair guidance unless callers select one recognized machine output mode
explicitly, such as `--output json`.
`account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`,
`income-statement`, and `changes-in-equity` can additionally write one PDF artifact through
`--pdf-out <path>`. That PDF export reuses the same canonical result model; it does not change the
JSON report payload itself, but successful JSON success envelopes now also publish one
`artifacts[]` entry with `format: "pdf"` and the normalized written `path`. Successful human and
CSV exports also emit a diagnostics info message with the same normalized artifact path. If the report result
succeeds but the PDF artifact later fails, stdout still carries the same report payload while
diagnostics emit a repair warning for the `--pdf-out` path.
Deterministic failures for commands that accept `--output human` are rendered in the same
human-facing format instead of falling back to JSON envelopes. Deterministic non-business contract
failures render with the `Rejected` heading in human mode so operator refusals do not masquerade
as generic runtime crashes.

Statement-report context also includes one comparative reference window derived from the selected
book's fiscal-year anchor. Trial balance now carries `comparativeRows[]`; financial position now
carries `comparativeSections[]`; income statement now carries `comparativeSections[]` and
`comparativeNetIncomeTotals[]`; changes in equity now carries `comparativeRows[]`,
`comparativeOpeningTotals[]`, `comparativeMovementTotals[]`, and `comparativeClosingTotals[]`.

Checked-in examples for the read/report surface:
- [examples/inspect-book-response.json](./examples/inspect-book-response.json)
- [examples/list-accounts-response.json](./examples/list-accounts-response.json)
- [examples/get-posting-response.json](./examples/get-posting-response.json)
- [examples/list-postings-response.json](./examples/list-postings-response.json)
- [examples/account-balance-response.json](./examples/account-balance-response.json)
- [examples/trial-balance-response.json](./examples/trial-balance-response.json)
- [examples/account-ledger-response.json](./examples/account-ledger-response.json)
- [examples/period-summary-response.json](./examples/period-summary-response.json)
- [examples/trial-balance-human.txt](./examples/trial-balance-human.txt)
- [examples/account-ledger.csv](./examples/account-ledger.csv)
- [examples/period-summary-human.txt](./examples/period-summary-human.txt)

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
| `account-role-conflict` | `declare-account` attempted to amend an existing account's immutable doctrinal role | `accountCode`, `existingAccountRole`, `requestedAccountRole` |
| `unknown-account` | a query named an undeclared account | `accountCode` |
| `posting-not-found` | `get-posting` targeted a posting id that does not exist in the selected book | `postingId` |
| `account-state-violations` | `preflight-entry` or `post-entry` found one or more undeclared or inactive accounts | `violations[]`, where each item includes `code` and `accountCode` |
| `inactive-account` | one item inside `account-state-violations.violations[]` named an inactive account | `accountCode` |
| `duplicate-idempotency-key` | the selected book already contains the same `idempotencyKey` | none |
| `opening-balance-window-closed` | `OPENING_BALANCE` was submitted after the book already contains its first committed posting | `firstBlockingPostingKind`, `firstBlockingEffectiveDate` |
| `opening-balance-touches-nominal-account` | `OPENING_BALANCE` touched a revenue or expense account | `accountCode`, `accountType` |
| `reversal-target-not-found` | `reversal.priorPostingId` does not exist in the selected book | `priorPostingId` |
| `reversal-already-exists` | the target posting already has a full reversal | `priorPostingId` |
| `reversal-does-not-negate-target` | a reversal request does not negate the target posting exactly | `priorPostingId` |

`unknown-account` and `posting-not-found` are query-side rejections.
`account-state-violations` is the posting-side rejection for account-registry failures, and may
report multiple issues in one response so callers can repair the entire entry before retrying.
One checked-in example lives at
[examples/account-state-violations-response.json](./examples/account-state-violations-response.json).

Malformed JSON, wrong field types, missing required fields, invalid date/time text, and
domain-validation failures return `status: "error"` with code `invalid-request`.
Argument and parsing failures may also carry a `hint` and `argument` field so a caller can correct
the invocation mechanically.
Journal-entry validation now reports every detected journal grammar violation in one deterministic
`invalid-request` response and publishes the full ordered set under `details.violations[]`, so
callers can repair the whole request before retrying without scraping prose.

Deterministic CLI-side `status: "error"` examples are also checked in:
- [examples/invalid-page-cursor-error.json](./examples/invalid-page-cursor-error.json)
- [examples/protected-book-verification-failed-error.json](./examples/protected-book-verification-failed-error.json)
- [examples/interactive-prompt-unavailable-error.json](./examples/interactive-prompt-unavailable-error.json)

When you want those malformed-input or deterministic-error examples as JSON from the live CLI,
request them explicitly with `--output json` on commands that support output negotiation.
