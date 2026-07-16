package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
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
  interface NativeQuery<T> {
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

  Optional<InventoryAccountState> findInventoryAccountState(AccountCode inventoryAccountCode) {
    lifecycle.ensureOpenSession();
    Objects.requireNonNull(inventoryAccountCode, "inventoryAccountCode");
    return queryInitialized(
        "Failed to query SQLite inventory state.",
        activeDatabase -> {
          Optional<RegisteredAccount> account =
              SqliteAccountStatementQueries.findOneAccount(activeDatabase, inventoryAccountCode);
          if (account.isEmpty() || account.orElseThrow().unitOfMeasure() == null) {
            return Optional.empty();
          }
          var unitOfMeasure = account.orElseThrow().unitOfMeasure();
          var bookIdentity =
              SqliteStatementQueries.loadBookIdentity(activeDatabase)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Initialized SQLite book is missing book identity."));
          return SqliteStatementQueries.queryWithStatement(
              activeDatabase,
              SqliteInventoryCostingSql.LOAD_INVENTORY_ON_HAND_BY_ACCOUNT,
              statement -> {
                statement.bindText(1, inventoryAccountCode.value());
                if (statement.step() == SqliteNativeResultCode.code("DONE")) {
                  return Optional.empty();
                }
                Quantity quantity =
                    Quantity.ofScaledUnits(unitOfMeasure.quantityScale(), statement.columnLong(0));
                Money costPoolMinor =
                    Money.ofMinorUnits(bookIdentity.functionalCurrency(), statement.columnLong(1));
                InventoryAccountState state =
                    new InventoryAccountState(
                        new WeightedAverageCostingMath.InventoryPool(quantity, costPoolMinor),
                        Optional.of(
                            CanonicalTemporalText.parseLocalDate(
                                SqlitePostingMapper.requiredText(statement, 2),
                                "inventoryOnHand.lastMovementDate")));
                if (statement.step() != SqliteNativeResultCode.code("DONE")) {
                  throw new IllegalStateException(
                      "SQLite inventory_on_hand query returned more than one row for account "
                          + inventoryAccountCode.value()
                          + ".");
                }
                return Optional.of(state);
              });
        });
  }

  List<InventoryMovementRecord> inventoryMovements(PostingId postingId) {
    lifecycle.ensureOpenSession();
    Objects.requireNonNull(postingId, "postingId");
    return queryInitialized(
        "Failed to query SQLite inventory movements.",
        activeDatabase -> {
          try (SqliteNativeStatement statement =
              activeDatabase.prepare(
                  SqliteInventoryCostingSql.LOAD_INVENTORY_MOVEMENTS_BY_POSTING_ID)) {
            statement.bindText(1, postingId.value());
            List<InventoryMovementRecord> movements = new java.util.ArrayList<>();
            while (statement.step() == SqliteNativeResultCode.code("ROW")) {
              movements.add(
                  new InventoryMovementRecord(
                      new AccountCode(SqlitePostingMapper.requiredText(statement, 0)),
                      CanonicalTemporalText.parseLocalDate(
                          SqlitePostingMapper.requiredText(statement, 1),
                          "inventoryMovement.effectiveDate"),
                      InventoryMovementKind.fromWireValue(
                          SqlitePostingMapper.requiredText(statement, 2)),
                      statement.columnLong(3),
                      statement.columnLong(4)));
            }
            return List.copyOf(movements);
          }
        });
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

  <T> T queryInitialized(String failureMessage, NativeQuery<T> query) {
    lifecycle.ensureOpenSession();
    try {
      return SqliteStoreOperations.retryTransientLockFailures(
          () -> query.run(lifecycle.initializedQueryDatabase()));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    }
  }
}
