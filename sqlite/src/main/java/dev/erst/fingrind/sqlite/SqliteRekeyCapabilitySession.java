package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseResolver;
import java.time.Instant;

/** Rekey-only wrapper over the shared SQLite store core. */
final class SqliteRekeyCapabilitySession extends SqliteDelegatingSession
    implements SqliteRekeySession {
  SqliteRekeyCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver,
      Instant rekeyedAt) {
    return store.rekeyBook(replacementPassphraseSource, passphraseResolver, rekeyedAt);
  }

  @Override
  public void close() {
    closeStore();
  }
}
