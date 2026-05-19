package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.time.Instant;

/** Public SQLite-backed session for rekey workflows only. */
public interface SqliteRekeySession extends AutoCloseable {
  /** Rekeys one opened protected book using one replacement passphrase source and timestamp. */
  ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver,
      Instant rekeyedAt);

  @Override
  void close();
}
