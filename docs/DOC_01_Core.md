---
afad: "3.5"
version: "0.25.0"
domain: CORE
updated: "2026-04-23"
route:
  keywords: [fingrind, core, money, positive-money, journal, balance-side, provenance, reversal, account-code, account-name, normal-balance, currency-code, idempotency]
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

## `CurrencyCode`

`CurrencyCode` is the canonical three-letter currency identifier used by `Money`.

```java
public record CurrencyCode(String value)
```

- Purpose: keep currency explicit and normalized
- Normalization: strips whitespace and uppercases with `Locale.ROOT`
- Validation: accepts exactly three uppercase ASCII letters

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
  and a credit side, mixed currencies, and unbalanced totals

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

`Money` is an exact non-negative decimal amount in one declared currency.

```java
public record Money(CurrencyCode currencyCode, BigDecimal amount)
```

- Purpose: preserve exact decimal semantics without floating-point behavior
- Normalization: strips trailing zeroes and normalizes negative scale to zero
- Validation: rejects `null` fields and negative amounts
- Usage: reused by balances and reports that legitimately need zero-valued totals

## `PositiveMoney`

`PositiveMoney` is an exact strictly positive amount in one declared currency.

```java
public record PositiveMoney(Money value)
```

- Purpose: make the journal-line positivity invariant structural instead of duplicating it
- Construction: accepts either a fully formed `Money` or direct currency-and-amount inputs
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

`SourceChannel` is the operating surface through which one posting entered FinGrind.

```java
public enum SourceChannel implements WireValue {
  CLI
}
```

- Purpose: record committed ingress explicitly
- Current scope: only `CLI` is currently supported
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

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
