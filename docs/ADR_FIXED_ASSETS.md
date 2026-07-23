---
afad: "5.0.1"
version: "0.61.0"
domain: ADR_FIXED_ASSETS
updated: "2026-07-17"
route:
  keywords: [fingrind, fixed assets, capitalization, depreciation, disposal, fixed-asset register]
  questions: ["how does fingrind account for fixed assets", "what owns fixed-asset depreciation", "how does a fixed-asset disposal work"]
---

# ADR: Fixed Assets

## Status

Published in `0.61.0`. Fixed-asset capitalization, executor-resolved straight-line depreciation, disposal, compensating reversals, and fixed-asset-register reporting are one public capability with one owned lifecycle boundary.

The release boundary is a functional-currency cost model with one straight-line schedule per asset. Leases, impairment, revaluation, tax depreciation, and statutory external reporting remain outside this context.

## Boundary And Language

The Fixed Assets context owns a **fixed asset**, its **capitalization**, **depreciation schedule**, periodic **depreciation charge**, **carrying amount**, and **disposal**. It does not own inventory, leases, impairment, revaluation, tax depreciation, or statutory reporting.

Its aggregate is one `FixedAsset`, identified by `FixedAssetId`. The executor owns schedule resolution and disposal gain-or-loss derivation; callers never author derived carrying values. SQLite persists immutable capitalization and lifecycle applications and defends ordering and one-disposal invariants.

The model follows the limited cost-model concepts needed for this product boundary. It is not a representation that the book complies with IAS 16 or any local statutory regime. The primary reference is the [IFRS Foundation's IAS 16 material](https://www.ifrs.org/content/dam/ifrs/publications/pdf-standards/english/2022/issued/part-a/ias-16-property-plant-and-equipment.pdf?bypass=on).

## Commands And Reports

The typed commands are `record-fixed-asset-capitalization`, `record-fixed-asset-depreciation`, and `record-fixed-asset-disposal`. The dedicated `fixed-asset-register` report contains cost, accumulated depreciation, current carrying amount, lifecycle dates, and disposal state. A disposed row has zero current `carryingAmount` and publishes its exact immutable pre-disposal amount separately as `carryingAmountAtDisposal`; this keeps the live register and the historical disposal evidence unambiguous. Accumulated depreciation is a declared contra asset linked to the capitalized asset account, so statements present it as a reduction rather than an asset with an unexplained credit balance. General-ledger reports project the resulting postings but do not replace the register.

## Invariants

- Capitalization identifies one asset, schedule, and distinct asset, depreciation, gain, and loss accounts.
- Depreciation never exceeds depreciable cost and never follows disposal.
- Disposal occurs once and derives gain or loss from the immutable carrying amount and proceeds.
- Reversals are compensating facts with explicit lifecycle lineage; historical records are never mutated or deleted.

## Publication Condition

Publication requires the typed commands, SQLite reversal state and durable constraints that bind lifecycle values to immutable posting facts, executor admission and resolution, request contracts and templates, read projection, fixed-asset register in every supported report format, end-to-end tests, and protected-book format `52`. Earlier book formats are rejected rather than upgraded in place. The release-boundary contract test verifies this condition against the public operation registry and this ADR.
