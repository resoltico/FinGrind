package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionRecoveryReceipt;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.PublicationTransactionStageReservation;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Proves pre-journal target failures remain typed and do not ask a transaction service to recover.
 */
class SqliteJournaledPairTargetReservationTest extends SqliteNativeBridgeTestSupport {
  @Test
  void mapsOccupiedTargetsAndAReservationIoFailureAtTheJournalBoundary() throws Exception {
    Path book = Files.writeString(tempDirectory.resolve("occupied.sqlite"), "occupied");
    ProtectedBookMaintenanceRejectionException bookFailure =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                prepare(
                    book, tempDirectory.resolve("occupied.key"), new UnreachableTransactions()));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
        bookFailure.rejection());

    Path secret = Files.writeString(tempDirectory.resolve("occupied-secret.key"), "occupied");
    ProtectedBookMaintenanceRejectionException secretFailure =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                prepare(
                    tempDirectory.resolve("occupied-secret.sqlite"),
                    secret,
                    new UnreachableTransactions()));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.SecretTargetOccupied.class, secretFailure.rejection());

    IllegalStateException journalFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                prepare(
                    tempDirectory.resolve("reserve.sqlite"),
                    tempDirectory.resolve("reserve.key"),
                    new ReservationIoTransactions()));
    assertInstanceOf(IOException.class, journalFailure.getCause());
  }

  @Test
  void mapsInvalidParentToTheExactTargetRoleAndValidatesBookRoleCombinations() throws Exception {
    Path blocker = Files.writeString(tempDirectory.resolve("parent-blocker"), "not a directory");
    ProtectedBookMaintenanceRejectionException invalidParent =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                prepare(
                    blocker.resolve("book.sqlite"),
                    tempDirectory.resolve("unreached.key"),
                    new UnreachableTransactions()));
    ProtectedBookMaintenanceRejection.ArtifactPathInvalid invalid =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class, invalidParent.rejection());
    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET, invalid.artifactRole());

    ProtectedBookMaintenanceRejectionException invalidSecretParent =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                prepare(
                    tempDirectory.resolve("valid-book.sqlite"),
                    blocker.resolve("book.key"),
                    new UnreachableTransactions()));
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
                invalidSecretParent.rejection())
            .artifactRole());

    Path target = tempDirectory.resolve("target.sqlite");
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
        SqliteProtectedBookPairPublicationTargets.occupiedBookTargetRejection(
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET, target));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BookDestinationOccupied.class,
        SqliteProtectedBookPairPublicationTargets.occupiedBookTargetRejection(
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, target));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteProtectedBookPairPublicationTargets.occupiedBookTargetRejection(
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET, target));
  }

  private void prepare(Path book, Path secret, PublicationTransactionService transactions) {
    try (SqlitePairPublicationPreparationResources resources =
        new SqlitePairPublicationPreparationResources()) {
      SqliteProtectedBookPairPublicationTargets.prepareJournaledWithHeldLeases(
          resources,
          secret,
          book,
          RestoredBookTargetPolicy.REQUIRE_ABSENT,
          ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
          ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
          new ProtectedBookPairPublicationRecoveryRequest.Backup(book, new UUID(7L, 8L)),
          transactions);
    }
  }

  /** Rejects transaction calls that target admission must not reach. */
  private static class UnreachableTransactions implements PublicationTransactionService {
    @Override
    public PublicationTransactionResult publish(PublicationTransactionRequest request) {
      throw new AssertionError("Target validation must finish before publication.");
    }

    @Override
    public PublicationTransactionStageReservation reserveStages(
        PublicationTransactionRequest request) throws IOException {
      throw new AssertionError("Target validation must finish before stage reservation.");
    }

    @Override
    public PublicationTransactionResult publishReservedStages(
        PublicationTransactionStageReservation reservation) {
      throw new AssertionError("Target validation must finish before staged publication.");
    }

    @Override
    public PublicationTransactionResult recover(PublicationTransactionId transactionId) {
      throw new AssertionError("Target validation must not recover a transaction.");
    }

    @Override
    public PublicationTransactionRecoveryReceipt recoverWithReceipt(
        PublicationTransactionId transactionId) {
      throw new AssertionError("Target validation must not read a transaction receipt.");
    }
  }

  /** Injects only stage-reservation I/O failure after target admission succeeds. */
  private static final class ReservationIoTransactions extends UnreachableTransactions {
    @Override
    public PublicationTransactionStageReservation reserveStages(
        PublicationTransactionRequest request) throws IOException {
      throw new IOException("injected journal reservation failure");
    }
  }
}
