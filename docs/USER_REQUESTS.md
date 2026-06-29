---
afad: "4.0"
version: "0.58.0"
domain: OPERATOR_REQUESTS
updated: "2026-06-29"
route:
  keywords: [fingrind, request-json, provenance, reversal, idempotency, ledger-plan, execute-plan, posting-shape, account-declaration]
  questions: ["what request json does fingrind accept", "what ledger plan shape does execute-plan accept", "what posting request fields does fingrind accept"]
---

# Request Shape Guide

**Purpose**: Show the accepted JSON request shapes owned by the CLI contract.
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
  payload must also fit within the same 4096-byte limit; this prompt route is accepted only when
  the selected stdout format is `text`, and machine stdout formats reject it as
  `invalid-request`

Every request JSON document must fit within FinGrind's `1048576`-byte UTF-8 payload limit whether
it comes from `--request-file <path>` or `--request-file -`.

`rekey-book` reuses those current-book routes and additionally requires exactly one replacement
passphrase source: `--new-book-key-file`, `--new-book-passphrase-stdin`, or
`--new-book-passphrase-prompt`.

## Posting Request Shape

Inspect the canonical posting-request scaffold:

```bash
fingrind print-request-template
```

Or, in a source checkout, inspect the checked-in companion example that carries the same scaffold
content:

```bash
cat docs/examples/request-template.json
```

The scaffold is intentionally a placeholder-first sample: `provenance.actorType` defaults to `PERSON`, the
emitted document carries explicit `replace-before-commit-*` evidence and provenance tokens, and
those placeholder values must be replaced before real-world use. On one book, an `idempotencyKey`
becomes single-use per book after the first committed posting.
The default posting scaffold uses the minimal `SALE` path with `cashAccountCode`,
`revenueAccountCode`, and `amount`. The raw direct-journal boundary stays available through
`print-request-template post-entry`, but it still has to move at least one declared
cash-and-cash-equivalent asset account.

The packaged CLI can surface the same request-shape truth without leaving the terminal:
`help record-sale`, `help post-entry`, `help declare-account`, and `help execute-plan` inline one
canonical template plus the accepted fields and enum vocabularies for their `--request-file`
payloads. On `execute-plan`, the posting model remains nested under the ledger-plan request shape
rather than surfacing as a second top-level posting document. When you need the raw scaffold bytes
directly, `print-request-template` accepts `declare-account` plus every posting-shaped topic:
`post-entry`, `preflight-entry`, `record-sale`, `record-expense`,
`record-owner-contribution`, `record-owner-withdrawal`, `record-opening-position`, and
`record-reversal`.

Current posting-request rules:
- all top-level date, enum, identifier, and provenance fields are JSON strings
- `entryKind` is required and selects the top-level write path
- `preflight-entry` accepts the full published posting-request family, the typed `record-*`
  commands require their matching business-entry `entryKind`, and raw `post-entry` requires
  `DIRECT_JOURNAL`
- `amount` and `lines[].amount` both use one exact money object with `currencyCode` and `minorUnits`
- every money-object `currencyCode` must be one canonical three-letter uppercase ISO 4217 code supported by FinGrind's pinned currency registry
- every money-object `minorUnits` must contain ASCII digits only, must not contain redundant leading zeroes, must not exceed 19 digits, and must fit inside FinGrind's exact supported minor-unit range
- every money object must decode to one strictly positive posted amount
- `effectiveDate`, `evidence`, and `provenance` are required for every entry kind
- `DIRECT_JOURNAL` requires balanced `lines`
- `DIRECT_JOURNAL` is rejected unless at least one `lines[].accountCode` references a declared
  `CASH_AND_CASH_EQUIVALENT` asset account
- `SALE` requires `cashAccountCode`, `revenueAccountCode`, and `amount`
- `EXPENSE` requires `expenseAccountCode`, `cashAccountCode`, and `amount`
- `OWNER_CONTRIBUTION` requires `cashAccountCode`, `equityAccountCode`, and `amount`
- `OWNER_WITHDRAWAL` requires `equityAccountCode`, `cashAccountCode`, and `amount`
- `OPENING_POSITION` requires `openingBalances`
- `REVERSAL` requires `lines` plus `reversal`
- every direct `DIRECT_JOURNAL` or `REVERSAL` entry must contain at least two journal lines
- `evidence.sourceDocuments` must contain at least one source-document object
- every `evidence.sourceDocuments[]` entry requires `sourceDocumentId`, `sourceDocumentType`, and `documentDate`
- on command-scoped `requestShapes.bookkeepingEntry` payloads, the selected `sourceDocumentType`
  policy is published directly on `sourceDocumentFields[]` and on the embedded executable schema;
  the full-family descriptor also keeps `sourceDocumentTypeMode`,
  `acceptedSourceDocumentTypes`, and `sourceDocumentTypeSemantics` on
  `entryKindSemantics[]`
- `evidence.approvals` is required as an array and may be empty
- every `evidence.approvals[]` entry requires `approvalId`, `approvalType`, `approverId`, `approverType`, `decision`, and `approvedAt`
- `lines[].accountCode` must start with an ASCII letter or digit, may then contain only ASCII letters, digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 255 characters
- every direct `DIRECT_JOURNAL` or `REVERSAL` entry must contain at least one `DEBIT` line and at least one `CREDIT` line
- every line inside one direct `DIRECT_JOURNAL` or `REVERSAL` entry must share the same `lines[].amount.currencyCode`
- every direct `DIRECT_JOURNAL` entry is rejected when debit-credit netting reduces every referenced account to zero, because that request would record no durable account movement
- every journal-line amount, every top-level `amount`, and every `openingBalances[].amount` must use the selected book's functional currency
- `foreignExchange` is optional for `DIRECT_JOURNAL`, `SALE`, `EXPENSE`,
  `OWNER_CONTRIBUTION`, `OWNER_WITHDRAWAL`, and `REVERSAL`, and must be absent for
  `OPENING_POSITION`
- `foreignExchange` requires `transactionAmount`, `functionalAmount`, `quotedRate`, and
  `treatmentKind`
- `foreignExchange.quotedRate` requires `transactionCurrencyAmount`,
  `functionalCurrencyAmount`, `quotedOn`, and `quoteSource`
- `foreignExchange.transactionAmount` and
  `foreignExchange.quotedRate.transactionCurrencyAmount` must share one distinct non-functional
  currency
- `foreignExchange.functionalAmount` and
  `foreignExchange.quotedRate.functionalCurrencyAmount` must use the selected book's functional
  currency
- typed `SALE`, `EXPENSE`, `OWNER_CONTRIBUTION`, and `OWNER_WITHDRAWAL` requests currently require
  `foreignExchange.treatmentKind: "SPOT_SETTLEMENT"`; direct `DIRECT_JOURNAL` and `REVERSAL`
  requests accept the broader published treatment vocabulary
- `foreignExchange` records foreign transaction facts without changing the journal-line currency,
  so mixed-currency journal lines remain rejected
- `reversal` is required only for `REVERSAL` and must be absent for every other `entryKind`
- required provenance fields are `actorId`, `actorType`, `commandId`, `idempotencyKey`, and `causationId`
- `provenance.idempotencyKey` must start with an ASCII letter or digit, may then contain only ASCII letters, digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 128 characters
- optional provenance field is `correlationId`
- `reversal.priorPostingId` and `reversal.reason` are both required when `reversal` is present
- `provenance.recordedAt` and `provenance.sourceChannel` are not accepted
- optional fields may be omitted; `null` is accepted for `reversal` and `correlationId`
- `reversal.priorPostingId` must already exist in the selected book
- a reversal requires one exact line-by-line negation of the target posting and only one reversal is allowed per target
- `OPENING_POSITION` may touch only `ASSET`, `LIABILITY`, or `EQUITY` accounts
- `OPENING_POSITION` is accepted only before the first committed posting exists in the selected book, so all adoption balances must be seeded inside one opening-only window
- `requestShapes.bookkeepingEntry.reachabilityMatrix[]` is the canonical per-classification truth for which declared-account cells are opening-reachable, operational-journal-reachable, or reversal-reachable; the built-in `RESULT_HOLDING` classification remains opening-reachable but is reserved from caller-authored standard journals and reversals
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
  "accountNodeKind": "POSTABLE",
  "financialPositionLineClassification": "CURRENT_ASSET",
  "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
}
```

Current account-declaration rules:
- `accountCode`, `accountName`, `accountType`, and `accountNodeKind` are required
- `cashFlowAssetClassification` is required when `accountType` is `ASSET` and is forbidden for
  non-asset accounts
- `parentAccountCode` is optional and declares one explicit chart parent when this account belongs
  under another declared account
- `accountCode` must start with an ASCII letter or digit, may then contain only ASCII letters,
  digits, `.`, `_`, `:`, `/`, or `-`, and must not exceed 255 characters
- `accountCode` is an opaque book-local identifier today; FinGrind does not infer account class or
  hierarchy from numeric ranges or prefixes
- `accountName` must be a non-blank string
- `accountType` must be one of the canonical chart classifications supported by FinGrind
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
- redeclaring an existing account with a different chart parent or declared taxonomy is
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

The default ledger-plan scaffold and the primary runnable plan example both use a typed `SALE`
posting step. Raw direct-journal plans remain available when a caller needs full line-level
control.

Current ledger-plan rules:
- top-level fields are `planId` and `steps`
- `planId` must be a non-blank string
- `steps` must contain at least one object and every `stepId` must be unique
- `ensure-book` is allowed only as the first step when a plan initializes a book
- every step requires `stepId` and `kind`
- `ensure-book` uses nested `ensureBook`, which requires `entityName`, `functionalCurrency`, and
  `fiscalYearStart`; the runtime persists the built-in doctrine facts and echoes them back in
  response payloads
- `declare-account` uses nested `declareAccount`
- `preflight-entry`, every committed `record-*` step, and raw `post-entry` use nested `posting`,
  which has the same shape as the normal posting request, including required
  `evidence.sourceDocuments[]` and `evidence.approvals[]`
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
- `print-plan-template` emits the canonical `execute-plan` scaffold shape as one placeholder-first
  workflow with one minimal sale posting step; replace the placeholder evidence and provenance
  values before real-world use
- execution semantics are not request knobs: plans are atomic, halt on first failed step, return
  one bounded aggregate summary by default, and return one complete journal when
  `--result-detail full` is selected; ordinary business steps keep their canonical `kind`,
  assertion entries optionally add `detailKind`, and unexpected begin, initialization-check,
  commit, or rollback failures end the journal with `kind: "plan-boundary"` plus
  `boundaryCheckpoint`
- unexpected transaction-boundary failures such as begin, commit, or rollback problems are mapped
  into the terminal rejected journal step instead of escaping as an untyped plan exception
- plan-journal steps now carry typed `data` records instead of generic fact bags
- money-bearing plan-journal `data` fields use objects carrying `currencyCode` and `minorUnits`
- successful `list-accounts` journal steps emit `count`, `pageLimit`, optional `nextCursor`,
  `hasMore`, and repeated typed `accounts[]`
- successful `list-postings` journal steps emit `count`, `pageLimit`, optional `nextCursor`,
  `hasMore`, and repeated typed summary `postings[]` with `postingId`, `postingKind`,
  `postingOriginKind`, `reversalState`, optional `reversesPostingId`, optional
  `reversedByPostingId`, `effectiveDate`, `recordedAt`, `debitTotal`, `creditTotal`,
  `accountCodes[]`, `sourceDocumentIds[]`, and `approvalIds[]`

For every non-plan single-command invocation, deterministic business rejections and deterministic
failures now use one JSON diagnostics envelope on stderr regardless of `--output`. Successful
stdout may be text, JSON, or CSV where advertised, but failing single-command invocations keep the
same parseable diagnostics shape with the same top-level `message`, optional `hint`, and any typed
detail payload that identifies the failing posting id, blocked close-reserved account code and
classification, account-state violation set, or related deterministic repair data. `execute-plan`
is the
exception: its `REJECTED` and `ASSERTION_FAILED` outcomes are primary result envelopes on stdout.

## Accepted Values

| Field | Accepted Values |
|:------|:----------------|
| `lines[].side` | `DEBIT`, `CREDIT` |
| `foreignExchange.treatmentKind` | `SPOT_SETTLEMENT`, `REALIZED_SETTLEMENT`, `UNREALIZED_REMEASUREMENT` |
| `provenance.actorType` | `PERSON`, `SYSTEM`, `AGENT` |
| `accountType` | `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE` |
| `accountNodeKind` | `POSTABLE`, `HEADER` |
| `financialPositionLineClassification` | `CURRENT_ASSET`, `NONCURRENT_ASSET`, `CURRENT_LIABILITY`, `NONCURRENT_LIABILITY`, `EQUITY_CONTRIBUTION`, `EQUITY_WITHDRAWAL`, `RESULT_HOLDING`, `RETAINED_ACCUMULATED`, `RESERVE`, `OTHER_EQUITY` |
| `profitAndLossLineClassification` | `OPERATING_REVENUE`, `OTHER_REVENUE`, `FINANCE_INCOME`, `COST_OF_SALES`, `OPERATING_EXPENSE`, `DEPRECIATION_AND_AMORTIZATION`, `FINANCE_EXPENSE`, `OTHER_EXPENSE` |

`lines[].side` is input polarity for one journal line. Response-side `balanceSide` is a derived net orientation for grouped balances, running balances, and report totals; it is not a second writable posting-line field.

## Response And Output Guide

Request shapes stay in this guide. Response envelopes, read and report payloads, capabilities
output, execute-plan results, and deterministic rejection or error payloads now live in
[USER_RESPONSES.md](./USER_RESPONSES.md).

That companion guide owns the full response contract, including:
- the shared `status`, `payload`, and optional `artifacts[]` envelope families
- the `capabilities` discovery payload and its typed descriptor inventories
- read and report payloads such as `inspect-book`, `list-postings`, `trial-balance`,
  `account-ledger`, and `cash-flow-statement`
- `execute-plan` summaries and optional journals
- deterministic rejection and error payloads, including
  [examples/account-state-violations-text.txt](./examples/account-state-violations-text.txt) and
  [examples/entry-semantics-violations-text.txt](./examples/entry-semantics-violations-text.txt)

For comparative report outputs, [USER_RESPONSES.md](./USER_RESPONSES.md) is the canonical owner of
the statement payload families and records that `cash-flow-statement` carries
`comparativeOpeningCashTotals[]`, `comparativeMovementTotals[]`, and
`comparativeClosingCashTotals[]` when one comparative period selection is requested and resolved.
