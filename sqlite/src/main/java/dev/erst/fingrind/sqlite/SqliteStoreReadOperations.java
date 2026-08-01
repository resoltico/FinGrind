package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
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
  private final AccountCatalogReadOperations accountCatalog;
  private final TaxRegistrationReadOperations taxRegistrations;
  private final InventoryReadOperations inventory;
  private final SqliteStoreLifecycleReadOperations lifecycleContexts;
  private final PostingLookupReadOperations postingLookup;
  private final ReportingReadOperations reporting;
  private final PostingHistoryReadOperations postingHistory;

  SqliteStoreReadOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(lifecycle, "lifecycle");
    this.queryOperations = new SqliteStoreQueryOperations(context, lifecycle);
    this.postingQueryOperations = new SqliteStorePostingQueryOperations(context, lifecycle);
    this.accountCatalog = new AccountCatalogReadOperations(queryOperations);
    this.taxRegistrations = new TaxRegistrationReadOperations(queryOperations);
    this.inventory = new InventoryReadOperations(queryOperations);
    this.lifecycleContexts = new SqliteStoreLifecycleReadOperations(queryOperations, lifecycle);
    this.postingLookup = new PostingLookupReadOperations(postingQueryOperations);
    SqliteStoreReportOperations reportOperations =
        new SqliteStoreReportOperations(context, lifecycle);
    this.reporting = new ReportingReadOperations(postingQueryOperations, reportOperations);
    this.postingHistory = new PostingHistoryReadOperations(postingQueryOperations);
  }

  BookLifecycleInspection inspectBook() {
    return queryOperations.inspectBook();
  }

  AccountCatalogReadOperations accountCatalog() {
    return accountCatalog;
  }

  TaxRegistrationReadOperations taxRegistrations() {
    return taxRegistrations;
  }

  InventoryReadOperations inventory() {
    return inventory;
  }

  SqliteStoreLifecycleReadOperations.AccrualCutoffReadOperations accrualCutoffLifecycle() {
    return lifecycleContexts.accrualCutoff();
  }

  SqliteStoreLifecycleReadOperations.FixedAssetReadOperations fixedAssets() {
    return lifecycleContexts.fixedAssets();
  }

  SqliteStoreLifecycleReadOperations.FinancingReadOperations financing() {
    return lifecycleContexts.financing();
  }

  SqliteStoreLifecycleReadOperations.RealizedForeignExchangeReadOperations
      realizedForeignExchange() {
    return lifecycleContexts.realizedForeignExchange();
  }

  SqliteStoreLifecycleReadOperations.LatvianPayrollReadOperations latvianPayroll() {
    return lifecycleContexts.latvianPayroll();
  }

  PostingLookupReadOperations postingLookup() {
    return postingLookup;
  }

  PostingHistoryReadOperations postingHistory() {
    return postingHistory;
  }

  ReportingReadOperations reporting() {
    return reporting;
  }

  /** Reads registered-account facts and pages from the Account Registry context. */
  static final class AccountCatalogReadOperations {
    private final SqliteStoreQueryOperations queryOperations;

    private AccountCatalogReadOperations(SqliteStoreQueryOperations queryOperations) {
      this.queryOperations = queryOperations;
    }

    Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return queryOperations.findAccount(accountCode);
    }

    Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return queryOperations.findAccounts(accountCodes);
    }

    List<RegisteredAccount> allAccounts() {
      return queryOperations.allAccounts();
    }

    AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      return queryOperations.listAccounts(query);
    }
  }

  /** Reads durable tax-registration facts and pages from the Tax Registration context. */
  static final class TaxRegistrationReadOperations {
    private final SqliteStoreQueryOperations queryOperations;

    private TaxRegistrationReadOperations(SqliteStoreQueryOperations queryOperations) {
      this.queryOperations = queryOperations;
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
  }

  /** Reads materialized inventory facts that the Inventory Costing context owns. */
  static final class InventoryReadOperations {
    private final SqliteStoreQueryOperations queryOperations;

    private InventoryReadOperations(SqliteStoreQueryOperations queryOperations) {
      this.queryOperations = queryOperations;
    }

    Optional<InventoryAccountState> findInventoryAccountState(AccountCode inventoryAccountCode) {
      return queryOperations.findInventoryAccountState(inventoryAccountCode);
    }

    List<InventoryMovementRecord> inventoryMovements(PostingId postingId) {
      return queryOperations.inventoryMovements(postingId);
    }
  }

  /** Reads individual postings and pages from the Posting History context. */
  static final class PostingLookupReadOperations {
    private final SqliteStorePostingQueryOperations postingQueryOperations;

    private PostingLookupReadOperations(SqliteStorePostingQueryOperations postingQueryOperations) {
      this.postingQueryOperations = postingQueryOperations;
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

    Map<PostingId, AttestationCommit> attestationCommitsFor(Set<PostingId> postingIds) {
      return postingQueryOperations.attestationCommitsFor(postingIds);
    }

    PostingHistoryPage listPostings(PostingHistoryQuery query) {
      return postingQueryOperations.listPostings(query);
    }
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
