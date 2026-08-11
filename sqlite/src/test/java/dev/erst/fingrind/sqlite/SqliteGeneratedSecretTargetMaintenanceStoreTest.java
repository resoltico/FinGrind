package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Covers generated-secret target admission without heuristic cleanup of retained evidence. */
class SqliteGeneratedSecretTargetMaintenanceStoreTest extends SqliteArtifactPublicationTestSupport {

  @Test
  void generatedSecretTargets_areRejectedAsMaintenanceRejectionsBeforeStaging() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path occupiedTarget = writeArtifact("occupied-generated.key", "occupied-secret");

    ProtectedBookMaintenanceRejectionException directRejection =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                admitBackupPair(
                    store, tempDirectory.resolve("occupied-generated.sqlite"), occupiedTarget));
    assertEquals(
        occupiedTarget,
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.SecretTargetOccupied.class,
                directRejection.rejection())
            .secretTargetPath());

    Path restoredBookPath = tempDirectory.resolve("occupied-restore").resolve("restored.sqlite");
    Path restoredKeyPath = writeArtifact("occupied-restore/restored.key", "occupied-secret");
    ProtectedBookMaintenanceRejectionException restoreRejection =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                admitRestoredBookPair(
                    store,
                    restoredBookPath,
                    restoredKeyPath,
                    RestoredBookTargetPolicy.REPLACE_SELECTED));
    assertEquals(
        restoredKeyPath,
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.SecretTargetOccupied.class,
                restoreRejection.rejection())
            .secretTargetPath());
  }

  @Test
  void backupAdmission_reportsTheGeneratedSecretRoleForAnInvalidKeyTarget() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path backupPath = tempDirectory.resolve("backup-key-role").resolve("backup.sqlite");
    Path parentBlocker = tempDirectory.resolve("backup-key-role").resolve("parent-blocker");
    writeArtifact("backup-key-role/parent-ready", "ready");
    Files.writeString(parentBlocker, "not-a-directory");
    Path invalidBackupKeyPath = parentBlocker.resolve("backup.key");

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () -> admitBackupPair(store, backupPath, invalidBackupKeyPath))
                .rejection());

    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET, rejection.artifactRole());
  }

  @Test
  void incompleteUnboundPair_remainsEvidenceBlockedWithoutDeletingOwnedStages() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path finalBackupPath = tempDirectory.resolve("retained-evidence").resolve("backup.sqlite");
    Path finalSecretPath = tempDirectory.resolve("retained-evidence").resolve("backup.key");
    writeArtifact("retained-evidence/parent-ready", "ready");
    SqliteOwnedStagedArtifact interruptedSecret =
        SqliteOwnedStagedArtifact.create(finalSecretPath, ".backup-key-", ".tmp");
    Files.writeString(interruptedSecret.stagedPath(), "interrupted-secret");
    Files.createLink(finalSecretPath, interruptedSecret.stagedPath());

    ProtectedBookPairPublicationAdmission admission =
        admitBackupPair(store, finalBackupPath, finalSecretPath);
    ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked blocked =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class, admission);

    assertEquals(finalBackupPath.toAbsolutePath().normalize(), blocked.bookArtifactPath());
    assertEquals(finalSecretPath.toAbsolutePath().normalize(), blocked.secretArtifactPath());
    assertFalse(Files.exists(finalBackupPath));
    assertTrue(Files.exists(finalSecretPath));
    assertTrue(Files.exists(interruptedSecret.stagedPath()));
    assertFalse(SqliteOwnedStageRecord.findFor(finalSecretPath).isEmpty());
  }

  @Test
  void activePreparedPair_cannotBeReplacedByOneConcurrentAdmissionAttempt() throws Exception {
    Path finalBackupPath = tempDirectory.resolve("active-pair").resolve("backup.sqlite");
    Path finalSecretPath = tempDirectory.resolve("active-pair").resolve("backup.key");
    writeArtifact("active-pair/parent-ready", "ready");
    SqliteProtectedBookMaintenanceStore firstStore = maintenanceStore();
    SqliteProtectedBookMaintenanceStore secondStore = maintenanceStore();

    ProtectedBookPairPublicationAdmission.Prepared prepared =
        assertInstanceOf(
            ProtectedBookPairPublicationAdmission.Prepared.class,
            admitBackupPair(firstStore, finalBackupPath, finalSecretPath));
    try (ProtectedBookMaintenanceStore.PreparedPairPublication ignored = prepared.publication();
        ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<ProtectedBookMaintenanceRejection> concurrentAttempt =
          executor.submit(
              () ->
                  assertThrows(
                          ProtectedBookMaintenanceRejectionException.class,
                          () -> admitBackupPair(secondStore, finalBackupPath, finalSecretPath))
                      .rejection());

      ProtectedBookMaintenanceRejection.ArtifactBusy busy =
          assertInstanceOf(
              ProtectedBookMaintenanceRejection.ArtifactBusy.class, concurrentAttempt.get());
      assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET, busy.artifactRole());
      assertEquals(finalSecretPath.toAbsolutePath().normalize(), busy.artifactPath());
      assertFalse(Files.exists(finalBackupPath));
      assertFalse(Files.exists(finalSecretPath));
    }

    assertFalse(Files.exists(finalBackupPath));
    assertFalse(Files.exists(finalSecretPath));
  }
}
