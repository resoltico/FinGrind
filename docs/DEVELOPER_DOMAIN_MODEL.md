---
afad: "4.0"
version: "0.30.0"
domain: DEVELOPER_DOMAIN_MODEL
updated: "2026-05-05"
route:
  keywords: [fingrind, domain model, bounded context, context map, ubiquitous language, bookkeeping, workflow, published language]
  questions: ["what are fingrind's bounded contexts", "what is the context map in fingrind", "which term is canonical for the owner of a book", "how does execute-plan relate to bookkeeping in fingrind"]
---

# Domain Model Reference

**Purpose**: Canonical domain language, bounded contexts, and context-map reference for FinGrind.
**Companion documents**:
- [DEVELOPER.md](./DEVELOPER.md)
- [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md)
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md)
- [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)

## Canonical Vocabulary

FinGrind records one protected book for one **accounting entity**.

That term is canonical across user-facing descriptions, developer theory, and machine-contract
facts:
- one book belongs to one accounting entity
- the selected SQLite path is the durable identity of that accounting entity's book
- account codes, postings, balances, and reports are book-local to that accounting entity

Retired wording:
- `business` is not the canonical owner term for a book
- bare `entity` is too loose on its own when the intended meaning is accounting scope

## Bounded Contexts

### Public Protocol Context

The `contract` module owns the **published language**:
- command ids, help/catalog metadata, machine-contract descriptors, request/response DTOs, error
  vocabulary, and public workflow schemas
- public write DTOs such as `DeclareAccountCommand`, `PostEntryCommand`, and `LedgerPlan`
- public read/report DTOs such as `DeclaredAccount`, `PostingFact`, `PostingPage`, and the report
  result families

This context is the external contract. It is not the working bookkeeping model.

### Bookkeeping Context

The bookkeeping context lives across:
- `core/` for accounting primitives and invariants
- `executor/src/main/java/dev/erst/fingrind/executor/bookkeeping/` for the internal bookkeeping
  language used by application services and stores

This context owns:
- account declaration and reactivation semantics
- posting acceptance rules
- committed posting shape used inside execution and storage
- book-local invariants such as duplicate idempotency rejection, reversal admissibility, and
  account activity requirements

The bookkeeping context uses `RegisteredAccount`, `PostingCommand`, `CommittedPosting`,
`PostingLineageModel`, `BookOpeningOutcome`, and `AccountDeclarationOutcome` as its local
language.

### Workflow Context

The workflow context lives in
`executor/src/main/java/dev/erst/fingrind/executor/workflow/`.

This context owns orchestration semantics for AI-authored or automation-authored plans:
- ordered steps
- assertions
- workflow-local plan ids and step ids
- translation from public `LedgerPlan` payloads into executable internal workflow steps

Assertions and step ordering belong here, not inside the bookkeeping model.

### Host And Adapter Contexts

`cli/` and `sqlite/` are host/adaptor contexts:
- `cli/` accepts or renders the published language
- `sqlite/` persists and retrieves bookkeeping state and projects read/report results

They do not redefine bookkeeping rules or public workflow vocabulary.

## Context Map

```text
CLI / bundle / container
        |
        v
Public protocol context (`contract`)
        |  published-language translators
        |  / anti-corruption layer
        v
Bookkeeping context (`core` + `executor.bookkeeping`)
        ^
        |
Workflow context (`executor.workflow`)
        |
        v
SQLite adapter (`sqlite`)
```

Interpretation:
- the CLI speaks the public protocol context
- translator classes are the anti-corruption layer between the published language and the internal
  bookkeeping/workflow contexts
- `execute-plan` enters through the public workflow schema, then runs as internal workflow steps
  that call bookkeeping services and read seams
- SQLite persists bookkeeping state and projects public read models back out through translators

## Ownership Rules

- Public contract DTOs are not the internal working model of bookkeeping or workflow code.
- Bookkeeping invariants must live with bookkeeping aggregates or bookkeeping policies, not inside
  the SQLite adapter.
- Workflow semantics must live with workflow types and services, not inside bookkeeping value
  objects.
- New transport layers or tools must translate at the boundary instead of importing protocol DTOs
  as their local model.

## Cue For Future Refactors

When a change touches business meaning, ask these questions before editing:
- Is this concept part of the public published language, the bookkeeping model, or the workflow
  model?
- Does the change alter one accounting entity's book semantics, or only transport/orchestration?
- Which translator or boundary should absorb the shape change?

If the answer is unclear, the context map above is the first place to repair before adding more
types.
