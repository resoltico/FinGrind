package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
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

  SqliteStoreReportOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    lifecycle.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase -> context.postingBalanceReader().accountBalance(activeDatabase, query));
  }

  List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    lifecycle.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingBalanceReader()
                .loadAccountTotals(activeDatabase, effectiveDateRange, postingCoverage));
  }

  Optional<LocalDate> latestPostingEffectiveDate() {
    lifecycle.ensureOpenSession();
    return queryReport(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteStatementQueries.loadOptionalText(
                    activeDatabase,
                    SqlitePostingSql.FIND_LATEST_POSTING_EFFECTIVE_DATE,
                    statement -> {})
                .map(
                    text ->
                        CanonicalTemporalText.parseLocalDate(text, "postingFact.effectiveDate")));
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

  List<InventoryValuationMovementRecord> inventoryValuationMovements(
      Optional<LocalDate> effectiveDateAsOf) {
    lifecycle.ensureOpenSession();
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    return queryReport(
        "Failed to query SQLite inventory valuation movements.",
        activeDatabase -> {
          try (SqliteNativeStatement statement =
              activeDatabase.prepare(
                  SqliteInventoryCostingSql.LOAD_INVENTORY_VALUATION_MOVEMENTS)) {
            String effectiveDateAsOfText = effectiveDateAsOf.map(LocalDate::toString).orElse(null);
            statement.bindText(1, effectiveDateAsOfText);
            statement.bindText(2, effectiveDateAsOfText);
            List<InventoryValuationMovementRecord> movements = new java.util.ArrayList<>();
            while (statement.step() == SqliteNativeResultCode.code("ROW")) {
              movements.add(
                  new InventoryValuationMovementRecord(
                      new dev.erst.fingrind.core.AccountCode(
                          SqlitePostingMapper.requiredText(statement, 0)),
                      CanonicalTemporalText.parseLocalDate(
                          SqlitePostingMapper.requiredText(statement, 1),
                          "inventoryMovement.effectiveDate"),
                      statement.columnLong(2),
                      dev.erst.fingrind.core.InventoryMovementKind.fromWireValue(
                          SqlitePostingMapper.requiredText(statement, 3)),
                      statement.columnLong(4),
                      statement.columnLong(5),
                      new dev.erst.fingrind.core.PostingId(
                          SqlitePostingMapper.requiredText(statement, 6))));
            }
            return List.copyOf(movements);
          }
        });
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
      return SqliteStoreOperations.retryTransientLockFailures(
          () -> query.run(initializedReportDatabase()));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    }
  }
}
