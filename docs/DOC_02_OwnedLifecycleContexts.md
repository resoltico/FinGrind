---
afad: "4.0"
version: "0.61.0"
domain: OWNED_LIFECYCLE_CONTEXTS
updated: "2026-07-16"
route:
  keywords: [fingrind, fixed assets, financing, realized foreign exchange, lifecycle contexts, register reports]
  questions: ["which lifecycle context owns fixed assets", "which types own financing", "how is realized foreign exchange represented"]
---

# Owned Lifecycle Contexts API Reference

This reference documents the three independently owned lifecycle contexts published in `0.61.0`. They add typed business events, durable facts, admission rules, compensating reversals, and register reports to one protected book. Each context owns its aggregate state and invariants; neither a generic journal nor another context may recreate its lifecycle facts.

The business boundaries and primary-source caveats are recorded in [ADR_FIXED_ASSETS.md](./ADR_FIXED_ASSETS.md), [ADR_FINANCING.md](./ADR_FINANCING.md), and [ADR_REALIZED_FOREIGN_EXCHANGE.md](./ADR_REALIZED_FOREIGN_EXCHANGE.md). The relevant primary references are the IFRS Foundation's [IAS 16 material](https://www.ifrs.org/content/dam/ifrs/publications/pdf-standards/english/2022/issued/part-a/ias-16-property-plant-and-equipment.pdf?bypass=on), [IFRS 9 standard page](https://www.ifrs.org/issued-standards/list-of-standards/ifrs-9-financial-instruments/), and [IAS 21 material](https://www.ifrs.org/content/dam/ifrs/publications/pdf-standards/english/2021/issued/part-a/ias-21-the-effects-of-changes-in-foreign-exchange-rates.pdf). These contexts are deliberately narrower than those standards and must not be represented as general statutory-compliance engines.

## `FixedAssetId`, `FixedAssetDepreciationSchedule`, `FixedAssetRecord`, `FixedAssetLookupStore`, `FixedAssetAdmissionPolicy`, And `FixedAssetAdmissionPolicy.Resolution`

```java
public record FixedAssetId(String value)
public record FixedAssetDepreciationSchedule(...)
public sealed interface FixedAssetRecord
public interface FixedAssetLookupStore
public final class FixedAssetAdmissionPolicy
public record FixedAssetAdmissionPolicy.Resolution(...)
```

- `FixedAssetId` identifies one fixed-asset aggregate in one book.
- `FixedAssetDepreciationSchedule` records the immutable straight-line schedule selected at capitalization.
- `FixedAssetRecord` and `FixedAssetLookupStore` expose retained capitalization, application, disposal, and reversal facts without a mutable parallel balance.
- `FixedAssetAdmissionPolicy` owns pre-persistence validation for schedule, lifecycle horizon, carrying value, and the single-disposal rule; `Resolution` names its accepted derived outcome.

## `FixedAssetBookkeepingEntryVariants`, `ResolvedFixedAssetDepreciation`, And `ResolvedFixedAssetDisposal`

```java
public sealed interface FixedAssetBookkeepingEntryVariants
public record ResolvedFixedAssetDepreciation(...)
public record ResolvedFixedAssetDisposal(...)
```

`FixedAssetBookkeepingEntryVariants` owns capitalization, depreciation, and disposal requests. The executor derives depreciation and disposal gain or loss into `ResolvedFixedAssetDepreciation` and `ResolvedFixedAssetDisposal`; callers supply source facts and never an asserted carrying value or derived gain or loss.

## `FixedAssetRegisterQuery`, `FixedAssetRegisterRow`, `FixedAssetRegisterReport`, `FixedAssetRegisterResult`, And `FixedAssetRegisterReportModelBuilder`

```java
public record FixedAssetRegisterQuery(...)
public record FixedAssetRegisterRow(...)
public record FixedAssetRegisterReport(...)
public sealed interface FixedAssetRegisterResult
public final class FixedAssetRegisterReportModelBuilder
```

`fixed-asset-register` projects retained cost, accumulated depreciation, carrying value, lifecycle dates, and disposal state through the shared report model. `--as-of` is an inclusive effective-date boundary, not a database-revision selector.

## `FinancingArrangementId`, `FinancingArrangementRecord`, `FinancingLookupStore`, `FinancingAdmissionPolicy`, And `FinancingAdmissionPolicy.Resolution`

```java
public record FinancingArrangementId(String value)
public sealed interface FinancingArrangementRecord
public interface FinancingLookupStore
public final class FinancingAdmissionPolicy
public record FinancingAdmissionPolicy.Resolution(...)
```

- `FinancingArrangementId` identifies one retained borrowing arrangement.
- `FinancingArrangementRecord` retains borrowing and compensating application facts; outstanding principal and unpaid interest are derived, never independently overwritten.
- `FinancingLookupStore` owns persistence lookup for admission and report projection.
- `FinancingAdmissionPolicy` admits only same-currency, ordered applications within principal and accrued-interest bounds; `Resolution` exposes the accepted executor result.

## `FinancingBookkeepingEntryVariants` And `ResolvedFinancingApplication`

```java
public sealed interface FinancingBookkeepingEntryVariants
public record ResolvedFinancingApplication(...)
```

The typed variants record borrowing, principal repayment, interest accrual, and interest payment. `ResolvedFinancingApplication` is executor-owned: it resolves retained arrangement accounts and exact lifecycle consequences from the durable arrangement rather than accepting a replacement journal from the caller.

## `FinancingRegisterQuery`, `FinancingRegisterRow`, `FinancingRegisterReport`, `FinancingRegisterResult`, And `FinancingRegisterReportModelBuilder`

```java
public record FinancingRegisterQuery(...)
public record FinancingRegisterRow(...)
public record FinancingRegisterReport(...)
public sealed interface FinancingRegisterResult
public final class FinancingRegisterReportModelBuilder
```

`financing-register` reconciles original principal, principal outstanding, accrued interest, paid interest, and settlement history to the general ledger through the shared report model.

## `ForeignCurrencyObligationId`, `ForeignCurrencyObligationRecord`, `RealizedForeignExchangeLookupStore`, `RealizedForeignExchangeAdmissionPolicy`, And `RealizedForeignExchangeAdmissionPolicy.Resolution`

```java
public record ForeignCurrencyObligationId(String value)
public sealed interface ForeignCurrencyObligationRecord
public interface RealizedForeignExchangeLookupStore
public final class RealizedForeignExchangeAdmissionPolicy
public record RealizedForeignExchangeAdmissionPolicy.Resolution(...)
```

`ForeignCurrencyObligationId` names one retained foreign-currency receivable. `ForeignCurrencyObligationRecord` retains its transaction amount, functional-currency carrying amount, quote attribution, settlement, and compensating reversals. The admission policy prevents out-of-order or duplicate settlement and requires exact agreement with the retained obligation; its `Resolution` holds the admitted outcome.

## `RealizedForeignExchangeBookkeepingEntryVariants` And `ResolvedRealizedForeignExchangeSettlement`

```java
public sealed interface RealizedForeignExchangeBookkeepingEntryVariants
public record ResolvedRealizedForeignExchangeSettlement(...)
```

The typed variants record obligation origination and settlement. `ResolvedRealizedForeignExchangeSettlement` derives the functional-currency gain or loss from retained carrying amount and settlement facts. It does not introduce mixed-currency journal lines or rate sourcing.

## `RealizedForeignExchangeRegisterQuery`, `RealizedForeignExchangeRegisterRow`, `RealizedForeignExchangeRegisterReport`, `RealizedForeignExchangeRegisterResult`, And `RealizedForeignExchangeRegisterReportModelBuilder`

```java
public record RealizedForeignExchangeRegisterQuery(...)
public record RealizedForeignExchangeRegisterRow(...)
public record RealizedForeignExchangeRegisterReport(...)
public sealed interface RealizedForeignExchangeRegisterResult
public final class RealizedForeignExchangeRegisterReportModelBuilder
```

`realized-foreign-exchange-register` publishes the retained transaction amount, functional carrying amount, settlement amount, derived realized result, quote attribution, and reversal state through the shared report model.

## Durable Boundary

All three contexts persist append-only lifecycle facts that are tied to their originating posting facts. SQLite independently rejects invalid ordering, duplicate terminal applications, and fact mutations; executor admission is the first defense, while register reports replay retained facts. Historical reversal is compensating only: no context edits or deletes its business history in place.
