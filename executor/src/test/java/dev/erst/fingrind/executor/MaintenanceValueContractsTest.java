package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingRequestPublishedLanguageTranslator;
import dev.erst.fingrind.executor.maintenance.BackupAcknowledgementConflictException;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Covers local maintenance failure values and control-flow exceptions at their contract boundary.
 */
class MaintenanceValueContractsTest {
  private static final Path BOOK_PATH = Path.of("book.sqlite");
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @Test
  void projectsMaintenanceFailuresWithoutChangingTheirPublishedContract() {
    ContractFailure contractFailure =
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
            BOOK_PATH, "storage unavailable", "repair storage", "--book-file");

    MaintenanceFailure maintenanceFailure = MaintenanceFailure.fromContractFailure(contractFailure);

    assertEquals(contractFailure, maintenanceFailure.toContractFailure());
  }

  @Test
  void retainsDeterministicRejectionsAndOriginalCausesInControlFlowExceptions() {
    ProtectedBookMaintenanceRejection rejection =
        new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(BOOK_PATH);
    IllegalStateException cause = new IllegalStateException("storage collision");
    ProtectedBookMaintenanceRejectionException exception =
        new ProtectedBookMaintenanceRejectionException(rejection, cause);

    assertEquals(rejection, exception.rejection());
    assertSame(cause, exception.getCause());
    BackupAcknowledgementConflictException conflict =
        new BackupAcknowledgementConflictException(BACKUP_ID);
    assertEquals(BACKUP_ID, conflict.backupId());
  }

  @Test
  void rejectsEmptyBlockingArtifactListsRatherThanPublishingAmbiguousRefusals() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(BOOK_PATH, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                BOOK_PATH, List.of()));
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void rejectsNullPublishedOpenBookCommandsAtTheTranslationBoundary() {
    assertThrows(
        NullPointerException.class,
        () -> BookkeepingRequestPublishedLanguageTranslator.fromPublished((OpenBookCommand) null));
  }

  @Test
  void translatesPublishedOpenBookIdentityWithoutRecreatingIt() {
    OpenBookCommand command = ExecutorAccountingTestSupport.openBookCommand();

    assertEquals(
        command.bookIdentity(),
        BookkeepingRequestPublishedLanguageTranslator.fromPublished(command));
  }

  @Test
  void rejectsUnattestedMaintenanceStoresBeforeInvokingAnyStorageOperation() {
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestedProtectedBookMaintenanceStore.require(new UnattestedStore()));
  }

  @Test
  void rejectsAggregateAttestationWhenTheTransactionCannotPersistIt() {
    AttestationPlanOperationAuthorizer authorizer =
        new AttestationPlanOperationAuthorizer(
            ignored -> {
              throw new AssertionError("The aggregate signer must not be invoked.");
            });
    authorizer.enterStep(0);
    authorizer.collectChildMutation(
        "declare-account", new AttestationOperationPreimages(new byte[] {1}, new byte[] {2}));

    assertThrows(
        UnsupportedOperationException.class,
        () ->
            new UnattestedLedgerPlanTransaction()
                .appendPlanAttestation(
                    "plan-id", Instant.parse("2026-07-21T00:00:00Z"), authorizer));
  }

  /** Exercises the default transaction policy without introducing a persistence implementation. */
  private static final class UnattestedLedgerPlanTransaction
      implements dev.erst.fingrind.executor.spi.LedgerPlanTransaction {
    @Override
    public void beginLedgerPlanTransaction() {}

    @Override
    public void commitLedgerPlanTransaction() {}

    @Override
    public void rollbackLedgerPlanTransaction() {}
  }

  /**
   * A deliberately incomplete store that proves attestation is mandatory at the adapter boundary.
   */
  private static final class UnattestedStore implements ProtectedBookMaintenanceStore {
    @Override
    public Path normalize(Path path, String argumentName) {
      throw unsupported();
    }

    @Override
    public PreparedPairPublication preparePairPublication(
        Path normalizedSecretTargetPath,
        Path normalizedBookTargetPath,
        RestoredBookTargetPolicy bookTargetPolicy,
        dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
            bookArtifactRole,
        dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
            secretArtifactRole) {
      throw unsupported();
    }

    @Override
    public List<Path> blockingArtifactsForBook(Path normalizedBookPath) {
      throw unsupported();
    }

    @Override
    public List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath) {
      throw unsupported();
    }

    @Override
    public BackupArtifactPairState backupArtifactPairState(
        Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
      throw unsupported();
    }

    @Override
    public void recoverInterruptedBackupPublication(
        Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath) {
      throw unsupported();
    }

    @Override
    public LeaseAcquisition acquireExistingArtifactLease(
        Path normalizedArtifactPath,
        dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole artifactRole) {
      throw unsupported();
    }

    @Override
    public LeaseAcquisition acquireManagedArtifactLease(
        Path normalizedArtifactPath,
        dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole artifactRole) {
      throw unsupported();
    }

    @Override
    public MaintenanceDecision<BookVerification> verifyInitializedBook(
        ProtectedBookAccess bookAccess,
        dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole artifactRole) {
      throw unsupported();
    }

    @Override
    public MaintenanceDecision<StagedBackupPair> stageBackupPair(
        VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication) {
      throw unsupported();
    }

    @Override
    public MaintenanceDecision<StagedRestoredBookPair> stageRestoredBookPair(
        VerifiedBook sourceBook, PreparedPairPublication preparedPairPublication) {
      throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
      return new UnsupportedOperationException(
          "Attestation boundary test must not invoke storage.");
    }
  }
}
