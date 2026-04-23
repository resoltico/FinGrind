package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.executor.BookAdministrationSession;
import dev.erst.fingrind.executor.BookReadSession;
import dev.erst.fingrind.executor.LedgerPlanSession;
import dev.erst.fingrind.executor.PostingBookSession;
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
  Optional<DeclaredAccount> findAccount(AccountCode accountCode);

  /** Finds one committed posting by idempotency key when it exists. */
  Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey);

  /** Rekeys one initialized FinGrind book and verifies the replacement secret durably. */
  RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase);

  @Override
  void close();
}
