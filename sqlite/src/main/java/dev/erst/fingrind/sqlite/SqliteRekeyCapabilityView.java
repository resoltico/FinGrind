package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.time.Instant;

/** Shared rekey delegation defaults for SQLite capability wrappers. */
interface SqliteRekeyCapabilityView extends SqliteRekeySession {
  /** Returns the mutation operations owner for the underlying SQLite store. */
  SqliteStoreMutationOperations storeMutationOperations();

  /** Returns the protected-book path owned by the underlying SQLite store. */
  java.nio.file.Path storeBookPath();

  @Override
  default ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess.PassphraseSource replacementPassphraseSource,
      SqlitePassphraseResolver passphraseResolver,
      Instant rekeyedAt) {
    return ContractDecision.accepted(
        storeMutationOperations()
            .rekeyBook(
                passphraseResolver
                    .resolve(
                        storeBookPath(),
                        replacementPassphraseSource,
                        SqlitePassphraseIntent.NEW_SECRET)
                    .requireAccepted(),
                rekeyedAt));
  }
}
