---
afad: "4.0"
version: "0.58.0"
domain: CONTRACT_EXECUTOR_READ
updated: "2026-06-29"
route:
  keywords: [fingrind, contract, executor, administration, reports, read-service, inspection, pagination, trial-balance, account-ledger, period-summary, interim-result-sweep, fiscal-year-close, financial-position, income-statement, cash-flow-statement, changes-in-equity, declare-tax-registration, list-tax-registrations, tax-obligation]
  questions: ["where are the read and report models documented in fingrind", "which doc covers BookReadService and report DTOs", "where are administration and query rejections documented", "where is interim-result-sweep documented", "where is fiscal-year-close documented", "where are the primary statement models documented", "where is the tax registration and filing surface documented"]
---

# Administration, Query, And Report Reference

This file documents the exported administration, inspection, query, and reporting models shared by
`contract` and the exported `executor` services that operate on those read-side surfaces.

## `BookAdministrationService`

`BookAdministrationService` owns explicit book initialization and account-registry writes.

```java
public final class BookAdministrationService
```

- Constructor: requires `BookAdministrationStore` and `Clock`
- Surface: `openBook(BookIdentity)` and `declareAccount(AccountDeclaration)`
- Policy: stamps lifecycle timestamps from the application clock
- Boundary: the service operates after the public `DeclareAccountCommand` has crossed the
  bookkeeping translator edge and become one local `BookIdentity`, `AccountDeclaration`, or
  `ReportingPeriod`

## `BookTemplateAccounts`

`BookTemplateAccounts` publishes the canonical starter-chart declarations for each built-in book
template.

```java
public final class BookTemplateAccounts
```

- Purpose: keep seeded starter-chart ownership explicit instead of scattering those declarations
  across CLI scaffolds, setup guides, or storage fixtures
- Surface: `declarations(BookTemplateId)` returns the typed `AccountDeclaration` list for one
  built-in template
- Current template line: `OWNER_MANAGED_SERVICE` seeds `cash`, `owner-capital`,
  `owner-draws`, `result-holding`, `service-revenue`, and `operating-expense`

## `BookReadService`

`BookReadService` is the published-language adapter for lifecycle inspection, read-side queries,
and office-worker reports.

```java
public final class BookReadService
```

- Constructor: requires `BookkeepingReadStore`
- Surface: `inspectBook()`, `listAccounts(...)`, `getPosting(...)`, `listPostings(...)`,
  `accountBalance(...)`, `trialBalance(...)`, `accountLedger(...)`, `periodSummary(...)`,
  `financialPosition(...)`, `incomeStatement(...)`, `cashFlowStatement(...)`, and
  `changesInEquity(...)`
- Boundary: this is the anti-corruption layer between public read/report DTOs and the local
  bookkeeping inspection/query/report model served by one selected `BookkeepingReadStore`
- Translators: the exported `BookInspectionPublishedLanguageTranslator` in the `executor` package
  projects the local `BookLifecycleInspection` family into public `BookInspection`, while
  `BookkeepingReadPagePublishedLanguageTranslator`,
  `BookkeepingReadReportPublishedLanguageTranslator`, and
  `BookkeepingReadStatementPublishedLanguageTranslator` project local read pages, reports,
  statements, and query rejections into the public read/report DTO surface

## `BookkeepingReadService` And `BookkeepingLookupOutcome`

`BookkeepingReadService` owns the local bookkeeping inspection, lookup, query, and reporting
semantics before any public DTO or rejection family is projected, and
`BookkeepingLookupOutcome` preserves lifecycle rejection, ordinary absence, and successful lookup
distinctly for internal workflow and assertion callers.

```java
public final class BookkeepingReadService
public sealed interface BookkeepingLookupOutcome<T>
```

- Constructor: requires `BookkeepingReadStore`
- Surface: `inspectBook()`, `findAccount(...)`, `findPosting(...)`, `listAccounts(...)`,
  `getPosting(...)`, `listPostings(...)`, `accountBalance(...)`, `trialBalance(...)`,
  `accountLedger(...)`, `periodSummary(...)`, `financialPosition(...)`,
  `incomeStatement(...)`, `cashFlowStatement(...)`, and `changesInEquity(...)`
- Lookup variants: `Found`, `Missing`, `Rejected`
- Statement computation owners: `BookkeepingReportingService` now coordinates
  `FinancialPositionStatementCalculator`, `IncomeStatementCalculator`,
  `CashFlowStatementCalculator`, and `ChangesInEquityStatementCalculator` inside
  `executor.bookkeeping.reporting` instead of carrying all statement doctrine inside the
  read-service collaborator
- Boundary: this service stays inside the bookkeeping context and returns only local lifecycle,
  lookup, query-rejection, and report-view outcomes

## `DeclareAccountCommand`

`DeclareAccountCommand` is the application-layer request to declare or reactivate one account.

```java
public record DeclareAccountCommand(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy)
```

- Purpose: keep account-registry writes typed at the contract boundary, including explicit chart
  classification, declared chart hierarchy, and statement-line taxonomy

## `DeclaredAccount`

`DeclaredAccount` is the durable account-registry projection returned by administration and
read/report surfaces.

```java
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy,
    boolean active,
    Instant declaredAt)
```

- Purpose: represent one declared account independently of CLI or SQLite concerns, including its
  immutable account classification and taxonomy
- Derived fact: `normalBalance()` remains part of the public response surface, but it is derived
  from `accountType` plus declared classification through `AccountTaxonomyDoctrine`

## `InterimResultSweepCommand`, `InterimResultSweepResult`, `FiscalYearCloseCommand`, `FiscalYearCloseResult`, `SweptInterimResult`, And `ClosedFiscalYear`

These types own the public close-command administration surface.

```java
public record InterimResultSweepCommand(ReportingPeriod reportingPeriod)
public sealed interface InterimResultSweepResult
public record SweptInterimResult(...)
public record FiscalYearCloseCommand(ReportingPeriod reportingPeriod)
public sealed interface FiscalYearCloseResult
public record ClosedFiscalYear(...)
```

- Purpose: request and describe the two explicit generated close operations: one contiguous
  interim-result sweep into the built-in `RESULT_HOLDING` target, and one fiscal-year close that
  finalizes the year into `RETAINED_ACCUMULATED`
- Result variants: each command publishes `...`/`Rejected`
- Durable facts: `SweptInterimResult` carries `sweepOrder`, the inclusive `ReportingPeriod`, the
  selected result-holding account code, the per-currency swept totals, the sweep timestamp, and
  every generated close posting id; `ClosedFiscalYear` carries `closeOrder`, the reporting period,
  the capital/result-holding/retained-accumulated target accounts, the close timestamp, and every
  generated close posting id

## `OpenBookCommand`

`OpenBookCommand` is the explicit initialization command for one new book.

```java
public record OpenBookCommand(BookIdentity bookIdentity)
```

- Purpose: require entity name, functional currency, and fiscal-year anchor at initialization time
- Validation: rejects `null` book identity

## `OpenBookResult`

`OpenBookResult` is the closed result family for explicit book initialization.

```java
public sealed interface OpenBookResult
```

- Variants: `Opened`, `Rejected`
- `Opened`: carries both the initialization instant and the persisted `BookIdentity`

## `DeclareAccountResult`

`DeclareAccountResult` is the closed result family for `declare-account`.

```java
public sealed interface DeclareAccountResult
```

- Variants: `Declared`, `Rejected`

## `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore`

These types own the explicit per-book tax-registration write surface.

```java
public record DeclareTaxRegistrationCommand(...)
public sealed interface DeclareTaxRegistrationResult
public record DeclaredTaxRegistration(...)
public final class TaxAdministrationService
public interface TaxAdministrationStore
```

- `DeclareTaxRegistrationCommand`: requests one owned tax registration with payable and
  recoverable account codes, one filing frequency plus due offset, and one or more declared tax
  codes
- `DeclareTaxRegistrationResult`: variants `Declared`, `Updated`, `Unchanged`, `Rejected`
- `DeclaredTaxRegistration`: durable current snapshot for one tax registration, including its
  declaration timestamp and declared code catalog
- `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`,
  `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxRate`, `TaxInclusionMode`, and
  `TaxApplicationKind` keep the public tax-registration vocabulary typed instead of collapsing it
  into transport strings
- `TaxCodeDefinition`: bundles one declared tax code's public identity, label, rate, inclusion
  mode, and application kind
- `TaxDeclarationRejection` and `TaxDefinitionViolation`: deterministic initialized-book and
  definition failures for tax-registration declaration and update
- `TaxAdministrationService`: validates initialized-book state plus referenced-account
  compatibility before delegating persistence
- `TaxAdministrationStore`: write-side port that persists one tax registration mutation at one
  supplied declaration instant

## `RekeyBookResult`

`RekeyBookResult` is the closed result family for explicit passphrase rotation.

```java
public sealed interface RekeyBookResult
```

- Variants: `Rekeyed`, `Rejected`

## `BackupBookResult`

`BackupBookResult` is the closed result family for verified closed-book backup export.

```java
public sealed interface BackupBookResult
```

- Variants: `BackedUp`, `Rejected`
- Purpose: export one verified encrypted SQLite backup pair consisting of the copied protected
  book file plus one newly materialized key file

## `RestoreBookResult`

`RestoreBookResult` is the closed result family for verified backup restore.

```java
public sealed interface RestoreBookResult
```

- Variants: `Restored`, `Rejected`
- Purpose: verify one supplied encrypted backup pair before replacing the target live book path,
  with the restored live book then reopened by the supplied backup key file

## `RekeyRollbackResult`

This type owns the public stale-rollback maintenance surface for interrupted rekeys.

```java
public sealed interface RekeyRollbackResult
```

- Variants: `Inspected`, `Restored`, `Deleted`, `Rejected`
- Purpose: model the explicit `inspect-rekey-rollback`, `restore-rekey-rollback`, and
  `delete-rekey-rollback` command results instead of hiding multiple maintenance workflows behind
  one action enum

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
- Migration posture: every inspection also publishes one `BookMigrationPolicy` that states the
  active protected-book format line accepts neither older nor newer formats and exposes no
  in-place upgrade path

## `BookMigrationPolicy`

`BookMigrationPolicy` is the machine-readable migration posture attached to every `BookInspection`.

```java
public record BookMigrationPolicy(...)
public enum BookMigrationPolicyMode
```

- Current mode: `hard-break-reject-older-formats`
- Purpose: make the current format-line policy explicit in the public contract instead of leaving
  “no migration executor” as prose-only theory

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

## `ListTaxRegistrationsQuery`, `TaxRegistrationPageCursor`, `TaxRegistrationPage`, `ListTaxRegistrationsResult`, `TaxObligationQuery`, `TaxObligationCodeSummary`, `TaxObligationReport`, `TaxObligationResult`, `TaxQueryRejection`, `TaxReadService`, `TaxReadStore`, `TaxRegistrationCatalogStore`, And `TaxRegistrationLookupStore`

These types own tax-registration pagination and tax-obligation reporting.

```java
public record ListTaxRegistrationsQuery(int limit, Optional<TaxRegistrationPageCursor> cursor)
public record TaxRegistrationPage(...)
public sealed interface ListTaxRegistrationsResult
public record TaxObligationQuery(...)
public record TaxObligationCodeSummary(...)
public record TaxObligationReport(...)
public sealed interface TaxObligationResult
public final class TaxReadService
public interface TaxReadStore
public interface TaxRegistrationCatalogStore
public interface TaxRegistrationLookupStore
```

- `ListTaxRegistrationsQuery` and `TaxRegistrationPageCursor`: request one stable paginated slice
  of the current tax-registration registry
- `TaxRegistrationPage`: couples one selected `BookIdentity`, ordered declared registrations, the
  requested page limit, and one optional next cursor
- `ListTaxRegistrationsResult`: variants `Listed`, `Rejected`
- `TaxObligationQuery`: requests one bounded filing-period view for one declared tax registration
- `TaxObligationCodeSummary` and `TaxObligationReport`: publish per-code and full-period totals in
  the selected book's functional currency, including output, recoverable-input,
  nonrecoverable-input, net-payable, and net-receivable totals
- `TaxObligationResult`: variants `Reported`, `Rejected`
- `TaxQueryRejection`: deterministic query refusals for uninitialized books, unknown tax
  registrations, and filing periods that do not match the registration's declared obligation
  frequency
- `TaxReadService`: translates ordered registry views and durable applied-tax facts into the
  public tax query and filing-report DTOs
- `TaxReadStore`: composite read-side port over lifecycle readiness, stable registration lookup,
  ordered registration catalog reads, and posting-range access
- `TaxRegistrationCatalogStore` and `TaxRegistrationLookupStore`: narrower seams for stable-id
  lookup and paginated registry reads

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
[DOC_01_Core_LedgerAndPosting.md](./DOC_01_Core_LedgerAndPosting.md).

```java
public final class CurrencyBalance
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

## `ComparativeRangeResolver`

`ComparativeRangeResolver` is the executor-owned translator from one typed comparative selection to
one concrete effective-date window.

```java
public final class ComparativeRangeResolver
```

- Purpose: centralize prior-period versus explicit-range expansion for both as-of and bounded
  report surfaces
- Entry points: `asOf(...)` resolves comparison windows for as-of reports and `period(...)`
  resolves comparison windows for bounded-period reports
- `ComparativeSelection.none()` resolves to `EffectiveDateRange.unbounded()`, while
  `ComparativeSelection.priorPeriod()` delegates the concrete fiscal comparison window to the
  selected `StatementComparativePolicy`

## `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult`

These types own the book-wide trial-balance report surface.

```java
public record TrialBalanceQuery(
    Optional<LocalDate> effectiveDateAsOf,
    PostingCoverage postingCoverage,
    ComparativeSelection comparativeSelection)
public record TrialBalanceRow(DeclaredAccount account, CurrencyBalance balance)
public record TrialBalanceReport(...)
public sealed interface TrialBalanceResult
```

- Purpose: request, carry, and result-wrap one as-of trial balance for the selected book
- Result variants: `Reported`, `Rejected`
- Report semantics: the report carries `BookIdentity`, `PostingCoverage`, and one optional
  comparative row set when the caller selects `comparativeSelection` other than `none`

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
- Report semantics: the report carries the selected book identity alongside the declared account,
  bounded ledger range, opening balances, movement rows, and closing balances

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
- Report semantics: the report carries the selected book identity alongside the bounded counts,
  currency totals, and flattened account activity rows

## `FinancialPositionQuery`, `FinancialPositionRow`, `FinancialPositionSection`, `FinancialPositionReport`, And `FinancialPositionResult`

These types own the public statement-of-financial-position surface.

```java
public record FinancialPositionQuery(
    Optional<LocalDate> effectiveDateAsOf,
    ComparativeSelection comparativeSelection)
public record FinancialPositionRow(...)
public record FinancialPositionSection(...)
public record FinancialPositionReport(...)
public sealed interface FinancialPositionResult
```

- Purpose: request and carry an as-of statement of financial position grouped by account type
- Result variants: `Reported`, `Rejected`
- Row semantics: every row publishes `lineClassification` plus `lineKind`; derived current-period
  result rows are explicit `lineKind: CURRENT_PERIOD_RESULT` records rather than implicit
  placeholders
- Comparative semantics: the report carries fiscal-year-anchored comparative sections only when
  the caller opts into one non-`none` comparative selection

## `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult`

These types own the public bounded income-statement surface.

```java
public record IncomeStatementQuery(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection)
public record IncomeStatementRow(...)
public record IncomeStatementSection(...)
public record IncomeStatementReport(...)
public sealed interface IncomeStatementResult
```

- Purpose: request and carry one bounded income statement grouped by nominal account type
- Result variants: `Reported`, `Rejected`
- Totals: the report includes per-section totals plus book-wide `netIncomeTotals`
- Comparative semantics: the report carries fiscal-year-anchored comparative sections and
  `comparativeNetIncomeTotals` only when the caller opts into one non-`none` comparative
  selection

## `CashFlowStatementQuery`, `CashFlowRow`, `CashFlowSection`, `CashFlowStatementReport`, And `CashFlowStatementResult`

These types own the public bounded statement of cash receipts and payments.

```java
public record CashFlowStatementQuery(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection)
public record CashFlowRow(...)
public record CashFlowSection(...)
public record CashFlowStatementReport(...)
public sealed interface CashFlowStatementResult
```

- Purpose: request and carry one bounded cash-basis statement classified into operating,
  investing, and financing sections
- Result variants: `Reported`, `Rejected`
- Row semantics: every row carries either one `profitAndLossLineClassification` or one
  `financialPositionLineClassification`, so the report stays traceable back to the declared
  account that explained the cash movement
- Total semantics: the report carries `openingCashTotals`, section totals, `movementTotals`, and
  `closingCashTotals`, plus comparative counterparts when the caller opts into one non-`none`
  comparative selection
- Articulation rule: opening cash plus movement equals closing cash per currency

## `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult`

These types own the public bounded statement-of-changes-in-equity surface.

```java
public record ChangesInEquityQuery(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection)
public record ChangesInEquityRow(...)
public record ChangesInEquityReport(...)
public sealed interface ChangesInEquityResult
```

- Purpose: request and carry opening, movement, and closing equity balances for one bounded period
- Result variants: `Reported`, `Rejected`
- Row semantics: every row publishes `lineClassification` plus `lineKind`; derived current-period
  result movements are explicit `lineKind: CURRENT_PERIOD_RESULT` rows
- Comparative semantics: the report carries one comparative rows and totals set only when the
  caller opts into one non-`none` comparative selection

## `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView`

These executor-owned local bookkeeping types carry the as-of financial-position model before it is
projected into public DTOs.

```java
public record FinancialPositionCriteria(
    Optional<LocalDate> effectiveDateAsOf,
    ComparativeSelection comparativeSelection)
public record FinancialPositionRowView(...)
public record FinancialPositionSectionView(...)
public record FinancialPositionView(...)
```

- Purpose: keep report-shaping semantics local to bookkeeping until the published-language
  translator renders them outward

## `IncomeStatementCriteria`, `IncomeStatementRowView`, `IncomeStatementSectionView`, And `IncomeStatementView`

These executor-owned local bookkeeping types carry the bounded income-statement model before it is
projected into public DTOs.

```java
public record IncomeStatementCriteria(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection)
public record IncomeStatementRowView(...)
public record IncomeStatementSectionView(...)
public record IncomeStatementView(...)
```

- Purpose: keep nominal-account movement shaping local to bookkeeping until the published-language
  translator renders it into public report DTOs

## `CashFlowStatementCriteria`, `CashFlowRowView`, `CashFlowSectionView`, And `CashFlowStatementView`

These executor-owned local bookkeeping types carry the bounded cash receipts/payments model before
it is projected into public DTOs.

```java
public record CashFlowStatementCriteria(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection)
public record CashFlowRowView(...)
public record CashFlowSectionView(...)
public record CashFlowStatementView(...)
```

- Purpose: keep cash-basis movement classification, articulation, and comparative shaping local
  to bookkeeping until the published-language translator renders them into public report DTOs
- Classification rule: counterpart rows stay tied to declared-account taxonomy, while cash and
  cash-equivalent accounts contribute only to opening/closing totals and never appear as
  counterpart rows

## `ChangesInEquityCriteria`, `ChangesInEquityRowView`, And `ChangesInEquityView`

These executor-owned local bookkeeping types carry the bounded changes-in-equity model before it
is projected into public DTOs.

```java
public record ChangesInEquityCriteria(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection)
public record ChangesInEquityRowView(...)
public record ChangesInEquityView(...)
```

- Purpose: keep equity opening/movement/closing shaping local to bookkeeping until the
  published-language translator renders it into public report DTOs

## `InterimResultSweepDraft`, `InterimResultSweepOutcome`, `SweptInterimResult`, `InterimResultTargetSelection`, `AcceptedInterimResultTargetSelection`, `RejectedInterimResultTargetSelection`, `InterimResultSweepPlan`, `InterimResultSweepPlanner`, And `InterimResultSweepService`

These executor-owned local bookkeeping types own interim-result-sweep generation and durable
close semantics before the public administration surface is projected.

```java
public record InterimResultSweepDraft(...)
public sealed interface InterimResultSweepOutcome
public record SweptInterimResult(...)
public sealed interface InterimResultTargetSelection
public final class AcceptedInterimResultTargetSelection
public final class RejectedInterimResultTargetSelection
public record InterimResultSweepPlan(...)
public final class InterimResultSweepPlanner
public final class InterimResultSweepService
```

- `InterimResultSweepDraft`: store-ready interim-result-sweep payload containing the reporting
  period, the sweep time, and every generated posting draft
- `InterimResultSweepOutcome`: closed family of accepted-versus-rejected local
  interim-result-sweep outcomes
- `SweptInterimResult`: durably stored interim-result sweep fact carrying `sweepOrder`,
  the transferred totals, and every generated sweep posting id
- `InterimResultTargetSelection`: closed result for the policy-owned result-holding account lookup
- `AcceptedInterimResultTargetSelection`: accepted result-holding selection carrying the chosen account
- `RejectedInterimResultTargetSelection`: rejected result-holding selection carrying the deterministic
  administration rejection plus candidate account codes
- `InterimResultSweepPlan`: generated interim-result-sweep posting drafts plus the transferred
  totals that the published sweep result projects afterward
- `InterimResultSweepPlanner`: bookkeeping-domain planner that selects the policy-owned result-holding
  account, validates close-horizon rules, and generates the `PostingKind.INTERIM_RESULT_SWEEP`
  drafts plus published transferred totals for one contiguous reporting period
- `InterimResultSweepService`: application service that coordinates lifecycle inspection, account
  catalog/store access, planner output, and durable interim-result-sweep persistence instead of
  owning the close recipe itself

## `CloseTargetSelection`, `AcceptedCloseTargetSelection`, `RejectedCloseTargetSelection`, `CloseTargetAccountSelector`, `FiscalYearCloseDraft`, `ClosedFiscalYearRecord`, `FiscalYearCloseOutcome`, `FiscalYearClosePlanner`, And `FiscalYearCloseService`

These executor-owned local bookkeeping types own the fiscal-year close target-selection and
durable year-end close flow before the public administration surface is projected.

```java
public sealed interface CloseTargetSelection
public final class AcceptedCloseTargetSelection
public final class RejectedCloseTargetSelection
public final class CloseTargetAccountSelector
public record FiscalYearCloseDraft(...)
public record ClosedFiscalYearRecord(...)
public sealed interface FiscalYearCloseOutcome
public final class FiscalYearClosePlanner
public final class FiscalYearCloseService
```

- `CloseTargetSelection`: closed result for resolving one required close-target classification
- `AcceptedCloseTargetSelection`: successful selection of the only active declared close-target account
- `RejectedCloseTargetSelection`: deterministic missing-versus-ambiguous close-target refusal plus
  the relevant candidate account codes
- `CloseTargetAccountSelector`: canonical classifier-based selector for close-owned equity targets
- `FiscalYearCloseDraft`: store-ready year-end close payload containing every generated durable
  fiscal-year-close posting
- `ClosedFiscalYearRecord`: durably stored local close fact carrying close order, selected close
  targets, and generated posting ids
- `FiscalYearCloseOutcome`: closed family of accepted-versus-rejected fiscal-year close outcomes
- `FiscalYearClosePlanner`: bookkeeping-domain planner for fiscal-year boundary validation,
  close-target selection, and generated year-end postings
- `FiscalYearCloseService`: application service that coordinates lifecycle inspection, planner
  output, and durable fiscal-year close persistence

## `BookAdministrationRejection`

`BookAdministrationRejection` is the closed family of deterministic lifecycle and account-registry
refusals.

```java
public sealed interface BookAdministrationRejection
```

- Variants: `BookAlreadyInitialized`, `BookNotInitialized`, `BookContainsSchema`,
  `AccountTypeConflict`, `AccountTaxonomyConflict`, `ParentAccountMissing`,
  `ParentAccountInactive`, `ParentAccountTypeConflict`, `ParentAccountNotHeader`,
  `ParentAccountTaxonomyConflict`, `AccountHierarchyCycle`,
  `CloseTargetAccountCandidateMissing`, `CloseTargetAccountCandidateAmbiguous`,
  `InterimResultSweepMustStartAt`, `InterimResultSweepFutureDate`,
  `InterimResultSweepCrossesFiscalYearBoundary`, `FiscalYearCloseMustStartAt`,
  `FiscalYearCloseMustEndAt`, `FiscalYearCloseFutureDate`

## `BookQueryRejection`

`BookQueryRejection` is the closed family of deterministic query/report refusals.

```java
public sealed interface BookQueryRejection
```

- Variants: `BookNotInitialized`, `UnknownAccount`, `PostingNotFound`

## `BookMaintenanceArtifactRole`, `BookMaintenancePathFailure`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint`

These public maintenance-contract types keep verification-driven maintenance outcomes typed and
redacted at the published-language edge.

```java
public enum BookMaintenanceArtifactRole implements WireValue
public enum BookMaintenancePathFailure implements WireValue
public enum BookMaintenanceVerificationFailure implements WireValue
public sealed interface BookMaintenanceRejection
public record PublicPathHint(String value)
```

- `BookMaintenanceArtifactRole`: keeps maintenance failures precise about whether the rejected
  artifact was the live book, backup source, backup target, backup-key target, rollback artifact,
  or restored target
- `BookMaintenancePathFailure`: keeps maintenance path-contract refusals typed as missing parent
  directory, parent path collision, missing owner traversal/write access, missing owner-only
  protection, non-regular target path, or unsupported secure filesystem
- `BookMaintenanceVerificationFailure`: keeps deterministic maintenance verification failures typed
  as missing, blank SQLite, foreign SQLite, unsupported format version, incomplete FinGrind book,
  or protected-book verification failure
- `PublicPathHint`: redacts filesystem paths to `<redacted>` or
  `<redacted>/<smallest-distinguishing-trailing-context>` so public maintenance output proves
  which artifact failed without leaking absolute operator paths
- Boundary: `BookMaintenanceRejection.ArtifactPathInvalid`,
  `BookMaintenanceRejection.ArtifactBusy`, and
  `BookMaintenanceRejection.ArtifactVerificationFailed` use these types so backup, restore, and
  rekey-recovery refusals preserve artifact role, path failure or verification class, and redacted
  path hints as
  first-class machine contract
  instead of collapsing maintenance verification into generic runtime failure text

## `BookMaintenanceRejection`

`BookMaintenanceRejection` is the closed family of deterministic maintenance-workflow refusals.

```java
public sealed interface BookMaintenanceRejection
```

- Variants: `BookHasBlockingArtifacts`, `BackupSourceHasBlockingArtifacts`,
  `ArtifactPathInvalid`, `ArtifactBusy`, `BackupDestinationAlreadyExists`,
  `BackupKeyFileAlreadyExists`, `ArtifactVerificationFailed`,
  `NoRollbackArtifactsFound`, `RollbackArtifactSelectionRequired`,
  `RollbackArtifactNotFound`, and `RollbackArtifactNotForBook`
- Purpose: preserve closed-copy and rollback-artifact safety as first-class rejection language
  instead of leaking maintenance mistakes as ad hoc storage exceptions

## `RejectionNarrative`

`RejectionNarrative` owns user-facing rejection prose for public rejection contracts.

```java
public final class RejectionNarrative
```

- Purpose: prevent CLI rendering and other public rejection surfaces from leaking Java class names
  as rejection text
