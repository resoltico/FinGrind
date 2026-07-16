package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.AccountLookupStore;
import dev.erst.fingrind.executor.spi.AccrualCutoffLookupStore;
import dev.erst.fingrind.executor.spi.InventoryMovementLookupStore;
import dev.erst.fingrind.executor.spi.InventoryStateLookupStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Account, inventory, and accrual-cutoff defaults for transaction-scoped posting validation. */
interface SqliteTransactionValidationAccountCapabilityView
    extends AccountLookupStore,
        AccrualCutoffLookupStore,
        InventoryMovementLookupStore,
        InventoryStateLookupStore {
  @Override
  default Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .findAccount(accountCode);
  }

  @Override
  default Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .findAccounts(accountCodes);
  }

  @Override
  default Optional<InventoryAccountState> findInventoryAccountState(
      AccountCode inventoryAccountCode) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .findInventoryAccountState(inventoryAccountCode);
  }

  @Override
  default Optional<AccrualCutoffRecord> findAccrualCutoff(AccrualCutoffId accrualCutoffId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .findAccrualCutoff(accrualCutoffId);
  }

  @Override
  default List<InventoryMovementRecord> inventoryMovements(PostingId postingId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .validationQueries()
        .inventoryMovements(postingId);
  }
}
