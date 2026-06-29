package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Account, tax-registry, and inspection reads over one SQLite-backed book session. */
final class SqliteStoreQueryOperations {
  /** One initialized-book point query executed against a live SQLite handle. */
  @FunctionalInterface
  private interface NativeQuery<T> {
    /** Runs one point query against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;

  SqliteStoreQueryOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  BookLifecycleInspection inspectBook() {
    lifecycle.ensureOpenSession();
    if (Files.notExists(context.bookPath())) {
      return SqliteBookLifecycleInspectionMapper.fromMissingPath();
    }
    try {
      return SqliteStoreOperations.retryTransientLockFailures(
          () -> {
            SqliteNativeDatabase activeDatabase = lifecycle.database();
            SqliteBookStateSnapshot snapshot = context.bookStateReader().snapshot(activeDatabase);
            return SqliteBookLifecycleInspectionMapper.fromSnapshot(snapshot, activeDatabase);
          });
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to inspect SQLite book.", exception);
    }
  }

  Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteAccountStatementQueries.findOneAccount(activeDatabase, accountCode));
  }

  Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    lifecycle.ensureOpenSession();
    Set<AccountCode> requestedAccounts =
        new LinkedHashSet<>(Objects.requireNonNull(accountCodes, "accountCodes"));
    if (requestedAccounts.isEmpty()) {
      return Map.of();
    }
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteAccountStatementQueries.findAccounts(activeDatabase, requestedAccounts));
  }

  List<RegisteredAccount> allAccounts() {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteAccountStatementQueries.loadAllAccounts(
                activeDatabase, SqlitePostingSql.LOAD_ALL_ACCOUNTS));
  }

  Optional<DeclaredTaxRegistration> findTaxRegistration(TaxRegistrationId taxRegistrationId) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteTaxStatementQueries.findOneTaxRegistration(activeDatabase, taxRegistrationId));
  }

  List<DeclaredTaxRegistration> allTaxRegistrations() {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.", SqliteTaxStatementQueries::loadAllTaxRegistrations);
  }

  TaxRegistrationPage listTaxRegistrations(ListTaxRegistrationsQuery query) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> SqliteTaxStatementQueries.loadTaxRegistrationPage(activeDatabase, query));
  }

  AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> SqliteAccountStatementQueries.loadAccountPage(activeDatabase, query));
  }

  private <T> T queryInitialized(String failureMessage, NativeQuery<T> query) {
    try {
      return SqliteStoreOperations.retryTransientLockFailures(
          () -> query.run(lifecycle.initializedQueryDatabase()));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    }
  }
}
