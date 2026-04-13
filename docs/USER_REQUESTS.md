---
afad: "3.5"
version: "0.8.0"
domain: USER_REQUESTS
updated: "2026-04-13"
route:
  keywords: [fingrind, request-json, response-json, provenance, reversal, idempotency, enums, payload, rejection, declare-account]
  questions: ["what request json does fingrind accept", "what response envelopes does fingrind return", "which enum values are valid in a fingrind request"]
---

# Request And Response Guide

**Purpose**: Show the accepted JSON request shapes and the output documents returned by the CLI.
**Prerequisites**: Familiarity with the packaged CLI in [USER_CLI.md](./USER_CLI.md).

## Posting Request Shape

Inspect the minimal valid posting request:

```bash
java -jar cli/build/libs/fingrind.jar print-request-template
```

Or inspect the checked-in example payload:

```bash
cat docs/examples/basic-posting-request.json
```

Current posting-request rules:
- all scalar fields are JSON strings, including dates, enums, and `amount`
- `lines[].amount` must be a plain decimal string such as `10.00`; exponent notation such as
  `1e6` is rejected
- `effectiveDate`, `lines`, and `provenance` are required
- `lines` must contain at least one line
- every line inside one entry must share the same `currencyCode`
- `reversal` is optional
- required provenance fields are `actorId`, `actorType`, `commandId`, `idempotencyKey`, and `causationId`
- optional provenance fields are `correlationId` and `reason`
- `provenance.reason` is required when `reversal` is present, and forbidden otherwise
- `provenance.recordedAt` and `provenance.sourceChannel` are not accepted
- optional fields may be omitted; `null` is accepted for `reversal`, `correlationId`, and `reason`
- `reversal.priorPostingId` must already exist in the selected book
- a reversal requires an exact line-by-line negation of the target posting and only one reversal is allowed per target
- legacy `correction` and `reversal.kind` fields are rejected

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

## Accepted Values

| Field | Accepted Values |
|:------|:----------------|
| `lines[].side` | `DEBIT`, `CREDIT` |
| `provenance.actorType` | `USER`, `SYSTEM`, `AGENT` |
| `normalBalance` | `DEBIT`, `CREDIT` |

## CLI Output Shapes

| Output | Returned By | Fields |
|:-------|:------------|:-------|
| success envelope | `help`, `version`, `capabilities`, `open-book`, `declare-account`, `list-accounts` | `status`, `payload` |
| raw request document | `print-request-template` | minimal valid posting request JSON |
| `preflight-accepted` | successful `preflight-entry` | `status`, `idempotencyKey`, `effectiveDate` |
| `committed` | successful `post-entry` | `status`, `postingId`, `idempotencyKey`, `effectiveDate`, `recordedAt` |
| `rejected` | deterministic business rejection | `status`, `code`, `message`, optional `idempotencyKey`, optional `details` |
| `error` | malformed input or runtime failure | `status`, `code`, `message`, optional `hint`, optional `argument` |

Dynamic fields:
- `capabilities.payload.timestamp` varies per invocation
- `open-book.payload.initializedAt` is stamped from the FinGrind clock
- `declare-account.payload.declaredAt` is stamped from the FinGrind clock on first declaration
- `committed.postingId` is generated per successful commit as a UUID v7 value
- `committed.recordedAt` is stamped from the FinGrind commit clock, not caller input

`preflight-accepted` is advisory. It confirms that the current request passed validation against
the current book state, but it is not a durable commit guarantee: `post-entry` still performs its
authoritative transactional checks before committing.

## Capabilities Discovery Shape

`capabilities` is the canonical machine contract and now exposes typed descriptors instead of raw
string lists for the drift-prone parts of the surface:

- `requestShapes.postEntry.topLevelFields`, `lineFields`, `provenanceFields`, and `reversalFields`
  are arrays of `{ "name", "presence", "description" }`
- `requestShapes.*.enumVocabularies` are arrays of `{ "name", "values" }` sourced from the live
  enum constants
- `responseModel.rejections` is an array of `{ "code", "description" }`
- `preflightSemantics` is the short machine hint and `preflight` expands it with
  `isCommitGuarantee`
- `currencyModel` declares the current single-currency scope and the explicit
  `multiCurrencyStatus: "not-supported"`

## Account Registry Responses

`open-book` success returns:
- `payload.bookFile`
- `payload.initializedAt`

`declare-account` success returns:
- `payload.accountCode`
- `payload.accountName`
- `payload.normalBalance`
- `payload.active`
- `payload.declaredAt`

`list-accounts` success returns:
- `payload` as a JSON array of declared-account objects
- each array entry includes `accountCode`, `accountName`, `normalBalance`, `active`, and `declaredAt`

## Deterministic Rejections

| Code | Meaning | Extra `details` |
|:-----|:--------|:----------------|
| `book-already-initialized` | `open-book` targeted a book that is already initialized | none |
| `book-contains-schema` | `open-book` targeted a pre-existing SQLite file that already has schema objects | none |
| `book-not-initialized` | the selected book does not exist or has not been opened yet | none |
| `account-normal-balance-conflict` | `declare-account` attempted to amend an existing account's normal balance | `accountCode`, `existingNormalBalance`, `requestedNormalBalance` |
| `unknown-account` | a posting line references an undeclared account | `accountCode` |
| `inactive-account` | a posting line references a declared but inactive account | `accountCode` |
| `duplicate-idempotency-key` | the selected book already contains the same `idempotencyKey` | none |
| `reversal-reason-required` | a reversal posting omitted `provenance.reason` | none |
| `reversal-reason-forbidden` | a non-reversal posting supplied `provenance.reason` | none |
| `reversal-target-not-found` | `reversal.priorPostingId` does not exist in the selected book | `priorPostingId` |
| `reversal-already-exists` | the target posting already has a full reversal | `priorPostingId` |
| `reversal-does-not-negate-target` | a reversal request does not negate the target posting exactly | `priorPostingId` |

`book-not-initialized`, `unknown-account`, and `inactive-account` can appear on both
`preflight-entry` and `post-entry`.
They represent validly parsed requests that the current book lifecycle or account registry refuses.

Malformed JSON, wrong field types, missing required fields, invalid date/time text, and
domain-validation failures return `status: "error"` with code `invalid-request`.
Argument and parsing failures may also carry a `hint` and `argument` field so a caller can correct
the invocation mechanically.
