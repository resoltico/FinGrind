---
afad: "3.5"
version: "0.9.0"
domain: CORE
updated: "2026-04-13"
route:
  keywords: [fingrind, core, journal, money, provenance, reversal, account-code, account-name, normal-balance, currency-code, idempotency]
  questions: ["what core value types does fingrind expose", "how does a journal entry work in fingrind", "how are request and committed provenance separated in fingrind"]
---

# Core API Reference

## `AccountCode`

`AccountCode` is the jurisdiction-agnostic account identifier carried by one journal line.

```java
public record AccountCode(String value)
```

- Purpose: name the account on one line without imposing a country-specific chart shape
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `AccountName`

`AccountName` is the non-blank display name stored in the account registry.

```java
public record AccountName(String value)
```

- Purpose: keep the account registry human-readable without leaving display names as raw strings
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `ActorId`

`ActorId` is the stable identifier for the actor that submitted one posting request.

```java
public record ActorId(String value)
```

- Purpose: keep actor identity explicit in request provenance
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `ActorType`

`ActorType` classifies the actor that initiated one posting request.

```java
public enum ActorType {
  USER,
  SYSTEM,
  AGENT
}
```

- Purpose: distinguish user, system, and agent callers without magic strings

## `CausationId`

`CausationId` is the stable identifier linking one posting request to its immediate cause.

```java
public record CausationId(String value)
```

- Purpose: preserve immediate cause lineage in request provenance
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

- Purpose: carry the accepted request provenance plus commit-time audit fields
- Validation: rejects `null` request provenance, `recordedAt`, and `sourceChannel`

## `CorrelationId`

`CorrelationId` is the stable identifier linking one posting request to a broader operation.

```java
public record CorrelationId(String value)
```

- Purpose: correlate multiple commands inside one larger workflow
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `CurrencyCode`

`CurrencyCode` is the ISO-style currency identifier used by `Money`.

```java
public record CurrencyCode(String value)
```

- Purpose: make currency explicit and canonical
- Normalization: strips whitespace and uppercases with `Locale.ROOT`
- Validation: accepts exactly three uppercase ASCII letters

## `IdempotencyKey`

`IdempotencyKey` is the caller-supplied duplicate-submission identity.

```java
public record IdempotencyKey(String value)
```

- Purpose: scope duplicate rejection inside one selected book
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `JournalEntry`

`JournalEntry` is the balanced journal grammar that crosses the application write boundary.

```java
public record JournalEntry(LocalDate effectiveDate, List<JournalLine> lines)
```

- Purpose: carry the accounting body of one posting request
- Normalization: defensively copies `lines`
- Validation: rejects `null` effective date, empty lines, mixed currencies, and unbalanced totals
- Contract impact: the CLI machine contract advertises this invariant as
  `currencyModel.scope = single-currency-per-entry`

## `JournalLine`

`JournalLine` is one debit or credit line inside a journal entry.

```java
public record JournalLine(AccountCode accountCode, EntrySide side, Money amount)
```

- Purpose: keep account, side, and amount explicit on every line
- Validation: rejects `null` fields and zero monetary amount

## `JournalLine.EntrySide`

`EntrySide` is the closed set of journal equation sides.

```java
public enum EntrySide {
  DEBIT,
  CREDIT
}
```

- Purpose: make line polarity explicit in the type system

## `Money`

`Money` is an exact decimal amount in one declared currency.

```java
public record Money(CurrencyCode currencyCode, BigDecimal amount)
```

- Purpose: preserve exact decimal semantics without floating-point behavior
- Normalization: strips trailing zeroes and normalizes negative scale to zero
- Validation: rejects `null` fields and negative amounts

## `NormalBalance`

`NormalBalance` is the side that increases a declared account.

```java
public enum NormalBalance {
  DEBIT,
  CREDIT
}
```

- Purpose: make the account registry explicit enough for validation and future trial-balance style reads
- Scope: bookkeeping-native and legislation-agnostic

## `PostingId`

`PostingId` is the stable identifier for one committed posting fact.

```java
public record PostingId(String value)
```

- Purpose: name one durable posting independently of request idempotency
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `RequestProvenance`

`RequestProvenance` is the caller-supplied provenance accepted at the posting request boundary.

```java
public record RequestProvenance(
    ActorId actorId,
    ActorType actorType,
    CommandId commandId,
    IdempotencyKey idempotencyKey,
    CausationId causationId,
    Optional<CorrelationId> correlationId,
    Optional<ReversalReason> reason)
```

- Purpose: carry the accepted request identity and reversal reason without commit-time audit fields
- Normalization: `null` optional fields become `Optional.empty()`
- Validation: rejects `null` required fields

## `ReversalReason`

`ReversalReason` is the human-readable reason recorded for a reversal posting.

```java
public record ReversalReason(String value)
```

- Purpose: preserve the operator-supplied reason for a reversal
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `ReversalReference`

`ReversalReference` is the additive link from a new posting fact to an earlier committed posting.

```java
public record ReversalReference(PostingId priorPostingId)
```

- Purpose: model reversal lineage outside the journal-entry grammar
- Validation: rejects `null` prior posting id

## `SourceChannel`

`SourceChannel` is the operating surface through which one posting request entered FinGrind.

```java
public enum SourceChannel {
  CLI
}
```

- Purpose: record the committed ingress channel explicitly
- Current scope: only `CLI` is supported in the current hard-break phase
