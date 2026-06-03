---
afad: "4.0"
version: "0.51.0"
domain: CONTRACT_EXECUTOR_READ
updated: "2026-06-03"
route:
  keywords: [fingrind, contract, executor, administration, reports, read-service, inspection, pagination, trial-balance, account-ledger, period-summary, transfer-period-result, financial-position, income-statement, changes-in-equity]
  questions: ["where are the read and report models documented in fingrind", "which doc covers BookReadService and report DTOs", "where are administration and query rejections documented", "where is transfer-period-result documented", "where are the primary statement models documented"]
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
- Current template line: `OWNER_MANAGED_SERVICE_CASH` seeds `cash`, `owner-capital`,
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
  `financialPosition(...)`, `incomeStatement(...)`, and `changesInEquity(...)`
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
  `incomeStatement(...)`, and `changesInEquity(...)`
- Lookup variants: `Found`, `Missing`, `Rejected`
- Statement computation owners: `BookkeepingReportingService` now coordinates
  `FinancialPositionStatementCalculator`, `IncomeStatementCalculator`, and
  `ChangesInEquityStatementCalculator` inside `executor.bookkeeping.reporting` instead of
  carrying all statement doctrine inside the read-service collaborator
- Boundary: this service stays inside the bookkeeping context and returns only local lifecycle,
  lookup, query-rejection, and report-view outcomes

## `DeclareAccountCommand`

`DeclareAccountCommand` is the application-layer request to declare or reactivate one account.

```java
public record DeclareAccountCommand(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountRole accountRole,
    AccountTaxonomy accountTaxonomy)
```

- Purpose: keep account-registry writes typed at the contract boundary, including explicit chart
  classification, doctrinal role, declared chart hierarchy, and statement-line taxonomy

## `DeclaredAccount`

`DeclaredAccount` is the durable account-registry projection returned by administration and
read/report surfaces.

```java
public record DeclaredAccount(
    AccountCode accountCode,
    AccountName accountName,
    AccountType accountType,
    AccountRole accountRole,
    AccountTaxonomy accountTaxonomy,
    boolean active,
    Instant declaredAt)
```

- Purpose: represent one declared account independently of CLI or SQLite concerns, including its
  immutable account classification, doctrinal role, and taxonomy
- Derived fact: `normalBalance()` remains part of the public response surface, but it is derived
  from `accountType` plus `accountRole` through `AccountSemantics`

## `PeriodResultTransferCommand`, `PeriodResultTransferResult`, And `TransferredPeriodResult`

These types own the public period-result-transfer administration surface.

```java
public record PeriodResultTransferCommand(
    ReportingPeriod reportingPeriod,
    AccountCode resultHoldingAccountCode)
public sealed interface PeriodResultTransferResult
public record TransferredPeriodResult(...)
```

- Purpose: request and describe one contiguous period-result transfer that writes generated
  transfer postings into one selected active equity account whose declared financial-position
  classification matches the built-in result-holding destination
- Result variants: `Transferred`, `Rejected`
- Durable fact: `TransferredPeriodResult` carries `transferOrder`, the inclusive `ReportingPeriod`, the
  selected result-holding account code used for the transfer, the per-currency transferred totals moved into
  equity, the transfer timestamp, and every generated transfer posting id

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

## `TrialBalanceQuery`, `TrialBalanceRow`, `TrialBalanceReport`, And `TrialBalanceResult`

These types own the book-wide trial-balance report surface.

```java
public record TrialBalanceQuery(Optional<LocalDate> effectiveDateAsOf)
public record TrialBalanceRow(DeclaredAccount account, CurrencyBalance balance)
public record TrialBalanceReport(...)
public sealed interface TrialBalanceResult
```

- Purpose: request, carry, and result-wrap one as-of trial balance for the selected book
- Result variants: `Reported`, `Rejected`
- Report semantics: the report carries `BookIdentity`, `PostingCoverage`, and one
  fiscal-year-anchored comparative row set in addition to the current rows

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
public record FinancialPositionQuery(Optional<LocalDate> effectiveDateAsOf)
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
- Comparative semantics: the report also carries fiscal-year-anchored comparative sections for the
  comparison as-of date

## `IncomeStatementQuery`, `IncomeStatementRow`, `IncomeStatementSection`, `IncomeStatementReport`, And `IncomeStatementResult`

These types own the public bounded income-statement surface.

```java
public record IncomeStatementQuery(LocalDate effectiveDateFrom, LocalDate effectiveDateTo)
public record IncomeStatementRow(...)
public record IncomeStatementSection(...)
public record IncomeStatementReport(...)
public sealed interface IncomeStatementResult
```

- Purpose: request and carry one bounded income statement grouped by nominal account type
- Result variants: `Reported`, `Rejected`
- Totals: the report includes per-section totals plus book-wide `netIncomeTotals`
- Comparative semantics: the report also carries fiscal-year-anchored comparative sections and
  `comparativeNetIncomeTotals`

## `ChangesInEquityQuery`, `ChangesInEquityRow`, `ChangesInEquityReport`, And `ChangesInEquityResult`

These types own the public bounded statement-of-changes-in-equity surface.

```java
public record ChangesInEquityQuery(LocalDate effectiveDateFrom, LocalDate effectiveDateTo)
public record ChangesInEquityRow(...)
public record ChangesInEquityReport(...)
public sealed interface ChangesInEquityResult
```

- Purpose: request and carry opening, movement, and closing equity balances for one bounded period
- Result variants: `Reported`, `Rejected`
- Row semantics: every row publishes `lineClassification` plus `lineKind`; derived current-period
  result movements are explicit `lineKind: CURRENT_PERIOD_RESULT` rows
- Comparative semantics: the report also carries one comparative prior-period rows/totals set

## `FinancialPositionCriteria`, `FinancialPositionRowView`, `FinancialPositionSectionView`, And `FinancialPositionView`

These executor-owned local bookkeeping types carry the as-of financial-position model before it is
projected into public DTOs.

```java
public record FinancialPositionCriteria(Optional<LocalDate> effectiveDateAsOf)
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
public record IncomeStatementCriteria(LocalDate effectiveDateFrom, LocalDate effectiveDateTo)
public record IncomeStatementRowView(...)
public record IncomeStatementSectionView(...)
public record IncomeStatementView(...)
```

- Purpose: keep nominal-account movement shaping local to bookkeeping until the published-language
  translator renders it into public report DTOs

## `ChangesInEquityCriteria`, `ChangesInEquityRowView`, And `ChangesInEquityView`

These executor-owned local bookkeeping types carry the bounded changes-in-equity model before it
is projected into public DTOs.

```java
public record ChangesInEquityCriteria(LocalDate effectiveDateFrom, LocalDate effectiveDateTo)
public record ChangesInEquityRowView(...)
public record ChangesInEquityView(...)
```

- Purpose: keep equity opening/movement/closing shaping local to bookkeeping until the
  published-language translator renders it into public report DTOs

## `PeriodResultTransferDraft`, `PeriodResultTransferOutcome`, `ResultHoldingSelection`, `AcceptedResultHoldingSelection`, `RejectedResultHoldingSelection`, `PeriodResultTransferPlan`, `PeriodResultTransferPlanner`, And `PeriodResultTransferService`

These executor-owned local bookkeeping types own period-result-transfer generation and durable close
semantics before the public administration surface is projected.

```java
public record PeriodResultTransferDraft(...)
public sealed interface PeriodResultTransferOutcome
public sealed interface ResultHoldingSelection
public final class AcceptedResultHoldingSelection
public final class RejectedResultHoldingSelection
public record PeriodResultTransferPlan(...)
public final class PeriodResultTransferPlanner
public final class PeriodResultTransferService
```

- `PeriodResultTransferDraft`: store-ready close payload containing the reporting period, the close time,
  and every generated posting draft
- `PeriodResultTransferOutcome`: closed family of accepted-versus-rejected local close outcomes
- `ResultHoldingSelection`: closed result for the policy-owned result-holding account lookup
- `AcceptedResultHoldingSelection`: accepted result-holding selection carrying the chosen account
- `RejectedResultHoldingSelection`: rejected result-holding selection carrying the deterministic
  administration rejection plus candidate account codes
- `PeriodResultTransferPlan`: generated close posting drafts plus the transferred totals that the
  published close result projects afterward
- `PeriodResultTransferPlanner`: bookkeeping-domain planner that selects the policy-owned result-holding
  account, validates close-horizon rules, and generates the `PostingKind.PERIOD_RESULT_TRANSFER` drafts plus
  published transferred totals for one contiguous reporting period
- `PeriodResultTransferService`: application service that coordinates lifecycle inspection, account
  catalog/store access, planner output, and durable close persistence instead of owning the close
  recipe itself

## `BookAdministrationRejection`

`BookAdministrationRejection` is the closed family of deterministic lifecycle and account-registry
refusals.

```java
public sealed interface BookAdministrationRejection
```

- Variants: `BookAlreadyInitialized`, `BookNotInitialized`, `BookContainsSchema`,
  `AccountRoleConflict`, `ResultHoldingAccountCandidateMissing`,
  `ResultHoldingAccountCandidateAmbiguous`, `PeriodResultTransferMustStartAt`

## `BookQueryRejection`

`BookQueryRejection` is the closed family of deterministic query/report refusals.

```java
public sealed interface BookQueryRejection
```

- Variants: `BookNotInitialized`, `UnknownAccount`, `PostingNotFound`

## `BookMaintenanceArtifactRole`, `BookMaintenanceVerificationFailure`, `BookMaintenanceRejection`, And `PublicPathHint`

These public maintenance-contract types keep verification-driven maintenance outcomes typed and
redacted at the published-language edge.

```java
public enum BookMaintenanceArtifactRole implements WireValue
public enum BookMaintenanceVerificationFailure implements WireValue
public sealed interface BookMaintenanceRejection
public record PublicPathHint(String value)
```

- `BookMaintenanceArtifactRole`: keeps maintenance failures precise about whether the rejected
  artifact was the live book, backup source, rollback artifact, or restored target
- `BookMaintenanceVerificationFailure`: keeps deterministic maintenance verification failures typed
  as missing, blank SQLite, foreign SQLite, unsupported format version, incomplete FinGrind book,
  or protected-book verification failure
- `PublicPathHint`: redacts filesystem paths to `<redacted>` or
  `<redacted>/<smallest-distinguishing-trailing-context>` so public maintenance output proves
  which artifact failed without leaking absolute operator paths
- Boundary: `BookMaintenanceRejection.ArtifactBusy` and
  `BookMaintenanceRejection.ArtifactVerificationFailed` use these types so backup, restore, and
  rekey-recovery refusals preserve artifact role, failure class, and redacted path hints as
  first-class machine contract
  instead of collapsing maintenance verification into generic runtime failure text

## `BookMaintenanceRejection`

`BookMaintenanceRejection` is the closed family of deterministic maintenance-workflow refusals.

```java
public sealed interface BookMaintenanceRejection
```

- Variants: `BookHasBlockingArtifacts`, `BackupSourceHasBlockingArtifacts`,
  `ArtifactBusy`, `BackupDestinationAlreadyExists`, `BackupKeyFileAlreadyExists`,
  `ArtifactVerificationFailed`,
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
