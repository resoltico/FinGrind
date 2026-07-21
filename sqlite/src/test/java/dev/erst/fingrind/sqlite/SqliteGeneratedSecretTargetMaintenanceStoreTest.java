package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Covers generated-secret target preconditions at the SQLite maintenance-store boundary. */
class SqliteGeneratedSecretTargetMaintenanceStoreTest extends SqliteArtifactPublicationTestSupport {

  @Test
  void generatedSecretTargets_areRejectedAsMaintenanceRejectionsBeforeStaging() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path occupiedTarget = writeArtifact("occupied-generated.key", "occupied-secret");

    ProtectedBookMaintenanceRejectionException directRejection =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                store.preparePairPublication(
                    occupiedTarget,
                    tempDirectory.resolve("occupied-generated.sqlite"),
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
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
                store.preparePairPublication(
                    restoredKeyPath,
                    restoredBookPath,
                    RestoredBookTargetPolicy.REPLACE_SELECTED,
                    ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));
    assertEquals(
        restoredKeyPath,
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.SecretTargetOccupied.class,
                restoreRejection.rejection())
            .secretTargetPath());
  }

  @Test
  void backupStaging_reportsTheGeneratedSecretRoleForAnInvalidKeyTarget() throws Exception {
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
                    () ->
                        store.preparePairPublication(
                            invalidBackupKeyPath,
                            backupPath,
                            RestoredBookTargetPolicy.REQUIRE_ABSENT,
                            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET))
                .rejection());

    assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET, rejection.artifactRole());
  }

  @Test
  void generatedSecretTargetPreparation_wrapsUnexpectedFilesystemIo() {
    Path targetPath = tempDirectory.resolve("preparation-io").resolve("target.key");
    java.io.IOException ioFailure = new java.io.IOException("preparation failed");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteProtectedBookPairPublicationPreparation.prepareGeneratedSecretTarget(
                    targetPath,
                    ignored -> {
                      throw ioFailure;
                    }));

    assertSame(ioFailure, exception.getCause());
  }

  @Test
  void interruptedOwnedSecretPublication_isRecoveredBeforeTheRetryReservesItsPair()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path finalBackupPath = tempDirectory.resolve("owned-recovery").resolve("backup.sqlite");
    Path finalSecretPath = tempDirectory.resolve("owned-recovery").resolve("backup.key");
    writeArtifact("owned-recovery/parent-ready", "ready");
    SqliteOwnedStagedArtifact interruptedSecret =
        SqliteOwnedStagedArtifact.create(finalSecretPath, ".backup-key-", ".tmp");
    Files.writeString(interruptedSecret.stagedPath(), "interrupted-secret");
    Files.createLink(finalSecretPath, interruptedSecret.stagedPath());

    try (ProtectedBookMaintenanceStore.PreparedPairPublication ignored =
        store.preparePairPublication(
            finalSecretPath,
            finalBackupPath,
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET)) {
      assertFalse(Files.exists(interruptedSecret.stagedPath()));
      assertFalse(Files.exists(finalBackupPath));
      assertFalse(Files.exists(finalSecretPath));
    }

    assertFalse(Files.exists(finalBackupPath));
    assertFalse(Files.exists(finalSecretPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalBackupPath).isEmpty());
    assertTrue(SqliteOwnedStageRecord.findFor(finalSecretPath).isEmpty());
  }

  @Test
  void foreignGeneratedSecret_doesNotAuthorizeCompanionBookInspectionDuringRecovery()
      throws Exception {
    Path companionBookPath = writeArtifact("foreign-secret/book.sqlite", "unrelated-book");
    Path foreignSecretPath = writeArtifact("foreign-secret/book.key", "unrelated-secret");
    SqliteOwnedStagedArtifact abandonedStage =
        SqliteOwnedStagedArtifact.create(foreignSecretPath, ".backup-key-", ".tmp");
    Files.writeString(abandonedStage.stagedPath(), "abandoned-owned-stage");
    AtomicInteger companionInspectionRequests = new AtomicInteger();
    SqliteProtectedBookMaintenanceStore store =
        new SqliteProtectedBookMaintenanceStore(
            KEY_FILE_RESOLVER,
            (bookPath, secretPath) -> {
              companionInspectionRequests.incrementAndGet();
              return false;
            });

    ProtectedBookMaintenanceRejectionException rejection =
        assertThrows(
            ProtectedBookMaintenanceRejectionException.class,
            () ->
                store.preparePairPublication(
                    foreignSecretPath,
                    companionBookPath,
                    RestoredBookTargetPolicy.REPLACE_SELECTED,
                    ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET));

    assertEquals(0, companionInspectionRequests.get());
    assertEquals(
        foreignSecretPath,
        assertInstanceOf(
                ProtectedBookMaintenanceRejection.SecretTargetOccupied.class, rejection.rejection())
            .secretTargetPath());
    assertEquals("unrelated-book", Files.readString(companionBookPath));
    assertTrue(SqliteOwnedStageRecord.findFor(foreignSecretPath).isEmpty());
  }

  @Test
  void activePreparedPair_cannotBeScavengedByOneConcurrentPreparationAttempt() throws Exception {
    Path finalBackupPath = tempDirectory.resolve("active-pair").resolve("backup.sqlite");
    Path finalSecretPath = tempDirectory.resolve("active-pair").resolve("backup.key");
    writeArtifact("active-pair/parent-ready", "ready");
    SqliteProtectedBookMaintenanceStore firstStore = maintenanceStore();
    SqliteProtectedBookMaintenanceStore secondStore = maintenanceStore();

    try (ProtectedBookMaintenanceStore.PreparedPairPublication ignored =
            firstStore.preparePairPublication(
                finalSecretPath,
                finalBackupPath,
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET);
        ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<ProtectedBookMaintenanceRejection> concurrentAttempt =
          executor.submit(
              () ->
                  assertThrows(
                          ProtectedBookMaintenanceRejectionException.class,
                          () ->
                              secondStore.preparePairPublication(
                                  finalSecretPath,
                                  finalBackupPath,
                                  RestoredBookTargetPolicy.REQUIRE_ABSENT,
                                  ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
                                  ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET))
                      .rejection());

      ProtectedBookMaintenanceRejection.ArtifactBusy busy =
          assertInstanceOf(
              ProtectedBookMaintenanceRejection.ArtifactBusy.class, concurrentAttempt.get());
      assertEquals(ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET, busy.artifactRole());
      assertEquals(finalBackupPath, busy.artifactPath());
      assertFalse(Files.exists(finalBackupPath));
      assertFalse(Files.exists(finalSecretPath));
      assertFalse(SqliteOwnedStageRecord.findFor(finalBackupPath).isEmpty());
      assertFalse(SqliteOwnedStageRecord.findFor(finalSecretPath).isEmpty());
    }

    assertFalse(Files.exists(finalBackupPath));
    assertFalse(Files.exists(finalSecretPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalBackupPath).isEmpty());
    assertTrue(SqliteOwnedStageRecord.findFor(finalSecretPath).isEmpty());
  }
}
