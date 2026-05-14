---
afad: "4.0"
version: "0.36.0"
domain: HUMAN_REQUESTS
updated: "2026-05-14"
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
source:
- `--book-key-file` with a UTF-8 passphrase file protected by POSIX owner-only permissions
  (`0400` or `0600`) on macOS/Linux or a Windows owner-only ACL on Windows; its containing
  directory must also remain owner-only, and the public examples keep this file under a separate
  `./secrets/` tree rather than beside the book
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

The scaffold is intentionally agent-first: `provenance.actorType` is `AGENT`, and
`effectiveDate`, `actorId`, `commandId`, `idempotencyKey`, and `causationId` are emitted as
replace-before-submit placeholders. Replace every placeholder before committing. On one book, an
`idempotencyKey` becomes single-use per book after the first committed posting.

The packaged CLI can surface the same request-shape truth without leaving the terminal:
`help post-entry`, `help declare-account`, and `help execute-plan` inline one canonical template
plus the accepted fields and enum vocabularies for their `--request-file` payloads.

Current posting-request rules:
- all top-level date, enum, identifier, and provenance fields are JSON strings
- `postingKind` is required and must be `STANDARD` or `OPENING_BALANCE`
- `lines[].amount` is one exact money object with `currencyCode` and `minorUnits`
- `lines[].amount.currencyCode` must be one canonical three-letter uppercase ISO 4217 code
  supported by FinGrind's pinned currency registry
- `lines[].amount.minorUnits` must contain ASCII digits only, must not contain redundant leading
  zeroes, must not exceed 19 digits, and must fit inside FinGrind's exact supported minor-unit
  range
- `lines[].amount` must decode to one strictly positive posted amount
- `postingKind`, `effectiveDate`, `lines`, and `provenance` are required
- `lines` must contain at least one journal line
- `lines[].accountCode` must start with an ASCII letter or digit, may then contain only ASCII
  letters, digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 255 characters
- every entry must contain at least one `DEBIT` line and at least one `CREDIT` line
- every line inside one entry must share the same `lines[].amount.currencyCode`
- every line inside one entry must use the selected book's functional currency
- `reversal` is optional
- required provenance fields are `actorId`, `actorType`, `commandId`, `idempotencyKey`, and `causationId`
- `provenance.idempotencyKey` must start with an ASCII letter or digit, may then contain only
  ASCII letters, digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 128 characters
- optional provenance field is `correlationId`
- `reversal.priorPostingId` and `reversal.reason` are both required when `reversal` is present
- `provenance.recordedAt` and `provenance.sourceChannel` are not accepted
- optional fields may be omitted; `null` is accepted for `reversal` and `correlationId`
- `reversal.priorPostingId` must already exist in the selected book
- a reversal requires an exact line-by-line negation of the target posting and only one reversal is allowed per target
- `OPENING_BALANCE` postings may touch only `ASSET`, `LIABILITY`, or `EQUITY` accounts
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
  "accountRole": "ORDINARY"
}
```

Current account-declaration rules:
- `accountCode`, `accountName`, `accountType`, and `accountRole` are required
- `accountCode` must start with an ASCII letter or digit, may then contain only ASCII letters,
  digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 255 characters
- `accountCode` is an opaque book-local identifier today; FinGrind does not infer account class or
  hierarchy from numeric ranges or prefixes
- `accountName` must be a non-blank string
- `accountType` must be one of the canonical chart classifications supported by FinGrind
- `accountRole` must be one of `ORDINARY`, `CONTRA`, or `RETAINED_EARNINGS`
- `RETAINED_EARNINGS` is valid only with `accountType: "EQUITY"`
- redeclaring an existing account may update the display name and reactivate the account
- redeclaring an existing account with a different `accountType` is rejected
- redeclaring an existing account with a different `accountRole` is rejected

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
- `open-book` uses nested `openBook`, which requires `entityName`, `functionalCurrency`, and
  `fiscalYearStart`
- `declare-account` uses nested `declareAccount`
- `preflight-entry` and `post-entry` use nested `posting`, which has the same shape as the normal
  posting request
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
- `print-plan-template` emits the canonical `execute-plan` scaffold shape, but the emitted
  placeholder values must be replaced before the request is accepted
- execution semantics are not request knobs: plans are atomic, halt on first failed step, and
  return a complete journal whose ordinary business steps keep their canonical `kind`, whose
  assertion entries optionally add `detailKind`, and whose unexpected begin,
  initialization-check, commit, or rollback failures end the journal with `kind:
  "plan-boundary"` plus `boundaryPhase`
- unexpected transaction-boundary failures such as begin, commit, or rollback problems are mapped
  into the terminal rejected journal step instead of escaping as an untyped plan exception
- plan-journal facts are typed objects with `kind`, `name`, and either `value` or nested `facts`
- money-bearing plan-journal facts use `kind: "money"` with a `value` object carrying
  `currencyCode` and `minorUnits`
- successful `list-accounts` journal steps emit `count`, `pageLimit`, optional `nextCursor`,
  `hasMore`, and repeated grouped `account` facts
- successful `list-postings` journal steps emit `count`, `pageLimit`, optional `nextCursor`,
  `hasMore`, and repeated grouped `posting` facts with nested `provenance`, `line`, and optional
  `reversal` groups

Human rejections and JSON rejection envelopes now stay aligned. Both surfaces carry the same
top-level `message`, optional `hint`, and any typed rejection details that identify the failing
posting id, retained-earnings account, account-state violation set, or related deterministic
repair data.

## Accepted Values

| Field | Accepted Values |
|:------|:----------------|
| `lines[].side` | `DEBIT`, `CREDIT` |
| `provenance.actorType` | `HUMAN`, `SYSTEM`, `AGENT` |
| `accountType` | `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE` |
| `accountRole` | `ORDINARY`, `CONTRA`, `RETAINED_EARNINGS` |

## CLI Output Shapes

| Output | Returned By | Fields |
|:-------|:------------|:-------|
| success envelope | `help`, `version`, `capabilities`, `generate-book-key-file`, `open-book`, `rekey-book`, `declare-account`, `inspect-book`, `list-accounts`, `get-posting`, `list-postings`, `account-balance`, `trial-balance`, `account-ledger`, `period-summary` | `status`, `payload` |
| raw request document | `print-request-template`, `print-plan-template` | canonical posting-request or AI-agent ledger-plan scaffold JSON |
| `ok` | successful `preflight-entry` | `status`, `payload.idempotencyKey`, `payload.effectiveDate` |
| `ok` | successful `post-entry` | `status`, `payload.postingId`, `payload.idempotencyKey`, `payload.effectiveDate`, `payload.recordedAt` |
| `ok` | successful `execute-plan` | `status`, `payload.planId`, `payload.status`, and `payload.journal` |
| `plan-rejected` | deterministic `execute-plan` step rejection | `status`, `code`, `message`, `details.plan` |
| `plan-assertion-failed` | failed `execute-plan` assertion | `status`, `code`, `message`, `details.plan` |
| `rejected` | deterministic single-command business rejection | `status`, `code`, `message`, optional `idempotencyKey`, optional `details` |
| `error` | malformed input or runtime failure | `status`, `code`, `message`, optional `hint`, optional `argument` |

Dynamic fields:
- `capabilities.payload.timestamp` varies per invocation
- `docs/examples/request-template.json` and `docs/examples/ledger-plan-template.json` are exact
  captures of `print-request-template` and `print-plan-template`; both intentionally keep the
  scaffold placeholders `replace-before-commit-effective-date` and
  `replace-before-commit-*` provenance values so callers must supply a real posting date plus
  real actor, command, idempotency, and causation values before commit
- `generate-book-key-file.payload.bookKeyFile` is the normalized absolute path of the created key file
- `open-book.payload.initializedAt` is stamped from the FinGrind clock
- `open-book.payload.bookIdentity.entityName`, `.functionalCurrency`, and `.fiscalYearStart`
  echo the persisted initialized-book identity
- `declare-account.payload.declaredAt` is stamped from the FinGrind clock on first declaration
- `inspect-book.payload.bookFile` is the normalized absolute path of the selected book
- `list-accounts` exposes `limit` plus an optional opaque `nextCursor`
- `list-postings` exposes `limit` plus an optional opaque `nextCursor`
- `committed.payload.postingId` is generated per successful commit as a UUID v7 value
- `committed.recordedAt` is stamped from the FinGrind commit clock, not caller input
- `ok.payload.journal.startedAt`, `finishedAt`, and step timestamps are stamped from the
  FinGrind execution clock
- plan-journal facts carry explicit `kind` metadata (`text`, `flag`, `count`, `group`), and grouped
  facts nest their child observations under `facts`
- successful `open-book` plan steps emit `initializedAt`, `entityName`, `functionalCurrency`, and
  `fiscalYearStart`
- successful `declare-account` plan steps emit `accountCode`, `accountName`, `accountType`,
  `accountRole`, `normalBalance`, `active`, and `declaredAt`
- successful `assert-account-balance` plan steps emit grouped `account` facts plus grouped
  `balance` buckets
- `execute-plan` accepts at most 100 steps, so returned plan journals are complete but bounded

Successful `preflight-entry` output is advisory. It confirms that the current request passed validation against
the current book state, but it is not a durable commit guarantee: `post-entry` still performs its
authoritative transactional checks before committing.

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
  `interactive-prompt-unavailable`
- `preflight.semantics` carries the short machine hint and `preflight.commitGuarantee`
  carries the advisory-versus-guaranteed commit relationship
- `currencyModel` declares the current single-currency scope and the explicit
  `multiCurrencyStatus: "not-supported"`
- `requestInput.bookPassphraseOptions` advertises the supported protected-book passphrase routes
- `requestInput.requestDocumentSemantics` advertises the strict JSON-object, duplicate-key, and
  unknown-field rules
- `environment` reports runtime distribution, protected-book requirements, and managed SQLite
  metadata, including `requiredCompileOptions`, `forbiddenCompileOptions`,
  `requiresSecureMemorySupport`, `requiredSqliteSourceId`,
  `runtime.compileOptionsVerification`, `runtime.runtimeProvenance`,
  `runtime.runtimeTrustBasis`, `runtime.loadedLibraryPath`, and
  `runtime.loadedSqliteSourceId`
- `commands` also lists `print-plan-template` and `execute-plan`, both rendered from the contract
  protocol catalog

## Book Initialization Responses

`open-book` success returns:
- `payload.bookFile`
- `payload.initializedAt`
- `payload.bookIdentity.entityName`
- `payload.bookIdentity.functionalCurrency`
- `payload.bookIdentity.fiscalYearStart`

## Book Inspection And Query Responses

`inspect-book` success returns:
- `payload.bookFile`
- `payload.state`
- `payload.compatibleWithCurrentBinary`
- `payload.canInitializeWithOpenBook`
- optional `payload.applicationId`
- optional `payload.detectedBookFormatVersion`
- `payload.supportedBookFormatVersion`
- optional `payload.initializedAt`
- optional `payload.bookIdentity.entityName`
- optional `payload.bookIdentity.functionalCurrency`
- optional `payload.bookIdentity.fiscalYearStart`

`payload.state` uses the stable lower-case vocabulary `missing`, `blank-sqlite`, `initialized`,
`foreign-sqlite`, `unsupported-format-version`, or `incomplete-fingrind`.
That `state` field is the canonical lifecycle discriminator; the JSON payload does not duplicate it
with a separate `initialized` flag.
`payload.canInitializeWithOpenBook` is true exactly when `open-book` may initialize the selected
path directly. The current public line reports `true` for `missing` and `blank-sqlite`, and
`false` for every other inspection state.

`list-accounts` success returns:
- `payload.limit`
- optional `payload.nextCursor`
- `payload.accounts[]`, where each entry includes `accountCode`, `accountName`, `accountType`,
  `accountRole`, derived `normalBalance`, `active`, and `declaredAt`

`get-posting` success returns:
- one committed posting payload with `payload.postingId`, `payload.effectiveDate`, `payload.recordedAt`, request-provenance fields, `sourceChannel`, optional `reversal`, and `lines[]`
- each `lines[].amount` value reuses the same exact money object shape with `currencyCode`,
  `minorUnits`

`list-postings` success returns:
- `payload.limit`
- optional `payload.nextCursor`
- `payload.postings[]`, where each posting has the same shape as `get-posting`

`account-balance` success returns:
- declared-account identity fields: `accountCode`, `accountName`, `accountType`,
  `accountRole`, derived `normalBalance`, `active`, `declaredAt`
- optional query filters: `effectiveDateFrom`, `effectiveDateTo`
- `balances[]`, where each bucket includes typed `debitTotal`, `creditTotal`, `netAmount`, and
  `balanceSide`
- every response-side money object uses the same exact money shape with `currencyCode`,
  `minorUnits`

## Report Responses

`trial-balance` success returns:
- optional `payload.effectiveDateTo`
- `payload.rows[]`, where each row includes `accountCode`, `accountName`, `accountType`,
  `accountRole`, derived `normalBalance`, `active`, `declaredAt`, typed `debitTotal`,
  `creditTotal`, `netAmount`, and `balanceSide`

`account-ledger` success returns:
- declared-account identity fields: `accountCode`, `accountName`, `accountType`,
  `accountRole`, derived `normalBalance`, `active`, `declaredAt`
- optional date filters: `effectiveDateFrom`, `effectiveDateTo`
- `openingBalances[]` and `closingBalances[]`, where each bucket includes typed `debitTotal`,
  `creditTotal`, `netAmount`, and `balanceSide`
- `entries[]`, where each row includes `postingId`, `effectiveDate`, `recordedAt`, typed
  `debitAmount`, `creditAmount`, `runningBalance`, `runningBalanceSide`, and
  `counterpartAccounts[]`

`period-summary` success returns:
- `payload.effectiveDateFrom`
- `payload.effectiveDateTo`
- `payload.postingCount`
- `payload.postingLineCount`
- `payload.accountsTouched`
- `payload.currencyTotals[]`, where each row includes typed `debitTotal`, `creditTotal`,
  `netAmount`, and `balanceSide`
- `payload.accountActivity[]`, where each row includes `accountCode`, `accountName`,
  `accountType`, `accountRole`, derived `normalBalance`, `active`, `declaredAt`, typed
  `debitTotal`, `creditTotal`, `netAmount`, and `balanceSide`

Commands that advertise `--output` default successful stdout to human text on an interactive
terminal and to JSON when stdout is redirected or captured. Discovery, administration, write, and
read/report commands can also render operator-facing `--output human`, and the tabular
read/report commands support `--output csv` for spreadsheet import. Invalid invocation failures
default to human repair guidance unless callers select one recognized machine output mode
explicitly, such as `--output json`.
`account-balance`, `trial-balance`, `account-ledger`, and `period-summary` can additionally write
one PDF artifact through `--pdf-out <path>`. That PDF export reuses the same canonical result
model; it does not change the JSON payload contract. Successful exports emit a diagnostics info
message with the normalized artifact path. If the report result succeeds but the PDF artifact later
fails, stdout still carries the same report payload while diagnostics emit a repair warning for the
`--pdf-out` path. Deterministic failures for commands that accept `--output human` are rendered in
the same human-facing format instead of falling back to JSON envelopes. Deterministic non-business
contract failures render with the `Rejected` heading in human mode so operator refusals do not
masquerade as generic runtime crashes.

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
