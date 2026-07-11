package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Facade that routes SQLite reads to narrower query and reporting helpers. */
final class SqliteStoreReadOperations {
  private final SqliteStoreQueryOperations queryOperations;
  private final SqliteStorePostingQueryOperations postingQueryOperations;
  private final ReportingReadOperations reporting;
  private final PostingHistoryReadOperations postingHistory;

  SqliteStoreReadOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(lifecycle, "lifecycle");
    this.queryOperations = new SqliteStoreQueryOperations(context, lifecycle);
    this.postingQueryOperations = new SqliteStorePostingQueryOperations(context, lifecycle);
    SqliteStoreReportOperations reportOperations =
        new SqliteStoreReportOperations(context, lifecycle);
    this.reporting = new ReportingReadOperations(postingQueryOperations, reportOperations);
    this.postingHistory = new PostingHistoryReadOperations(postingQueryOperations);
  }

  BookLifecycleInspection inspectBook() {
    return queryOperations.inspectBook();
  }

  Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return queryOperations.findAccount(accountCode);
  }

  Optional<InventoryAccountState> findInventoryAccountState(AccountCode inventoryAccountCode) {
    return queryOperations.findInventoryAccountState(inventoryAccountCode);
  }

  List<InventoryMovementRecord> inventoryMovements(PostingId postingId) {
    return queryOperations.inventoryMovements(postingId);
  }

  Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return queryOperations.findAccounts(accountCodes);
  }

  List<RegisteredAccount> allAccounts() {
    return queryOperations.allAccounts();
  }

  Optional<DeclaredTaxRegistration> findTaxRegistration(TaxRegistrationId taxRegistrationId) {
    return queryOperations.findTaxRegistration(taxRegistrationId);
  }

  List<DeclaredTaxRegistration> allTaxRegistrations() {
    return queryOperations.allTaxRegistrations();
  }

  TaxRegistrationPage listTaxRegistrations(ListTaxRegistrationsQuery query) {
    return queryOperations.listTaxRegistrations(query);
  }

  AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    return queryOperations.listAccounts(query);
  }

  Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    return postingQueryOperations.findExistingPosting(idempotencyKey);
  }

  Optional<CommittedPosting> findPosting(PostingId postingId) {
    return postingQueryOperations.findPosting(postingId);
  }

  Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return postingQueryOperations.findReversalFor(priorPostingId);
  }

  PostingHistoryPage listPostings(PostingHistoryQuery query) {
    return postingQueryOperations.listPostings(query);
  }

  PostingHistoryReadOperations postingHistory() {
    return postingHistory;
  }

  ReportingReadOperations reporting() {
    return reporting;
  }

  /** Reads posting-history facts that support close policy and posting-history projections. */
  static final class PostingHistoryReadOperations {
    private final SqliteStorePostingQueryOperations postingQueryOperations;

    private PostingHistoryReadOperations(SqliteStorePostingQueryOperations postingQueryOperations) {
      this.postingQueryOperations = postingQueryOperations;
    }

    List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return postingQueryOperations.postings(effectiveDateRange);
    }

    Optional<LocalDate> earliestPostingEffectiveDate() {
      return postingQueryOperations.earliestPostingEffectiveDate();
    }

    Optional<LocalDate> transferredThroughEffectiveDate() {
      return postingQueryOperations.transferredThroughEffectiveDate();
    }
  }

  /** Reads report-facing balances, statement projections, and reporting-window postings. */
  static final class ReportingReadOperations {
    private final SqliteStorePostingQueryOperations postingQueryOperations;
    private final SqliteStoreReportOperations reportOperations;

    private ReportingReadOperations(
        SqliteStorePostingQueryOperations postingQueryOperations,
        SqliteStoreReportOperations reportOperations) {
      this.postingQueryOperations = postingQueryOperations;
      this.reportOperations = reportOperations;
    }

    Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
      return reportOperations.accountBalance(query);
    }

    List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      return reportOperations.accountTotals(effectiveDateRange, postingCoverage);
    }

    List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return postingQueryOperations.postings(effectiveDateRange);
    }

    Optional<LocalDate> latestPostingEffectiveDate() {
      return reportOperations.latestPostingEffectiveDate();
    }

    TrialBalanceView trialBalance(TrialBalanceCriteria query) {
      return reportOperations.trialBalance(query);
    }

    AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
      return reportOperations.accountLedger(query, account);
    }

    PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
      return reportOperations.periodSummary(query);
    }
  }
}
