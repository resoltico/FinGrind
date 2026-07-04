package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Lifecycle seam for opening, protecting, backing up, and rolling back books. */
interface CliBookLifecycleWorkflow {
  /** Opens or initializes one protected book through the selected access route. */
  ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command);

  /** Replaces the passphrase material protecting one existing book. */
  ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, PassphraseSource replacementPassphraseSource);

  /** Creates a backup copy plus its companion key artifact. */
  ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath);

  /** Restores one protected book from a backup artifact set. */
  ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath, Path bookKeyFilePath, Path backupFilePath, Path backupKeyFilePath);

  /** Reads rollback metadata for the most recent interrupted rekey flow. */
  ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath);

  /** Deletes rollback artifacts once the operator accepts their removal. */
  ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
      BookAccess bookAccess, @Nullable Path rollbackArtifactPath);

  /** Restores the rollback snapshot back into the primary book location. */
  ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      PassphraseSource expectedPassphraseSource);
}
