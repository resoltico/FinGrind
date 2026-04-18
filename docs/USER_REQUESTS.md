---
afad: "3.5"
version: "0.17.0"
domain: USER_REQUESTS
updated: "2026-04-18"
route:
  keywords: [fingrind, request-json, response-json, provenance, reversal, idempotency, payload, rejection, inspect-book, list-postings, account-balance, ledger-plan, execute-plan]
  questions: ["what request json does fingrind accept", "what response envelopes does fingrind return", "how does list-accounts pagination work in fingrind", "what does inspect-book return", "what ledger plan shape does execute-plan accept"]
---

# Request And Response Guide

**Purpose**: Show the accepted JSON request shapes and the output documents returned by the CLI.
**Prerequisites**: Familiarity with the packaged CLI in [USER_CLI.md](./USER_CLI.md).

Book-bound commands pair these JSON payloads with `--book-file` plus exactly one passphrase
source:
- `--book-key-file` with a UTF-8 passphrase file protected by POSIX owner-only permissions
  (`0400` or `0600`) on macOS/Linux or a Windows owner-only ACL on Windows
- `--book-passphrase-stdin` with one UTF-8 passphrase payload from standard input
- `--book-passphrase-prompt` with an interactive non-echo terminal prompt

## Posting Request Shape

Inspect the minimal valid posting request:

```bash
fingrind print-request-template
```

Or inspect the checked-in example payload:

```bash
cat docs/examples/basic-posting-request.json
```

Current posting-request rules:
- all scalar fields are JSON strings, including dates, enums, and `amount`
- `lines[].amount` must be a plain decimal string greater than zero, such as `10.00`; exponent
  notation such as `1e6` is rejected
- `effectiveDate`, `lines`, and `provenance` are required
- `lines` must contain at least one journal line
- every entry must contain at least one `DEBIT` line and at least one `CREDIT` line
- every line inside one entry must share the same `currencyCode`
- `reversal` is optional
- required provenance fields are `actorId`, `actorType`, `commandId`, `idempotencyKey`, and `causationId`
- optional provenance field is `correlationId`
- `reversal.priorPostingId` and `reversal.reason` are both required when `reversal` is present
- `provenance.recordedAt` and `provenance.sourceChannel` are not accepted
- optional fields may be omitted; `null` is accepted for `reversal` and `correlationId`
- `reversal.priorPostingId` must already exist in the selected book
- a reversal requires an exact line-by-line negation of the target posting and only one reversal is allowed per target
- legacy `correction` and `reversal.kind` fields are rejected
- unknown fields are rejected at every object level
- duplicate JSON object keys are rejected

## Account-Declaration Request Shape

`declare-account` accepts one book-local account-definition document:

```json
{
  "accountCode": "1000",
  "accountName": "Cash",
  "normalBalance": "DEBIT"
}
```

Current account-declaration rules:
- `accountCode`, `accountName`, and `normalBalance` are required
- `accountCode` and `accountName` must be non-blank strings
- `normalBalance` must describe the side that increases the account
- redeclaring an existing account may update the display name and reactivate the account
- redeclaring an existing account with a different `normalBalance` is rejected

## Ledger-Plan Request Shape

Inspect the canonical AI-agent scaffold:

```bash
fingrind print-plan-template
```

Or inspect the checked-in runnable example:

```bash
cat docs/examples/ledger-plan-request.json
```

Current ledger-plan rules:
- top-level fields are `planId` and `steps`
- `planId` must be a non-blank string
- `steps` must contain at least one object and every `stepId` must be unique
- `open-book` is allowed only as the first step when a plan initializes a book
- every step requires `stepId` and `kind`
- `open-book` takes no nested payload
- `declare-account` uses nested `declareAccount`
- `preflight-entry` and `post-entry` use nested `posting`, which has the same shape as the normal
  posting request
- `list-accounts`, `list-postings`, and `account-balance` use nested `query`
- `list-accounts.query` accepts `limit` plus optional `offset`
- `list-postings.query` accepts optional `accountCode`, optional effective-date bounds, required
  `limit`, and optional opaque `cursor`
- `account-balance.query` accepts `accountCode` plus optional effective-date bounds
- `get-posting` uses `postingId`
- assertion steps use `kind: "assert"` plus a nested `assertion` object
- supported assertion kinds are `assert-account-declared`, `assert-account-active`,
  `assert-posting-exists`, and `assert-account-balance`
- `assert-account-balance` assertions accept `accountCode`, optional `effectiveDateFrom`,
  optional `effectiveDateTo`, `currencyCode`, `netAmount`, and `balanceSide`
- unknown fields are rejected at every object level
- `print-plan-template` emits the accepted `execute-plan` request shape directly
- execution semantics are not request knobs: plans are atomic, halt on first failed step, and
  return a complete per-step journal with canonical `kind` plus optional `detailKind`
- plan-journal facts are typed objects with `kind`, `name`, and either `value` or nested `facts`

## Accepted Values

| Field | Accepted Values |
|:------|:----------------|
| `lines[].side` | `DEBIT`, `CREDIT` |
| `provenance.actorType` | `USER`, `SYSTEM`, `AGENT` |
| `normalBalance` | `DEBIT`, `CREDIT` |

## CLI Output Shapes

| Output | Returned By | Fields |
|:-------|:------------|:-------|
| success envelope | `help`, `version`, `capabilities`, `generate-book-key-file`, `open-book`, `rekey-book`, `declare-account`, `inspect-book`, `list-accounts`, `get-posting`, `list-postings`, `account-balance` | `status`, `payload` |
| raw request document | `print-request-template`, `print-plan-template` | minimal valid posting request JSON or runnable ledger-plan JSON |
| `preflight-accepted` | successful `preflight-entry` | `status`, `idempotencyKey`, `effectiveDate` |
| `committed` | successful `post-entry` | `status`, `postingId`, `idempotencyKey`, `effectiveDate`, `recordedAt` |
| `plan-committed` | successful `execute-plan` | `status`, `payload.planId`, `payload.status`, and `payload.journal` |
| `plan-rejected` | deterministic `execute-plan` step rejection | `status`, `code`, `message`, `details.plan` |
| `plan-assertion-failed` | failed `execute-plan` assertion | `status`, `code`, `message`, `details.plan` |
| `rejected` | deterministic single-command business rejection | `status`, `code`, `message`, optional `idempotencyKey`, optional `details` |
| `error` | malformed input or runtime failure | `status`, `code`, `message`, optional `hint`, optional `argument` |

Dynamic fields:
- `capabilities.payload.timestamp` varies per invocation
- `generate-book-key-file.payload.bookKeyFile` is the normalized absolute path of the created key file
- `open-book.payload.initializedAt` is stamped from the FinGrind clock
- `declare-account.payload.declaredAt` is stamped from the FinGrind clock on first declaration
- `inspect-book.payload.bookFile` is the normalized absolute path of the selected book
- `list-accounts` exposes `limit`, `offset`, and `hasMore`
- `list-postings` exposes `limit` plus an optional opaque `nextCursor`
- `committed.postingId` is generated per successful commit as a UUID v7 value
- `committed.recordedAt` is stamped from the FinGrind commit clock, not caller input
- `plan-committed.payload.journal.startedAt`, `finishedAt`, and step timestamps are stamped from the
  FinGrind execution clock
- plan-journal facts carry explicit `kind` metadata (`text`, `flag`, `count`, `group`), and grouped
  facts nest their child observations under `facts`
- `execute-plan` accepts at most 100 steps, so returned plan journals are complete but bounded

`preflight-accepted` is advisory. It confirms that the current request passed validation against
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
- `requestShapes.*.enumVocabularies` are arrays of `{ "name", "values" }` sourced from the live
  enum constants
- `responseModel.rejections` is an array of `{ "code", "description" }`
- `preflightSemantics` is the short machine hint and `preflight` expands it with
  `isCommitGuarantee`
- `currencyModel` declares the current single-currency scope and the explicit
  `multiCurrencyStatus: "not-supported"`
- `requestInput.bookPassphraseOptions` advertises the supported protected-book passphrase routes
- `requestInput.requestDocumentSemantics` advertises the strict JSON-object, duplicate-key, and
  unknown-field rules
- `environment` reports runtime distribution, protected-book requirements, and managed SQLite
  metadata
- `commands` also lists `print-plan-template` and `execute-plan`, both rendered from the contract
  protocol catalog

## Book Inspection And Query Responses

`inspect-book` success returns:
- `payload.bookFile`
- `payload.state`
- `payload.initialized`
- `payload.compatibleWithCurrentBinary`
- `payload.canInitializeWithOpenBook`
- optional `payload.applicationId`
- optional `payload.detectedBookFormatVersion`
- `payload.supportedBookFormatVersion`
- `payload.migrationPolicy`
- optional `payload.initializedAt`

`payload.state` uses the stable lower-case vocabulary `missing`, `blank-sqlite`, `initialized`,
`foreign-sqlite`, `unsupported-format-version`, or `incomplete-fingrind`.

`list-accounts` success returns:
- `payload.limit`
- `payload.offset`
- `payload.hasMore`
- `payload.accounts[]`, where each entry includes `accountCode`, `accountName`, `normalBalance`, `active`, and `declaredAt`

`get-posting` success returns:
- one committed posting payload with `postingId`, `effectiveDate`, `recordedAt`, request-provenance fields, `sourceChannel`, optional `reversal`, and `lines[]`

`list-postings` success returns:
- `payload.limit`
- optional `payload.nextCursor`
- `payload.postings[]`, where each posting has the same shape as `get-posting`

`account-balance` success returns:
- declared-account identity fields: `accountCode`, `accountName`, `normalBalance`, `active`, `declaredAt`
- optional query filters: `effectiveDateFrom`, `effectiveDateTo`
- `balances[]`, where each bucket includes `currencyCode`, `debitTotal`, `creditTotal`, `netAmount`, and `balanceSide`

Checked-in examples for the read/query surface:
- [examples/inspect-book-response.json](./examples/inspect-book-response.json)
- [examples/list-accounts-response.json](./examples/list-accounts-response.json)
- [examples/get-posting-response.json](./examples/get-posting-response.json)
- [examples/list-postings-response.json](./examples/list-postings-response.json)
- [examples/account-balance-response.json](./examples/account-balance-response.json)

Checked-in examples for the ledger-plan surface:
- [examples/ledger-plan-template.json](./examples/ledger-plan-template.json)
- [examples/ledger-plan-request.json](./examples/ledger-plan-request.json)
- [examples/execute-plan-committed-response.json](./examples/execute-plan-committed-response.json)
- [examples/execute-plan-assertion-failed-response.json](./examples/execute-plan-assertion-failed-response.json)

## Deterministic Rejections

| Code | Meaning | Extra `details` |
|:-----|:--------|:----------------|
| `book-already-initialized` | `open-book` targeted a book that is already initialized | none |
| `book-contains-schema` | `open-book` targeted a pre-existing SQLite file that already has schema objects | none |
| `administration-book-not-initialized` | an administration command targeted a book that does not exist or has not been opened yet | none |
| `query-book-not-initialized` | a query command targeted a book that does not exist or has not been opened yet | none |
| `posting-book-not-initialized` | a posting command targeted a book that does not exist or has not been opened yet | none |
| `account-normal-balance-conflict` | `declare-account` attempted to amend an existing account's normal balance | `accountCode`, `existingNormalBalance`, `requestedNormalBalance` |
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
