package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationRegistryMutationResult;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.executor.ProtectedBookMaintenanceService;
import dev.erst.fingrind.sqlite.SqliteProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** SQLite-backed lifecycle workflow for initialization, key rotation, and backup. */
final class SqliteCliLifecycleWorkflow implements CliBookLifecycleWorkflow {
  private final ProtectedBookMaintenanceService maintenanceService;
  private final SqliteCliOpenBookWorkflow openBookWorkflow;

  SqliteCliLifecycleWorkflow(Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this(clock, passphraseResolver, SqliteCliOpenBookWorkflow::publishedOpenedBook);
  }

  SqliteCliLifecycleWorkflow(
      Clock clock,
      CliBookPassphraseResolver passphraseResolver,
      SqliteCliOpenBookWorkflow.OpenedBookResultFactory openedBookResultFactory) {
    Clock checkedClock = Objects.requireNonNull(clock, "clock");
    CliBookPassphraseResolver checkedPassphraseResolver =
        Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    this.openBookWorkflow =
        new SqliteCliOpenBookWorkflow(
            checkedClock,
            checkedPassphraseResolver,
            Objects.requireNonNull(openedBookResultFactory, "openedBookResultFactory"));
    this.maintenanceService =
        new ProtectedBookMaintenanceService(
            checkedClock, new SqliteProtectedBookMaintenanceStore(checkedPassphraseResolver));
  }

  @Override
  public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command) {
    return openBookWorkflow.openBook(bookAccess, command);
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, Path newBookKeyFilePath) {
    return maintenanceService.rekeyBook(bookAccess, newBookKeyFilePath);
  }

  @Override
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath, UUID backupId) {
    return maintenanceService.backupBook(
        bookAccess, backupFilePath, backupBookKeyFilePath, backupId);
  }

  @Override
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupFilePath,
      Path backupKeyFilePath,
      List<AttestationCredentialSource> attestationCredentialSources) {
    return maintenanceService.restoreBook(
        bookFilePath,
        newBookKeyFilePath,
        backupFilePath,
        backupKeyFilePath,
        attestationCredentialSources);
  }

  @Override
  public ContractDecision<AttestationRegistryMutationResult> mutateRegistry(
      BookAccess bookAccess, AttestationRegistryMutation mutation) {
    return maintenanceService.mutateRegistry(bookAccess, mutation);
  }
}
