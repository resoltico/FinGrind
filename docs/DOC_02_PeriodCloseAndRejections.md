---
afad: "5.0.1"
version: "0.64.0"
domain: CONTRACT_PERIOD_CLOSE
updated: "2026-09-01"
scope:
  paths: ["contract/src/main/java/dev/erst/fingrind/contract/bookkeeping", "executor/src/main/java/dev/erst/fingrind/executor/bookkeeping"]
  symbols: ["InterimResultSweepDraft", "InterimResultSweepOutcome", "InterimResultSweepPlanner", "InterimResultSweepService", "FiscalYearCloseDraft", "FiscalYearCloseOutcome", "FiscalYearClosePlanner", "FiscalYearCloseService", "BookAdministrationRejection", "BookQueryRejection"]
route:
  keywords: [fingrind, period-close, interim-result-sweep, fiscal-year-close, close-rejection, report-rejection]
  questions: ["where is interim result sweep documented", "where is fiscal year close documented", "where are close and report rejections documented"]
---

# Period Close And Rejection Reference

This file documents executor-owned period-close planning and the deterministic public
administration and read-side rejection vocabulary.

## `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `RecordedInterimResultSweep`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService`

These executor-owned local bookkeeping types own interim-result-sweep generation and durable
close semantics before the public administration surface is projected.

```java
public record InterimResultSweepDraft(...)
public sealed interface InterimResultSweepOutcome
public record RecordedInterimResultSweep(...)
public sealed interface InterimResultTargetSelection
public final class AcceptedInterimResultTargetSelection
public final class RejectedInterimResultTargetSelection
public record InterimResultSweepPlan(...)
public final class InterimResultSweepPlanner {
    public static InterimResultSweepPlanner forBookIdentity(BookIdentity bookIdentity)
}
public final class InterimResultSweepService
```

- `InterimResultSweepDraft`: store-ready interim-result-sweep payload containing the reporting
  period, the sweep time, and every generated posting draft
- `InterimResultSweepOutcome`: closed family of accepted-versus-rejected local
  interim-result-sweep outcomes
- `RecordedInterimResultSweep`: durably stored interim-result sweep fact carrying `sweepOrder`,
  the transferred totals, and every generated sweep posting id
- `InterimResultTargetSelection`: closed result for the policy-owned result-holding account lookup
- `AcceptedInterimResultTargetSelection`: accepted result-holding selection carrying the chosen account
- `RejectedInterimResultTargetSelection`: rejected result-holding selection carrying the deterministic
  administration rejection plus candidate account codes
- `InterimResultSweepPlan`: generated interim-result-sweep posting drafts plus the transferred
  totals that the published sweep result projects afterward
- `InterimResultSweepPlanner`: bookkeeping-domain planner that selects the policy-owned result-holding
  account, derives the inclusive reporting period from the selected through date plus the immutable
  `BookIdentity` book-start date or the prior transferred-through horizon, validates close-horizon rules before
  durable mutation, and generates the
  `PostingKind.INTERIM_RESULT_SWEEP` drafts plus published transferred totals. Construction is
  bound to the initialized `BookIdentity`; callers cannot supply an internal policy-pack type.
- `InterimResultSweepService`: application service that coordinates lifecycle inspection, account
  catalog/store access, planner output, and durable interim-result-sweep persistence instead of
  owning the close recipe itself

## `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService`

These executor-owned local bookkeeping types own the fiscal-year close target-selection and
durable year-end close flow before the public administration surface is projected.

```java
public sealed interface CloseTargetSelection
public final class AcceptedCloseTargetSelection
public final class RejectedCloseTargetSelection
public final class CloseTargetAccountSelector
public record FiscalYearCloseDraft(...)
public record ClosedFiscalYearRecord(...)
public sealed interface FiscalYearCloseOutcome
public final class FiscalYearClosePlanner {
    public static FiscalYearClosePlanner forBookIdentity(BookIdentity bookIdentity)
}
public final class FiscalYearCloseService
```

- `CloseTargetSelection`: closed result for resolving one required close-target classification
- `AcceptedCloseTargetSelection`: successful selection of the only active declared close-target account
- `RejectedCloseTargetSelection`: deterministic missing-versus-ambiguous close-target refusal plus
  the relevant candidate account codes
- `CloseTargetAccountSelector`: canonical classifier-based selector for close-owned equity targets
- `FiscalYearCloseDraft`: store-ready year-end close payload containing every generated durable
  fiscal-year-close posting
- `ClosedFiscalYearRecord`: durably stored local close fact carrying close order, selected close
  targets, and generated posting ids
- `FiscalYearCloseOutcome`: closed family of accepted-versus-rejected fiscal-year close outcomes
- `FiscalYearClosePlanner`: bookkeeping-domain planner for fiscal-year boundary derivation from
  the initialized book identity plus selected fiscal-year label, close-target selection, and
  generated year-end postings. Construction is bound to the initialized `BookIdentity`; callers
  cannot supply an internal policy-pack type.
- `FiscalYearCloseService`: application service that coordinates lifecycle inspection, planner
  output, and durable fiscal-year close persistence

## `BookAdministrationRejection`

`BookAdministrationRejection` is the closed family of deterministic lifecycle, account-registry,
and fiscal-period refusals.

```java
public sealed interface BookAdministrationRejection
```

- Variants: `BookAlreadyInitialized`, `BookNotInitialized`, `BookContainsSchema`,
  `AccountTypeConflict`, `AccountTaxonomyConflict`, `ParentAccountMissing`,
  `ParentAccountInactive`, `ParentAccountTypeConflict`, `ParentAccountNotHeader`,
  `ParentAccountTaxonomyConflict`, `AccountHierarchyCycle`,
  `CloseTargetAccountCandidateMissing`, `CloseTargetAccountCandidateAmbiguous`,
  `InterimResultSweepMustStartAt`, `InterimResultSweepFutureDate`,
  `InterimResultSweepCrossesFiscalYearBoundary`, `FiscalYearCloseMustStartAt`,
  `FiscalYearCloseMustEndAt`, `FiscalYearClosePrecedesTransferredThroughHorizon`,
  `FiscalYearCloseFutureDate`, `FiscalYearCloseRequiresGeneratedPostings` (a top-level variant)
- Fiscal-year close constraint: a close is rejected when its planner produces no durable close
  postings. The refusal persists no close record, audit event, or attestation operation.

## `BookQueryRejection`

`BookQueryRejection` is the closed family of deterministic query/report refusals.

```java
public sealed interface BookQueryRejection
```

- Variants: `BookNotInitialized`, `UnknownAccount`, `PostingNotFound`

The maintenance rejection and path-presentation contract is documented in
[DOC_02_BookMaintenanceContracts.md](./DOC_02_BookMaintenanceContracts.md).
