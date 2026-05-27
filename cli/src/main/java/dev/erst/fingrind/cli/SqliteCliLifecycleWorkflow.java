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
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.ProtectedBookMaintenanceService;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.sqlite.SqliteAdministrationSessions;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqliteProtectedBookMaintenanceStore;
import dev.erst.fingrind.sqlite.SqliteRekeySessions;
import dev.erst.fingrind.sqlite.secret.SqlitePassphraseIntent;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** SQLite-backed lifecycle workflow for initialization, key rotation, backup, and rollback. */
final class SqliteCliLifecycleWorkflow implements CliBookLifecycleWorkflow {
  private final Clock clock;
  private final CliBookPassphraseResolver passphraseResolver;
  private final ProtectedBookMaintenanceService maintenanceService;

  SqliteCliLifecycleWorkflow(Clock clock, CliBookPassphraseResolver passphraseResolver) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.passphraseResolver = Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    this.maintenanceService =
        new ProtectedBookMaintenanceService(
            this.clock, new SqliteProtectedBookMaintenanceStore(this.passphraseResolver));
  }

  @Override
  public ContractDecision<OpenBookResult> openBook(BookAccess bookAccess, OpenBookCommand command) {
    return SqliteCliWorkflowSessions.withAdministrationSession(
        SqliteAdministrationSessions.openResolved(
            bookAccess,
            SqliteBookSessionMode.READ_WRITE_CREATE,
            passphraseResolver,
            SqlitePassphraseIntent.NEW_SECRET),
        bookSession ->
            BookkeepingPublishedLanguageTranslator.toPublished(
                new BookAdministrationService(bookSession, bookSession, bookSession, clock)
                    .openBook(BookkeepingPublishedLanguageTranslator.fromPublished(command))));
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, PassphraseSource replacementPassphraseSource) {
    return SqliteCliWorkflowSessions.withRekeySession(
        SqliteRekeySessions.openResolved(
            bookAccess, passphraseResolver, SqlitePassphraseIntent.EXISTING_SECRET),
        bookSession ->
            bookSession.rekeyBook(
                replacementPassphraseSource, passphraseResolver, clock.instant()));
  }

  @Override
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    return maintenanceService.backupBook(bookAccess, backupFilePath, backupBookKeyFilePath);
  }

  @Override
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    return maintenanceService.restoreBook(bookFilePath, backupFilePath, backupBookKeyFilePath);
  }

  @Override
  public ContractDecision<RekeyRollbackResult> inspectRekeyRollback(Path bookFilePath) {
    return maintenanceService.inspectRekeyRollback(bookFilePath);
  }

  @Override
  public ContractDecision<RekeyRollbackResult> deleteRekeyRollback(
      BookAccess bookAccess, @Nullable Path rollbackArtifactPath) {
    return maintenanceService.deleteRekeyRollback(bookAccess, rollbackArtifactPath);
  }

  @Override
  public ContractDecision<RekeyRollbackResult> restoreRekeyRollback(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      PassphraseSource expectedPassphraseSource) {
    return maintenanceService.restoreRekeyRollback(
        bookFilePath, rollbackArtifactPath, expectedPassphraseSource);
  }
}
