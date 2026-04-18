---
afad: "3.5"
version: "0.16.0"
domain: CORE
updated: "2026-04-17"
route:
  keywords: [fingrind, core, protocol, operation-catalog, journal, money, positive-money, provenance, reversal, account-code, account-name, normal-balance, currency-code, idempotency]
  questions: ["what core value types does fingrind expose", "how does a journal entry work in fingrind", "how are request and committed provenance separated in fingrind", "where do the core accounting invariants live"]
---

# Core API Reference

## `ProtocolCatalog`

`ProtocolCatalog` is the contract-owned registry for public operation and model metadata.

```java
public final class ProtocolCatalog
```

- Purpose: own operation ids, aliases, display labels, execution modes, summaries, help usage,
  quick-start examples, hard book-model facts, preflight facts, currency facts, and shared status
  lists before executor or CLI rendering
- Surface: `operations()`, `operation(...)`, `operationName(...)`, `findByToken(...)`,
  `operationNames(...)`, and global fact accessors
- Contract: CLI parsing, `help`, `capabilities`, rejection text, paging defaults, docs linting, and
  Jazzer support consume this registry instead of reauthoring command ids

## `ProtocolOperation`

`ProtocolOperation` is one structured command descriptor in the contract protocol catalog.

```java
public record ProtocolOperation(
    OperationId id,
    OperationCategory category,
    String displayLabel,
    List<String> aliases,
    List<String> options,
    ExecutionMode executionMode,
    String usage,
    String analysisSummary,
    List<String> examples)
```

- Purpose: keep operation metadata machine-readable before it is serialized by `MachineContract`
- Validation: rejects `null` fields and defensively copies list fields

## `OperationId`

`OperationId` is the canonical enum of public FinGrind operation identifiers.

```java
public enum OperationId
```

- Members: `HELP`, `VERSION`, `CAPABILITIES`, `PRINT_REQUEST_TEMPLATE`, `PRINT_PLAN_TEMPLATE`,
  `GENERATE_BOOK_KEY_FILE`, `OPEN_BOOK`, `REKEY_BOOK`, `DECLARE_ACCOUNT`, `INSPECT_BOOK`,
  `LIST_ACCOUNTS`, `GET_POSTING`, `LIST_POSTINGS`, `ACCOUNT_BALANCE`, `EXECUTE_PLAN`,
  `PREFLIGHT_ENTRY`, `POST_ENTRY`
- Surface: `wireName()` returns the stable CLI and wire identifier

## `OperationCategory`

`OperationCategory` groups public operations for the capabilities surface.

```java
public enum OperationCategory {
  DISCOVERY,
  ADMINISTRATION,
  QUERY,
  WRITE
}
```

- Purpose: drive `capabilities.discoveryCommands`, `administrationCommands`, `queryCommands`, and
  `writeCommands` from one enum-backed catalog

## `ExecutionMode`

`ExecutionMode` describes the public output mode for one operation.

```java
public enum ExecutionMode
```

- Members: `JSON_ENVELOPE`, `RAW_JSON`
- Surface: `wireValue()` returns values such as `json-envelope` and `raw-json`

## `ProtocolLimits`

`ProtocolLimits` owns shared public query limits.

```java
public final class ProtocolLimits
```

- Constants: `PAGE_LIMIT_MIN = 1`, `PAGE_LIMIT_MAX = 200`, `DEFAULT_PAGE_LIMIT = 50`,
  `PAGE_OFFSET_MIN = 0`, `DEFAULT_PAGE_OFFSET = 0`
- Consumers: contract query models, CLI argument defaults, help rendering, and Jazzer support

## `ProtocolOptions`

`ProtocolOptions` owns canonical public CLI option spellings.

```java
public final class ProtocolOptions
```

- Purpose: prevent parser, help, request-input descriptors, and passphrase-source models from
  carrying divergent option strings
- Surface: constants for book, passphrase, request, posting, account, date, limit, and offset
  options plus helpers for rendered passphrase and pagination syntax

## `BookModelFacts`

`BookModelFacts` is the structured contract-owned description of the protected-book model.

```java
public record BookModelFacts(
    String boundary,
    String entityScope,
    String filesystem,
    String credential,
    String initialization,
    String accountRegistry,
    String migration,
    String currencyScope)
```

- Purpose: keep hard book-model limitations in core before `help` or `capabilities` render them

## `CurrencyFacts`

`CurrencyFacts` is the structured contract-owned description of currency support.

```java
public record CurrencyFacts(String scope, String multiCurrencyStatus, String description)
```

- Purpose: publish the current single-currency-per-entry model without duplicating text in CLI code

## `PreflightFacts`

`PreflightFacts` is the structured contract-owned description of preflight semantics.

```java
public record PreflightFacts(String semantics, boolean commitGuarantee, String description)
```

- Purpose: publish advisory preflight behavior once for help, capabilities, and user-facing docs

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

`JournalEntry` is the balanced journal grammar that crosses the contract write boundary.

```java
public record JournalEntry(LocalDate effectiveDate, List<JournalLine> lines)
```

- Purpose: carry the accounting body of one posting request
- Normalization: defensively copies `lines`
- Validation: rejects `null` effective date, empty lines, entries that do not contain both a debit
  and a credit side, mixed currencies, and unbalanced totals
- Contract impact: the CLI machine contract advertises this invariant as
  `currencyModel.scope = single-currency-per-entry`

## `JournalLine`

`JournalLine` is one debit or credit line inside a journal entry.

```java
public record JournalLine(AccountCode accountCode, EntrySide side, PositiveMoney amount)
```

- Purpose: keep account, side, and amount explicit on every line
- Compatibility: accepts a general `Money` value through a convenience overload, then upgrades it
  into `PositiveMoney`
- Validation: rejects `null` fields and requires a strictly positive amount

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

`Money` is an exact non-negative decimal amount in one declared currency.

```java
public record Money(CurrencyCode currencyCode, BigDecimal amount)
```

- Purpose: preserve exact decimal semantics without floating-point behavior
- Normalization: strips trailing zeroes and normalizes negative scale to zero
- Validation: rejects `null` fields and negative amounts
- Usage: reused by balance and reporting surfaces that legitimately need zero-valued totals

## `PositiveMoney`

`PositiveMoney` is an exact strictly positive amount in one declared currency.

```java
public record PositiveMoney(Money value)
```

- Purpose: make the journal-line positivity invariant structural instead of splitting it between
  `Money` and `JournalLine`
- Construction: accepts either a fully formed `Money` value or direct currency-and-amount inputs
- Validation: rejects zero-valued amounts with the canonical journal-line error

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
    Optional<CorrelationId> correlationId)
```

- Purpose: carry the accepted request identity without commit-time audit fields
- Optionality: callers pass `Optional.empty()` explicitly for absent `correlationId`
- Validation: rejects `null` required fields and `null` optionals

## `ReversalReason`

`ReversalReason` is the human-readable reason recorded for a reversal posting.

```java
public record ReversalReason(String value)
```

- Purpose: preserve the operator-supplied reason carried by reversal posting lineage
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
- Current scope: only `CLI` is currently supported
