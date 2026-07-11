---
afad: "4.0"
version: "0.60.0"
domain: CONTRACT_EXECUTOR_INCOME_STATEMENT_PRESENTATION
updated: "2026-07-11"
route:
  keywords: [fingrind, income-statement, gross-profit, multi-step, presentation, trading, report, csv, pdf, text]
  questions: ["where is the trading income statement presentation documented", "which doc covers IncomeStatementPresentationSupport", "where is gross profit modeled in fingrind"]
---

# Income Statement Presentation Reference

This file documents the public helpers that keep trading income statements truthful across text,
CSV, JSON, and PDF projection surfaces.

## `IncomeStatementGrossProfitSupport`

`IncomeStatementGrossProfitSupport` is the contract-owned helper that inserts the gross-profit
section for trading income-statement projections.

```java
public final class IncomeStatementGrossProfitSupport
```

- Purpose: keep the revenue-minus-cost-of-sales grouping owned in one public helper instead of re-deriving gross-profit projection rules separately inside every report builder or renderer
- Scope: folds revenue and cost-of-sales rows into one typed gross-profit section while preserving per-currency totals and stable section ordering ahead of operating expenses
- Boundary: service-only books do not publish a gross-profit section because their doctrines do not carry inventory or cost-of-sales accounts

## `IncomeStatementPresentationSupport`, `IncomeStatementPresentationSupport.SectionCode`, And `IncomeStatementPresentationSupport.PresentationSection`

These public helpers own the truthful multi-step presentation that trading income statements
project across text, CSV, JSON, and PDF surfaces.

```java
public final class IncomeStatementPresentationSupport
public enum IncomeStatementPresentationSupport.SectionCode
public record IncomeStatementPresentationSupport.PresentationSection(...)
```

- Purpose: keep top-to-bottom trading statement presentation semantics owned in one public seam instead of re-deriving section ordering, section codes, and rendered totals inside each projector
- `SectionCode`: publishes the stable machine-readable section codes and human-readable titles used for revenue, cost-of-sales, other revenue/income, and operating expense presentation
- `PresentationSection`: carries one rendered section's code, rows, totals, and renderability decision so projector families can stay format-specific without owning accounting meaning
- Boundary: service books still project nominal revenue and expense sections directly; the helper only reshapes reporting for doctrines that own trading inventory and gross-profit presentation
