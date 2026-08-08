---
afad: "5.0.1"
version: "0.62.2"
domain: ACCRUAL_CUTOFFS
updated: "2026-08-09"
route:
  keywords: [fingrind, accrual cut-off, prepayment, deferred revenue, accrued expense, recognition, settlement, schedule, reversal, as-of]
  questions: ["how do accrual cut-offs work in fingrind", "how do I record a prepayment", "how do I recognize deferred revenue", "how do I settle an accrued expense", "what does accrual-cutoff-schedule report"]
---

# Accrual Cut-offs API Reference

This file documents the owned accrual cut-off context for accrual-basis books.

## `AccrualCutoffId`, `AccrualCutoffKind`, `AccrualCutoffApplicationKind`, And `AccrualCutoffRecognitionInterval`

```java
public record AccrualCutoffId(String value)
public enum AccrualCutoffKind
public enum AccrualCutoffApplicationKind
public record AccrualCutoffRecognitionInterval(LocalDate startDate, LocalDate endDate)
```

- An `AccrualCutoffId` identifies one durable aggregate in one book.
- `AccrualCutoffKind` distinguishes `PREPAYMENT`, `DEFERRED_REVENUE`, and `ACCRUED_EXPENSE`.
- `AccrualCutoffApplicationKind` distinguishes recognition from accrued-expense settlement.
- Recognition intervals are inclusive and apply only to prepayments and deferred revenue.

## `AccrualCutoffBookkeepingEntryVariants`, `AccrualCutoffAdmissionPolicy`, `AccrualCutoffEntrySemanticsViolations`, And `PostingAccrualCutoffRejectionSemantics`

```java
public sealed interface AccrualCutoffBookkeepingEntryVariants
public final class AccrualCutoffAdmissionPolicy
public record AccrualCutoffAdmissionPolicy.Resolution(...)
public final class AccrualCutoffEntrySemanticsViolations
public final class PostingAccrualCutoffRejectionSemantics
```

- `record-prepayment` moves cash into a prepaid-expense asset, then admits exact manual expense recognition within the declared interval.
- `record-deferred-revenue` moves cash into a deferred-revenue liability, then admits exact manual revenue recognition within the declared interval.
- `record-accrued-expense` records an expense and accrued-expense liability; `record-accrued-expense-settlement` pays that liability.
- `record-accrual-cutoff-recognition` and `record-accrued-expense-settlement` resolve their account pair from the referenced aggregate. Callers supply the aggregate id and exact amount, never a replacement journal.
- The admission policy rejects cash-basis use, unknown or duplicate aggregate ids, invalid lifecycle kind, out-of-interval recognition, backdated lifecycle facts, and over-application before persistence.
- `record-reversal` writes a compensating lifecycle fact whenever it reverses a cut-off origin, recognition, or settlement. A reversal must preserve the lifecycle horizon; an origin can be reversed only after all active lifecycle applications have been reversed.

## `ResolvedAccrualCutoffApplication`

```java
public record ResolvedAccrualCutoffApplication(...)
```

`ResolvedAccrualCutoffApplication` is executor-owned resolution output for a recognition or settlement request. It names the admitted aggregate kind, application kind, and fixed debit and credit accounts; callers do not substitute those derived accounting facts.

## `AccrualCutoffRecord`, `AccrualCutoffLookupStore`, And `BookkeepingAccrualCutoffReadService`

```java
public sealed interface AccrualCutoffRecord
public interface AccrualCutoffLookupStore
public final class BookkeepingAccrualCutoffReadService
```

- The aggregate stores an immutable origin and append-only lifecycle applications.
- Remaining amount is derived as original amount less the signed lifecycle total; it is never a mutable cached balance.
- The lookup store is the persistence boundary for aggregate admission and reporting. SQLite independently enforces matching typed origins, account taxonomy, lifecycle order, currency, remaining amount, and append-only facts.

## `AccrualCutoffScheduleQuery`, `AccrualCutoffScheduleRow`, `AccrualCutoffScheduleReport`, `AccrualCutoffScheduleResult`, And `AccrualCutoffScheduleReportModelBuilder`

```java
public record AccrualCutoffScheduleQuery(Optional<LocalDate> effectiveDateAsOf)
public record AccrualCutoffScheduleRow(...)
public record AccrualCutoffScheduleReport(...)
public sealed interface AccrualCutoffScheduleResult
public final class AccrualCutoffScheduleReportModelBuilder
```

- `accrual-cutoff-schedule` publishes each cut-off’s original amount, applied amount, remaining amount, recognition interval where applicable, and lifecycle horizon.
- `--as-of` is an inclusive effective-date cutoff. It does not select a durable database revision or imply revision-addressable report replay.
- Text, JSON, CSV, and PDF projections all derive from the one shared report model. Carrying and remaining amounts remain exact minor-unit values in machine output.

## Operative Boundary

This context owns manual exact-amount lifecycle applications. It does not infer periodic allocation, compose cut-off events with tax or foreign-exchange resolution, or provide query-at-revision semantics. Fixed assets, financing, realized foreign exchange, and other payroll profiles remain separate excluded contexts; the narrow Latvian 2026 monthly-payroll profile is owned independently by [ADR_LATVIAN_PAYROLL.md](./ADR_LATVIAN_PAYROLL.md).
