---
afad: "5.0.1"
version: "0.61.0"
domain: CORE_DECIMAL_BOUNDARIES
updated: "2026-07-16"
route:
  keywords: [fingrind, decimal, money, quantity, unit-of-measure, inventory costing, weighted average, currency, percentage, tax-rate, exchange-rate, ratio, basis-points, boundaries]
  questions: ["can I use Money for tax rates in fingrind", "how should future exchange rates be modeled in fingrind", "what are the decimal boundaries in fingrind", "why does fingrind forbid generic BigDecimal domain seams", "does fingrind have one exported non-money exact quantity type"]
---

# Monetary And Decimal Boundaries

**Purpose**: define which decimal-bearing concepts already exist in FinGrind, which do not, and
how future decimal domains must enter the model without reopening the retired free-form money seam.

## Current Exact Boundaries

FinGrind currently models two exact numeric concept families in the shared kernel:

- `CurrencyUnit`: one supported ISO currency unit plus its exact minor-unit scale
- `Money`: one exact non-negative posted monetary value in minor units
- `PositiveMoney`: one exact strictly positive journal-line monetary value
- `MonetaryAmount`: the machine-facing published-language projection of exact money
- `Quantity`: one exact non-negative scaled quantity with no embedded unit meaning
- `UnitOfMeasure`: one stable unit token that owns which `Quantity` scale is admissible
- `WeightedAverageCostingMath`: one pure weighted-average arithmetic owner that combines exact
  `Quantity` and exact `Money` without publishing one generic decimal seam

`CurrencyUnit`, `Money`, `PositiveMoney`, and `MonetaryAmount` are only for posted monetary
facts, derived balances, and machine/public projections of those same monetary facts.

`Quantity` and `UnitOfMeasure` are only for exact quantity-bearing facts. They do not create one
generic decimal toolkit, and they do not own tax, FX, percentage, or ratio semantics.

`CurrencyUnit` resolves from FinGrind's checked-in currency-unit registry snapshot, not from the
host JVM's mutable runtime currency table. That keeps durable exact-money semantics under
repository control.

They are **not** a generic decimal toolkit.

## Concepts That Must Not Reuse `Money`

The following future concepts must not be represented with `Money`, `PositiveMoney`, or
`MonetaryAmount`:

- tax rates
- percentages
- exchange rates
- discount rates
- allocation ratios
- interest rates
- any other non-monetary decimal factor

Why:

- money denotes one quantity in one currency unit
- rates and ratios denote relationships, not posted amounts
- tax and FX semantics carry their own invariants, grammar, persistence rules, and lifecycle
  meaning
- overloading money for those meanings would collapse separate bounded contexts into one false
  shared type

## Required Shape For Future Decimal Domains

When FinGrind later introduces tax, FX, discounts, or related domains, each new decimal-bearing
concept must arrive as its own closed type in the context that owns the business meaning.

The minimum required design questions are:

1. What is the canonical name in the ubiquitous language?
2. Which bounded context owns it?
3. Is it a percentage, a ratio, a quoted exchange rate, or some other relationship concept?
4. What is the exact representation?
5. What input grammar is accepted?
6. What arithmetic is legal?
7. What durable encoding is authoritative?
8. What machine-facing contract shape is published?

Do not answer those questions later with a generic `BigDecimal` field and a comment.

## Implemented And Remaining Type Split

The current repository now publishes one exported non-money exact quantity line:

- `Quantity`: one exact scaled quantity with no embedded unit meaning
- `UnitOfMeasure`: one stable per-account owner for the admissible `Quantity` scale
- `WeightedAverageCostingMath`: one pure arithmetic owner that consumes exact quantity and exact
  money without turning either into one generic decimal abstraction; its admitted positive pools
  stay above one currency minor unit per smallest quantity increment so half-up disposal rounding
  cannot strand positive quantity against a zero carrying-cost pool

The next decimal-bearing concepts must arrive as separate types in their owning contexts rather
than as a shared generic decimal seam:

- `TaxRate`: one tax-policy-owned rate with jurisdictional meaning and effective scope
- `QuotedExchangeRate`: one FX-context-owned quote with source, date, and directional meaning
- `AllocationRatio`: one exact non-money ratio for pro-rating or apportionment

## Guardrail

Product Java surfaces must remain free of generic `BigDecimal` domain seams. FinGrind's
repository-level verifier `scripts/test-no-product-bigdecimal.sh` enforces that rule across:

- `core`
- `contract`
- `executor`
- `cli`
- `sqlite`
- `report-pdf`
- Jazzer production support code

That guardrail exists so future decimal work starts by naming the business concept first instead of
quietly smuggling generic decimal state back into the product model.
