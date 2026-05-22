---
afad: "4.0"
version: "0.44.0"
domain: ADR_10X_ACCOUNTING_FOUNDATION
updated: "2026-05-22"
route:
  keywords: [fingrind, 10x, accounting foundation, roadmap, doctrine, evidence, business events, tax, fx, cash flow, disclosures]
  questions: ["what exactly must fingrind implement to reach a 10 out of 10 accounting foundation", "what is fingrind's exact roadmap from the current bookkeeping kernel to best in class", "which bounded contexts are missing from fingrind today"]
---

# 10/10 Accounting Foundation ADR

**Purpose**: State FinGrind's exact current maturity, the target meaning of "10/10", and the
hard-break implementation sequence required to reach that target.
**Companion documents**:
- [ADR_ACCOUNTING_BASELINE.md](./ADR_ACCOUNTING_BASELINE.md)
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md)
- [DEVELOPER_AGGREGATES.md](./DEVELOPER_AGGREGATES.md)
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md)

## Decision

FinGrind is not yet a 10/10 bookkeeping or accounting product line.

Current exact posture:
- the protected-book storage and truth-ownership split are strong enough for one durable
  single-entity bookkeeping kernel
- the current built-in bookkeeping/reporting kernel is materially narrower than the product line's
  long-term ambition
- the current public write surface is too raw because caller-authored postings remain the primary
  mutation language

The repository may claim a 10/10 accounting foundation only after the missing bounded contexts
below exist as executable, durable, and tested system owners.

## Current Exact State

The current hard-break line is:
- one protected book for one accounting entity
- one functional currency per book
- exact-money append-only postings
- explicit chart and statement taxonomy
- entity-form-aware period close
- built-in financial position, income statement, and changes-in-equity reports
- deterministic maintenance, runtime, bundle, and release discipline

The current hard-break line is not:
- one full statutory reporting product
- one full IFRS or local-GAAP engine
- one finished SME operating-accounting system
- one evidence-rich bookkeeping platform
- one multi-currency, tax-aware, disclosure-capable accounting foundation

## 10/10 Meaning

For FinGrind, "10/10" means:

1. FinGrind owns accounting meaning above storage mechanics.
2. Durable accepted facts carry source-document and approval evidence.
3. Typed business-event commands replace raw journal mechanics as the primary public write model.
4. Cash-flow, disclosure, tax, and FX foundations exist as first-class adjacent contexts.
5. Operational subledgers and reporting overlays publish into the ledger instead of leaking their
   logic into ad hoc postings.
6. The public protocol, CLI, examples, and docs tell the exact truth about that implemented
   system.

## Missing Bounded Contexts

### 1. Evidence Context

Must own:
- source-document references and durable evidence bundles
- approval references and approval evidence
- evidence-link invariants between accepted business/accounting events and durable postings

Completion gate:
- no caller-authored posting or business event commits without first-class evidence payloads under
  the published contract
- persisted posting and event facts retain their evidence links
- query/output surfaces expose the same evidence facts deterministically

### 2. Business-Event Context

Must own:
- typed business-event commands
- business-event admissibility rules
- business-event-to-posting recipe translation
- event-level lifecycle and rejection language

Completion gate:
- raw `post-entry` is no longer the primary public write surface
- one business event, not one manual journal, becomes the normal public command language
- posting recipes are owned, tested, and traceable back to event facts plus evidence

### 3. Cash-Flow Reporting Context

Must own:
- cash-flow classification doctrine
- cash and cash-equivalent movement semantics
- a first-class statement-of-cash-flows report

Completion gate:
- built-in reporting includes a statement of cash flows with deterministic classification rules and
  articulation tests against other built-in statements

### 4. Tax Foundation Context

Must own:
- registrations, tax codes, rates, inclusivity, recoverability, and period obligations
- tax-bearing business-event and posting semantics
- tax liability/receivable reporting facts

Completion gate:
- tax is no longer manual posting folklore
- tax determination and reporting are executable owned facts, not external operator convention

### 5. Foreign-Exchange Context

Must own:
- transaction currency facts
- rate evidence
- settlement, remeasurement, and realized/unrealized FX doctrine

Completion gate:
- mixed-currency business events and resulting accounting treatment are owned by one explicit
  context instead of being rejected outright

### 6. Disclosure Context

Must own:
- disclosure-note payloads
- accounting-policy note facts
- report-package composition for built-in reporting

Completion gate:
- built-in reporting is not limited to statement bodies alone

### 7. Operational Subledger Contexts

Must own:
- receivables
- payables
- invoicing
- settlement
- inventory
- payroll

Completion gate:
- operating flows create business events that publish accounting facts into the protected book
- ledger users are not forced to emulate operations directly with raw journal requests

## Implementation Order

The hard-break implementation order is:

1. Evidence context
2. Business-event context
3. Cash-flow reporting context
4. Tax foundation context
5. Foreign-exchange context
6. Disclosure context
7. Operational subledger contexts
8. Jurisdiction or standards overlays only after the neutral foundation above is executable

This order is deliberate:
- evidence must arrive before business events can be trusted
- business events must arrive before tax, FX, and subledgers can publish owned semantics cleanly
- reporting overlays must not outrun the facts they need

## Rules For Every Phase

1. No fake extension seams. A context is not published until it owns commands, state, storage, and
   tests.
2. No backwards-compatibility preservation for obsolete write models or placeholder schemas.
3. No documentation claims ahead of executable ownership.
4. No adapter-owned domain meaning.
5. Every new context must land with conformance tests that express the local ubiquitous language.

## Evidence For Progress

Progress toward 10/10 is proven only by:
- executable contracts
- durable storage
- query/report projection
- replayable examples
- quality-gate coverage
- updated doctrine and docs

It is not proven by:
- comments about future extensibility
- placeholder DTOs
- dormant schema columns
- abstract service names
- roadmap prose alone

## Current Session Commitment

This session begins the 10/10 route with two hard-break actions:

1. codify the doctrine and target posture so the repository theory is explicit
2. implement the evidence foundation as the first durable missing bounded context
