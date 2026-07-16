---
afad: "4.0"
version: "0.60.0"
domain: CONTRACT_EXECUTOR_WRITE
updated: "2026-07-15"
route:
  keywords: [fingrind, contract, executor, posting, preflight, commit, posting-rejection, ledger-plan, assertion, journal, uuid-v7, tax-selection, applied-tax, fixed-assets, financing, realized-foreign-exchange]
  questions: ["where are posting and ledger plan types documented in fingrind", "which doc covers PostingApplicationService and LedgerPlanService", "where are posting rejections and plan journals documented", "where is tax selection versus applied tax documented", "where are fixed asset financing and realized foreign exchange posting models documented"]
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

## `BookkeepingEntry`, `TypedBookkeepingEntry`, `BookkeepingEntrySurface`, And `BookkeepingEntryKind`

`BookkeepingEntry` is the public bookkeeping write model that makes typed business events the
primary caller-authored surface while preserving explicit direct-journal, opening-position, and
reversal forms. `TypedBookkeepingEntry` is the closed event family whose journal derives from
caller-authored business facts. `BookkeepingEntrySurface` is the shared published view of every
caller-authored variant, and `BookkeepingEntryKind` is the closed caller-authored vocabulary that
selects those entry families.

```java
public sealed interface BookkeepingEntry
public sealed interface TypedBookkeepingEntry
public interface BookkeepingEntrySurface
public enum BookkeepingEntryKind
```

- Direct journal: `DirectJournal` carries one caller-authored `JournalEntry` for the raw escape
  hatch
- Typed business events: standard, inventory, accrual cut-off, fixed-asset, financing,
  realized-foreign-exchange, and Latvian payroll sealed families preserve caller-authored event
  facts that FinGrind translates into one canonical journal entry
- Distinct forms: `OpeningPosition` establishes a book's opening balances, while `Reversal`
  identifies and negates an existing posting; neither represents a new economic event
- Shared view: `BookkeepingEntrySurface` owns the derived accessors that every caller-authored
  variant publishes consistently, including `entryKind()`, `journalEntry()`, `postingKind()`,
  `postingOriginKind()`, `postingLineage()`, `lines()`, and optional foreign-exchange facts
- Purpose: keep the business-event-first write language explicit without introducing a second write
  kernel

## `StandardBookkeepingEntryVariants`

`StandardBookkeepingEntryVariants` is the sealed typed-entry family for ordinary sales,
purchases, expenses, receipts, payments, and owner transfers outside the inventory and accrual
cut-off bounded contexts.

```java
public sealed interface StandardBookkeepingEntryVariants extends TypedBookkeepingEntry
```

- Purpose: let admission, account resolution, and request-fingerprint code dispatch ordinary
  business events without matching inventory or accrual-cut-off variants
- Boundary: inventory, accrual-cut-off, fixed-asset, financing, realized-foreign-exchange, and
  payroll event families remain separately sealed because they own additional state, lifecycle,
  and accounting invariants

## `FixedAssetBookkeepingEntryVariants`, `FinancingBookkeepingEntryVariants`, And `RealizedForeignExchangeBookkeepingEntryVariants`

These three separately sealed typed-entry families own lifecycle facts that cannot be represented
truthfully by a generic journal recipe.

```java
public sealed interface FixedAssetBookkeepingEntryVariants extends TypedBookkeepingEntry
public sealed interface FinancingBookkeepingEntryVariants extends TypedBookkeepingEntry
public sealed interface RealizedForeignExchangeBookkeepingEntryVariants extends TypedBookkeepingEntry
```

- Fixed assets: capitalization names the asset and its straight-line schedule; depreciation and
  disposal identify the retained asset. The executor resolves depreciation and disposal gain or
  loss from durable lifecycle facts, while `fixed-asset-register` exposes the reconciliation view.
  The [Fixed Assets ADR](./ADR_FIXED_ASSETS.md) defines the boundary and links the IAS 16 primary
  source; it is not an IAS 16 compliance claim.
- Financing: borrowing creates the arrangement, and later principal repayment, interest accrual,
  and interest payment identify it. The executor preserves principal and unpaid-interest bounds,
  while `financing-register` reconciles the arrangement. The
  [Financing ADR](./ADR_FINANCING.md) defines the boundary and links the IFRS 9 primary source; it
  is not an IFRS 9 compliance claim.
- Realized foreign exchange: foreign-currency receivable origination and settlement retain the
  caller's quote evidence while the executor derives the realized result. The
  `realized-foreign-exchange-register` reconciles the retained carrying amount and settlement.
  The [Realized Foreign Exchange ADR](./ADR_REALIZED_FOREIGN_EXCHANGE.md) defines the boundary and
  links IAS 21 and the ECB reference-rate source; it does not select a rate source or assert IAS
  21 compliance.

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
  caller-authored business event or raw direct journal plus first-class retained evidence and
  provenance
- Boundary: typed business-event commands are the normal public write language, while direct
  balanced journal lines remain the explicit fallback for non-inventory cases that do not fit
  those event families; raw direct journals reject every line resolving to the `INVENTORY` role
  because they cannot carry exact inventory quantity truth
- Money policy: public money amounts arrive as exact positive `MonetaryAmount` values, while the
  direct journal and reversal variants carry one already-validated `JournalEntry`

## `TaxSelection` And `AppliedTax`

`TaxSelection` is the caller-authored request-side selector for tax-aware posting families, while
`AppliedTax` is the resolved durable fact retained after FinGrind validates and translates that
selection.

```java
public record TaxSelection(TaxRegistrationId taxRegistrationId, TaxCode taxCode)
public record AppliedTax(...)
```

- `TaxSelection`: lets one sale, purchase, capitalization, or expense request point at one declared
  tax registration and one declared tax code; there is no neutral free-form tax payload outside
  those typed selectors
- `AppliedTax`: preserves the resolved registration, code name, rate, inclusion mode, application
  kind, taxable amount, tax amount, gross amount, and optional tax account code as one committed
  posting fact
- Boundary: request-side selectors stay on the published write DTOs, while `AppliedTax` travels on
  committed posting and reporting surfaces after translation and validation

## `SettlementAdjunct`

`SettlementAdjunct` is the optional request-side settlement fact carried by receipt and payment
entries.

```java
public record SettlementAdjunct(AccountCode accountCode, MonetaryAmount amount)
```

- Purpose: keep settlement discounts, fees, and write-offs as one explicit owned nested fact
  instead of burying them inside ad hoc extra lines or free-form notes
- Validation: requires one positive amount and one declared adjunct account code
- Boundary: this request-side fact is distinct from the core classifier's derived
  `AccountRole.SETTLEMENT_ADJUNCT`

## `InventoryRelief`

`InventoryRelief` is the optional request-side goods-relief fact carried only by trading-book sale
entries.

```java
public record InventoryRelief(
    AccountCode inventoryAccountCode,
    AccountCode costOfSalesAccountCode,
    QuantityText quantity)
```

- Purpose: keep inventory depletion and cost-of-sales recognition on the typed sale path instead
  of forcing goods-trading books back to raw journals
- Validation: trading books require it on sale requests, non-trading books reject it, and the
  quantity must resolve through the selected inventory account's unit of measure before posting
  translation derives cost of sales
- Boundary: this request-side fact remains distinct from the translated journal lines that debit
  cost-of-sales and credit inventory after posting translation

## `QuantityText`, `ResolvedInventoryAcquisition`, `ResolvedInventoryCosting`, And `ResolvedInventoryDisposal`

These types keep caller-authored inventory quantity separate from executor-owned resolved costing.

```java
public record QuantityText(String value)
public record ResolvedInventoryAcquisition(
    Quantity quantityAcquired, MonetaryAmount preTaxCost, MonetaryAmount carryingCost)
public record ResolvedInventoryCosting(
    Money costOfSales,
    Quantity quantityRelieved,
    Money roundedMovingAverageUnitCostProjection)
public record ResolvedInventoryDisposal(
    Money carryingCost,
    Quantity quantityDisposed,
    Money roundedMovingAverageUnitCostProjection)
```

- `QuantityText`: carries one canonical non-negative plain-decimal quantity string before the
  selected inventory account's `UnitOfMeasure` resolves scale and exact integer units
- `ResolvedInventoryAcquisition`: records the exact acquired quantity, pre-tax acquisition cost,
  and full carrying-cost delta that the executor resolved for one purchase request before durable
  posting
- `ResolvedInventoryCosting`: records the exact relieved quantity, the derived cost of sales, and
  the rounded read-time moving-average unit-cost projection that explains one sale-side inventory
  relief without becoming authoritative cost truth
- `ResolvedInventoryDisposal`: records the exact relieved quantity and carrying cost for one
  inventory decrease, together with a rounded read-time projection that remains non-authoritative
- Boundary: caller-authored request facts stay on `QuantityText`, while exact acquisition and
  disposal values become executor-owned resolution facts only after admissibility and costing
  policy run, and `journalEntry()` stays one caller-derivable skeleton until executor-owned tax,
  costing, and reversal resolution complete the posted journal

## `InventoryMovementPrecedesAccountHorizon`, `InventoryQuantityBelowZero`, And `InventoryWriteDownExceedsCarryingCost`

These published account-state detail types name the deterministic inventory-state refusals that can
appear inside `PostingRejection.AccountStateViolations`.

```java
public record InventoryMovementPrecedesAccountHorizon(...)
public record InventoryQuantityBelowZero(...)
public record InventoryWriteDownExceedsCarryingCost(...)
```

- `InventoryMovementPrecedesAccountHorizon`: the request tried to append one inventory movement
  before the inventory account's existing effective-date horizon
- `InventoryQuantityBelowZero`: the request tried to relieve more exact quantity than the selected
  inventory account has on hand
- `InventoryWriteDownExceedsCarryingCost`: the request tried to reduce carrying cost below zero on
  one inventory account
- Boundary: these are published contract facts. The executor owns the first-defense admission rule,
  then translates local violation models into these public detail records

## `PostEntryCommandTranslator`

`PostEntryCommandTranslator` maps one published `PostEntryCommand` into the local posting command
required by the built-in bookkeeping kernel.

```java
public final class PostEntryCommandTranslator
```

- Purpose: keep business-event-to-posting translation owned at the application boundary instead of
  leaking it into the CLI, request loaders, or posting service
- Current scope: direct journals pass through unchanged, while standard, inventory,
  accrual-cut-off, fixed-asset, financing, realized-foreign-exchange, payroll, opening-position,
  and reversal families translate into the exact canonical journal and durable posting origin they
  claim

## `ResolvedJournal`

`ResolvedJournal` is the success-side semantic payload that records the fully expanded journal and
its classification.

```java
public record ResolvedJournal(
    JournalEntry expandedLines,
    @Nullable AppliedTax appliedTax,
    @Nullable ForeignExchangeDetails foreignExchangeDetails,
    ClassificationResult classification)
```

- Purpose: keep success-side preflight and commit feedback truthful about the exact expanded
  journal, resolved tax, retained foreign-exchange facts, and semantic classification that
  FinGrind will validate and commit
- Boundary: raw-admission, evidence validation, and typed-verb self-consistency consume this full
  object instead of recomputing reduced tuples from individual success fields

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
- Success payload: both `PreflightAccepted` and `Committed` now carry one `resolvedJournal` so the
  public success surface can publish the exact expanded journal and its semantic classification

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

## `PostingCommand`, `PostingLineageModel`, `PostingOriginatingEntryValidator`, And `PostingRequestModel`

These records and sealed interfaces are the local bookkeeping write model used after published
requests cross the translator boundary.

```java
public record PostingCommand(...)
public sealed interface PostingLineageModel
public final class PostingOriginatingEntryValidator
public interface PostingRequestModel
```

- `PostingCommand`: one translated bookkeeping write request with journal entry, lineage,
  accounting evidence, provenance, and source channel
- `PostingLineageModel`: the local direct-versus-reversal lineage family used by bookkeeping
  policies and stores
- `PostingOriginatingEntryValidator`: the executor-side guard that keeps retained caller-authored
  entry facts aligned with the posting kind, origin kind, journal entry, and lineage they
  annotate
- `PostingRequestModel`: the shared local shape consumed by bookkeeping validation and materialized
  posting facts, including first-class evidence

## `PostEntryResolutionSupport`, `PostEntryResolutionSupport.ResolutionOutcome`, `InventoryPostingResolution`, `AcceptedPosting`, `PostingAccountStatePolicy`, `InventoryAdmissionPolicy`, And `InventoryAdmissionPolicy.InventoryAdmissionFailure`

These executor-owned types resolve typed entry requests into one accepted posting shape and its
inventory side effects before commit.

```java
public final class PostEntryResolutionSupport
public record PostEntryResolutionSupport.ResolutionOutcome(...)
public record InventoryPostingResolution(...)
public record AcceptedPosting(...)
public final class PostingAccountStatePolicy
public final class InventoryAdmissionPolicy
public static final class InventoryAdmissionPolicy.InventoryAdmissionFailure
```

- `PostEntryResolutionSupport`: runs tax resolution, reversal resolution, executor-owned inventory
  costing, and lifecycle-context resolution plus admission in one owner-controlled sequence so
  callers do not compose partial semantic pipelines themselves
- `PostEntryResolutionSupport.ResolutionOutcome`: returns both the resolved entry plus any
  deterministic rejection reached during that resolution pass
- `InventoryPostingResolution`: packages one resolved entry together with the exact inventory
  movements and resulting per-account on-hand states that the executor derived
- `AcceptedPosting`: is the fully admissible posting shape ready for durable commit, retaining both
  the caller-authored entry and the resolved originating entry when they exist
- `PostingAccountStatePolicy`: owns declared-account, active-account, and postable-account checks
  before deeper semantic policies run
- `InventoryAdmissionPolicy`: is the first defense for inventory horizon, quantity-floor,
  carrying-cost-floor, inventory unit-of-measure compatibility, and exact acquisition-cost
  admission checks before SQLite trigger backstops
- `InventoryAdmissionPolicy.InventoryAdmissionFailure`: wraps one deterministic
  `BookkeepingPostingRejection` when inventory admission rejects prior to commit

## `InventoryEntrySemanticsViolations`

`InventoryEntrySemanticsViolations` is the executor-local construction boundary for inventory
event rejections whose published codes, messages, and repair guidance are owned by the contract.

```java
public final class InventoryEntrySemanticsViolations
```

- Purpose: translates canonical inventory rejection semantics into the local bookkeeping rejection
  shape used by executor policies
- Scope: trading-template eligibility, sale relief requirements, unit-of-measure compatibility,
  exact acquisition cost, foreign-exchange functional-cost alignment, raw-journal protection, and
  opening-position quantity rules
- Boundary: it never invents public error language; the canonical contract rejection semantics
  remain the single source of that language

## `PostingAcceptancePolicy`, `PostingAcceptancePolicy.Decision`, `BookkeepingAdministrationRejection`, `BookkeepingAdministrationRejectionPublishedMapper`, `BookkeepingPostingRejection`, `BookkeepingRequestPublishedLanguageTranslator`, And `BookkeepingPublishedLanguageTranslator`

`PostingAcceptancePolicy` owns bookkeeping-side admission rules, while
`BookkeepingAdministrationRejection`, `BookkeepingAdministrationRejectionPublishedMapper`,
`BookkeepingPostingRejection`, `BookkeepingRequestPublishedLanguageTranslator`, and
`BookkeepingPublishedLanguageTranslator` keep local bookkeeping refusals and boundary translation
out of the published protocol surface.

```java
public final class PostingAcceptancePolicy
public sealed interface PostingAcceptancePolicy.Decision
public sealed interface BookkeepingAdministrationRejection
public final class BookkeepingAdministrationRejectionPublishedMapper
public sealed interface BookkeepingPostingRejection
public final class BookkeepingRequestPublishedLanguageTranslator
public final class BookkeepingPublishedLanguageTranslator
```

- `PostingAcceptancePolicy`: composes the bookkeeping-side admission rules for initialization,
  duplicate idempotency, caller-authored posting family, functional-currency alignment,
  closed-period checks, opening-position admission rules, account state,
  close-reserved classification checks,
  and reversal admissibility against one `PostingValidationStore`
- `PostingAcceptancePolicy.Decision`: distinguishes fresh acceptance, idempotent replay, and
  deterministic rejection while carrying the computed `RequestFingerprint` only once
- `BookkeepingAdministrationRejection`: local refusal family for bookkeeping initialization and
  account-declaration rules before translation into public `BookAdministrationRejection`
- `BookkeepingAdministrationRejectionPublishedMapper`: maps bookkeeping-administration refusals
  into the public administration rejection surface without reintroducing transport concerns into
  bookkeeping policy code
- `BookkeepingPostingRejection`: local refusal family for posting validation and reversal
  admissibility before translation into public `PostingRejection`
- `BookkeepingRequestPublishedLanguageTranslator`: translates `OpenBookCommand`,
  `DeclareAccountCommand`, and explicit close commands into the local working model before any
  bookkeeping rule evaluates them
- `BookkeepingPublishedLanguageTranslator`: translates committed postings, bookkeeping outcomes,
  and local rejection families back into the published protocol surface instead of letting
  transport DTOs become the local working model

## `BookkeepingAccountSemanticsViolations`, `BookkeepingEvidenceSemanticsViolations`, `BookkeepingEntryModeSemanticsViolations`, And `BookkeepingTaxSemanticsViolations`

These four bookkeeping-owned namespaces split entry-semantics violations by account doctrine,
evidence truthfulness, direct-journal and basis admission, and tax-selection semantics so each
validator emits one local refusal from the owner of that meaning.

```java
public final class BookkeepingAccountSemanticsViolations
public final class BookkeepingEvidenceSemanticsViolations
public final class BookkeepingEntryModeSemanticsViolations
public final class BookkeepingTaxSemanticsViolations
```

- Purpose: keep request-truthful violation prose and stable codes compiler-owned instead of
  scattering string assembly across posting validators while still making semantic ownership
  explicit at the call site
- Surface:
  `BookkeepingAccountSemanticsViolations` owns account-type, cash-classification, financial-position,
  distinct-role, and resolved-role refusals plus referenced-account set assembly;
  `BookkeepingEvidenceSemanticsViolations` owns source-document acceptance and evidence-class
  conflict refusals;
  `BookkeepingEntryModeSemanticsViolations` owns economic-null, verb-versus-basis, raw-journal
  shadowing, bundled-event, cash-line, and opening-window refusals;
  `BookkeepingTaxSemanticsViolations` owns unknown registration, unknown code, and
  application-kind refusals
- Boundary: these helpers stay inside local bookkeeping validation and emit local
  `BookkeepingPostingRejection.EntrySemanticsViolation` records before the published
  `PostingRejection` surface is assembled

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
- Validation: rejects blank plan ids, empty step lists, duplicate step ids, and `ensure-book`
  outside the first step
- Boundary: executor translates this published plan into the internal workflow context before any
  bookkeeping step executes

## `LedgerStep`

`LedgerStep` is the sealed family of executable plan steps.

```java
public sealed interface LedgerStep
```

- Families: `EnsureBook`, `DeclareAccount`, `DeclareTaxRegistration`, `PreflightEntry`,
  `PostEntry`, `InspectBook`, `ListAccounts`, `GetPosting`, `ListPostings`, `AccountBalance`,
  `Assert`
- Purpose: keep plan execution exhaustively typed instead of routing through maps
- Tax setup: `DeclareTaxRegistration` keeps account declaration and tax registration as separate
  ordered plan effects, so a plan can set up both prerequisite accounts and the registration in
  one atomic transaction without making tax registration own account creation
- Surface: committed posting steps publish the typed `record-*` workflow kinds when the nested
  `PostEntryCommand` carries one business entry, while raw direct-journal fallback stays on the
  `post-entry` kind
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

## `BookWorkflowBoundaryCheckpoint`, `BookWorkflowFact`, `BookWorkflowFailure`, `BookWorkflowJournalDescriptor`, `BookWorkflowJournalEntry`, And `BookWorkflowExecutionJournal`

These types are the internal workflow execution record used while
`BookWorkflowExecutionService` is running and deciding whether to commit or roll back.

```java
public enum BookWorkflowBoundaryCheckpoint
public sealed interface BookWorkflowFact
public record BookWorkflowFailure(...)
public sealed interface BookWorkflowJournalDescriptor
public sealed interface BookWorkflowJournalEntry
public enum BookWorkflowExecutionStatus
public record BookWorkflowExecutionJournal(...)
```

- `BookWorkflowBoundaryCheckpoint`: local begin/initialization-check/commit/rollback failure checkpoints
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
workflow context and delegates local journal projection to the narrower
`BookWorkflowPublishedJournalTranslator`.

```java
public final class BookWorkflowPublishedLanguageTranslator
```

- Purpose: keep plan orchestration semantics and published workflow DTOs decoupled
- Boundary: translates request plans into local steps/assertions at ingress, while leaving journal
  projection to the dedicated workflow-journal translator

## `BookWorkflowPublishedJournalTranslator`

`BookWorkflowPublishedJournalTranslator` projects local workflow journal descriptors, entries,
failures, and facts into the published execution-journal and plan-result surface.

```java
final class BookWorkflowPublishedJournalTranslator
```

- Purpose: keep journal/boundary/assertion projection independent from plan-ingress mapping
- Boundary: translates local workflow journals, failures, and `BookWorkflowFact` observations
  into `LedgerJournal*`, `LedgerFact`, and assertion/boundary surface families at egress

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

## `LedgerFactKind`

`LedgerFactKind` owns the stable wire vocabulary for ledger-fact discriminators.

```java
public enum LedgerFactKind implements WireValue
```

- Members: `TEXT`, `FLAG`, `COUNT`, `MONEY`, `GROUP`
- Purpose: keep fact-kind wire tokens canonical across contract DTOs, CLI JSON payloads, and any
  future machine readers instead of retyping raw discriminator strings

## `LedgerStepKind`, `LedgerJournalKind`, `LedgerAssertionKind`, `LedgerBoundaryCheckpoint`, `LedgerStepStatus`, And `LedgerPlanStatus`

These types own the stable ledger-plan wire vocabulary.

```java
public enum LedgerStepKind
public sealed interface LedgerJournalKind
public enum LedgerAssertionKind
public enum LedgerBoundaryCheckpoint
public enum LedgerStepStatus
public enum LedgerPlanStatus
```

- Purpose: keep plan/journal tokens compiler-owned and renderer-independent
- Ownership: every standard `LedgerJournalKind` is its canonical `LedgerStepKind`; only the
  journal-only `plan-boundary` marker is represented by `LedgerJournalKind.BoundaryKind`
- Surface: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, `LedgerPlanFailure`, And `LedgerPlanResult`

These types carry the durable execution record returned by `execute-plan`.

```java
public sealed interface LedgerJournalStep
public sealed interface LedgerJournalEntry
public record LedgerExecutionJournal(...)
public record LedgerStepFailure(String code, String message, List<LedgerFact> facts)
public enum LedgerPlanFailure
public sealed interface LedgerPlanResult
```

- Purpose: return one plan-level result plus one per-step journal that agents can inspect safely
- `LedgerPlanFailure` owns plan-outcome codes and their explicit status/category descriptors so
  unexpected plan failures remain published contract vocabulary rather than renderer-local strings
- Structure: `LedgerJournalStep` owns the canonical journal kind; assertion entries may attach
  `detailKind`, committed business-entry journal steps publish the corresponding `record-*` kind,
  raw direct-journal fallback stays `post-entry`, tax-registration steps publish the declared
  registration snapshot as typed data, and unexpected begin, initialization-check, commit, or
  rollback failures use the dedicated `plan-boundary` kind plus a
  `boundaryCheckpoint`
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

## `PostingRejection`, `PostingInventoryRejectionSemantics`, And `PostingRejectionSemantics`

`PostingRejection` is the closed family of deterministic write-side refusals,
`PostingInventoryRejectionSemantics` owns the inventory-specific entry-semantics vocabulary, and
`PostingRejectionSemantics` owns the remaining canonical entry-semantics builders derived from
business facts.

```java
public sealed interface PostingRejection
public final class PostingInventoryRejectionSemantics
public final class PostingRejectionSemantics
```

- Variants: `BookNotInitialized`, `AccountStateViolations`, `EntrySemanticsViolations`,
  `IdempotencyKeyConflict`, `BookFunctionalCurrencyMismatch`,
  `SweptInterimResultViolation`, `OpeningPositionWindowClosed`,
  `OpeningPositionTouchesNominalAccount`, `ReservedResultClassification`,
  `ReversalTargetNotFound`, `ReversalTargetIsReversal`, `ReversalAlreadyExists`,
  `ReversalDoesNotNegateTarget`
- `AccountStateViolationDetail`: stable top-level detail payload for one aggregated
  `AccountStateViolations` issue, kept separate from the closed rejection family so the family
  stays focused on refusal variants while adapters still receive one typed detail shape
- `InventoryMovementPrecedesAccountHorizon`: published account-state subtype for one inventory
  movement that attempts to backdate before the selected account's existing movement horizon
- `InventoryQuantityBelowZero`: published account-state subtype for one inventory decrease that
  would drive exact quantity on hand below zero, including the attempted decrease and resulting
  shortfall quantity
- `InventoryWriteDownExceedsCarryingCost`: published account-state subtype for one inventory
  carrying-cost decrease that would drive the selected pool below zero
- `PostingRejection`: keep validly parsed but inadmissible postings machine-distinguishable
- `PostingInventoryRejectionSemantics`: build canonical inventory-only admission violations for
  trading-template requirements, opening inventory, and raw-journal inventory movement attempts
- `ReservedResultClassification`: names both the blocked account code and the close-reserved
  classification, covering both `RESULT_HOLDING` and `RETAINED_ACCUMULATED`
- `ReversalTargetIsReversal`: makes reversal lineage terminal, so restoring business effect after
  one reversal requires one fresh operational entry instead of a reversal-of-reversal redo
- `PostingRejectionSemantics`: build canonical non-inventory account-type, classification,
  evidence, and economic-nullity violations plus the referenced-account set used to evaluate them
