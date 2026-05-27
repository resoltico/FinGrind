---
afad: "4.0"
version: "0.48.0"
domain: CONTRACT_EXECUTOR_WRITE
updated: "2026-05-27"
route:
  keywords: [fingrind, contract, executor, posting, preflight, commit, posting-rejection, ledger-plan, assertion, journal, uuid-v7]
  questions: ["where are posting and ledger plan types documented in fingrind", "which doc covers PostingApplicationService and LedgerPlanService", "where are posting rejections and plan journals documented"]
---

# Posting And Ledger Plan Reference

This file documents the exported write-side `contract` models, the exported local
bookkeeping/workflow context types that support them, and the exported `executor` services that
own posting preflight, posting commit, and atomic ledger-plan execution.

## `PostingLineage`

`PostingLineage` is the structural direct-versus-reversal lineage carried by commands, drafts, and
committed facts.

```java
public sealed interface PostingLineage
```

- Variants: `Direct`, `Reversal`
- Purpose: keep direct postings and reversal postings structurally distinct

## `BookkeepingEntry` And `BookkeepingEntryKind`

`BookkeepingEntry` is the public bookkeeping write model that makes typed business events the
default write surface, and `BookkeepingEntryKind` is the closed caller-authored vocabulary that
selects those entry families.

```java
public sealed interface BookkeepingEntry
public enum BookkeepingEntryKind
```

- Typed events: `CashRevenue`, `CashExpense`, `EquityContribution`, and `EquityWithdrawal`
- Administrative adjustments: `OpeningBalanceAdjustment`, `CorrectionAdjustment`, and
  `ReversalAdjustment` are the explicit non-business-event routes for adoption balances,
  corrections, and exact reversal postings
- Purpose: keep business-event intent explicit at the public boundary before the executor projects
  it into canonical journal postings

## `PostEntryCommand`

`PostEntryCommand` is the application-layer request to preflight or commit one caller-authored
bookkeeping entry.

```java
public record PostEntryCommand(
    BookkeepingEntry entry,
    AccountingEvidence evidence,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel)
```

- Purpose: carry the write-boundary payload after CLI parsing and request validation, including the
  caller-authored business event plus first-class retained evidence and provenance
- Boundary: typed business events are primary; raw journal lines appear only through the named
  administrative adjustment variants
- Money policy: typed entry amounts arrive as exact positive `MonetaryAmount` values, while the
  administrative adjustment variants carry one already-validated `JournalEntry`

## `PostEntryCommandTranslator`

`PostEntryCommandTranslator` maps one published `PostEntryCommand` into the local posting command
required by the built-in bookkeeping kernel.

```java
public final class PostEntryCommandTranslator
```

- Purpose: keep typed business-event recipes owned at the application boundary instead of leaking
  raw journal construction into the CLI, request loaders, or posting service
- Current scope: `CashRevenue`, `CashExpense`, `EquityContribution`, and `EquityWithdrawal` become
  canonical standard postings, while the three named administrative adjustment variants preserve
  explicit caller-authored `JournalEntry` handling for their bounded purposes

## `PostEntryResult`, `PreflightEntryResult`, And `CommitEntryResult`

These result families separate posting preflight from posting commit while preserving one shared
contract surface.

```java
public sealed interface PostEntryResult
public sealed interface PreflightEntryResult extends PostEntryResult
public sealed interface CommitEntryResult extends PostEntryResult
```

- Purpose: make `preflight-entry` unable to return `Committed` and `post-entry` unable to return
  `PreflightAccepted` at compile time

## `PostingDraft`

`PostingDraft` is the commit-ready posting model that defers `postingId` allocation until the store
accepts the write.

```java
public record PostingDraft(
    JournalEntry journalEntry,
    PostingLineage postingLineage,
    PostingKind postingKind,
    PostingOriginKind postingOriginKind,
    AccountingEvidence evidence,
    CommittedProvenance provenance)
```

- Purpose: separate accepted commit metadata, canonical durable posting kind, and durable id
  assignment from the preserved typed posting-origin fact
- Surface: `materialize(PostingId)` creates the final internal committed posting fact
- Boundary: this is an executor bookkeeping model, not the public published language

## `PostingCommand`, `PostingLineageModel`, And `PostingRequestModel`

These records and sealed interfaces are the local bookkeeping write model used after published
requests cross the translator boundary.

```java
public record PostingCommand(...)
public sealed interface PostingLineageModel
public interface PostingRequestModel
```

- `PostingCommand`: one translated bookkeeping write request with journal entry, lineage,
  accounting evidence, provenance, and source channel
- `PostingLineageModel`: the local direct-versus-reversal lineage family used by bookkeeping
  policies and stores
- `PostingRequestModel`: the shared local shape consumed by bookkeeping validation and materialized
  posting facts, including first-class evidence

## `PostingAcceptancePolicy`, `BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, And `BookkeepingPublishedLanguageTranslator`

`PostingAcceptancePolicy` owns bookkeeping-side admission rules, while
`BookkeepingAdministrationRejection`, `BookkeepingPostingRejection`, and
`BookkeepingPublishedLanguageTranslator` keep local bookkeeping refusals and boundary translation
out of the published protocol surface.

```java
public final class PostingAcceptancePolicy
public sealed interface BookkeepingAdministrationRejection
public sealed interface BookkeepingPostingRejection
public final class BookkeepingPublishedLanguageTranslator
```

- `PostingAcceptancePolicy`: composes the bookkeeping-side admission rules for initialization,
  duplicate idempotency, caller-authored posting family, functional-currency alignment,
  closed-period checks, opening-balance restrictions, account state, result-holding reservation,
  and reversal admissibility against one `PostingValidationStore`
- `BookkeepingAdministrationRejection`: local refusal family for bookkeeping initialization and
  account-declaration rules before translation into public `BookAdministrationRejection`
- `BookkeepingPostingRejection`: local refusal family for posting validation and reversal
  admissibility before translation into public `PostingRejection`
- `BookkeepingPublishedLanguageTranslator`: translates `DeclareAccountCommand`,
  `PostEntryCommand`, `CommittedPosting`, and bookkeeping outcomes at the host boundary instead of
  letting transport DTOs become the local working model

## `LedgerPlanId` And `LedgerStepId`

These value types are the caller-supplied stable identifiers for one plan and one plan step.

```java
public record LedgerPlanId(String value)
public record LedgerStepId(String value)
```

- Purpose: keep plan and step identity typed instead of carrying raw strings through the contract

## `LedgerPlan`

`LedgerPlan` is the canonical published workflow document accepted by `execute-plan`.

```java
public record LedgerPlan(LedgerPlanId planId, List<LedgerStep> steps)
```

- Purpose: bundle one ordered workflow with stable per-step ids
- Validation: rejects blank plan ids, empty step lists, duplicate step ids, and `open-book` outside
  the first step
- Boundary: executor translates this published plan into the internal workflow context before any
  bookkeeping step executes

## `LedgerStep`

`LedgerStep` is the sealed family of executable plan steps.

```java
public sealed interface LedgerStep
```

- Families: `OpenBook`, `DeclareAccount`, `PreflightEntry`, `PostEntry`, `InspectBook`,
  `ListAccounts`, `GetPosting`, `ListPostings`, `AccountBalance`, `Assert`
- Purpose: keep plan execution exhaustively typed instead of routing through maps
- Scope: current ledger plans intentionally stop at book inspection, listings, posting lookup, and
  account-balance queries; office-worker report commands stay on the standalone CLI surface
- Boundary: these step DTOs are published-language workflow inputs, not bookkeeping aggregates

## `LedgerAssertion`

`LedgerAssertion` is the sealed family of first-class postcondition checks for AI-agent workflows.

```java
public sealed interface LedgerAssertion
```

- Families: `AccountDeclared`, `AccountActive`, `PostingExists`, `AccountBalanceEquals`
- Purpose: let one plan assert intended outcomes without inventing CLI-local test commands
- Money policy: `AccountBalanceEquals` uses the same exact-money contract as posted journal lines,
  so workflow assertions cannot express amounts that the posting boundary would reject
- Boundary: executor evaluates assertions inside the internal workflow context after translating
  the published DTO family

## `BookWorkflowPlan`, `BookWorkflowStep`, And `BookWorkflowAssertion`

These types are the local workflow context consumed by `BookWorkflowExecutionService` after the
public plan schema crosses the translator edge.

```java
public record BookWorkflowPlan(String planId, List<BookWorkflowStep> steps)
public sealed interface BookWorkflowStep
public sealed interface BookWorkflowAssertion
```

- `BookWorkflowPlan`: local ordered workflow model with plain-string plan identity
- `BookWorkflowStep`: local executable step family that holds translated bookkeeping write
  commands, translated bookkeeping read criteria, and local assertion steps instead of public
  query DTOs
- `BookWorkflowAssertion`: local assertion family evaluated against bookkeeping/read outcomes and
  local balance criteria

## `BookWorkflowBoundaryPhase`, `BookWorkflowFact`, `BookWorkflowFailure`, `BookWorkflowJournalDescriptor`, `BookWorkflowJournalEntry`, And `BookWorkflowExecutionJournal`

These types are the internal workflow execution record used while
`BookWorkflowExecutionService` is running and deciding whether to commit or roll back.

```java
public enum BookWorkflowBoundaryPhase
public sealed interface BookWorkflowFact
public record BookWorkflowFailure(...)
public sealed interface BookWorkflowJournalDescriptor
public sealed interface BookWorkflowJournalEntry
public enum BookWorkflowExecutionStatus
public record BookWorkflowExecutionJournal(...)
```

- `BookWorkflowBoundaryPhase`: local begin/initialization-check/commit/rollback failure phases
- `BookWorkflowFact`: workflow-owned machine-readable per-step observation family used while the
  internal journal is being built
- `BookWorkflowFailure`: local failure payload with stable code, message, and workflow-owned
  facts
- `BookWorkflowJournalDescriptor`: local executed-step-or-boundary descriptor
- `BookWorkflowJournalEntry`: local per-step journal entry emitted before public projection
- `BookWorkflowExecutionStatus`: local execution outcome derived before public journal/result
  projection
- `BookWorkflowExecutionJournal`: local full-run journal used to derive the final execution status

## `BookWorkflowPublishedLanguageTranslator`

`BookWorkflowPublishedLanguageTranslator` maps the public `LedgerPlan` family onto the local
workflow context and projects local workflow metadata back into the public execution journal and
plan result surface.

```java
public final class BookWorkflowPublishedLanguageTranslator
```

- Purpose: keep plan orchestration semantics and published workflow DTOs decoupled
- Boundary: translates request plans into local steps/assertions at ingress, and translates local
  workflow journals, failures, and `BookWorkflowFact` observations back into `LedgerJournal*`,
  `LedgerFact`, and `LedgerPlanResult` at egress

## `LedgerFact`

`LedgerFact` is the sealed family of typed per-step journal observations.

```java
public sealed interface LedgerFact
```

- Families: `Text`, `Flag`, `Count`, `Money`, `Group`
- Purpose: keep published step observations machine-readable without collapsing everything to
  strings after the local workflow facts have crossed the translator boundary
- Money facts: `LedgerFact.Money` carries the same public `MonetaryAmount` shape used by request,
  template, and response contracts, so agent consumers do not have to infer money semantics from
  free-form text

## `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryPhase`, `LedgerStepStatus`, And `LedgerPlanStatus`

These enums own the stable ledger-plan wire vocabulary.

```java
public enum LedgerStepKind
public enum LedgerJournalKind
public enum LedgerAssertionKind
public enum LedgerBoundaryPhase
public enum LedgerStepStatus
public enum LedgerPlanStatus
```

- Purpose: keep plan/journal tokens compiler-owned and renderer-independent
- Surface: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, And `LedgerPlanResult`

These types carry the durable execution record returned by `execute-plan`.

```java
public sealed interface LedgerJournalStep
public sealed interface LedgerJournalEntry
public record LedgerExecutionJournal(...)
public record LedgerStepFailure(String code, String message, List<LedgerFact> facts)
public sealed interface LedgerPlanResult
```

- Purpose: return one plan-level result plus one per-step journal that agents can inspect safely
- Structure: `LedgerJournalStep` owns the canonical journal kind; assertion entries may attach
  `detailKind`, while unexpected begin, initialization-check, commit, or rollback failures use the
  dedicated `plan-boundary` kind plus a `boundaryPhase`
- Bound: `LedgerPlan` accepts at most 100 steps, which bounds full journal responses
- Boundary: these are published workflow protocol outputs; executor keeps a separate local
  workflow journal model while the plan is actually executing

## `PostingApplicationService`

`PostingApplicationService` owns preflight and commit behavior for posting entries.

```java
public final class PostingApplicationService
```

- Constructor: requires `PostingValidationStore`, `PostingCommitStore`, `PostingIdGenerator`, and `Clock`
- Surface: `preflight(PostEntryCommand)` and `commit(PostEntryCommand)`
- Boundary: the service owns application-boundary entry semantics first, then translates the
  published `PostEntryCommand` into one local `PostingCommand` before delegating commit semantics
  to `executor.bookkeeping.posting.BookkeepingPostingService`

## `BookkeepingPostingService`

`BookkeepingPostingService` owns local bookkeeping preflight and commit behavior before any public
published-language projection.

```java
public final class BookkeepingPostingService
```

- Constructor: requires `PostingValidationStore`, `PostingCommitStore`, `PostingIdGenerator`,
  `Clock`, and one explicit `KernelAccountingRules`
- Surface: `preflight(PostingCommand)` and `commit(PostingCommand)`
- Boundary: this service stays inside the bookkeeping context, applies the built-in bookkeeping
  kernel rules, and returns only local admission and commit outcomes

## `BookWorkflowExecutionService`

`BookWorkflowExecutionService` owns atomic execution of the local workflow model.

```java
public final class BookWorkflowExecutionService
```

- Constructor: requires `LedgerPlanTransaction`, `BookAdministrationStore`, `BookkeepingReadStore`, `PostingValidationStore`, `PostingCommitStore`, `PostingIdGenerator`, and `Clock`
- Surface: `execute(BookWorkflowPlan)`
- Policy: runs the whole local plan inside one durable transaction and rolls back on the first
  rejected step or failed assertion
- Boundary: this service stays inside the workflow context and returns one local
  `BookWorkflowExecutionResult`

## `LedgerPlanService`

`LedgerPlanService` is the published-language adapter for `execute-plan`.

```java
public final class LedgerPlanService
```

- Constructor: requires `LedgerPlanTransaction`, `BookAdministrationStore`, `BookkeepingReadStore`, `PostingValidationStore`, `PostingCommitStore`, `PostingIdGenerator`, and `Clock`
- Boundary: the service translates the public `LedgerPlan` into the local workflow model, delegates
  execution to `BookWorkflowExecutionService`, then projects the local execution result back into
  the public `LedgerPlanResult` surface

## `PostingIdGenerator`

`PostingIdGenerator` supplies the next posting identity during commit.

```java
public interface PostingIdGenerator {
  PostingId nextPostingId();
}
```

- Purpose: keep posting-id generation explicit and injectable

## `UuidV7PostingIdGenerator`

`UuidV7PostingIdGenerator` is FinGrind's project-owned production posting-id generator.

```java
public final class UuidV7PostingIdGenerator implements PostingIdGenerator
```

- Purpose: generate time-ordered UUID v7 posting ids without an external dependency

## `PostingRejection`

`PostingRejection` is the closed family of deterministic write-side refusals.

```java
public sealed interface PostingRejection
```

- Variants: `BookNotInitialized`, `AccountStateViolations`, `DuplicateIdempotencyKey`,
  `ReversalTargetNotFound`, `ReversalAlreadyExists`, `ReversalDoesNotNegateTarget`
- Purpose: keep validly parsed but inadmissible postings machine-distinguishable
