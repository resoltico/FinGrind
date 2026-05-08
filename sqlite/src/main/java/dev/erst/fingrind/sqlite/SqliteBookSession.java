package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.AtomicBookStore;
import java.util.Optional;

/** Public SQLite-backed book-session surface for CLI, tooling, and fuzz harnesses. */
public interface SqliteBookSession extends AtomicBookStore, AutoCloseable {
  /** Finds one declared account by code when it exists. */
  @Override
  Optional<RegisteredAccount> findAccount(AccountCode accountCode);

  /** Finds one committed posting by idempotency key when it exists. */
  @Override
  Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey);

  /** Rekeys one initialized FinGrind book using a contract-level replacement secret source. */
  dev.erst.fingrind.contract.ContractDecision<RekeyBookResult> rekeyBook(
      dev.erst.fingrind.contract.BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver);

  @Override
  void close();
}
