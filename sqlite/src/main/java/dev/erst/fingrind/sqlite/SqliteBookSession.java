package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.executor.BookAdministrationSession;
import dev.erst.fingrind.executor.BookReadSession;
import dev.erst.fingrind.executor.LedgerPlanSession;
import dev.erst.fingrind.executor.PostingBookSession;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.Optional;

/** Public SQLite-backed book-session surface for CLI, tooling, and fuzz harnesses. */
public interface SqliteBookSession extends LedgerPlanSession, AutoCloseable {
  /** Returns the administration view for opening books and managing accounts. */
  @Override
  BookAdministrationSession administrationSession();

  /** Returns the posting view for preflight and commit operations. */
  @Override
  PostingBookSession postingSession();

  /** Returns the query view for read-only inspection and listing flows. */
  @Override
  BookReadSession readSession();

  /** Finds one declared account by code when it exists. */
  Optional<RegisteredAccount> findAccount(AccountCode accountCode);

  /** Finds one committed posting by idempotency key when it exists. */
  Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey);

  /** Rekeys one initialized FinGrind book using a contract-level replacement secret source. */
  dev.erst.fingrind.contract.ContractDecision<RekeyBookResult> rekeyBook(
      dev.erst.fingrind.contract.BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver);

  @Override
  void close();
}
