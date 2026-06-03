---
afad: "4.0"
version: "0.51.0"
domain: CORE
updated: "2026-06-03"
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

## `AccountCodePolicy`

`AccountCodePolicy` is the canonical owner for what account-code text means in current FinGrind
books.

```java
public final class AccountCodePolicy
```

- Purpose: keep account-code meaning explicit instead of letting callers infer numeric ranges,
  parent-child hierarchy, or type semantics from string prefixes
- Current contract: `meaning() == OPAQUE_BOOK_LOCAL_IDENTIFIER` and
  `chartStructure() == PARENT_CHILD_HIERARCHY`
- Validation: `validate(AccountCode, AccountType, AccountRole, AccountTaxonomy)` confirms one
  declared account against the current hierarchical-chart, opaque-identifier policy

## `AccountCodePolicy.Meaning`

`AccountCodePolicy.Meaning` names the semantic contract FinGrind assigns to declared account code
text.

- Purpose: make it explicit that current books treat account codes as opaque identifiers rather
  than taxonomy-carrying ranges
- Current contract: `OPAQUE_BOOK_LOCAL_IDENTIFIER`

## `AccountCodePolicy.ChartStructure`

`AccountCodePolicy.ChartStructure` names the chart topology supported by current FinGrind books.

- Purpose: make the current parent-child chart contract explicit instead of leaving hierarchy
  support to implication
- Current contract: `PARENT_CHILD_HIERARCHY`

## `AccountName`

`AccountName` is the non-blank display name stored in the account registry.

```java
public record AccountName(String value)
```

- Purpose: keep account display text typed instead of using raw strings
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `BookEntityName`

`BookEntityName` is the canonical accounting-entity name stored in one initialized book.

```java
public record BookEntityName(String value)
```

- Purpose: keep book identity explicit instead of leaving initialized books anonymous
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `AccountingBasis`

`AccountingBasis` is the canonical accounting-basis posture carried by one protected book.

```java
public enum AccountingBasis implements WireValue
```

- Purpose: state whether the initialized book follows one cash-basis or other future basis policy
  instead of leaving that premise implicit
- Current contract: `CASH_BASIS`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `AccountingFrameworkPosition`

`AccountingFrameworkPosition` is the canonical reporting-framework posture carried by one
protected book.

```java
public enum AccountingFrameworkPosition implements WireValue
```

- Purpose: keep the current non-statutory management posture explicit instead of implying one
  statutory or standards-compliance claim
- Current contract: `NON_STATUTORY_INTERNAL_MANAGEMENT`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `EntityForm`

`EntityForm` is the canonical legal-form posture carried by one protected book.

```java
public enum EntityForm implements WireValue
```

- Purpose: separate one book's entity-form assumption from chart taxonomy and reporting language
- Current contract: `OWNER_MANAGED_SINGLE_ENTITY`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `BookTemplateId`

`BookTemplateId` is the canonical guided setup template identifier carried by one protected book.

```java
public enum BookTemplateId implements WireValue
```

- Purpose: make the seeded starter-chart family explicit instead of hiding it in executor setup
  code
- Current contract: `OWNER_MANAGED_SERVICE_CASH`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `BookDoctrine`

`BookDoctrine` is the canonical doctrine owner for one protected book's accounting posture.

```java
public record BookDoctrine(
    AccountingKernelProfileId accountingKernelProfileId,
    AccountingBasis accountingBasis,
    AccountingFrameworkPosition accountingFrameworkPosition,
    EntityForm entityForm,
    BookTemplateId bookTemplateId)
```

- Purpose: keep kernel profile, accounting basis, framework posture, entity form, and starter
  template under one persisted doctrine owner
- Validation: rejects `null` doctrine components

## `BookDoctrines`

`BookDoctrines` publishes the built-in doctrine bundles FinGrind can persist today.

```java
public final class BookDoctrines
```

- Purpose: centralize the current built-in doctrine so open-book, discovery, SQLite, CLI, and
  tests all speak one doctrine bundle
- Current built-in doctrine: `INTERNAL_MANAGEMENT_OWNER_MANAGED_CASH_SERVICE`

## `AccountingEvidence`

`AccountingEvidence` is the first-class bundle of source documents and approvals attached to one
posting request or one committed posting fact.

```java
public record AccountingEvidence(
    List<SourceDocumentReference> sourceDocuments,
    List<ApprovalReference> approvals)
```

- Purpose: make evidence a durable accounting fact instead of external operator folklore
- Validation: rejects `null` collections, rejects empty `sourceDocuments`, and rejects duplicate
  source documents or approvals by identifier
- Surface: `sourceDocuments()` and `approvals()` preserve caller order after validation

## `ApprovalId`

`ApprovalId` is the stable identifier for one approval artifact referenced by accounting evidence.

```java
public record ApprovalId(String value)
```

- Purpose: carry one durable approval identity through request, storage, and query surfaces
- Validation: rejects `null`, blank text, and values outside the public approval-id grammar

## `ApprovalType`

`ApprovalType` is the stable public classifier for one approval artifact referenced by accounting
evidence.

```java
public record ApprovalType(String value)
```

- Purpose: distinguish approval evidence kinds without inventing adapter-owned enums prematurely
- Validation: rejects `null`, blank text, and values outside the public approval-type grammar

## `ApprovalDecision`

`ApprovalDecision` is the canonical approval outcome retained as part of posting evidence.

```java
public enum ApprovalDecision implements WireValue
```

- Purpose: keep approval outcomes explicit and stable across request, storage, and query surfaces
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `ApprovalReference`

`ApprovalReference` is the typed evidence link to one approval artifact.

```java
public record ApprovalReference(
    ApprovalId approvalId,
    ApprovalType approvalType,
    ActorId approverId,
    ActorType approverType,
    ApprovalDecision decision,
    Instant approvedAt)
```

- Purpose: keep approval evidence structured and durable across request and committed-posting
  surfaces
- Validation: rejects `null` approval id, approval type, approver id, approver type, decision,
  or approval timestamp

## `EntityProfile`

`EntityProfile` is the structured neutral entity descriptor embedded in one book identity.

```java
public record EntityProfile(BookEntityName displayName)
```

- Purpose: keep the accounting-entity display identity explicit without overloading book identity
  with non-doctrinal descriptive metadata
- Validation: rejects `null` display name

## `BookIdentity`

`BookIdentity` is the structured identity metadata persisted with one book.

```java
public record BookIdentity(
    EntityProfile entityProfile,
    BookDoctrine bookDoctrine,
    CurrencyUnit functionalCurrency,
    FiscalYearStart fiscalYearStart)
```

- Purpose: couple entity profile, persisted doctrine, functional currency, and fiscal-year anchor
  as one typed bookkeeping fact for one initialized book
- Surface: `entityName()` keeps the most common identity fact accessible without unwrapping the
  full entity profile
- Validation: rejects `null` entity profile, doctrine, functional currency, and fiscal-year
  start

## `AccountingKernelProfileId`

`AccountingKernelProfileId` is the durable identifier for one executable accounting-kernel
profile.

```java
public record AccountingKernelProfileId(String value)
```

- Purpose: persist one explicit kernel-profile owner with each initialized book instead of hiding
  doctrine only in prose
- Validation: rejects blank values, values longer than 120 characters, uppercase characters, and
  tokens outside lowercase kebab-case

## `AccountingKernelProfiles`

`AccountingKernelProfiles` publishes the built-in executable accounting-kernel profile inventory.

```java
public final class AccountingKernelProfiles
```

- Purpose: keep built-in profile ids centralized so CLI, executor, SQLite, discovery, and tests
  all speak one canonical vocabulary
- Current built-in profile: "internal-management-cash-bookkeeping-kernel"

## `AccountType`

`AccountType` is the canonical chart-of-accounts classification for one declared account.

```java
public enum AccountType implements WireValue {
  ASSET,
  LIABILITY,
  EQUITY,
  REVENUE,
  EXPENSE
}
```

- Purpose: distinguish account taxonomy from `NormalBalance`, which is only debit-versus-credit
  increase polarity
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `AccountRole`

`AccountRole` is the doctrinal role that determines whether one account behaves ordinarily or as a
contra account.

```java
public enum AccountRole implements WireValue {
  ORDINARY,
  POLARITY_INVERTED
}
```

- Purpose: separate account polarity from account classification so FinGrind can model contra
  behavior without overloading statement-line taxonomy
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `AccountNodeKind`

`AccountNodeKind` is the canonical hierarchy role for one declared account.

```java
public enum AccountNodeKind implements WireValue
```

- Purpose: distinguish roll-up headers from directly postable accounts in the chart hierarchy
- Surface: `allowsPosting()` and `allowsChildren()` make the hierarchy contract explicit
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `AccountTaxonomy`

`AccountTaxonomy` is the canonical declaration bundle for chart hierarchy and statement taxonomy on
one declared account.

```java
public record AccountTaxonomy(
    AccountNodeKind nodeKind,
    Optional<AccountCode> parentAccountCode,
    Optional<FinancialPositionLineClassification> financialPositionLineClassification,
    Optional<ProfitAndLossLineClassification> profitAndLossLineClassification)
```

- Purpose: keep node role, hierarchy, and report taxonomy on the declared account instead of
  inferring them from account codes, names, or renderer-local rules
- Validation role: `AccountSemantics.validate(...)` enforces which taxonomy branch must be present
  for each `AccountType`
- Factory: `empty()` returns the neutral postable taxonomy before account-type-specific validation
  applies

## `FinancialPositionLineClassification`

`FinancialPositionLineClassification` is the canonical balance-sheet taxonomy vocabulary for
declared accounts and derived equity lines.

```java
public enum FinancialPositionLineClassification implements WireValue
```

- Scope: classifies ASSET, LIABILITY, and EQUITY lines, including current/noncurrent buckets and
  entity-form-sensitive equity classes such as `EQUITY_CONTRIBUTION`, `EQUITY_WITHDRAWAL`,
  `RESULT_HOLDING`, and derived `CURRENT_PERIOD_RESULT` statement rows
- Surface: `accountType()` maps each classification back to its owning `AccountType`
- Wire contract: `wireValue()`, `wireValues()`, `declaredAccountWireValues()`, and
  `fromWireValue(...)` own the stable public vocabulary

## `ProfitAndLossLineClassification`

`ProfitAndLossLineClassification` is the canonical income-statement taxonomy vocabulary for nominal
accounts.

```java
public enum ProfitAndLossLineClassification implements WireValue
```

- Scope: classifies REVENUE and EXPENSE lines such as `OPERATING_REVENUE`, `COST_OF_SALES`, and
  `OTHER_EXPENSE`
- Surface: `accountType()` maps each classification back to REVENUE or EXPENSE ownership
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `StatementLineKind`

`StatementLineKind` records whether one public statement row came from a declared account or from a
derived statement line.

```java
public enum StatementLineKind implements WireValue
```

- Purpose: prevent renderers, clients, and workflow assertions from guessing whether a row is
  registry-owned or statement-derived
- Built-in values: `DECLARED_ACCOUNT` and `CURRENT_PERIOD_RESULT`
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary

## `AccountSemantics`

`AccountSemantics` is the canonical doctrinal owner for account polarity, taxonomy compatibility,
closing eligibility, and profit-or-loss close behavior.

```java
public final class AccountSemantics
```

- Purpose: keep normal-balance derivation, contra inversion, taxonomy validation, and
  nominal-account close semantics out of CLI, SQLite, and reporting adapters
- Surface: `validate(...)`, `normalBalance(...)`, `closesTemporaryProfitAndLossAccountType(...)`, and
  `profitAndLossContributionMinorUnits(...)`
- Doctrine: ordinary balance polarity is derived from `AccountType`, `POLARITY_INVERTED` reverses that
  polarity deliberately, and built-in close destinations are selected through
  `FinancialPositionLineClassification.RESULT_HOLDING` inside `AccountTaxonomy`

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
  PERSON,
  SYSTEM,
  AGENT
}
```

- Purpose: distinguish person, system, and agent callers without free-form strings
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

## `BalanceMath`

`BalanceMath` is the shared-kernel owner for exact balance arithmetic reused by bookkeeping reads
and reports.

```java
public final class BalanceMath
```

- Purpose: keep running-balance arithmetic and side derivation in the core bookkeeping model
  instead of inside one SQLite adapter helper
- Surface: `currencyBalance(...)`, `balanceSide(...)`, and `absoluteMinorUnits(...)`
- Bounds: overflow during running-balance absolute-value projection is rejected as a deterministic
  state error

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

## `FiscalYearStart`

`FiscalYearStart` is the canonical month-day anchor for one book's fiscal year boundary.

```java
public record FiscalYearStart(MonthDay value)
```

- Purpose: make fiscal-year configuration explicit in book identity and enforce that one
  `transfer-period-result` range stays inside one fiscal year while comparative statement windows can be
  derived from the same declared anchor
- Validation: rejects invalid month-day values; `toString()` renders the canonical `MM-dd` form
  used on public command surfaces

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

## `CanonicalTemporalText`

`CanonicalTemporalText` is the canonical owner for FinGrind's persisted and machine-contract
date/time text grammar.

```java
public final class CanonicalTemporalText
```

- Purpose: keep one executable owner for canonical `YYYY-MM-DD` local dates and canonical UTC
  instants instead of letting Java parsers, JSON Schema hints, and SQLite checks drift apart
- Surface: `parseLocalDate(...)`, `parseUtcInstant(...)`, `formatLocalDate(...)`,
  `formatUtcInstant(...)`, `isCanonicalLocalDate(...)`, and `isCanonicalUtcInstant(...)`
- Contract: `LOCAL_DATE_PATTERN` and `UTC_INSTANT_PATTERN` are the exact grammar fragments reused
  by CLI parsing, machine-readable request schemas, and SQLite file-format constraints

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

## `PostingKind`

`PostingKind` is the durable posting-family discriminator for one committed posting.

```java
public enum PostingKind implements WireValue {
  STANDARD,
  OPENING_BALANCE,
  PERIOD_RESULT_TRANSFER
}
```

- Purpose: distinguish ordinary business postings, opening adoption balances, and generated
  period-result-transfer postings without leaking implementation-specific marker strings
- Opening-balance boundary: `OPENING_BALANCE` is a one-time adoption-state posting family that is
  admitted only before the first committed posting enters the book
- Wire contract: `wireValue()`, `wireValues()`, and `fromWireValue(...)` own the stable public
  vocabulary
- Surface: committed postings publish one durable `PostingKind`, while caller-authored
  `BookkeepingEntryKind` inputs determine whether FinGrind derives `STANDARD`,
  `OPENING_BALANCE`, or internal `PERIOD_RESULT_TRANSFER` postings

## `PostingOriginKind`

`PostingOriginKind` is the durable origin vocabulary preserved for one committed posting after
FinGrind has projected a published bookkeeping entry into canonical journal lines.

```java
public enum PostingOriginKind implements WireValue {
  CASH_REVENUE,
  CASH_EXPENSE,
  EQUITY_CONTRIBUTION,
  EQUITY_WITHDRAWAL,
  OPEN_ACCOUNTING_POSITION,
  REVERSAL_ADJUSTMENT,
  REVERSAL_ADJUSTMENT,
  PERIOD_RESULT_TRANSFER
}
```

- Purpose: preserve the originating published entry family after all ordinary typed entries have
  converged into `PostingKind.STANDARD`
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

## `ReportingPeriod`

`ReportingPeriod` is the inclusive bounded period used by transfer-period-result administration, income
statements, and statements of changes in equity.

```java
public record ReportingPeriod(LocalDate effectiveDateFrom, LocalDate effectiveDateTo)
```

- Purpose: keep period-result-transfer and bounded-report semantics structural instead of treating them as
  parallel pairs of raw dates
- Validation: rejects `null` bounds and rejects `effectiveDateFrom` after `effectiveDateTo`
- Surface: `effectiveDateRange()`, `contains(...)`, and `dayAfter()`

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
