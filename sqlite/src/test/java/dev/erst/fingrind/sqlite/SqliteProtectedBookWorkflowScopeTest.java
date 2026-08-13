package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies workflow-scope identity access and its one-way admission lifecycle. */
class SqliteProtectedBookWorkflowScopeTest extends SqliteArtifactPublicationTestSupport {

  @Test
  void reportsItsSourceAndRejectsAdmissionAfterCloseOrAConsumedTargetHandoff() {
    Path source = tempDirectory.resolve("source.sqlite");
    Path bookTarget = tempDirectory.resolve("book.sqlite");
    Path secretTarget = tempDirectory.resolve("book.key");
    ProtectedBookPairPublicationRecoveryRequest.Backup request =
        new ProtectedBookPairPublicationRecoveryRequest.Backup(source, UUID.randomUUID());

    try (SqliteProtectedBookWorkflowScope closed = scope(source, bookTarget, secretTarget)) {
      assertEquals(source, closed.artifactPath());
      closed.close();
      IllegalStateException closedFailure =
          assertThrows(
              IllegalStateException.class,
              () -> closed.admitPairPublication(RestoredBookTargetPolicy.REQUIRE_ABSENT, request));
      assertEquals(
          "The FinGrind maintenance workflow scope is already closed.", closedFailure.getMessage());
    }

    try (SqliteWorkflowLeaseScope consumedLeaseScope =
        leaseScope(source, bookTarget, secretTarget)) {
      consumedLeaseScope.takeTargetAdmissionLeases().close();
      try (SqliteProtectedBookWorkflowScope consumed =
          new SqliteProtectedBookWorkflowScope(
              consumedLeaseScope,
              preparation(),
              bookTarget,
              secretTarget,
              ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
              ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET)) {
        assertThrows(
            IllegalStateException.class,
            () -> consumed.admitPairPublication(RestoredBookTargetPolicy.REQUIRE_ABSENT, request));
        IllegalStateException admittedFailure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    consumed.admitPairPublication(
                        RestoredBookTargetPolicy.REQUIRE_ABSENT, request));
        assertEquals(
            "The FinGrind maintenance workflow scope has already admitted its exact target pair.",
            admittedFailure.getMessage());
      }
    }
  }

  private SqliteProtectedBookWorkflowScope scope(Path source, Path bookTarget, Path secretTarget) {
    return new SqliteProtectedBookWorkflowScope(
        leaseScope(source, bookTarget, secretTarget),
        preparation(),
        bookTarget,
        secretTarget,
        ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
        ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
  }

  private SqliteWorkflowLeaseScope leaseScope(Path source, Path bookTarget, Path secretTarget) {
    return new SqliteWorkflowLeaseScope(
        source,
        List.of(new SqliteHeldLease(source, () -> {})),
        new SqliteHeldLease(bookTarget, () -> {}),
        new SqliteHeldLease(secretTarget, () -> {}));
  }

  private SqliteProtectedBookPairPublicationPreparation preparation() {
    return SqliteProtectedBookPairPublicationPreparation.journaledForTesting(
        maintenanceStore(),
        () -> {
          throw new AssertionError(
              "Closed workflow scope must not open a publication transaction.");
        });
  }
}
