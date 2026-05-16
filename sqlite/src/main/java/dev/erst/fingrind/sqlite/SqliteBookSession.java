package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import dev.erst.fingrind.executor.spi.PeriodCloseStore;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import java.time.Instant;
import java.util.Optional;

/** Public SQLite-backed book-session surface for CLI, tooling, and fuzz harnesses. */
public interface SqliteBookSession
    extends BookAdministrationStore,
        BookkeepingReadStore,
        PostingValidationStore,
        PostingCommitStore,
        PeriodCloseStore,
        LedgerPlanTransaction,
        AccountCatalogStore,
        AutoCloseable {
  /** Finds one declared account by code when it exists. */
  @Override
  Optional<RegisteredAccount> findAccount(AccountCode accountCode);

  /** Finds one committed posting by idempotency key when it exists. */
  @Override
  Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey);

  /** Rekeys one initialized FinGrind book using a contract-level replacement secret source. */
  dev.erst.fingrind.contract.runtime.ContractDecision<RekeyBookResult> rekeyBook(
      dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver,
      Instant rekeyedAt);

  @Override
  void close();
}
