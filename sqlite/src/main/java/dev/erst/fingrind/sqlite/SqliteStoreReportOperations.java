package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import java.nio.file.Files;
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

  private final SqliteStoreContext store;

  SqliteStoreReportOperations(SqliteStoreContext store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
    store.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase -> store.postingReader().accountBalance(activeDatabase, query));
  }

  TrialBalanceReport trialBalance(TrialBalanceQuery query) {
    store.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase -> store.reportReader().trialBalance(activeDatabase, query));
  }

  AccountLedgerReport accountLedger(AccountLedgerQuery query, DeclaredAccount account) {
    store.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase -> store.reportReader().accountLedger(activeDatabase, query, account));
  }

  PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
    store.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase -> store.reportReader().periodSummary(activeDatabase, query));
  }

  private SqliteNativeDatabase initializedReportDatabase() {
    if (Files.notExists(store.bookPath())) {
      throw new IllegalStateException(SqliteBookContract.NOT_INITIALIZED_BOOK_MESSAGE);
    }
    SqliteSessionDatabase activeDatabase = store.database();
    store.requireInitializedBook(activeDatabase.nativeDatabase());
    return activeDatabase.nativeDatabase();
  }

  private <T> T queryReport(String failureMessage, NativeReport<T> query) {
    try {
      return query.run(initializedReportDatabase());
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    }
  }
}
