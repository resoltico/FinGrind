---
afad: "5.0.1"
version: "0.64.0"
domain: OPERATOR_MUTATION_RESPONSES
updated: "2026-09-01"
route:
  keywords: [fingrind, mutation-response, posting-response, execute-plan-response, attestation-commit, attestation-review, receipt-response, idempotent-replay]
  questions: ["what JSON does a FinGrind mutation return", "what does execute-plan return", "how does FinGrind publish attestation provenance", "what does a receipt response contain"]
---

# Mutation And Attestation Response Guide

**Purpose**: Define the response contracts for bookkeeping mutations, ledger plans, and verifiable
operation attestation.
**Companion references**: [USER_RESPONSES.md](./USER_RESPONSES.md) owns shared envelopes and
discovery; [USER_BOOK_ATTESTATION.md](./USER_BOOK_ATTESTATION.md) owns credential custody and
verification workflows.

## Posting And Plan Responses

- `preflight-entry.payload.resolvedJournal` publishes the expanded journal and semantic
  classification that passed advisory validation. It is not a commit guarantee: the corresponding
  write performs authoritative transactional validation.
- A committed posting has a UUIDv7 `postingId`, clock-stamped `recordedAt`, and exact resolved
  journal. `idempotentReplay` is true only when a normalized request matched an existing posting;
  a fresh commit has `attestationCommit.{operationOrder,operationHead}`, while a replay has
  `attestationCommit: null` because no new operation was appended.
- Every `execute-plan` payload includes `attestationDisposition` and `attestationCommit`.
  `appended` requires the exact aggregate operation; `read-only` proves credential-free execution;
  `no-durable-child-mutation` proves the mutation-capable boundary completed without a durable
  child, such as an all-idempotent replay. The latter two require `attestationCommit: null`, as do
  rejected and assertion-failed plan payloads.
- `capabilities --output json --detail full` publishes the closed outcome table at
  `payload.fullContract.planExecution.attestationOutcomes`; the focused response view and
  `payload.fullContract.requestShapes.ledgerPlan.execution.attestationOutcomes` publish the same
  token, commit mode, and credential mode. Consumers must use that typed table rather than infer
  the pairing from prose.
- Every successful attested mutation—including book opening, account and tax administration,
  sweeps, fiscal close, maintenance, and registry mutation—uses the same exact
  `payload.attestationCommit`; successful no-ops carry `null`.
- A full plan journal uses typed `data` records. It never contains genesis steps; create immutable
  book identity with `open-book` first. Account steps expose `outcome` and `account`, committed
  and lookup steps expose typed evidence, assertion steps expose account and repeated balances,
  and a plan is limited to 100 complete steps.

## Verified Evidence And Readback

- `verify-book` returns `bookId`, the verified head, and its signed `previousHead`; genesis uses
  64 zero hexadecimal characters for the predecessor.
- Receipt export and verification each return `receiptFile`, `bookId`, and the exact
  `receiptAttestationAnchor.{operationOrder,operationHead}`. The head is 64 lowercase hexadecimal
  characters; JSON uses the canonical physical receipt path while text uses its redacted hint.
  Export also returns warnings and the `attestation-receipt-v1` artifact, while verification returns
  findings.
- `get-posting`, `list-postings`, account-ledger rows, and full-plan query steps expose an
  `attestationCommit` projection reconstructed from the verified immutable chain, never copied into
  mutable posting state. CSV flattens it to `attestationOperationOrder` and
  `attestationOperationHead`; list and ledger text/PDF present the canonical inline attestation
  order, while JSON and CSV retain the exact head. An unavailable link renders as `(none)` or its
  equivalent explicit unavailable state.
- `attestation-review` returns the book, verified head, and one flat finding per affected
  operation. Text groups findings by declaration and compresses only exact consecutive ranges. A
  strict `verify-book --require-clean-attestation` refusal has no success payload but retains
  `bookId`, verified and previous heads, and the same `reviewFindings` in typed details.
- A review window beyond the verified head is `attestation-review-window-exceeds-head` with
  `credentialKeyId`, first and nullable last affected order, and `verifiedHeadOrder`; it returns no
  verification result or finding.

## Domain-Specific Facts

- `get-posting` publishes retained Latvian payroll calculation and settlement facts where present;
  `latvian-payroll-register` is an operational reconciliation, not an EDS filing.
- `payload.resultDetail`, plan summary times, and full-journal step times are clock-stamped by
  FinGrind. Successful declaration, posting, query, and assertion data remain typed rather than
  generic fact arrays.
