package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.maintenance.BackupAcknowledgementConflictException;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import java.nio.file.Path;
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
}
