package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.executor.BookReadService;
import dev.erst.fingrind.executor.TaxReadService;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqliteReadSession;
import dev.erst.fingrind.sqlite.SqliteReadSessions;
import java.util.function.Function;

/** Opens one protected SQLite read session for focused CLI read capabilities. */
@FunctionalInterface
interface SqliteCliReadSessionOperations {
  /** Returns the owner of non-interactive and prompted book passphrase resolution. */
  CliBookPassphraseResolver passphraseResolver();

  /** Runs one bookkeeping read against a freshly opened protected book session. */
  default <T> ContractDecision<T> withBookRead(
      BookAccess bookAccess, Function<BookReadService, T> work) {
    return withRead(
        bookAccess, bookSession -> work.apply(new BookReadService(bookSession, bookSession)));
  }

  /** Runs one tax read against a freshly opened protected book session. */
  default <T> ContractDecision<T> withTaxRead(
      BookAccess bookAccess, Function<TaxReadService, T> work) {
    return withRead(bookAccess, bookSession -> work.apply(new TaxReadService(bookSession)));
  }

  /** Owns one protected SQLite read-session lifetime. */
  default <T> ContractDecision<T> withRead(
      BookAccess bookAccess, Function<SqliteReadSession, T> work) {
    return SqliteCliWorkflowSessions.withReadSession(
        SqliteReadSessions.openResolved(
            bookAccess, passphraseResolver(), SqlitePassphraseIntent.EXISTING_SECRET),
        work);
  }
}
