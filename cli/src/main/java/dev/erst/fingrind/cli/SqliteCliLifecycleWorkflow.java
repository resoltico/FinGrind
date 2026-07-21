package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.executor.AttestationCredentialException;
import dev.erst.fingrind.executor.AttestationGenesisFactory;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.ProtectedBookMaintenanceService;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingRequestPublishedLanguageTranslator;
import dev.erst.fingrind.sqlite.SqliteAdministrationSessions;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqliteProtectedBookMaintenanceStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
    ContractFailure destinationFailure = occupiedBookDestinationFailure(bookAccess.bookFilePath());
    if (destinationFailure != null) {
      return ContractDecision.rejected(destinationFailure);
    }
    return genesisEvidence(command)
        .fold(
            genesisEvidence ->
                SqliteCliWorkflowSessions.withAdministrationSession(
                    SqliteAdministrationSessions.openNewBookResolved(
                        bookAccess, passphraseResolver, SqlitePassphraseIntent.NEW_SECRET),
                    bookSession ->
                        BookkeepingPublishedLanguageTranslator.toPublished(
                            new BookAdministrationService(
                                    bookSession, bookSession, bookSession, clock)
                                .openAttestedBook(
                                    BookkeepingRequestPublishedLanguageTranslator.fromPublished(
                                        command),
                                    genesisEvidence))),
            ContractDecision::rejected);
  }

  private ContractDecision<AttestationEvidence> genesisEvidence(OpenBookCommand command) {
    OpenBookCommand checkedCommand = Objects.requireNonNull(command, "command");
    try {
      return ContractDecision.accepted(
          AttestationGenesisFactory.create(
              checkedCommand.bookIdentity(),
              clock.instant(),
              checkedCommand.attestationFounders()));
    } catch (AttestationCredentialException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
              exception.credentialPath(),
              "FinGrind could not open the selected attestation founder credential.",
              "Confirm the founder key and passphrase files are readable, distinct, and match, then rerun open-book.",
              ProtocolOptions.Attestation.FOUNDER_KEY_FILE));
    }
  }

  private static @Nullable ContractFailure occupiedBookDestinationFailure(Path bookFilePath) {
    Path normalizedBookFilePath = bookFilePath.toAbsolutePath().normalize();
    if (!Files.exists(normalizedBookFilePath, LinkOption.NOFOLLOW_LINKS)) {
      return null;
    }
    return ContractErrors.Descriptor.BOOK_DESTINATION_OCCUPIED.failure(
        "The selected --book-file destination already exists; "
            + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
            + " will not access or replace it.",
        "Choose a missing --book-file destination before opening a new book.",
        ProtocolBookAccessOptions.BOOK_FILE);
  }

  @Override
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, Path newBookKeyFilePath) {
    return maintenanceService.rekeyBook(bookAccess, newBookKeyFilePath);
  }

  @Override
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath) {
    return maintenanceService.backupBook(bookAccess, backupFilePath, backupBookKeyFilePath);
  }

  @Override
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupFilePath,
      Path backupKeyFilePath,
      boolean replaceExistingBook) {
    return maintenanceService.restoreBook(
        bookFilePath, newBookKeyFilePath, backupFilePath, backupKeyFilePath, replaceExistingBook);
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
