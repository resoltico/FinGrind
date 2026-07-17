---
afad: "4.0"
version: "0.61.0"
domain: CORE
updated: "2026-07-16"
route:
  keywords: [fingrind, core, account-code, account-name, accounting-basis, account-taxonomy, cash-flow-asset-classification, book-doctrine, currency-unit, quantity, unit-of-measure, inventory-costing, weighted-average, idempotency, temporal-text, fiscal-year-start, reachability]
  questions: ["what core value types does fingrind expose", "where do the core accounting invariants live", "how does account doctrine work in fingrind", "what account and identity primitives are in the fingrind core module", "where are quantity and weighted-average inventory costing primitives documented"]
---

# Core API Reference

This file documents the exported `core` module only. `core` owns the accounting vocabulary and
invariants that higher layers reuse. It does not own protocol metadata, CLI options, request
templates, storage access, or report rendering.

## `AccountCode`

`AccountCode` is the jurisdiction-agnostic account identifier carried by journal lines and account
registries.

```java
public record AccountCode(String value)
```

- Purpose: represent one book-local account without hard-coding a chart-of-accounts scheme
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `AccountCodePolicy`

`AccountCodePolicy` is the canonical owner for what account-code text means in current FinGrind
books.

```java
public final class AccountCodePolicy
```

- Purpose: keep account-code meaning explicit instead of letting callers infer numeric ranges,
  parent-child hierarchy, or type semantics from string prefixes
- Current contract: `meaning() == OPAQUE_BOOK_LOCAL_IDENTIFIER` and
  `chartStructure() == PARENT_CHILD_HIERARCHY`
- Validation: `validate(AccountCode, AccountType, AccountTaxonomy)` confirms one declared account
  against the current hierarchical-chart, opaque-identifier policy

## `AccountCodePolicy.Meaning`

`AccountCodePolicy.Meaning` names the semantic contract FinGrind assigns to declared account code
text.

- Purpose: make it explicit that current books treat account codes as opaque identifiers rather
  than taxonomy-carrying ranges
- Current contract: `OPAQUE_BOOK_LOCAL_IDENTIFIER`

## `AccountCodePolicy.ChartStructure`

`AccountCodePolicy.ChartStructure` names the chart topology supported by current FinGrind books.

- Purpose: make the current parent-child chart contract explicit instead of leaving hierarchy
  support to implication
- Current contract: `PARENT_CHILD_HIERARCHY`

## `AccountName`

`AccountName` is the non-blank display name stored in the account registry.

```java
public record AccountName(String value)
```

- Purpose: keep account display text typed instead of using raw strings
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `BookEntityName`

`BookEntityName` is the canonical accounting-entity name stored in one initialized book.

```java
public record BookEntityName(String value)
```

- Purpose: keep book identity explicit instead of leaving initialized books anonymous
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `AccountingBasis`

`AccountingBasis` is the canonical basis-of-accounting posture carried by one protected book.

```java
public enum AccountingBasis implements WireValue
```

- Purpose: keep the current cash-basis doctrine explicit instead of implying accrual or mixed
  basis support
- Current contract: `CASH_BASIS`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `AccountingFrameworkPosition`

`AccountingFrameworkPosition` is the canonical reporting-framework posture carried by one
protected book.

```java
public enum AccountingFrameworkPosition implements WireValue
```

- Purpose: keep the current non-statutory management posture explicit instead of implying one
  statutory or standards-compliance claim
- Current contract: `NON_STATUTORY_INTERNAL_MANAGEMENT`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `EntityForm`

`EntityForm` is the canonical legal-form posture carried by one protected book.

```java
public enum EntityForm implements WireValue
```

- Purpose: separate one book's entity-form assumption from chart taxonomy and reporting language
- Current contract: `OWNER_MANAGED_SINGLE_ENTITY`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

Book-template selection, persisted book doctrine, built-in doctrine bundles, and their display
labels continue in [DOC_01_Core_BookDoctrine.md](./DOC_01_Core_BookDoctrine.md).

## `AccountingEvidence`

`AccountingEvidence` is the first-class bundle of source documents and approvals attached to one
posting request or one committed posting fact.

```java
public record AccountingEvidence(
    List<SourceDocumentReference> sourceDocuments,
    List<ApprovalReference> approvals)
```

- Purpose: make evidence a durable accounting fact instead of external operator folklore
- Validation: rejects `null` collections and rejects empty `sourceDocuments`
- Surface: `sourceDocuments()` and `approvals()` preserve caller order after validation
- Durable boundary: duplicate source-document ids or approval ids remain representable in memory
  and are rejected only by the protected-book posting uniqueness constraints

## `ApprovalId`

`ApprovalId` is the stable identifier for one approval artifact referenced by accounting evidence.

```java
public record ApprovalId(String value)
```

- Purpose: carry one durable approval identity through request, storage, and query surfaces
- Validation: rejects `null`, blank text, and values outside the public approval-id grammar

## `ApprovalType`

`ApprovalType` is the stable public classifier for one approval artifact referenced by accounting
evidence.

```java
public record ApprovalType(String value)
```

- Purpose: distinguish approval evidence kinds without inventing adapter-owned enums prematurely
- Validation: rejects `null`, blank text, and values outside the public approval-type grammar

## `ApprovalDecision`

`ApprovalDecision` is the canonical approval outcome retained as part of posting evidence.

```java
public enum ApprovalDecision implements WireValue
```

- Purpose: keep approval outcomes explicit and stable across request, storage, and query surfaces
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `ApprovalReference`

`ApprovalReference` is the typed evidence link to one approval artifact.

```java
public record ApprovalReference(
    ApprovalId approvalId,
    ApprovalType approvalType,
    ActorId approverId,
    ActorType approverType,
    ApprovalDecision decision,
    Instant approvedAt)
```

- Purpose: keep approval evidence structured and durable across request and committed-posting
  surfaces
- Validation: rejects `null` approval id, approval type, approver id, approver type, decision,
  or approval timestamp

## `EntityProfile`

`EntityProfile` is the structured neutral entity descriptor embedded in one book identity.

```java
public record EntityProfile(BookEntityName displayName)
```

- Purpose: keep the accounting-entity display identity explicit without overloading book identity
  with non-doctrinal descriptive metadata
- Validation: rejects `null` display name

## `BookIdentity`

`BookIdentity` is the structured identity metadata persisted with one book.

```java
public record BookIdentity(
    EntityProfile entityProfile,
    BookDoctrine bookDoctrine,
    CurrencyUnit functionalCurrency,
    FiscalYearStart fiscalYearStart)
```

- Purpose: couple entity profile, persisted doctrine, functional currency, and fiscal-year anchor
  as one typed bookkeeping fact for one initialized book
- Surface: `entityName()` keeps the most common identity fact accessible without unwrapping the
  full entity profile
- Validation: rejects `null` entity profile, doctrine, functional currency, and fiscal-year
  start

## `AccountingKernelProfileId`

`AccountingKernelProfileId` is the durable identifier for one executable accounting-kernel
profile.

```java
public record AccountingKernelProfileId(String value)
```

- Purpose: persist one explicit kernel-profile owner with each initialized book instead of hiding
  doctrine only in prose
- Validation: rejects blank values, values longer than 120 characters, uppercase characters, and
  tokens outside lowercase kebab-case

## `AccountingKernelProfiles`

`AccountingKernelProfiles` publishes the built-in executable accounting-kernel profile inventory.

```java
public final class AccountingKernelProfiles
```

- Purpose: keep built-in profile ids centralized so CLI, executor, SQLite, discovery, and tests
  all speak one canonical vocabulary
- Current built-in profile: "internal-management-bookkeeping-kernel"

## `AccountType`

`AccountType` is the canonical chart-of-accounts classification for one declared account.

```java
public enum AccountType implements WireValue {
  ASSET,
  LIABILITY,
  EQUITY,
  REVENUE,
  EXPENSE
}
```

- Purpose: distinguish account taxonomy from `NormalBalance`, which is only debit-versus-credit
  increase polarity
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## Polarity Ownership

There is no standalone polarity-side field in the current kernel. Declared classification owns
polarity through `AccountTaxonomyDoctrine`, so the stored account contract carries `accountType` plus
taxonomy and derives `normalBalance()` from that combination.

## `AccountNodeKind`

`AccountNodeKind` is the canonical hierarchy role for one declared account.

```java
public enum AccountNodeKind implements WireValue
```

- Purpose: distinguish roll-up headers from directly postable accounts in the chart hierarchy
- Surface: `allowsPosting()` and `allowsChildren()` make the hierarchy contract explicit
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `AccountTaxonomy`

`AccountTaxonomy` is the canonical declaration bundle for chart hierarchy and statement taxonomy on
one declared account.

```java
public record AccountTaxonomy(
    AccountNodeKind nodeKind,
    Optional<AccountCode> parentAccountCode,
    Optional<FinancialPositionLineClassification> financialPositionLineClassification,
    Optional<ProfitAndLossLineClassification> profitAndLossLineClassification,
    Optional<CashFlowAssetClassification> cashFlowAssetClassification)
```

- Purpose: keep node role, hierarchy, and report taxonomy on the declared account instead of
  inferring them from account codes, names, or renderer-local rules
- Validation role: `AccountTaxonomyDoctrine.validate(...)` enforces which taxonomy branch must be present
  for each `AccountType`, including the required cash-versus-non-cash classification for asset
  accounts
- Factory: `empty()` returns the neutral postable taxonomy before account-type-specific validation
  applies

## `FinancialPositionLineClassification`

`FinancialPositionLineClassification` is the canonical balance-sheet taxonomy vocabulary for
declared accounts and derived equity lines.

```java
public enum FinancialPositionLineClassification implements WireValue
```

- Scope: classifies ASSET, LIABILITY, and EQUITY declared accounts, including current/noncurrent
  buckets and equity classes such as `EQUITY_CONTRIBUTION`, `EQUITY_WITHDRAWAL`,
  `RESULT_HOLDING`, and `RETAINED_ACCUMULATED`
- Surface: `accountType()` maps each classification back to its owning `AccountType`
- Wire contract: `wireValue()`, `wireValues()`, `declaredAccountWireValues()`, and
  `fromWireValue(...)` own the stable public vocabulary

## `ProfitAndLossLineClassification`

`ProfitAndLossLineClassification` is the canonical income-statement taxonomy vocabulary for nominal
accounts.

```java
public enum ProfitAndLossLineClassification implements WireValue
```

- Scope: classifies REVENUE and EXPENSE lines such as `OPERATING_REVENUE`, `COST_OF_SALES`, and `OTHER_EXPENSE`
- Surface: `accountType()` maps each classification back to REVENUE or EXPENSE ownership
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `CashFlowAssetClassification` And `CashFlowSectionKind`

`CashFlowAssetClassification` is the declared-account cash doctrine for asset accounts, and `CashFlowSectionKind` is the public statement-section vocabulary for cash receipts and payments.

```java
public enum CashFlowAssetClassification implements WireValue
public enum CashFlowSectionKind implements WireValue
```

- `CashFlowAssetClassification`: distinguishes `CASH_AND_CASH_EQUIVALENT` asset accounts from `NON_CASH` assets so request validation, posting semantics, and cash-basis reporting all reuse one canonical asset classification
- `CashFlowSectionKind`: owns the stable `OPERATING`, `INVESTING`, and `FINANCING` vocabulary used by the cash receipts/payments statement across JSON, text, CSV, and PDF surfaces
- Wire contract: both enums own `wireValue()`, `wireValues()`, and `fromWireValue(...)`

## `AccountClassificationReachability`

`AccountClassificationReachability` is the current-kernel doctrine owner for which declared-account
classification cells are opening-reachable, operational-journal-reachable, and reversal-reachable.

```java
public final class AccountClassificationReachability
```

- Purpose: keep per-classification write-route truth out of discovery prose, CLI help fragments,
  and validator-local literals
- Surface: `currentKernel()` publishes the full classification matrix, while `reachabilityFor(...)`, `openingReachable(...)`, `operationalJournalReachable(...)`, and `reversalReachable(...)` project the same doctrine onto one validated `AccountTaxonomy`
- Doctrine: `RESULT_HOLDING` and `RETAINED_ACCUMULATED` remain declarable and opening-reachable but are not caller-operationally writable or reversal-reachable because the close commands reserve those classifications for generated close postings

## `AccountClassificationReachability.ReachabilityCell`

`AccountClassificationReachability.ReachabilityCell` is one published classification row inside the
current-kernel reachability matrix.

```java
public record ReachabilityCell(
    String classificationFamily,
    AccountType accountType,
    String classification,
    boolean declarable,
    boolean openingReachable,
    boolean operationalJournalReachable,
    boolean reversalReachable)
```

- Purpose: keep the declarable-versus-reachable truth for one classification cell typed and
  executable instead of scattering that matrix across request validation, discovery, and prose
- Validation: rejects blank family or classification labels, rejects `null` account types, and
  rejects non-declarable cells that claim any reachable write path

## `StatementLineKind`

`StatementLineKind` records whether one public statement row came from a declared account or from a
derived statement line.

```java
public enum StatementLineKind implements WireValue
```

- Purpose: prevent renderers, clients, and workflow assertions from guessing whether a row is
  registry-owned or statement-derived
- Built-in values: `DECLARED_ACCOUNT` and `CURRENT_PERIOD_RESULT`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `AccountTaxonomyDoctrine`

`AccountTaxonomyDoctrine` is the canonical doctrinal owner for declared-account taxonomy
compatibility, normal-balance derivation, and cash-account participation.

```java
public final class AccountTaxonomyDoctrine
```

- Purpose: keep declared-account taxonomy validation and polarity derivation out of CLI, SQLite,
  and reporting adapters
- Surface: `validate(...)`, `normalBalance(...)`, and `cashAndCashEquivalent(...)`
- Doctrine: balance-sheet accounts require one `FinancialPositionLineClassification`; asset
  accounts additionally require one `CashFlowAssetClassification`; nominal accounts require one
  `ProfitAndLossLineClassification` and forbid balance-sheet taxonomy fields

## `AccountStructureDoctrine`

`AccountStructureDoctrine` is the canonical doctrinal owner for account-node posting structure and
parent-child hierarchy compatibility.

```java
public final class AccountStructureDoctrine
```

- Purpose: keep chart hierarchy meaning out of account-registry and posting-policy adapters
- Surface: `allowsPosting(...)`, `allowsChildren(...)`, and `parentChildHierarchyCompatible(...)`
- Doctrine: one balance-sheet parent-child edge must preserve the same financial-position and
  asset cash-flow classifications, while one nominal parent-child edge must preserve the same
  profit-and-loss classification

## `ProfitAndLossAccountDoctrine`

`ProfitAndLossAccountDoctrine` is the canonical doctrinal owner for temporary-account close
participation and current-period profit-or-loss contribution.

```java
public final class ProfitAndLossAccountDoctrine
```

- Purpose: keep nominal-account close behavior and profit contribution arithmetic out of
  bookkeeping adapters and report renderers
- Surface: `closesTemporaryProfitAndLossAccountType(...)` and
  `profitAndLossContributionMinorUnits(...)`
- Doctrine: only `REVENUE` and `EXPENSE` accounts close into current-period result, and positive
  contribution values increase profit while negative values reduce profit

## `ActorId`

`ActorId` is the stable identifier for the caller recorded in request provenance.

```java
public record ActorId(String value)
```

- Purpose: keep actor identity explicit in request provenance
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `ActorType`

`ActorType` classifies the actor that initiated one posting request.

```java
public enum ActorType implements WireValue {
  PERSON,
  SYSTEM,
  AGENT
}
```

- Purpose: distinguish person, system, and agent callers without free-form strings
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `BalanceSide`

`BalanceSide` is the side of one computed net balance, including the balanced zero state.

```java
public enum BalanceSide implements WireValue {
  DEBIT,
  CREDIT,
  ZERO
}
```

- Purpose: represent computed balance polarity for grouped balances and running ledgers
- Wire contract: `wireValue()` and `fromWireValue(...)` own the stable public vocabulary
- Distinction: `BalanceSide` is derived outcome state for grouped balances, while
  `JournalLine.EntrySide` is caller-authored polarity for one posted line

## `BalanceMath`

`BalanceMath` is the shared-kernel owner for exact balance arithmetic reused by bookkeeping reads
and reports.

```java
public final class BalanceMath
```

- Purpose: keep running-balance arithmetic and side derivation in the core bookkeeping model
  instead of inside one SQLite adapter helper
- Surface: `currencyBalance(...)`, `balanceSide(...)`, and `absoluteMinorUnits(...)`
- Bounds: overflow during running-balance absolute-value projection is rejected as a deterministic
  state error

## `CausationId`

`CausationId` links one request to its immediate cause.

```java
public record CausationId(String value)
```

- Purpose: preserve causal lineage in request provenance
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `CommandId`

`CommandId` is the stable caller-visible identifier for one posting command.

```java
public record CommandId(String value)
```

- Purpose: identify one logical command at the request boundary
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `CommittedProvenance`

`CommittedProvenance` is the durable audit metadata created when a posting is committed.

```java
public record CommittedProvenance(
    RequestProvenance requestProvenance,
    Instant recordedAt,
    SourceChannel sourceChannel)
```

- Purpose: carry accepted caller provenance plus commit-time audit metadata
- Validation: rejects `null` request provenance, `recordedAt`, and `sourceChannel`

## `CorrelationId`

`CorrelationId` links one request to a broader workflow.

```java
public record CorrelationId(String value)
```

- Purpose: correlate related commands without overloading `CommandId`
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `CurrencyUnit`

`CurrencyUnit` is the canonical ISO-backed currency owner used by `Money` and `PositiveMoney`.

```java
public final class CurrencyUnit
```

- Purpose: keep currency identity and exact minor-unit scale structural instead of treating them as
  free-form strings plus formatter policy
- Construction: `CurrencyUnit.of(String)` resolves one supported ISO 4217 code from FinGrind's
  pinned repository-owned currency-unit registry snapshot and publishes its exact
  `minorUnitScale()`
- Validation: rejects whitespace-padded, non-uppercase, non-ISO, or unsupported currency-unit
  codes, and rejects units whose published scale falls outside FinGrind's supported exact-money
  range of `0..9`

## `Quantity`

`Quantity` is the shared-kernel exact non-negative quantity value used by inventory-costing math
and future quantity-bearing accounting facts.

```java
public final class Quantity implements Comparable<Quantity>
```

- Purpose: represent one exact quantity as scaled integer units without embedding one unit of
  measure or falling back to one generic decimal seam
- Representation: `scale()` and `scaledUnits()` together own the exact value, while
  `canonicalDecimal()` publishes the canonical plain-decimal text form
- Surface: `ofScaledUnits(...)`, `zero(...)`, `parse(...)`, `plus(...)`, `minus(...)`, and
  `compareTo(...)` all preserve exact same-scale arithmetic
- Validation: rejects negative scaled units, unsupported scales outside `0..9`, malformed decimal
  text, redundant leading zeroes, and any subtraction that would drive quantity below zero

## `UnitOfMeasure`

`UnitOfMeasure` is the stable per-account unit token that owns which exact quantity scale is
admissible for that unit.

```java
public record UnitOfMeasure(String token, int quantityScale)
```

- Purpose: keep unit identity and quantity-scale ownership explicit instead of burying that scale
  in individual quantity values or caller-local parsing code
- Surface: `parseQuantity(...)` parses one quantity at the owned `quantityScale`, and
  `requireCompatible(...)` rejects quantities whose scale does not match the unit's declared scale
- Validation: rejects blank or overlength tokens, non-ASCII token grammar drift, and unsupported
  quantity scales outside `0..9`

## `UnitOfMeasure.QuantityIncompatibleWithUnitOfMeasureException`

`UnitOfMeasure.QuantityIncompatibleWithUnitOfMeasureException` is the dedicated failure raised
when quantity text contradicts the unit's owned exact quantity scale.

```java
public static final class QuantityIncompatibleWithUnitOfMeasureException extends IllegalArgumentException
```

- Purpose: publish unit-scale contradiction as one typed failure so higher layers can map it to
  deterministic rejections without depending on incidental parser exception text
- Surface: `quantityText()`, `unitOfMeasure()`, and `reason()` return the rejected text, the
  owning unit, and the stable rejection reason that caller-facing layers may publish
- Trigger: raised by `parseQuantity(...)` when the provided quantity text cannot resolve at the
  owned `quantityScale`

## `InventoryCostingDoctrine`

`InventoryCostingDoctrine` is the stable wire vocabulary for shared-kernel inventory-costing
method identifiers.

```java
public enum InventoryCostingDoctrine implements WireValue
```

- Purpose: publish the current inventory-costing doctrine line explicitly instead of leaving the
  method implied by executor-local code or ADR prose
- Current contract: `WEIGHTED_AVERAGE`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `InventoryMovementKind`

`InventoryMovementKind` is the stable wire vocabulary for one durable inventory-ledger movement
row kind.

```java
public enum InventoryMovementKind implements WireValue
```

- Purpose: publish the durable inventory replay vocabulary explicitly instead of burying movement
  kinds inside SQLite literals, executor-local switches, or verification-only code paths
- Current contract: `ACQUISITION`, `CAPITALIZATION`, `COUNT_INCREASE`, `OPENING`, `DISPOSAL`,
  `WRITE_DOWN`, `SHRINKAGE`, and `REVERSAL_COMP`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  movement-kind vocabulary

## `WeightedAverageCostingMath`

`WeightedAverageCostingMath` is the pure exact-costing owner for weighted-average acquisition,
disposal, write-down, reversal restore, and non-authoritative unit-cost projection.

```java
public final class WeightedAverageCostingMath
```

- Purpose: keep weighted-average inventory math exact, pure, and reusable without leaking any I/O,
  storage, or report projection concerns into the shared kernel
- Surface: `acquire(...)`, `dispose(...)`, `writeDown(...)`, `reversalRestore(...)`, and
  `roundedMovingAverageUnitCostProjection(...)`
- Disposal doctrine: `dispose(...)` derives cost of sales from the exact carrying-cost pool and
  exact quantity on hand, and a zeroing disposal consumes the entire remaining pool so quantity
  zero and cost-pool zero stay equivalent
- Admission doctrine: because the carrying-cost truth boundary is still one `Money` value at the
  currency minor-unit scale, positive pools that would strand positive quantity against a zero
  remaining pool under half-up disposal rounding are rejected up front
- Failure surface: exact-costing invariants now publish dedicated failure types for disposal
  overdraws plus exact-pool zero-equivalence and minor-unit-floor violations, so higher layers can
  translate those failures without depending on incidental exception text
- Projection boundary: `roundedMovingAverageUnitCostProjection(...)` is one read-time display
  projection only; it is never one authoritative stored fact and never feeds back into cost of
  sales

## `WeightedAverageCostingMath.InventoryPool`

`WeightedAverageCostingMath.InventoryPool` is one exact on-hand quantity and carrying-cost state
pair for one homogeneous inventory pool.

```java
public record InventoryPool(Quantity quantityOnHand, Money costPool)
```

- Purpose: keep the exact quantity/cost-pool truth boundary structural instead of passing those
  values around as unrelated arguments
- Surface: `zero(...)` returns one zero pool for the selected currency and quantity scale
- Validation: rejects `null` components, rejects quantity/cost-pool states where only one side is
  zero, and rejects positive pools whose carrying cost falls below one currency minor unit per
  smallest quantity increment because such states would break the zero-to-zero invariant under
  half-up disposal rounding

## `WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException`

`WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException` is the dedicated failure raised
when one exact pool would leave quantity and carrying cost disagreeing about whether inventory is
present.

```java
public static final class InventoryPoolZeroEquivalenceException extends IllegalArgumentException
```

- Purpose: publish the zero-to-zero truth breach explicitly instead of leaving higher layers to
  infer it from a generic validation message
- Surface: `quantityOnHand()` and `costPool()` return the rejected attempted pool state
- Trigger: raised when one side of the exact pool is zero and the other is positive

## `WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException`

`WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException` is the dedicated failure raised
when one positive exact pool lacks enough carrying cost to preserve zero-to-zero truth at the
currency minor-unit boundary.

```java
public static final class InventoryPoolMinorUnitFloorException extends IllegalArgumentException
```

- Purpose: expose the exact pool-floor invariant as a first-class contract instead of burying it
  in one generic constructor failure
- Surface: `quantityOnHand()`, `costPool()`, and `minimumRequiredMinorUnits()` describe the
  rejected attempted pool state and the minimum carrying-cost floor it violated
- Trigger: raised when positive quantity would carry fewer currency minor units than smallest
  quantity increments

## `WeightedAverageCostingMath.Disposal`

`WeightedAverageCostingMath.Disposal` is the exact weighted-average disposal outcome containing the
derived cost of sales and the remaining pool.

```java
public record Disposal(InventoryPool remainingPool, Money costOfSales)
```

- Purpose: publish both sides of one disposal atomically so callers cannot separate the derived
  cost of sales from the remaining exact pool state
- Validation: rejects `null` values and rejects cost-of-sales currency drift from the remaining
  pool currency

## `WeightedAverageCostingMath.DisposedQuantityExceedsOnHandException`

`WeightedAverageCostingMath.DisposedQuantityExceedsOnHandException` is the dedicated failure raised
when one weighted-average disposal attempts to relieve more quantity than the exact pool contains.

```java
public static final class DisposedQuantityExceedsOnHandException extends IllegalArgumentException
```

- Purpose: publish quantity overdraw as one typed exact-costing failure so higher layers can map
  it to domain rejections without matching on message text
- Surface: `disposedQuantity()` and `quantityOnHand()` return the rejected request and the exact
  available on-hand quantity
- Trigger: raised by `dispose(...)` before any weighted-average cost-of-sales calculation runs

## `WeightedAverageCostingMath.InexactAcquisitionCostException`

`WeightedAverageCostingMath.InexactAcquisitionCostException` is the dedicated failure raised when
one quantity and unit-cost pair cannot compose one exact acquisition carrying cost at the currency
minor-unit boundary.

```java
public static final class InexactAcquisitionCostException extends IllegalArgumentException
```

- Purpose: expose acquisition exactness failure as one typed contract instead of leaving higher
  layers to infer it from generic arithmetic validation
- Surface: `quantity()` and `unitCost()` return the rejected quantity and unit cost facts
- Trigger: raised by `acquire(...)` before inventory state changes when exact quantity multiplied
  by unit cost would not resolve to one exact carrying-cost amount

## `FiscalYearStart`

`FiscalYearStart` is the canonical month-day anchor for one book's fiscal year boundary.

```java
public record FiscalYearStart(MonthDay value)
```

- Purpose: make fiscal-year configuration explicit in book identity and enforce that one
  `interim-result-sweep` range stays inside one fiscal year while `fiscal-year-close` starts at
  that anchor and ends at the fiscal year end, with comparative statement windows derived from the
  same declared anchor
- Validation: rejects invalid month-day values; `toString()` renders the canonical `MM-dd` form
  used on public command surfaces

## `EffectiveDateRange`

`EffectiveDateRange` is the shared-kernel effective-date filter reused by public bookkeeping
queries, public workflow assertions, and executor-owned bookkeeping criteria.

```java
public sealed interface EffectiveDateRange
```

- Purpose: keep date-range filtering semantics in the shared kernel instead of treating them as
  contract-owned DTO shape
- Variants: `unbounded`, `from`, `to`, and `bounded`
- Validation: bounded ranges reject a lower bound after the upper bound

## `EffectiveDateHorizonPolicy`

`EffectiveDateHorizonPolicy` is the shared UTC-date horizon owner for write and close workflows.

```java
public final class EffectiveDateHorizonPolicy
public static final class FutureEffectiveDateException
```

- Purpose: keep "effective date must not fall after current UTC date" doctrine owned by one
  executable policy instead of re-deriving that rule in each posting, sweep, and close workflow
- Surface: `requireNotAfterToday(LocalDate, Clock)` compares against the current UTC date derived
  from the supplied clock
- Failure: `FutureEffectiveDateException` publishes both the attempted effective date and the
  current UTC date so higher layers can render deterministic rejections and repair hints

## `ReportingPeriod`

`ReportingPeriod` is the inclusive bounded period used by `interim-result-sweep`,
`fiscal-year-close`, income statements, statements of cash receipts and payments, and statements
of changes in equity.

```java
public record ReportingPeriod(LocalDate effectiveDateFrom, LocalDate effectiveDateTo)
```

- Purpose: keep bounded reporting, interim-result-sweep, and fiscal-year-close semantics
  structural instead of treating them as parallel pairs of raw dates
- Validation: rejects `null` bounds and rejects `effectiveDateFrom` after `effectiveDateTo`
- Surface: `effectiveDateRange()`, `contains(...)`, and `dayAfter()`

## `CanonicalTemporalText`

`CanonicalTemporalText` is the canonical owner for FinGrind's persisted and machine-contract
date/time text grammar.

```java
public final class CanonicalTemporalText
```

- Purpose: keep one executable owner for canonical `YYYY-MM-DD` local dates and canonical UTC
  instants instead of letting Java parsers, JSON Schema hints, and SQLite checks drift apart
- Surface: `parseLocalDate(...)`, `parseUtcInstant(...)`, `formatLocalDate(...)`,
  `formatUtcInstant(...)`, `isCanonicalLocalDate(...)`, and `isCanonicalUtcInstant(...)`
- Contract: `LOCAL_DATE_PATTERN` and `UTC_INSTANT_PATTERN` are the exact grammar fragments reused
  by CLI parsing, machine-readable request schemas, and SQLite file-format constraints

## `IdempotencyKey`

`IdempotencyKey` is the caller-supplied duplicate-submission identity.

```java
public record IdempotencyKey(String value)
```

- Purpose: scope duplicate detection inside one selected book
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `RequestFingerprint`

`RequestFingerprint` is the versioned semantic fingerprint for one normalized posting request.

```java
public record RequestFingerprint(int version, String sha256Hex)
```

- Purpose: keep idempotent replay tied to one canonical request-model owner instead of raw input
  bytes
- Current contract: `CURRENT_VERSION` names the active semantic-fingerprint version
- Validation: rejects non-positive versions and any digest that is not one lowercase 64-character
  SHA-256 hex value

Journal, posting, and request-provenance primitives continue in
[DOC_01_Core_LedgerAndPosting.md](./DOC_01_Core_LedgerAndPosting.md).

Source-document evidence identifiers, reversal lineage primitives, committed source-channel
vocabulary, and the shared `WireValue` contract continue in
[DOC_01_Core_EvidenceAndWire.md](./DOC_01_Core_EvidenceAndWire.md).
