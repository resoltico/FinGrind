---
afad: "5.0.1"
version: "0.62.2"
domain: CORE_BOOK_DOCTRINE
updated: "2026-08-09"
route:
  keywords: [fingrind, book doctrine, book template, inventory costing, weighted average, accounting basis]
  questions: ["how does a book choose its accounting doctrine", "which book templates does fingrind provide", "when is inventory costing required"]
---

# Book Doctrine Core Reference

This file documents the exported doctrine family in the `core` module. It owns the template,
accounting posture, inventory-costing selection, and display labels persisted with one book.

## `BookTemplateId`

`BookTemplateId` is the canonical guided setup template identifier carried by one protected book.

```java
public enum BookTemplateId implements WireValue
```

- Purpose: make the seeded template family explicit instead of hiding it in executor setup code
- Current contract: `OWNER_MANAGED_SERVICE`, `OWNER_MANAGED_TRADING`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public vocabulary

## `BookDoctrine`

`BookDoctrine` is the canonical doctrine owner for one protected book's accounting posture.

```java
public record BookDoctrine(
    AccountingKernelProfileId accountingKernelProfileId,
    AccountingBasis accountingBasis,
    AccountingFrameworkPosition accountingFrameworkPosition,
    EntityForm entityForm,
    BookTemplateId bookTemplateId,
    @Nullable InventoryCostingDoctrine inventoryCostingDoctrine)
```

- Purpose: keep kernel profile, accounting basis, framework posture, entity form, and starter template plus inventory-costing selection under one persisted doctrine owner
- Validation: rejects `null` required doctrine components; trading templates require `WEIGHTED_AVERAGE`, while service templates forbid an inventory-costing doctrine

## `BookDoctrines`

`BookDoctrines` publishes the built-in doctrine bundles FinGrind can persist today.

```java
public final class BookDoctrines
```

- Purpose: centralize the current built-in doctrine so open-book, discovery, SQLite, CLI, and tests all speak one doctrine bundle
- Current built-in doctrines: `INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE`, which is cash-basis, non-statutory internal management, owner-managed single entity, and owner-managed service; `INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL`, which is the equivalent accrual-basis service doctrine; `INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING`, which is cash-basis owner-managed trading with moving weighted-average; and `INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL`, which is the equivalent accrual-basis trading doctrine

## `BookDoctrineDisplay`

`BookDoctrineDisplay` is the operator-facing label owner for persisted doctrine values.

```java
public final class BookDoctrineDisplay
```

- Purpose: translate persisted doctrine identifiers into stable human-facing labels for CLI, PDF, and other operator surfaces
- Current label families: accounting kernel, accounting basis, framework posture, entity form, seed template, and inventory costing
- Boundary: this is a presentation helper over persisted doctrine values, not a second doctrine source
