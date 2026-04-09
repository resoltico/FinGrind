---
afad: "3.5"
version: "0.1.0"
domain: USER_REQUESTS
updated: "2026-04-08"
route:
  keywords: [fingrind, request-json, response-json, provenance, correction, idempotency, enums, payload]
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
- `effectiveDate`, `lines`, and `provenance` are required
- `lines` must contain at least one line
- `correction` is optional
- `provenance.recordedAt` is optional; when omitted, FinGrind uses the current clock instant
- optional fields may be omitted; `null` is also accepted for `correction`, `correlationId`, `recordedAt`, and `reason`

## Accepted Values

| Field | Accepted Values |
|:------|:----------------|
| `lines[].side` | `DEBIT`, `CREDIT` |
| `provenance.actorType` | `USER`, `SYSTEM`, `AGENT` |
| `provenance.sourceChannel` | `CLI`, `API`, `TEST` |
| `correction.kind` | `REVERSAL`, `AMENDMENT` |

## CLI Output Shapes

| Output | Returned By | Fields |
|:-------|:------------|:-------|
| success envelope | `help`, `version`, `capabilities` | `status`, `payload` |
| raw request document | `print-request-template` | minimal valid posting request JSON |
| `preflight-accepted` | successful `preflight-entry` | `status`, `idempotencyKey`, `effectiveDate` |
| `committed` | successful `post-entry` | `status`, `postingId`, `idempotencyKey`, `effectiveDate`, `recordedAt` |
| `rejected` | deterministic business rejection | `status`, `code`, `message`, `idempotencyKey` |
| `error` | malformed input or runtime failure | `status`, `code`, `message`, optional `hint`, optional `argument` |

Dynamic fields:
- `capabilities.payload.timestamp` varies per invocation
- `committed.postingId` is generated per successful commit
- `committed.recordedAt` depends on the request or current clock

## Deterministic Rejections

The current deterministic rejection code is `DUPLICATE_IDEMPOTENCY_KEY`.
It can appear on either `preflight-entry` or `post-entry` when the selected book already contains the same idempotency key.

Malformed JSON, wrong field types, missing required fields, invalid date/time text, and domain-validation failures return `status: "error"` with code `invalid-request`.
Argument and parsing failures may also carry a `hint` and `argument` field so a caller can correct the invocation mechanically.
