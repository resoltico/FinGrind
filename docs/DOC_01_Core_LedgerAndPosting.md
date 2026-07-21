---
afad: "5.0.1"
version: "0.61.0"
domain: CORE
updated: "2026-07-21"
route:
  keywords: [fingrind, core, journal, money, positive-money, posting-kind, posting-origin-kind, posting-coverage, reporting-period, request-provenance, currency-balance, normal-balance]
  questions: ["how does a journal entry work in fingrind", "where are money and posting primitives documented", "which doc file covers RequestProvenance", "what ledger primitives are in the fingrind core module"]
---

# Core Ledger And Posting Reference

This companion file continues the exported `core` reference for journal grammar, exact-money
types, durable posting vocabulary, bounded reporting periods, and caller-supplied request
provenance.

Book doctrine remains in [DOC_01_Core_BookDoctrine.md](./DOC_01_Core_BookDoctrine.md); book
identity, temporal parsing, and the remaining shared-kernel primitives remain in
[DOC_01_Core.md](./DOC_01_Core.md).

## `JournalEntry`

`JournalEntry` is the balanced journal grammar that crosses the write boundary.

```java
public record JournalEntry(LocalDate effectiveDate, List<JournalLine> lines)
```

- Purpose: carry one complete accounting event with its effective date and lines
- Normalization: defensively copies `lines`
- Validation: rejects `null` effective date, empty lines, entries that do not contain both a debit
  and a credit side, mixed currencies, and unbalanced totals; validation now reports every detected
  journal-entry violation in one deterministic pass

## `JournalEntryValidationException`

`JournalEntryValidationException` is the aggregated request-validation failure raised when one
journal entry violates multiple grammar rules at once.

```java
public final class JournalEntryValidationException extends IllegalArgumentException
```

- Purpose: carry every detected journal-entry violation in one ordered failure object so callers can
  repair the whole entry before retrying
- Surface: `violations()` returns the full deterministic violation list, and `getMessage()` joins
  that list into one caller-facing summary

## `JournalLine`

`JournalLine` is one debit or credit line inside a journal entry.

```java
public record JournalLine(AccountCode accountCode, EntrySide side, PositiveMoney amount)
```

- Purpose: keep account, side, and strictly positive amount explicit on every line
- Compatibility: accepts a general `Money` through a convenience constructor, then upgrades it to
  `PositiveMoney`
- Validation: rejects `null` fields

## `JournalLine.EntrySide`

`EntrySide` is the closed set of journal-equation sides.

```java
public enum EntrySide implements WireValue {
  DEBIT,
  CREDIT
}
```

- Purpose: make line polarity explicit in the type system and wire vocabulary
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `AccountRole`

`AccountRole` is the classifier-owned role vocabulary derived from one declared account's type and
taxonomy.

```java
public enum AccountRole implements WireValue
```

- Purpose: distinguish anchor roles such as `CASH`, `RECEIVABLE`, `PAYABLE`, `REVENUE`,
  `EXPENSE`, `EQUITY_CONTRIBUTED`, and `EQUITY_DRAWS` from tolerated adjunct roles such as
  `SETTLEMENT_ADJUNCT` and `AUX`
- Derivation: `from(AccountType, AccountTaxonomy)` is the canonical owner that maps declared
  account truth onto classifier semantics
- Current doctrine: `SETTLEMENT_ADJUNCT` covers settlement-only lines such as sales discounts,
  settlement fees, and bad-debt write-offs, while `AUX` covers non-trade balance-sheet lines and
  finance-income or finance-expense lines such as FX gains and losses so those accounts cannot
  anchor a typed operational event by themselves
- Surface: `anchorRole()` exposes whether the role participates in typed-event anchoring, and
  `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable wire vocabulary

## `AnchorEntry`

`AnchorEntry` is one direction-aware anchor-role incidence inside the journal classifier's
signature.

```java
public record AnchorEntry(AccountRole role, EntrySide side)
```

- Purpose: capture the role-plus-side fact that lets the classifier distinguish settled sales,
  credit sales, receipts, payments, and other typed business-event signatures
- Validation: rejects non-anchor roles such as `SETTLEMENT_ADJUNCT` and `AUX`

## `EvidenceClass`

`EvidenceClass` is the coarse retained-evidence vocabulary carried into resolved-journal
semantics.

```java
public enum EvidenceClass implements WireValue {
  CASH_SETTLEMENT,
  INVOICE,
  OTHER
}
```

- Purpose: keep evidence-class validation typed and closed instead of re-deriving it from
  source-document text at every semantic rule
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `EconomicEventClass`

`EconomicEventClass` is the total classifier outcome vocabulary for one resolved journal.

```java
public enum EconomicEventClass implements WireValue
```

- Members: `SETTLED_SALE`, `CREDIT_SALE`, `SETTLED_EXPENSE`, `CREDIT_EXPENSE`,
  `AR_SETTLEMENT`, `AP_SETTLEMENT`, `OWNER_CONTRIBUTION`, `OWNER_WITHDRAWAL`, `OPENING`,
  `REVERSAL`, `COMPOUND_OPERATIONAL`, and `ADJUSTMENT`
- Purpose: keep the event-class decision explicit after FinGrind expands one caller-authored
  business entry or raw direct journal into the canonical journal boundary
- Surface: `typedSingleton()` distinguishes published singleton events from
  `COMPOUND_OPERATIONAL` and `ADJUSTMENT`, while `wireValue()`, `wireValues()`, and
  `fromWireValue(...)` own the stable wire vocabulary

## `StructuralContext`

`StructuralContext` carries the non-account-shape facts that win journal classification outright.

```java
public record StructuralContext(
    Optional<PostingId> reversesPriorPosting, boolean adoptionOpeningEntry)
```

- Purpose: keep reversal lineage and the opening-only adoption window explicit instead of
  inferring them from journal lines
- Surface: `ordinary()` returns the default non-structural context used by ordinary operational
  and adjustment entries

## `ClassificationResult`

`ClassificationResult` is the total classifier outcome retained for one resolved journal.

```java
public record ClassificationResult(
    EconomicEventClass eventClass,
    Set<AnchorEntry> anchorSignature,
    Set<EconomicEventClass> containedTypedEvents,
    boolean hasCashLine,
    EvidenceClass evidenceClass,
    StructuralContext structural)
```

- Purpose: preserve the final event class together with the exact anchor signature, any contained
  typed events, cash-line presence, evidence class, and structural context that produced it
- Boundary: this is the semantic classification fact consumed by raw-admission, evidence, and
  typed-verb validation; callers do not recompute the classifier from projected payload fragments

## `JournalClassifier`

`JournalClassifier` is the total semantic classifier over anchor-role incidence, retained evidence,
and structural context.

```java
public final class JournalClassifier
```

- Purpose: classify one resolved journal into a structural singleton, exact typed singleton,
  `COMPOUND_OPERATIONAL`, or `ADJUSTMENT` from one canonical owner
- Ownership: `classify(...)` derives the anchor signature and cash-line fact from the supplied
  journal lines through the owning account-role resolution path instead of accepting those semantic
  fragments from an external helper
- Public seam: `JournalClassifier.AccountRoleLookup` is the narrow role-resolution callback that
  callers provide so classifier ownership stays inside `JournalClassifier` instead of duplicating
  anchor-signature or cash-line derivation elsewhere
- Current doctrine: exact typed signatures admit settled sale, credit sale, settled expense,
  credit expense, accounts-receivable settlement, accounts-payable settlement, owner
  contribution, and owner withdrawal; non-exact signatures that still contain one or more typed
  pairs become `COMPOUND_OPERATIONAL`
- Output: `classify(...)` returns one full `ClassificationResult`, not just one bare enum

## `Money`

`Money` is the canonical exact non-negative posted-money value encoded in one currency unit's
minor units.

```java
public final class Money implements Comparable<Money>
```

- Purpose: close the money invariant inside the shared kernel instead of exposing `BigDecimal`
  state through the public API
- Construction: `ofMinorUnits(...)`, `zero(...)`, and `parse(...)` are the only public creation
  seams
- Grammar: parsing accepts only canonical unsigned plain-decimal text, rejects exponent notation,
  rejects redundant leading zeroes, and enforces the selected currency unit's exact fractional
  scale
- Representation: the authoritative stored state is `minorUnits()` plus `currencyUnit()`, while
  `canonicalDecimal()` projects the stable operator/machine decimal form at the currency unit's scale
- Bounds: parsing and arithmetic reject values outside FinGrind's supported exact minor-unit range
- Usage: reused by balances and reports that legitimately need zero-valued totals
- Boundary: `Money` is only for posted monetary facts; future tax rates, percentages, exchange
  rates, and allocation ratios must enter as separate exact types instead of reusing this model.
  See [DOC_01_DecimalBoundaries.md](./DOC_01_DecimalBoundaries.md).

## `CurrencyBalance`

`CurrencyBalance` is one shared per-currency grouped balance bucket derived from debit and credit
totals.

```java
public final class CurrencyBalance
```

- Purpose: keep grouped per-currency balance math in the shared kernel instead of embedding that
  shape in one protocol context
- Construction: `CurrencyBalance.ofTotals(...)` is the canonical factory; callers do not supply
  `netAmount` or `balanceSide` directly
- Invariant: the balance side and absolute net amount are derived from the debit and credit totals,
  so mathematically false grouped balances cannot be forged

## `PositiveMoney`

`PositiveMoney` is the journal-line-specific exact strictly positive money value.

```java
public final class PositiveMoney
```

- Purpose: make journal-line positivity structural instead of duplicating that rule across
  posting, persistence, and workflow surfaces
- Construction: `PositiveMoney.of(Money)` lifts an exact money value after `Money` has already
  closed currency and scale semantics; `parse(...)` is available for direct positive parsing
- Surface: `money()`, `currencyUnit()`, `minorUnits()`, and `canonicalDecimal()` expose the exact
  posted amount without reopening the invariant
- Validation: rejects zero-valued amounts with the canonical journal-line error

## `NormalBalance`

`NormalBalance` is the side that increases a declared account.

```java
public enum NormalBalance implements WireValue {
  DEBIT,
  CREDIT
}
```

- Purpose: keep account-behavior metadata explicit for validation and reporting
- Scope: bookkeeping-native and legislation-agnostic
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `BookkeepingEntryKind`

`BookkeepingEntryKind` is the stable caller-authored write vocabulary for the public bookkeeping
surface.

```java
public enum BookkeepingEntryKind implements WireValue {
  DIRECT_JOURNAL,
  SALE_SETTLED,
  SALE_ON_CREDIT,
  EXPENSE_SETTLED,
  EXPENSE_ON_CREDIT,
  RECEIPT,
  PAYMENT,
  OWNER_CONTRIBUTION,
  OWNER_WITHDRAWAL,
  OPENING_POSITION,
  REVERSAL
}
```

- Purpose: keep the primary business-entry families explicit while preserving one raw
  direct-journal boundary
- Surface: `entryKind` selects the caller-submittable request shape for `post-entry`,
  `preflight-entry`, the typed `record-*` commands, and nested plan posting steps
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `BookkeepingEntry`, `TypedBookkeepingEntry`, And `BookkeepingEntrySurface`

`BookkeepingEntry` is the caller-authored closed set that the public write surface accepts before
FinGrind materializes one canonical balanced journal entry. `TypedBookkeepingEntry` is its closed
economic-event subset, while `BookkeepingEntrySurface` is the shared published view that every
caller-authored variant implements.

```java
public sealed interface BookkeepingEntry
public sealed interface TypedBookkeepingEntry
public interface BookkeepingEntrySurface
```

- Purpose: model the typed business-event families and the raw direct-journal fallback without a
  second write kernel
- Typed events: `SaleSettled`, `SaleOnCredit`, `PurchaseSettled`, `PurchaseOnCredit`, inventory
  maintenance events, `ExpenseSettled`, `ExpenseOnCredit`, `Receipt`, `Payment`,
  `OwnerContribution`, and `OwnerWithdrawal`
- Distinct forms: `DirectJournal` carries a caller-authored journal, `OpeningPosition` establishes
  opening balances, and `Reversal` references a prior posting rather than representing a new
  economic event
- Surface: `entryKind()`, `journalEntry()`, `postingKind()`, `postingOriginKind()`,
  `postingLineage()`, `lines()`, and optional foreign-exchange facts live on
  `BookkeepingEntrySurface`, giving every caller-authored variant one consistent derived view
- Boundary: callers may bypass the typed business-event commands only through `DirectJournal`; no
  parallel recipe taxonomy survives on the public write surface
- Adjuncts: `Receipt` and `Payment` may carry one optional settlement-side adjunct; typed sale,
  purchase, inventory-capitalization, and expense variants may carry one owned tax selector; and
  eligible typed variants may carry foreign-exchange facts before FinGrind expands the canonical
  journal

## `PostingKind`

`PostingKind` is the durable posting-family discriminator for one committed posting.

```java
public enum PostingKind implements WireValue {
  STANDARD,
  OPENING_BALANCE,
  INTERIM_RESULT_SWEEP,
  FISCAL_YEAR_CLOSE
}
```

- Purpose: distinguish ordinary business postings, opening adoption balances, generated
  interim-result sweeps, and generated fiscal-year closes without leaking
  implementation-specific marker strings
- Adoption boundary: caller-authored `OPENING_POSITION` requests project into durable
  `OPENING_BALANCE` postings, and that durable posting family is admitted only before the first
  committed posting enters the book
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary
- Surface: committed postings publish one durable `PostingKind`; caller-authored
  `BookkeepingEntryKind` inputs determine whether FinGrind derives `STANDARD` or
  `OPENING_BALANCE` postings, while close workflows generate internal
  `INTERIM_RESULT_SWEEP` and `FISCAL_YEAR_CLOSE` postings

## `PostingOriginKind`

`PostingOriginKind` is the durable origin vocabulary preserved for one committed posting after
FinGrind has projected a published bookkeeping entry into canonical journal lines.

```java
public enum PostingOriginKind implements WireValue {
  DIRECT_JOURNAL,
  SALE_SETTLED,
  SALE_ON_CREDIT,
  EXPENSE_SETTLED,
  EXPENSE_ON_CREDIT,
  RECEIPT,
  PAYMENT,
  OWNER_CONTRIBUTION,
  OWNER_WITHDRAWAL,
  OPENING_POSITION,
  REVERSAL,
  INTERIM_RESULT_SWEEP,
  FISCAL_YEAR_CLOSE
}
```

- Purpose: preserve the originating published entry family after direct-journal and typed
  business-entry requests have converged into `PostingKind.STANDARD`
- Surface: committed postings and adapter projections expose `postingOriginKind` as a durable fact
  for auditing, analytics, and future adjacent-context integration
- Scope: settled sale, sale on credit, settled expense, expense on credit, receipt, payment,
  owner contribution, owner withdrawal, opening position, reversal, interim-result sweep, and
  fiscal-year close each retain one distinct durable origin kind
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `PostingCoverage`

`PostingCoverage` is the canonical vocabulary for whether one read path includes all posting kinds
or excludes generated transfer postings.

```java
public enum PostingCoverage implements WireValue {
  ALL_POSTING_KINDS,
  NON_CLOSING_POSTINGS
}
```

- Purpose: make report and query coverage explicit instead of burying close-entry inclusion rules
  inside adapter-local filters
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `PostingId`

`PostingId` is the stable identifier for one committed posting.

```java
public record PostingId(String value)
```

- Purpose: identify durable postings independently of request idempotency
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `RequestProvenance`

`RequestProvenance` is the caller-supplied provenance accepted at the posting boundary.

```java
public record RequestProvenance(
    CommandId commandId,
    IdempotencyKey idempotencyKey,
    CausationId causationId,
    Optional<CorrelationId> correlationId)
```

- Purpose: carry stable command lineage without a free-text caller identity or commit-time audit
  fields
- Validation: rejects `null` required fields and `null` optionals
- Optionality: callers pass `Optional.empty()` explicitly for absent `correlationId`

Source-document evidence identifiers, reversal lineage primitives, committed source-channel
vocabulary, and the shared `WireValue` contract continue in
[DOC_01_Core_EvidenceAndWire.md](./DOC_01_Core_EvidenceAndWire.md).
