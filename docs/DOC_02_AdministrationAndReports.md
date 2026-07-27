---
afad: "5.0.1"
version: "0.61.0"
domain: CONTRACT_EXECUTOR_READ
updated: "2026-07-26"
route:
  keywords: [fingrind, contract, executor, administration, reports, read-service, inspection, pagination, trial-balance, account-ledger, period-summary, inventory-valuation, financial-position, income-statement, cash-flow-statement, changes-in-equity, declare-tax-registration, list-tax-registrations, tax-obligation]
  questions: ["where are the read and report models documented in fingrind", "which doc covers BookReadService and report DTOs", "where are the primary statement models documented", "where is the tax registration and filing surface documented"]
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

`BookTemplateAccounts` publishes the canonical account declarations for each built-in book
template.

```java
public final class BookTemplateAccounts
```

- Purpose: keep seeded template ownership explicit instead of scattering those declarations
  across CLI scaffolds, setup guides, or storage fixtures
- Surface: `declarations(BookDoctrine)` returns the typed `AccountDeclaration` list for the
  selected built-in doctrine
- Current template lines: `OWNER_MANAGED_SERVICE` with cash basis seeds cash, owner-capital,
  owner-draws, result-holding, retained-accumulated, service-revenue, and operating-expense;
  the accrual variant of that same service template adds accounts-receivable,
  accounts-payable, sales-discount-allowance, settlement-fee, and bad-debt-write-off; and
  `OWNER_MANAGED_TRADING`, whose cash basis seeds inventory with its `unit` unit of measure,
  sales-revenue, sales-discount-allowance, cost-of-sales, inventory-write-down-loss,
  inventory-shrinkage-loss, inventory-count-gain, and the same owner-equity close targets, while
  the accrual trading variant adds accounts-receivable, accounts-payable, settlement-fee, and
  bad-debt-write-off

## `BookReadService`

`BookReadService` is the published-language adapter for lifecycle inspection, read-side queries,
and office-worker reports.

```java
public final class BookReadService
```

- Constructor: requires `BookkeepingReadStore`
- Surface: `inspectBook()`, `listAccounts(...)`, `getPosting(...)`, `listPostings(...)`,
  `accountBalance(...)`, `trialBalance(...)`, `accountLedger(...)`, `periodSummary(...)`,
  `inventoryValuation(...)`, `financialPosition(...)`, `incomeStatement(...)`,
  `cashFlowStatement(...)`, and `changesInEquity(...)`
- Boundary: this is the anti-corruption layer between public read/report DTOs and the local
  bookkeeping inspection/query/report model served by one selected `BookkeepingReadStore`
- Translators: the exported `BookInspectionPublishedLanguageTranslator` in the `executor` package
  projects the local `BookLifecycleInspection` family into public `BookInspection`, while
  `BookkeepingReadPagePublishedLanguageTranslator`,
  `BookkeepingReadReportPublishedLanguageTranslator`, and
  `BookkeepingReadStatementPublishedLanguageTranslator` project local read pages, reports,
  statements, and query rejections into the public read/report DTO surface

## CLI Report Projections

The public CLI projects each canonical report DTO in two deliberately separate forms. Text and PDF
use `ReportModel` as the human presentation model. JSON uses a report-family-specific semantic
payload containing canonical `bookIdentity`, a family-specific `resolvedQuery`, result metadata,
and exact report facts; CSV is the corresponding typed row table. This separation keeps display
columns, alignment, labels, and formatted money out of the machine contract while preserving one
shared operator presentation model.

The income-statement payload includes exact `grossProfitTotals` and, when requested,
`comparativeGrossProfitTotals`, so the machine result retains the same trading subtotal facts that
the human projection presents. CSV remains row-only and does not mix those totals into its record
family.

`resolvedQuery` records accepted and resolved inputs, not a durable read revision. The account
balance and account ledger query forms always retain their own optional effective-date bounds as
explicit `null` values when omitted; they never use a generic query record or irrelevant null
fields from another report family. A later
back-dated posting in an open accounting period can change a report with the same query. Exact
replay therefore remains outside the report DTO contract until the book owns a durable revision
model.

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
    AccountTaxonomy accountTaxonomy,
    @Nullable UnitOfMeasure unitOfMeasure)
```

- Purpose: keep account-registry writes typed at the contract boundary, including explicit chart
  classification, declared chart hierarchy, statement-line taxonomy, and the inventory account's
  owned unit token plus exact quantity scale
- Validation: inventory account declarations require `unitOfMeasure`, while every non-inventory
  declaration forbids it

## `DeclaredAccount`

`DeclaredAccount` is the durable account-registry projection returned by administration and
read/report surfaces.

```java
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountTaxonomy accountTaxonomy,
    @Nullable UnitOfMeasure unitOfMeasure,
    boolean active,
    Instant declaredAt)
```

- Purpose: represent one declared account independently of CLI or SQLite concerns, including its
  immutable account classification, taxonomy, optional contra-account relationship, and any owned
  inventory unit metadata
- Derived fact: `normalBalance()` remains part of the public response surface, but it is derived
  from `accountType`, declared classification, and an optional valid contra relationship through
  `AccountTaxonomyDoctrine`

## `InterimResultSweepCommand`, `InterimResultSweepResult`, `FiscalYearCloseCommand`, `FiscalYearCloseResult`, `SweptInterimResult`, And `ClosedFiscalYear`

These types own the public close-command administration surface.

```java
public record InterimResultSweepCommand(LocalDate throughEffectiveDate)
public sealed interface InterimResultSweepResult
public record SweptInterimResult(...)
public record FiscalYearCloseCommand(int fiscalYearLabel)
public sealed interface FiscalYearCloseResult
public record ClosedFiscalYear(...)
```

- Purpose: request and describe the two explicit generated close operations: one interim-result
  sweep through one inclusive effective date into the built-in `RESULT_HOLDING` target, and one
  fiscal-year close selected by fiscal-year label and derived from the initialized book identity
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

- Purpose: require entity name, functional currency, fiscal-year anchor, and the selected book
  doctrine at initialization time
- Validation: rejects a `null` book identity; trading-book doctrine requires moving
  weighted-average inventory costing while service-book doctrine forbids it

## `OpenBookResult`

`OpenBookResult` is the closed result family for explicit book initialization.

```java
public sealed interface OpenBookResult
```

- Variants: `Opened`, `Rejected`
- `Opened`: carries the initialization instant, persisted `BookIdentity`, chain-derived genesis
  `AttestationRegistryInspection` trust root, and the exact `AttestationCommit` for that genesis
  operation

## `DeclareAccountResult`

`DeclareAccountResult` is the closed result family for `declare-account`.

```java
public sealed interface DeclareAccountResult
```

- Variants: `Declared`, `Reactivated`, `Renamed`, `Unchanged`, `Rejected`; every changed-account
  variant carries its exact `AttestationCommit`, while `Unchanged` carries no commit

## `DeclareTaxRegistrationCommand`, `DeclareTaxRegistrationResult`, `DeclaredTaxRegistration`, `TaxRegistrationId`, `TaxRegistrationName`, `TaxRegistrationNumber`, `TaxJurisdiction`, `TaxObligationFrequency`, `TaxCode`, `TaxCodeName`, `TaxCodeDefinition`, `TaxRate`, `TaxInclusionMode`, `TaxApplicationKind`, `TaxDeclarationRejection`, `TaxDefinitionViolation`, `TaxAdministrationService`, And `TaxAdministrationStore`

These types own the explicit per-book tax-registration write surface.

```java
public record DeclareTaxRegistrationCommand(...)
public sealed interface DeclareTaxRegistrationResult
public record DeclaredTaxRegistration(...)
public final class TaxAdministrationService
public interface TaxAdministrationStore
```

- `DeclareTaxRegistrationCommand`: requests one owned tax registration with already-declared
  payable and recoverable account codes, one filing frequency plus due offset, and one or more
  declared tax codes; it never creates prerequisite accounts implicitly
- `DeclareTaxRegistrationResult`: variants `Declared`, `Updated`, `Unchanged`, `Rejected`; a
  declared or updated registration carries its exact `AttestationCommit`, while `Unchanged` carries
  no commit
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

- Variants: `Rekeyed`, `Rejected`; `Rekeyed` carries the exact `AttestationCommit` for the
  completed key-rotation operation and `pairPublicationCompletion`: `published` when this
  invocation durably published the final pair or `recovered` when it reconciled the exact earlier
  completion-uncertain pair without a new rotation mutation

## `BackupBookResult`

`BackupBookResult` is the closed result family for verified closed-book backup export.

```java
public sealed interface BackupBookResult
```

- Variants: `BackedUp`, `AcknowledgementPending`, `AcknowledgementAuthorizationRejected`, and
  `Rejected`
- Purpose: export one verified encrypted SQLite backup pair consisting of the copied protected
  book file plus one newly materialized key file
- `BackedUp`: returns the published pair and its acknowledgement state. `acknowledged` always
  carries the exact append commit, `already-present` never carries one, and `resumed` may either
  append the acknowledgement or observe the exact acknowledgement already present
- `AcknowledgementPending`: the pair is published but an operational interruption left the
  source-book acknowledgement undetermined; retain the pair and rerun the exact tuple
- `AcknowledgementAuthorizationRejected`: the pair remains published, but current-head
  authorization refused its source-book acknowledgement; it carries the precise attestation
  refusal so the caller can correct authorization and rerun the exact tuple
- `Rejected`: deterministic backup-maintenance refusal
- Every published backup outcome carries `pairPublicationCompletion`, independently of its
  acknowledgement state and commit: `published` means this invocation durably published the
  backup/key pair, `recovered` means it reconciled the exact completion-uncertain pair, and
  `already-published` means an acknowledgement retry verified the complete existing pair without
  publishing it again.

## `RestoreBookResult`

`RestoreBookResult` is the closed result family for verified backup restore.

```java
public sealed interface RestoreBookResult
```

- Variants: `Restored`, `Rejected`; `Restored` carries the exact `AttestationCommit` for the
  completed restore operation and `pairPublicationCompletion`: `published` when this invocation
  durably published the absent destination pair or `recovered` when it reconciled the exact earlier
  completion-uncertain pair without a new restore mutation
- Purpose: verify one supplied encrypted backup pair before publishing an absent target live-book
  path, while re-encrypting the restored live book under the selected destination key file

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

- Current mode: `hard-break-reject-noncurrent-formats`
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

## `AccountLedgerPageCursor`, `AccountLedgerPagination`, `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult`

These types own the running ledger surface for one declared account.

```java
public record AccountLedgerPageCursor(
    LocalDate effectiveDate,
    Instant recordedAt,
    PostingId postingId)
public record AccountLedgerPagination(
    int limit,
    Optional<AccountLedgerPageCursor> cursor,
    Optional<AccountLedgerPageCursor> nextCursor)
public record AccountLedgerQuery(
    AccountCode accountCode,
    EffectiveDateRange effectiveDateRange,
    PostingCoverage postingCoverage,
    int limit,
    Optional<AccountLedgerPageCursor> cursor)
public record AccountLedgerEntry(
    PostingFact postingFact,
    CurrencyBalance movement,
    Money runningNetAmount,
    BalanceSide runningBalanceSide,
    @Nullable AttestationCommit attestationCommit)
public record AccountLedgerReport(...)
public sealed interface AccountLedgerResult
```

- Purpose: request and carry a running ledger with opening balances, activity rows, and closing
  balances
- Result variants: `Reported`, `Rejected`
- Report semantics: the report carries the selected book identity alongside the declared account,
  bounded ledger range, keyset page boundary, opening balances, movement rows, and closing balances
- Pagination semantics: the opaque cursor names the final row from the prior page in canonical
  `(effectiveDate, recordedAt, postingId)` order; opening balances include that prior row so the
  next returned row continues the running balance without replaying it
- Attestation semantics: each returned movement can carry the exact verified operation order and
  head that committed its posting. This is a read-time projection from verified immutable evidence;
  it is not a mutable posting backlink.

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
- Totals: the report includes per-section totals plus book-wide `netIncomeTotals`; trading books
  also publish one explicit gross-profit section between revenue and operating expense
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

Period-close planners, fiscal-period administration rejections, and query/report rejections are
documented in [DOC_02_PeriodCloseAndRejections.md](./DOC_02_PeriodCloseAndRejections.md).
