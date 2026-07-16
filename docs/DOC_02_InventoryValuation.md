---
afad: "4.0"
version: "0.61.0"
domain: INVENTORY_VALUATION
updated: "2026-07-16"
route:
  keywords: [fingrind, inventory valuation, inventory, weighted average, quantity on hand, carrying value, cost pool, movements, as-of, get-posting]
  questions: ["how does inventory valuation work in fingrind", "what does inventory-valuation return", "how does get-posting expose derived inventory cost of sales", "where are inventory valuation models documented"]
---

# Inventory Valuation API Reference

This file documents the public and local point-in-time inventory valuation surface.

## `InventoryValuationQuery`, `InventoryValuationAccount`, `InventoryValuationMovement`, `InventoryValuationReport`, `InventoryValuationResult`, `InventoryValuationCriteria`, And `InventoryValuationView`

```java
public record InventoryValuationQuery(Optional<LocalDate> effectiveDateAsOf, boolean includeMovements)
public record InventoryValuationAccount(...)
public record InventoryValuationMovement(...)
public record InventoryValuationReport(...)
public sealed interface InventoryValuationResult
public record InventoryValuationCriteria(...)
public record InventoryValuationView(...)
```

- `InventoryValuationQuery`: uses `--as-of` to select an inclusive effective-date cutoff and `--movements` to request durable movement detail.
- The executor replays the ordered durable ledger per inventory account and forms the authoritative result from exact quantity and `cost_pool_minor` deltas.
- `InventoryValuationAccount`: publishes the owned unit of measure, exact quantity on hand, exact carrying value, and `roundedMovingAverageUnitCostProjection` only as a read-time informational projection.
- `InventoryValuationMovement`: exposes the effective date, store-owned account sequence, durable movement kind, exact signed deltas, and source posting identity when detail is requested.
- Costed-sale `get-posting` readback reconstructs `ResolvedInventoryCosting` from the same canonical movement replay, including exact cost of sales, relieved quantity, and the informational rounded projection.
- Invariant: carrying value never derives from quantity times the rounded projection; the exact cost pool and canonical replay order remain the source of truth.
- CSV uses the single `inventory-valuation` record family. Every row carries the account snapshot; with `--movements`, one row is emitted for each matching movement and accounts without a matching movement retain one snapshot row with blank movement columns.

`InventoryValuationReportModelBuilder` is documented in [DOC_02_SharedReportModel.md](./DOC_02_SharedReportModel.md).
