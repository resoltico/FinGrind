---
afad: "4.0"
version: "0.33.0"
domain: CONTRACT_EXECUTOR_READ
updated: "2026-05-08"
route:
  keywords: [fingrind, contract, executor, administration, reports, read-service, inspection, pagination, trial-balance, account-ledger, period-summary]
  questions: ["where are the read and report models documented in fingrind", "which doc covers BookReadService and report DTOs", "where are administration and query rejections documented"]
---

# Administration, Query, And Report Reference

This file documents the exported administration, inspection, query, and reporting models shared by
`contract` and the exported `executor` services that operate on those read-side surfaces.

## `BookAdministrationService`

`BookAdministrationService` owns explicit book initialization and account-registry writes.

```java
public final class BookAdministrationService
```

- Constructor: requires `BookStore` and `Clock`
- Surface: `openBook()` and `declareAccount(AccountDeclaration)`
- Policy: stamps lifecycle timestamps from the application clock
- Boundary: the service operates after the public `DeclareAccountCommand` has crossed the
  bookkeeping translator edge and become one local `AccountDeclaration`

## `BookReadService`

`BookReadService` is the published-language adapter for lifecycle inspection, read-side queries,
and office-worker reports.

```java
public final class BookReadService
```

- Constructor: requires `BookStore`
- Surface: `inspectBook()`, `listAccounts(...)`, `getPosting(...)`, `listPostings(...)`,
  `accountBalance(...)`, `trialBalance(...)`, `accountLedger(...)`, `periodSummary(...)`
- Boundary: this is the anti-corruption layer between public read/report DTOs and the local
  bookkeeping inspection/query/report model served by one selected `BookStore`
- Translators: the exported `BookInspectionPublishedLanguageTranslator` in the `executor` package
  projects the local `BookLifecycleInspection` family into public `BookInspection`, while
  `BookkeepingReadPublishedLanguageTranslator` projects local `BookkeepingQueryRejection`,
  criteria, pages, and report views into the public read/report DTO surface

## `BookkeepingReadService` And `BookkeepingLookupOutcome`

`BookkeepingReadService` owns the local bookkeeping inspection, lookup, query, and reporting
semantics before any public DTO or rejection family is projected, and
`BookkeepingLookupOutcome` preserves lifecycle rejection, ordinary absence, and successful lookup
distinctly for internal workflow and assertion callers.

```java
public final class BookkeepingReadService
public sealed interface BookkeepingLookupOutcome<T>
```

- Constructor: requires `BookStore`
- Surface: `inspectBook()`, `findAccount(...)`, `findPosting(...)`, `listAccounts(...)`,
  `getPosting(...)`, `listPostings(...)`, `accountBalance(...)`, `trialBalance(...)`,
  `accountLedger(...)`, `periodSummary(...)`
- Lookup variants: `Found`, `Missing`, `Rejected`
- Boundary: this service stays inside the bookkeeping context and returns only local lifecycle,
  lookup, query-rejection, and report-view outcomes

## `DeclareAccountCommand`

`DeclareAccountCommand` is the application-layer request to declare or reactivate one account.

```java
public record DeclareAccountCommand(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance)
```

- Purpose: keep account-registry writes typed at the contract boundary

## `DeclaredAccount`

`DeclaredAccount` is the durable account-registry projection returned by administration and
read/report surfaces.

```java
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    NormalBalance normalBalance,
    boolean active,
    Instant declaredAt)
```

- Purpose: represent one declared account independently of CLI or SQLite concerns

## `OpenBookResult`

`OpenBookResult` is the closed result family for explicit book initialization.

```java
public sealed interface OpenBookResult
```

- Variants: `Opened`, `Rejected`

## `DeclareAccountResult`

`DeclareAccountResult` is the closed result family for `declare-account`.

```java
public sealed interface DeclareAccountResult
```

- Variants: `Declared`, `Rejected`

## `RekeyBookResult`

`RekeyBookResult` is the closed result family for explicit passphrase rotation.

```java
public sealed interface RekeyBookResult
```

- Variants: `Rekeyed`, `Rejected`

## `BookInspection`

`BookInspection` is the machine-readable lifecycle and compatibility snapshot returned by
`inspect-book`.

```java
public sealed interface BookInspection
```

- Variants: `Missing`, `Existing`, `Initialized`
- Purpose: distinguish missing, blank, initialized, foreign, unsupported, and incomplete books
- Wire state: `status().wireValue()` is the stable lower-case/hyphenated vocabulary.
  `Existing.status()` is the owner of the specific blank/foreign/unsupported/incomplete state.

## `ListAccountsQuery`

`ListAccountsQuery` is the paginated read model for the declared-account registry.

```java
public record ListAccountsQuery(int limit, Optional<AccountPageCursor> cursor)
```

- Purpose: keep account-registry paging explicit and keyset-resumable

## `AccountPageCursor`

`AccountPageCursor` is the stable opaque keyset cursor for ascending account-code pagination.

```java
public record AccountPageCursor(AccountCode accountCode)
```

- Purpose: continue account-registry pagination without offset scans
- Wire contract: `wireValue()` and `fromWireValue(...)` own the base64url encoding

## `AccountPage`

`AccountPage` is one stable page of declared accounts.

```java
public record AccountPage(
    List<DeclaredAccount> accounts,
    int limit,
    Optional<AccountPageCursor> nextCursor)
```

- Purpose: couple one account slice to the next keyset cursor

## `ListAccountsResult`

`ListAccountsResult` is the closed result family for `list-accounts`.

```java
public sealed interface ListAccountsResult
```

- Variants: `Listed`, `Rejected`

## `GetPostingResult`

`GetPostingResult` is the closed result family for committed-posting lookup.

```java
public sealed interface GetPostingResult
```

- Variants: `Found`, `Rejected`

## `EffectiveDateRange`

`EffectiveDateRange` is the shared-kernel effective-date filter reused by public bookkeeping
queries and workflow assertions. Its canonical owner is [DOC_01_Core.md](./DOC_01_Core.md).

```java
public sealed interface EffectiveDateRange
```

- Variants: `Unbounded`, `From`, `To`, `Bounded`
- Purpose: reuse one structural date-range concept without making the public contract package own
  the internal query model
- Surface: `contains(...)` plus factory helpers like `of(...)` and `unbounded()`

## `PostingPageCursor`

`PostingPageCursor` is the stable opaque keyset cursor for reverse-chronological posting history.

```java
public record PostingPageCursor(LocalDate effectiveDate, Instant recordedAt, PostingId postingId)
```

- Purpose: continue posting-history pagination without offset scans
- Wire contract: `wireValue()` and `fromWireValue(...)` own the base64url binary encoding

## `ListPostingsQuery`

`ListPostingsQuery` is the filtered paging model for committed posting history.

```java
public record ListPostingsQuery(
    Optional<AccountCode> accountCode,
    EffectiveDateRange effectiveDateRange,
    int limit,
    Optional<PostingPageCursor> cursor)
```

- Purpose: keep history filtering and pagination typed at the contract boundary

## `PostingPage`

`PostingPage` is one stable page of committed postings.

```java
public record PostingPage(
    List<PostingFact> postings,
    int limit,
    Optional<PostingPageCursor> nextCursor)
```

- Purpose: couple one posting-history slice to the next keyset cursor

## `ListPostingsResult`

`ListPostingsResult` is the closed result family for posting-history queries.

```java
public sealed interface ListPostingsResult
```

- Variants: `Listed`, `Rejected`

## `AccountBalanceQuery`

`AccountBalanceQuery` is the grouped-balance request for one declared account.

```java
public record AccountBalanceQuery(AccountCode accountCode, EffectiveDateRange effectiveDateRange)
```

- Purpose: request grouped per-currency totals for one account, optionally within an effective-date
  window

## `CurrencyBalance`

`CurrencyBalance` is the shared-kernel per-currency grouped balance bucket. Its canonical owner is
[DOC_01_Core.md](./DOC_01_Core.md).

```java
public record CurrencyBalance(
    Money debitTotal,
    Money creditTotal,
    Money netAmount,
    BalanceSide balanceSide)
```

- Purpose: reuse one grouped balance concept across public reports and local bookkeeping views

## `AccountBalanceSnapshot`

`AccountBalanceSnapshot` is the payload returned by `account-balance`.

```java
public record AccountBalanceSnapshot(
    DeclaredAccount account,
    Optional<LocalDate> effectiveDateFrom,
    Optional<LocalDate> effectiveDateTo,
    List<CurrencyBalance> balances)
```

- Purpose: keep account identity, optional date filters, and grouped balances together

## `AccountBalanceResult`

`AccountBalanceResult` is the closed result family for grouped-balance queries.

```java
public sealed interface AccountBalanceResult
```

- Variants: `Reported`, `Rejected`

## `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult`

These types own the book-wide trial-balance report surface.

```java
public record TrialBalanceQuery(Optional<LocalDate> effectiveDateTo)
public record TrialBalanceRow(DeclaredAccount account, CurrencyBalance balance)
public record TrialBalanceReport(Optional<LocalDate> effectiveDateTo, List<TrialBalanceRow> rows)
public sealed interface TrialBalanceResult
```

- Purpose: request, carry, and result-wrap one as-of trial balance for the selected book
- Result variants: `Reported`, `Rejected`

## `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult`

These types own the running ledger surface for one declared account.

```java
public record AccountLedgerQuery(AccountCode accountCode, EffectiveDateRange effectiveDateRange)
public record AccountLedgerEntry(
    PostingFact postingFact,
    CurrencyBalance movement,
    Money runningNetAmount,
    BalanceSide runningBalanceSide)
public record AccountLedgerReport(...)
public sealed interface AccountLedgerResult
```

- Purpose: request and carry one running ledger with opening balances, activity rows, and closing
  balances
- Result variants: `Reported`, `Rejected`

## `PeriodSummaryQuery`, `PeriodCurrencySummary`, `PeriodAccountActivityRow`, `PeriodSummaryReport`, And `PeriodSummaryResult`

These types own the bounded book-wide period summary surface.

```java
public record PeriodSummaryQuery(LocalDate effectiveDateFrom, LocalDate effectiveDateTo)
public record PeriodCurrencySummary(CurrencyBalance totals)
public record PeriodAccountActivityRow(DeclaredAccount account, CurrencyBalance movement)
public record PeriodSummaryReport(...)
public sealed interface PeriodSummaryResult
```

- Purpose: request and carry bounded posting counts, currency totals, and flattened account activity
- Result variants: `Reported`, `Rejected`

## `BookAdministrationRejection`

`BookAdministrationRejection` is the closed family of deterministic lifecycle and account-registry
refusals.

```java
public sealed interface BookAdministrationRejection
```

- Variants: `BookAlreadyInitialized`, `BookNotInitialized`, `BookContainsSchema`,
  `NormalBalanceConflict`

## `BookQueryRejection`

`BookQueryRejection` is the closed family of deterministic query/report refusals.

```java
public sealed interface BookQueryRejection
```

- Variants: `BookNotInitialized`, `UnknownAccount`, `PostingNotFound`

## `RejectionNarrative`

`RejectionNarrative` owns user-facing rejection prose for public rejection contracts.

```java
public final class RejectionNarrative
```

- Purpose: prevent CLI rendering and other public rejection surfaces from leaking Java class names
  as rejection text
