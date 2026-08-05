---
afad: "5.0.1"
version: "0.62.1"
domain: ADR_REALIZED_FOREIGN_EXCHANGE
updated: "2026-08-05"
route:
  keywords: [fingrind, realized foreign exchange, foreign-currency obligation, FX settlement, realized gain, realized loss]
  questions: ["how does fingrind record realized foreign exchange", "what owns a foreign-currency obligation", "how is an FX settlement derived"]
---

# ADR: Realized Foreign Exchange

## Status

Published in `0.61.0`. Typed foreign-currency receivable origination, one-time settlement, derived realized gain or loss, compensating reversals, and realized-foreign-exchange-register reporting are one public capability with one owned lifecycle boundary.

The release boundary is limited to one foreign-currency receivable and one active settlement per retained obligation while all journal lines remain in the book's functional currency. Rate sourcing, remeasurement, translation of foreign operations, hedging, and mixed-currency journal lines remain outside this context.

## Boundary And Language

The Realized Foreign Exchange context owns a **foreign-currency obligation**, its retained functional-currency **carrying amount**, a one-time **settlement**, and the derived **realized gain or loss**. It consumes an FX quote retained with the originating or settlement posting but does not own rate sourcing, remeasurement, translation of foreign operations, hedging, or multi-currency journal lines.

Its aggregate is one `ForeignCurrencyObligation`, identified by `ForeignCurrencyObligationId`. The executor verifies a settlement against the retained transaction quantity and carrying amount, derives the realized result, and prevents duplicate settlement. SQLite persists immutable origin and settlement facts plus explicit compensating-reversal lineage.

The context is deliberately narrower than IAS 21. It is not an assertion of IAS 21 compliance; the [IFRS Foundation's IAS 21 material](https://www.ifrs.org/content/dam/ifrs/publications/pdf-standards/english/2021/issued/part-a/ias-21-the-effects-of-changes-in-foreign-exchange-rates.pdf) is the primary reference for the broader accounting domain.

## Commands And Reports

The typed commands are `record-foreign-currency-obligation` and `record-realized-foreign-exchange-settlement`. The dedicated `realized-foreign-exchange-register` exposes original transaction amount, functional carrying amount, settlement amount, derived result, quote attribution, and reversal state.

## Invariants

- An obligation has one retained transaction currency, functional currency, and carrying amount.
- Settlement cannot precede origination and occurs once while active.
- The derived gain or loss reconciles exactly to carrying amount and functional settlement amount.
- Reversals are explicit compensating facts; immutable historical facts remain visible.

## Publication Condition

Publication requires typed commands, reversal-aware storage that binds settlement facts to the retained transaction and functional foreign-exchange facts, executor resolution, request contracts and templates, register reporting, end-to-end tests, and protected-book format `57`. Non-current book formats, whether older or newer, are rejected rather than upgraded in place. The release-boundary contract test verifies this condition against the public operation registry and this ADR.
