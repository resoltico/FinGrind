---
afad: "5.0.1"
version: "0.61.0"
domain: DEVELOPER_REJECTION_TEXT_SURFACE
updated: "2026-07-16"
route:
  keywords: [rejection text surface, text rejection layout, violation sections, machine envelope, operator guidance, repair guidance]
  questions: ["how does FinGrind render posting rejection text after the lean machine-envelope break", "what text layout governs nested repairable rejection families", "what owns the slim posting rejection message and hint contract"]
---

# Rejection Text-Surface Contract

**Purpose**: Record the live machine-envelope and text-mode contract for nested repairable posting
rejection families.
**Status**: Implemented and verified on 2026-06-18.
**Scope**:
- posting-side nested repairable rejection families only:
  - `entry-semantics-violations`
  - `account-state-violations`
- `--output text` rejection rendering
- the paired machine top-level `message` and `hint` for those same families

## Current Executable Posture

The live executable contract now splits responsibility cleanly:
- the machine envelope publishes ordered `details.violations[]` items with canonical `code`,
  `field`, `message`, `category`, and `repair`, plus account-specific locator fields for
  `account-state-violations`
- the machine top-level `message` is a stable family count-summary
- nested repairable posting families do not emit a top-level machine `hint`
- text mode renders one issue section per violation and owns the human-readable repair guidance

This document therefore records the implemented boundary, not a pending design exercise.

## Design Constraints

Any approved implementation must preserve these rules:
- nested repairable families keep their canonical ordered `details.violations[]` machine contract
- singleton rejection families do not grow `violations[]`
- preflight and commit surfaces stay text-identical for the same rejection payload
- the machine top-level `message` must become a stable summary, not a concatenation of issue prose
- nested repairable families must not emit a top-level machine `hint`; the issue set already owns
  the repair narrative
- the text surface must remain at least as useful as the current executable output for a human
  operator reading stderr

## Division Of Responsibility

### Machine envelope

For nested repairable posting families only:
- `message`: stable count-summary owned by the rejection family
- `hint`: omitted
- `details.violations[]`: canonical per-issue machine repair data

Message forms:
- `account-state-violations`:
  - singular: `Posting rejected with 1 account-state issue.`
  - plural: `Posting rejected with <N> account-state issues.`
- `entry-semantics-violations`:
  - singular: `Posting rejected with 1 entry-semantics issue.`
  - plural: `Posting rejected with <N> entry-semantics issues.`

This keeps the machine envelope:
- format-stable across one issue vs many issues
- independent of the specific violation prose
- free of redundant repair narration
- small enough to quote in logs, automation, and structured diagnostics

### Text surface

For nested repairable posting families only, text mode is the owned human explanation path.

The text surface renders:
- one short family summary near the top
- one readable section per violation
- the full per-issue `message` and `repair`
- locator fields only where they help the operator act

The text surface does not depend on the machine top-level `message` or `hint` carrying the full
human narrative.

## Text Layout

### Shared header

```text
Rejected
  Code             <family-code>
  Summary          <stable family summary>
  Idempotency key  <idempotency-key-if-present>
```

Rules:
- the label is `Summary`, not `Message`, because this line is no longer the full human narrative
- no top-level `Hint` row for nested repairable posting families
- the operator-facing repair guidance lives in the issue sections below
- the header uses the same singular/plural family-summary string as the machine `message`

### Account-state issue section

```text
Issue 1 | unknown-account
  Field         lines[].accountCode
  Category      account-registry
  Account code  9998
  Why           Journal line references undeclared account '9998'.
  Repair        Declare the missing account before retrying the posting.
```

Additional rule:
- render `Account node kind` only when the violation carries it

### Entry-semantics issue section

```text
Issue 2 | source-document-type-not-accepted
  Field     evidence.sourceDocuments[].sourceDocumentType
  Category  source-document-type
  Why       Entry kind 'SALE_SETTLED' does not accept sourceDocumentType 'invoice'. Accepted values: cash-receipt, bank-deposit, card-settlement.
  Repair    Use an accepted source document type for the selected entry kind's source-document policy.
```

Additional rule:
- omit `Field` when the violation has no field locator

### Example: multiple entry-semantics issues

```text
Rejected
  Code             entry-semantics-violations
  Summary          Posting rejected with 2 entry-semantics issues.
  Idempotency key  idem-multi-violation

Issue 1 | distinct-role-accounts-required
  Category  account-role-assignment
  Why       Entry kind 'SALE_SETTLED' requires cashAccountCode and revenueAccountCode to reference distinct accounts, but both point to '1000'.
  Repair    Assign distinct accounts to the semantic role fields named in the violation.

Issue 2 | source-document-type-not-accepted
  Field     evidence.sourceDocuments[].sourceDocumentType
  Category  source-document-type
  Why       Entry kind 'SALE_SETTLED' does not accept sourceDocumentType 'invoice'. Accepted values: cash-receipt, bank-deposit, card-settlement.
  Repair    Use an accepted source document type for the selected entry kind's source-document policy.
```

### Example: non-postable account with node kind

```text
Rejected
  Code             account-state-violations
  Summary          Posting rejected with 1 account-state issue.
  Idempotency key  idem-account-state

Issue 1 | non-postable-account
  Field              lines[].accountCode
  Category           account-node-kind
  Account code       3000
  Account node kind  HEADER
  Why                Journal line references header account '3000', declared as 'HEADER', which cannot accept direct postings.
  Repair             Replace the header account with a postable account before retrying the posting.
```

## Operator Value Rationale

The implemented text surface preserves every operator-relevant fact the older executable text
surface exposed, while removing duplicated top-level prose from the machine envelope.

| Operator need | Retired top-level prose model | Current text surface |
| --- | --- | --- |
| identify rejection family | top-level `Code` row | top-level `Code` row |
| know issue count quickly | top-level `Reported issues` row and repeated prose | top-level `Summary` line |
| inspect each issue separately | `Issue N` sections | `Issue N | <code>` sections |
| see stable per-issue code | inside each issue section | inside each issue heading |
| see locator fields | inside each issue section | inside each issue section |
| see full explanation | top-level prose plus issue sections | issue sections only |
| see repair guidance | top-level hint plus issue sections | issue sections only |
| quote one stable machine line in logs | retired top-level prose varied by issue set | stable family summary |

The current layout is therefore at least as useful because it:
- keeps all issue-level facts
- keeps one readable family header
- removes only duplicated prose
- improves the stability of machine-visible top-level text

## Non-Goals

This contract does not authorize:
- changing JSON field names
- changing nested `details.violations[]` ordering
- changing singleton rejection families
- changing exit codes
- changing account-state or entry-semantics canonical violation owners
- changing non-posting rejection text surfaces

## Standing Verification Rules

The executable surface must:
- keep the machine envelope lean and summary-only for nested repairable families
- omit top-level `hint` for nested repairable families in both JSON and text-mode projection
- render the full human guidance only in text mode
- drive both preflight and commit through the same text layout
- preserve top-level `hint` behavior for singleton and non-posting rejection families unless a
  separate change updates those contracts
- verify the text layout with snapshot-style tests for:
  - single issue
  - multiple issues
  - with and without `field`
  - with and without `accountNodeKind`
  - singular and plural machine `message` summaries
  - absent top-level `hint` for nested repairable families

## Decision Record

This surface is governed by these decisions:
- the nested repairable machine envelope keeps `message` plus `details`, and drops `hint`
- `Summary` is the correct text-mode header label for those families
- `Issue N | <code>` is the approved issue heading shape
- `Why` and `Repair` are the approved human-facing issue labels
- the current text surface is judged at least as useful as the retired top-level-prose model
- further changes should preserve these decisions unless this contract is revised explicitly

Checked-in operator examples live at
[examples/account-state-violations-text.txt](./examples/account-state-violations-text.txt) and
[examples/entry-semantics-violations-text.txt](./examples/entry-semantics-violations-text.txt).
