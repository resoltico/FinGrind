---
afad: "4.0"
version: "0.34.0"
domain: CORE
updated: "2026-05-10"
route:
  keywords: [fingrind, core, money, positive-money, journal, balance-side, provenance, reversal, account-code, account-name, normal-balance, currency-unit, idempotency, minor-units]
  questions: ["what core value types does fingrind expose", "how does a journal entry work in fingrind", "where do the core accounting invariants live", "what bookkeeping primitives are in the fingrind core module"]
---

# Core API Reference

This file documents the exported `core` module only. `core` owns the accounting vocabulary and
invariants that higher layers reuse. It does not own protocol metadata, CLI options, request
templates, storage access, or report rendering.

## `AccountCode`

`AccountCode` is the jurisdiction-agnostic account identifier carried by journal lines and account
registries.

```java
public record AccountCode(String value)
```

- Purpose: represent one book-local account without hard-coding a chart-of-accounts scheme
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `AccountName`

`AccountName` is the non-blank display name stored in the account registry.

```java
public record AccountName(String value)
```

- Purpose: keep account display text typed instead of using raw strings
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `ActorId`

`ActorId` is the stable identifier for the caller recorded in request provenance.

```java
public record ActorId(String value)
```

- Purpose: keep actor identity explicit in request provenance
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `ActorType`

`ActorType` classifies the actor that initiated one posting request.

```java
public enum ActorType implements WireValue {
  HUMAN,
  SYSTEM,
  AGENT
}
```

- Purpose: distinguish human, system, and agent callers without free-form strings
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `BalanceSide`

`BalanceSide` is the side of one computed net balance, including the balanced zero state.

```java
public enum BalanceSide implements WireValue {
  DEBIT,
  CREDIT,
  ZERO
}
```

- Purpose: represent computed balance polarity for grouped balances and running ledgers
- Wire contract: `wireValue()` and `fromWireValue(...)` own the stable public vocabulary

## `CausationId`

`CausationId` links one request to its immediate cause.

```java
public record CausationId(String value)
```

- Purpose: preserve causal lineage in request provenance
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `CommandId`

`CommandId` is the stable caller-visible identifier for one posting command.

```java
public record CommandId(String value)
```

- Purpose: identify one logical command at the request boundary
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `CommittedProvenance`

`CommittedProvenance` is the durable audit metadata created when a posting is committed.

```java
public record CommittedProvenance(
    RequestProvenance requestProvenance,
    Instant recordedAt,
    SourceChannel sourceChannel)
```

- Purpose: carry accepted caller provenance plus commit-time audit metadata
- Validation: rejects `null` request provenance, `recordedAt`, and `sourceChannel`

## `CorrelationId`

`CorrelationId` links one request to a broader workflow.

```java
public record CorrelationId(String value)
```

- Purpose: correlate related commands without overloading `CommandId`
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `CurrencyUnit`

`CurrencyUnit` is the canonical ISO-backed currency owner used by `Money` and `PositiveMoney`.

```java
public final class CurrencyUnit
```

- Purpose: keep currency identity and exact minor-unit scale structural instead of treating them as
  free-form strings plus formatter policy
- Construction: `CurrencyUnit.of(String)` resolves one supported ISO 4217 code from FinGrind's
  pinned repository-owned currency-unit registry snapshot and publishes its exact
  `minorUnitScale()`
- Validation: rejects whitespace-padded, non-uppercase, non-ISO, or unsupported currency-unit
  codes, and rejects units whose published scale falls outside FinGrind's supported exact-money
  range of `0..9`

## `EffectiveDateRange`

`EffectiveDateRange` is the shared-kernel effective-date filter reused by public bookkeeping
queries, public workflow assertions, and executor-owned bookkeeping criteria.

```java
public sealed interface EffectiveDateRange
```

- Purpose: keep date-range filtering semantics in the shared kernel instead of treating them as
  contract-owned DTO shape
- Variants: `unbounded`, `from`, `to`, and `bounded`
- Validation: bounded ranges reject a lower bound after the upper bound

## `InteractionLimits`

`InteractionLimits` is the shared-kernel owner for page-size defaults and workflow step limits that
both the public contract and the local executor contexts enforce.

```java
public final class InteractionLimits
```

- Purpose: keep paging defaults, paging hard limits, and ledger-plan step limits in one shared
  owner instead of duplicating them between public protocol helpers and local executor models
- Current contract: `REQUEST_PAYLOAD_MAX_BYTES = 1048576`, `PAGE_LIMIT_MIN = 1`,
  `DEFAULT_PAGE_LIMIT = 50`, `PAGE_LIMIT_MAX = 200`, `LEDGER_PLAN_STEP_MAX = 100`

## `IdempotencyKey`

`IdempotencyKey` is the caller-supplied duplicate-submission identity.

```java
public record IdempotencyKey(String value)
```

- Purpose: scope duplicate detection inside one selected book
- Validation: rejects `null` and blank text after stripping surrounding whitespace

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
  `canonicalDecimal()` projects the stable human/machine decimal form at the currency unit's scale
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

## `ReversalReason`

`ReversalReason` is the human-readable reason recorded for a reversal posting.

```java
public record ReversalReason(String value)
```

- Purpose: preserve operator-supplied reversal narrative in typed form
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `ReversalReference`

`ReversalReference` is the additive link from a new posting to an earlier committed posting.

```java
public record ReversalReference(PostingId priorPostingId)
```

- Purpose: model reversal lineage outside the journal grammar
- Validation: rejects `null` prior posting id

## `SourceChannel`

`SourceChannel` is the singleton owner of the current public committed-entry surface token.

```java
public final class SourceChannel implements WireValue
```

- Purpose: record committed ingress explicitly without pretending the current public line has an
  extensible source-channel taxonomy
- Current scope: only the singleton `CLI` instance is currently supported
- Wire contract: `CLI.wireValue()`, `wireValues()`, `values()`, and `fromWireValue(...)` own the
  stable public vocabulary

## `WireValue`

`WireValue` is the explicit contract for stable machine-facing enum vocabulary owned by FinGrind.

```java
public interface WireValue {
  String wireValue();
  static <E extends Enum<E> & WireValue> List<String> wireValues(Class<E> enumType)
  static <E extends Enum<E> & WireValue> E fromWireValue(
      Class<E> enumType, String wireValue, String unsupportedValueLabel)
}
```

- Purpose: make stable JSON and protocol tokens a compile-time contract instead of a reflective
  convention
- Scope: implemented by exported enums whose public wire form must remain decoupled from Java enum
  constant names
- Parsing: `wireValues(...)` exposes the declaration-order public vocabulary, and
  `fromWireValue(...)` resolves one stable token through the shared cached enum-vocabulary owner
  instead of forcing each enum to reimplement its own lookup logic
