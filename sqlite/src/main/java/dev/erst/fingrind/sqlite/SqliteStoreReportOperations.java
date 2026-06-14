package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Reporting reads over one initialized SQLite-backed book session. */
final class SqliteStoreReportOperations {
  /** One initialized-book report query executed against a live SQLite handle. */
  @FunctionalInterface
  private interface NativeReport<T> {
    /** Runs one report query against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;
  private final SqliteStoreQueryOperations queryOperations;

  SqliteStoreReportOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.queryOperations = new SqliteStoreQueryOperations(this.context, this.lifecycle);
  }

  Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    lifecycle.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase -> context.postingBalanceReader().accountBalance(activeDatabase, query));
  }

  Optional<LocalDate> latestPostingEffectiveDate() {
    return queryOperations.latestPostingEffectiveDate();
  }

  TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    lifecycle.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase -> context.reportReader().trialBalance(activeDatabase, query));
  }

  AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    lifecycle.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase -> context.reportReader().accountLedger(activeDatabase, query, account));
  }

  PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    lifecycle.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase -> context.reportReader().periodSummary(activeDatabase, query));
  }

  private SqliteNativeDatabase initializedReportDatabase() {
    if (Files.notExists(context.bookPath())) {
      throw new IllegalStateException(SqliteBookContract.NOT_INITIALIZED_BOOK_MESSAGE);
    }
    SqliteNativeDatabase activeDatabase = lifecycle.database();
    lifecycle.requireInitializedBook(activeDatabase);
    return activeDatabase;
  }

  private <T> T queryReport(String failureMessage, NativeReport<T> query) {
    try {
      return query.run(initializedReportDatabase());
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    }
  }
}
