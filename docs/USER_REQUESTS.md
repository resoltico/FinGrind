---
afad: "3.5"
version: "0.4.0"
domain: USER_REQUESTS
updated: "2026-04-10"
route:
  keywords: [fingrind, request-json, response-json, provenance, reversal, idempotency, enums, payload, rejection]
  questions: ["what request json does fingrind accept", "what response envelopes does fingrind return", "which enum values are valid in a fingrind request"]
---

# Request And Response Guide

**Purpose**: Show the accepted JSON request shape and the output documents returned by the CLI.
**Prerequisites**: Familiarity with the packaged CLI in [USER_CLI.md](./USER_CLI.md).

## Request Shape

Inspect the minimal valid request:

```bash
java -jar cli/build/libs/fingrind.jar print-request-template
```

Or inspect the checked-in example payload:

```bash
cat docs/examples/basic-posting-request.json
```

Current request rules:
- all scalar fields are JSON strings, including dates, enums, and `amount`
- `lines[].amount` must be a plain decimal string such as `10.00`; exponent notation such as
  `1e6` is rejected
- `effectiveDate`, `lines`, and `provenance` are required
- `lines` must contain at least one line
- `reversal` is optional
- required provenance fields are `actorId`, `actorType`, `commandId`, `idempotencyKey`, and `causationId`
- optional provenance fields are `correlationId` and `reason`
- `provenance.reason` is required when `reversal` is present, and forbidden otherwise
- `provenance.recordedAt` and `provenance.sourceChannel` are not accepted
- optional fields may be omitted; `null` is accepted for `reversal`, `correlationId`, and `reason`
- `reversal.priorPostingId` must already exist in the selected book
- a reversal requires an exact line-by-line negation of the target posting and only one reversal is allowed per target
- legacy `correction` and `reversal.kind` fields are rejected

## Accepted Values

| Field | Accepted Values |
|:------|:----------------|
| `lines[].side` | `DEBIT`, `CREDIT` |
| `provenance.actorType` | `USER`, `SYSTEM`, `AGENT` |

## CLI Output Shapes

| Output | Returned By | Fields |
|:-------|:------------|:-------|
| success envelope | `help`, `version`, `capabilities` | `status`, `payload` |
| raw request document | `print-request-template` | minimal valid posting request JSON |
| `preflight-accepted` | successful `preflight-entry` | `status`, `idempotencyKey`, `effectiveDate` |
| `committed` | successful `post-entry` | `status`, `postingId`, `idempotencyKey`, `effectiveDate`, `recordedAt` |
| `rejected` | deterministic business rejection | `status`, `code`, `message`, `idempotencyKey`, optional `details` |
| `error` | malformed input or runtime failure | `status`, `code`, `message`, optional `hint`, optional `argument` |

Dynamic fields:
- `capabilities.payload.timestamp` varies per invocation
- `committed.postingId` is generated per successful commit
- `committed.recordedAt` is stamped from the FinGrind commit clock, not caller input

## Deterministic Rejections

| Code | Meaning | Extra `details` |
|:-----|:--------|:----------------|
| `duplicate-idempotency-key` | the selected book already contains the same `idempotencyKey` | none |
| `reversal-reason-required` | a reversal posting omitted `provenance.reason` | none |
| `reversal-reason-forbidden` | a non-reversal posting supplied `provenance.reason` | none |
| `reversal-target-not-found` | `reversal.priorPostingId` does not exist in the selected book | `priorPostingId` |
| `reversal-already-exists` | the target posting already has a full reversal | `priorPostingId` |
| `reversal-does-not-negate-target` | a reversal request does not negate the target posting exactly | `priorPostingId` |

These codes can appear on either `preflight-entry` or `post-entry`.
They represent validly parsed requests that the current book state or reversal policy refuses.

Malformed JSON, wrong field types, missing required fields, invalid date/time text, and domain-validation failures return `status: "error"` with code `invalid-request`.
Argument and parsing failures may also carry a `hint` and `argument` field so a caller can correct the invocation mechanically.
