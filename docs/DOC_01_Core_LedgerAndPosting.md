---
afad: "4.0"
version: "0.58.0"
domain: CORE
updated: "2026-06-29"
route:
  keywords: [fingrind, core, journal, money, positive-money, posting-kind, posting-origin-kind, posting-coverage, reporting-period, request-provenance, currency-balance, normal-balance]
  questions: ["how does a journal entry work in fingrind", "where are money and posting primitives documented", "which doc file covers RequestProvenance", "what ledger primitives are in the fingrind core module"]
---

# Core Ledger And Posting Reference

This companion file continues the exported `core` reference for journal grammar, exact-money
types, durable posting vocabulary, bounded reporting periods, and caller-supplied request
provenance.

Account doctrine, book identity, temporal parsing, and shared kernel primitives remain in
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
  SALE,
  EXPENSE,
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

## `BookkeepingEntry`

`BookkeepingEntry` is the caller-authored closed set that the public write surface accepts before
FinGrind materializes one canonical balanced journal entry.

```java
public sealed interface BookkeepingEntry
```

- Purpose: model the typed business-event families and the raw direct-journal fallback without a
  second write kernel
- Current variants: `DirectJournal`, `Sale`, `Expense`, `OwnerContribution`, `OwnerWithdrawal`,
  `OpeningPosition`, and `Reversal`
- Surface: `entryKind()` exposes the caller-authored public variant and `journalEntry()` derives
  the exact `JournalEntry` that the write kernel validates and commits
- Boundary: callers may bypass the typed business-event commands only through `DirectJournal`; no
  parallel recipe taxonomy survives on the public write surface

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
  SALE,
  EXPENSE,
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
    ActorId actorId,
    ActorType actorType,
    CommandId commandId,
    IdempotencyKey idempotencyKey,
    CausationId causationId,
    Optional<CorrelationId> correlationId)
```

- Purpose: carry caller identity and lineage without commit-time audit fields
- Validation: rejects `null` required fields and `null` optionals
- Optionality: callers pass `Optional.empty()` explicitly for absent `correlationId`

Source-document evidence identifiers, reversal lineage primitives, committed source-channel
vocabulary, and the shared `WireValue` contract continue in
[DOC_01_Core_EvidenceAndWire.md](./DOC_01_Core_EvidenceAndWire.md).
