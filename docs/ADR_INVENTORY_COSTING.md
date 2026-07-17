---
afad: "4.0"
version: "0.61.0"
domain: ADR_INVENTORY_COSTING
updated: "2026-07-16"
route:
  keywords: [fingrind, inventory costing, weighted average, cost pool, quantity, cost of sales, inventory subledger, capitalization]
  questions: ["what inventory costing doctrine is fingrind implementing first", "what is the source of truth for inventory costing in fingrind", "how will fingrind derive cost of sales for inventory movements", "why can't direct journals touch inventory in fingrind"]
---

# Inventory Costing ADR

**Purpose**: Declare FinGrind's live inventory-costing doctrine, its exact truth boundary, and the
constraints that later inventory work must preserve.
**Companion documents**:
- [ADR_ACCOUNTING_FOUNDATION.md](./ADR_ACCOUNTING_FOUNDATION.md)
- [ADR_ACCOUNTING_KERNEL_SCOPE.md](./ADR_ACCOUNTING_KERNEL_SCOPE.md)
- [DOC_01_DecimalBoundaries.md](./DOC_01_DecimalBoundaries.md)
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md)
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md)

## Decision

FinGrind's first inventory-costing doctrine is one perpetual moving weighted-average model over one
homogeneous inventory pool per inventory account.

The live executable kernel owns inventory as a first-class operational subledger: typed commands,
exact quantity and carrying-cost state, append-only movement replay, SQLite backstops, executor
admission, public contracts, and conformance tests are one coherent system owner.

## Inventory Context Boundary

The first inventory line is one supporting operational-subledger context that publishes accounting
facts into one protected book.

That context owns:
- inventory quantity and carrying-cost state
- inventory movements as an append-only ledger
- cost-of-sales derivation for inventory disposals
- inventory-specific admissibility rules
- inventory-specific write vocabulary above the raw journal path

That context does not yet own:
- one SKU or item master
- FIFO or other alternative costing doctrines
- one warehouse or location model
- one landed-cost allocation engine beyond caller-capitalized amounts

## Accepted Doctrine

The live doctrine preserves these decisions:

- one costing method only in the first executable line: perpetual moving weighted-average
- FIFO is deferred to later work on the same inventory movement ledger, not to a separate truth
  owner
- one homogeneous costed pool per inventory account
- one mandatory unit of measure owned by the inventory account
- one exact `Quantity` value with no embedded unit-of-measure ownership
- one purchase write shape based on `quantity + unitCost`
- one sale-relief write shape based on quantity only, with cost of sales derived by FinGrind
- one typed capitalization family in the first executable line for separately paid carrying-cost
  increases, including operator-capitalized freight, duty, and handling
- one typed adjustment family for write-down, shrinkage, and count-increase events
- one executor-owned derived cost-of-sales resolution step in the same deferred-resolution family
  as tax and reversal
- no raw direct-journal path may touch inventory accounts

## Source Of Truth Boundary

The exact costing truth is only:
- the per-account `(quantity, cost_pool_minor)` state
- the canonical replay order `(effective_date, account_sequence)`

Every exposed unit-cost value is named `roundedMovingAverageUnitCostProjection`.
That name is deliberate: it is one rounded, read-time projection derived from exact
`cost_pool_minor / quantity`, not one authoritative stored fact.

Therefore:
- cost of sales is always computed as
  `roundHalfUp(cost_pool × qtyDisposed / qtyOnHand)` from the exact pool and exact on-hand quantity
- `roundedMovingAverageUnitCostProjection` is never stored as authoritative state
- `roundedMovingAverageUnitCostProjection` is never fed back into cost-of-sales derivation
- carrying value equals the exact cost pool, never
  `quantity × roundedMovingAverageUnitCostProjection`

## Acquisition And Disposal Doctrine

Acquisitions, capitalization, and count increases grow the pool. Disposals, shrinkage, and
write-downs reduce it.

Shared acquisition rule:
- input `unitCost` or `amount` is one exact functional-currency, pre-VAT carrying cost
- operator-capitalized freight, duty, and handling enter the same carrying-cost amount before pool
  admission
- `INPUT_EXPENSE_RECOVERABLE` posts to one recoverable-tax account and stays outside the pool
- `INPUT_EXPENSE_NONRECOVERABLE` is capitalized into the pool
- foreign-exchange translation resolves before the amount enters the pool
- `poolDelta = baseFunctionalCost + nonrecoverableInputTax`

Shared disposal rule:
- cost of sales is derived as `roundHalfUp(cost_pool × qtyDisposed / qtyOnHand)` against the
  exact pool and exact on-hand quantity
- exact-to-zero disposals consume the entire remaining pool so quantity zero and pool zero stay
  equivalent
- because the first executable pool is still stored at the currency minor-unit boundary, admitted
  positive pools must retain at least one currency minor unit per smallest quantity increment;
  otherwise a half-up partial disposal can strand positive quantity against a zero remaining pool
  and break the shared-kernel truth boundary

## Ordering And Mutation Rules

Inventory movements are append-only durable facts.

The durable storage contract preserves:
- store-owned per-account `account_sequence`
- unique `(inventory_account, account_sequence)`
- replay order `(effective_date, account_sequence)`
- no updates or deletes after movement insert
- no movement insert earlier than the account's accepted movement horizon

The executor admission layer owns the first rejection. SQLite defends single-row invariants with
per-row `CHECK` constraints and cross-row ordering, horizon, and immutability invariants with
`before insert`, `before update`, and `before delete` triggers. `verify` replays the movement
ledger to prove the materialized on-hand state still matches exact truth.

## Public Write-Surface Consequences

Because inventory costing is one real owned context:
- trading sales cannot accept operator-authored cost-of-sales amounts
- direct journals cannot move inventory
- every inventory movement must pass through one typed command that updates the movement ledger
- preflight and commit feedback must publish the exact derived cost of sales FinGrind used
- the amount-only `record-opening-position` surface must reject inventory opening because it cannot publish exact inventory quantity truth

The first inventory-owned write vocabulary therefore includes:
- purchases
- inventory capitalization
- sales with quantity-based relief
- write-down
- shrinkage
- count increase
- one quantity-aware inventory opening command
- reversals as compensating movements

## Rejected Alternatives

These alternatives are intentionally rejected for the first doctrine:

- FIFO in the first executable release as a separate ledger or separate truth owner
- periodic weighted-average
- operator-authored cost-of-sales amounts
- one per-SKU item master before one executable pool-based inventory context exists
- one cached authoritative moving-average unit cost
- raw direct-journal inventory movement

## Evidence Of Completion

The doctrine is not complete until the executable system proves:
- pool-to-zero arithmetic under property tests
- deterministic same-date replay through `(effective_date, account_sequence)`
- quantity-floor and write-down-bound rejections
- exact reversal value conservation
- recoverable versus nonrecoverable tax treatment on acquisitions and capitalization
- exact carrying value equality with `cost_pool_minor`
- public reports and posting readback that expose
  `roundedMovingAverageUnitCostProjection` as a projection and cost of sales as exact derived truth
