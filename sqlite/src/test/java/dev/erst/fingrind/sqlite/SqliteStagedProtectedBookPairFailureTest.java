package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Exercises staged backup and restore cleanup when publication cannot complete atomically. */
class SqliteStagedProtectedBookPairFailureTest extends SqliteArtifactPublicationTestSupport {
  private static final String BACKUP_READ_FAILURE_DIRECTORY = "backup-read-failure";
  private static final String BACKUP_SEAL_FAILURE_DIRECTORY = "backup-seal-failure";
  private static final String STAGED_BACKUP_FILE_NAME = "staged.sqlite";
  private static final String STAGED_KEY_FILE_NAME = "staged.key";
  private static final String FINAL_BACKUP_FILE_NAME = "backup.sqlite";
  private static final String FINAL_KEY_FILE_NAME = "backup.key";

  @Test
  void stagedBackupPair_sealsAnExactSnapshotAndRefusesFurtherSnapshotAccess() throws Exception {
    Path stagedBackupPath = writeArtifact("backup-sealed/staged.sqlite", "encrypted backup");
    Path stagedKeyPath = writeArtifact("backup-sealed/staged.key", "backup key");
    Path finalBackupPath = tempDirectory.resolve("backup-sealed").resolve("backup.sqlite");
    Path finalKeyPath = tempDirectory.resolve("backup-sealed").resolve("backup.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      byte[] snapshot = stagedPair.snapshot();
      byte[] sealedArtifact = Arrays.copyOf(snapshot, snapshot.length + 3);
      sealedArtifact[sealedArtifact.length - 3] = 1;
      sealedArtifact[sealedArtifact.length - 2] = 2;
      sealedArtifact[sealedArtifact.length - 1] = 3;

      stagedPair.sealArtifact(sealedArtifact);

      assertArrayEquals(sealedArtifact, Files.readAllBytes(stagedBackupPath));
      assertThrows(IllegalStateException.class, stagedPair::snapshot);
      assertThrows(IllegalStateException.class, stagedPair::verifyInitializedBackup);
      assertThrows(IllegalStateException.class, () -> stagedPair.sealArtifact(sealedArtifact));

      stagedPair.rollback();
      stagedPair.commit();
    }

    assertFalse(Files.exists(stagedBackupPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(finalBackupPath));
    assertFalse(Files.exists(finalKeyPath));
  }

  @Test
  void stagedBackupPair_refusesArtifactsThatDoNotStrictlyExtendItsExactSnapshot() throws Exception {
    Path stagedBackupPath = writeArtifact("backup-invalid-seal/staged.sqlite", "encrypted backup");
    Path stagedKeyPath = writeArtifact("backup-invalid-seal/staged.key", "backup key");
    Path finalBackupPath = tempDirectory.resolve("backup-invalid-seal").resolve("backup.sqlite");
    Path finalKeyPath = tempDirectory.resolve("backup-invalid-seal").resolve("backup.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      byte[] snapshot = stagedPair.snapshot();
      assertEquals(
          "Backup artifact must begin with the exact staged encrypted snapshot.",
          assertThrows(IllegalArgumentException.class, () -> stagedPair.sealArtifact(snapshot))
              .getMessage());

      byte[] alteredPrefix = Arrays.copyOf(snapshot, snapshot.length + 1);
      alteredPrefix[0] ^= 1;
      assertEquals(
          "Backup artifact must begin with the exact staged encrypted snapshot.",
          assertThrows(IllegalArgumentException.class, () -> stagedPair.sealArtifact(alteredPrefix))
              .getMessage());
    }

    assertFalse(Files.exists(stagedBackupPath));
    assertFalse(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedBackupPair_reportsReadAndSealStorageFailuresAfterStaging() throws Exception {
    Path readFailureDirectory = tempDirectory.resolve(BACKUP_READ_FAILURE_DIRECTORY);
    Path unreadableBackupPath =
        writeArtifact(BACKUP_READ_FAILURE_DIRECTORY + "/" + STAGED_BACKUP_FILE_NAME, "backup");
    Path unreadableKeyPath =
        writeArtifact(BACKUP_READ_FAILURE_DIRECTORY + "/" + STAGED_KEY_FILE_NAME, "key");
    Path unreadableFinalBackupPath = readFailureDirectory.resolve(FINAL_BACKUP_FILE_NAME);
    Path unreadableFinalKeyPath = readFailureDirectory.resolve(FINAL_KEY_FILE_NAME);
    requirePosixPermissions(unreadableBackupPath);
    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(
                    unreadableFinalBackupPath, unreadableBackupPath),
                unreadableFinalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(unreadableFinalKeyPath, unreadableKeyPath),
                unreadableFinalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      Files.setPosixFilePermissions(unreadableBackupPath, Set.of(PosixFilePermission.OWNER_WRITE));

      IllegalStateException readFailure =
          assertThrows(IllegalStateException.class, stagedPair::snapshot);

      assertEquals(
          "Failed to read the staged encrypted backup snapshot.", readFailure.getMessage());
      assertInstanceOf(IOException.class, readFailure.getCause());
    }

    Path sealFailureDirectory = tempDirectory.resolve(BACKUP_SEAL_FAILURE_DIRECTORY);
    Path unwritableBackupPath =
        writeArtifact(BACKUP_SEAL_FAILURE_DIRECTORY + "/" + STAGED_BACKUP_FILE_NAME, "backup");
    Path unwritableKeyPath =
        writeArtifact(BACKUP_SEAL_FAILURE_DIRECTORY + "/" + STAGED_KEY_FILE_NAME, "key");
    Path unwritableFinalBackupPath = sealFailureDirectory.resolve(FINAL_BACKUP_FILE_NAME);
    Path unwritableFinalKeyPath = sealFailureDirectory.resolve(FINAL_KEY_FILE_NAME);
    requirePosixPermissions(unwritableBackupPath);
    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(
                    unwritableFinalBackupPath, unwritableBackupPath),
                unwritableFinalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(unwritableFinalKeyPath, unwritableKeyPath),
                unwritableFinalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      byte[] snapshot = stagedPair.snapshot();
      byte[] sealedArtifact = Arrays.copyOf(snapshot, snapshot.length + 1);
      Files.setPosixFilePermissions(unwritableBackupPath, Set.of(PosixFilePermission.OWNER_READ));

      IllegalStateException sealFailure =
          assertThrows(IllegalStateException.class, () -> stagedPair.sealArtifact(sealedArtifact));

      assertEquals("Failed to seal the staged attested backup artifact.", sealFailure.getMessage());
      assertInstanceOf(IOException.class, sealFailure.getCause());
    }
  }

  @Test
  void untransferredVerifiedBackupSnapshot_closesItsBookAndDiscardsItsStage() throws Exception {
    Path finalArtifactPath = tempDirectory.resolve("backup-snapshot").resolve("backup.sqlite");
    Files.createDirectories(
        Objects.requireNonNull(finalArtifactPath.getParent(), "artifact parent"));
    SqliteOwnedStagedArtifact stage =
        SqliteOwnedStagedArtifact.create(finalArtifactPath, ".snapshot-", ".sqlite");
    Path stagePath = stage.stagedPath();
    try (SqliteVerifiedBackupSnapshot snapshot = new SqliteVerifiedBackupSnapshot(stage)) {
      snapshot.attachBook(new SqliteVerifiedBook(finalArtifactPath, testPassphrase()));
      snapshot.close();
    }

    assertFalse(Files.exists(stagePath));
  }

  @Test
  void stagedBackupPair_rejectsAnOccupiedGeneratedKeyTargetAndCleansItsStage() throws Exception {
    Path stagedBackupPath = writeArtifact("backup-key-collision/staged.sqlite", "backup");
    Path stagedKeyPath = writeArtifact("backup-key-collision/staged.key", "key");
    Path finalBackupPath = tempDirectory.resolve("backup-key-collision").resolve("backup.sqlite");
    Path finalKeyPath = writeArtifact("backup-key-collision/backup.key", "occupied");
    byte[] occupiedKeyBefore = Files.readAllBytes(finalKeyPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      ProtectedBookMaintenanceRejectionException exception =
          assertThrows(ProtectedBookMaintenanceRejectionException.class, stagedPair::commit);
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.SecretTargetOccupied.class, exception.rejection());
    }

    assertFalse(Files.exists(stagedBackupPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertArrayEquals(occupiedKeyBefore, Files.readAllBytes(finalKeyPath));
  }

  @Test
  void stagedBackupPair_cleansUpWhenTheStagedKeyDisappearsBeforePublication() throws Exception {
    Path stagedBackupPath = writeArtifact("backup-io/staged.sqlite", "backup");
    Path stagedKeyPath = tempDirectory.resolve("backup-io").resolve("missing.key");
    Path finalBackupPath = tempDirectory.resolve("backup-io").resolve("backup.sqlite");
    Path finalKeyPath = tempDirectory.resolve("backup-io").resolve("backup.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      assertThrows(IllegalStateException.class, stagedPair::commit);
    }

    assertFalse(Files.exists(stagedBackupPath));
    assertFalse(Files.exists(finalBackupPath));
    assertFalse(Files.exists(finalKeyPath));
  }

  @Test
  void stagedBackupPair_rollbackClosesBothPassphraseStatesAndRetainsOnlyARecordedPublication()
      throws Exception {
    Path missingKeyBackupPath = writeArtifact("backup-null-passphrase/staged.sqlite", "backup");
    Path missingKeyPath = tempDirectory.resolve("backup-null-passphrase").resolve("missing.key");
    Path missingKeyFinalBackupPath =
        tempDirectory.resolve("backup-null-passphrase").resolve("backup.sqlite");
    Path missingKeyFinalPath =
        tempDirectory.resolve("backup-null-passphrase").resolve("backup.key");
    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(
                    missingKeyFinalBackupPath, missingKeyBackupPath),
                missingKeyFinalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(missingKeyFinalPath, missingKeyPath),
                missingKeyFinalPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      assertThrows(IllegalStateException.class, stagedPair::commit);
    }
    assertFalse(Files.exists(missingKeyBackupPath));

    Path publishedStagePath = writeArtifact("backup-published-stage/staged.sqlite", "backup");
    Path publishedKeyStagePath = writeArtifact("backup-published-stage/staged.key", "key");
    Path publishedFinalPath =
        tempDirectory.resolve("backup-published-stage").resolve("backup.sqlite");
    Path publishedKeyFinalPath =
        tempDirectory.resolve("backup-published-stage").resolve("backup.key");
    SqliteOwnedStagedArtifact publishedStage =
        SqliteOwnedStagedArtifact.recordExisting(publishedFinalPath, publishedStagePath);
    SqliteOwnedStagedArtifact publishedKeyStage =
        SqliteOwnedStagedArtifact.recordExisting(publishedKeyFinalPath, publishedKeyStagePath);
    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                publishedStage,
                publishedFinalPath,
                publishedKeyStage,
                publishedKeyFinalPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      setPrivateField(stagedPair, "backupFilePublished", true);
      stagedPair.rollback();
    }

    assertTrue(Files.exists(publishedStagePath));
    assertFalse(Files.exists(publishedKeyStagePath));
    publishedStage.discard();
  }

  @Test
  void stagedBackupPair_reclaimsItsPublishedKeyBeforeSecondaryCleanupFails() throws Exception {
    Path stagedBackupPath = writeArtifact("backup-cleanup-failure/staged.sqlite", "backup");
    Path stagedKeyPath = writeArtifact("backup-cleanup-failure/staged.key", "key");
    Path finalBackupPath = writeArtifact("backup-cleanup-failure/backup.sqlite", "occupied");
    Path finalKeyPath = tempDirectory.resolve("backup-cleanup-failure").resolve("backup.key");
    SqliteOwnedStagedArtifact stagedBackup =
        SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath);
    Path stagedBackupRecordPath =
        ownedRecordPath(
            Objects.requireNonNull(stagedBackupPath.getParent(), "stagedBackup parent"));
    SqliteOwnedStagedArtifact stagedKey =
        SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);
    Files.delete(stagedBackupRecordPath);
    Files.createDirectory(stagedBackupRecordPath);
    Files.writeString(stagedBackupRecordPath.resolve("cleanup-blocker"), "altered");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                stagedBackup,
                finalBackupPath,
                stagedKey,
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      IllegalStateException failure = assertThrows(IllegalStateException.class, stagedPair::commit);
      assertTrue(String.valueOf(failure.getMessage()).contains("Failed to roll back"));
    }

    assertEquals("occupied", Files.readString(finalBackupPath));
    assertFalse(Files.exists(finalKeyPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertTrue(Files.isDirectory(stagedBackupRecordPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
  }

  @Test
  void stagedRestoredBookPair_reclaimsItsPublishedKeyBeforeSecondaryCleanupFails()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-cleanup-failure/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-cleanup-failure/staged.key", "key");
    Path finalBookPath = tempDirectory.resolve("restore-cleanup-failure").resolve("book.sqlite");
    Path finalKeyPath = tempDirectory.resolve("restore-cleanup-failure").resolve("book.key");
    SqliteOwnedStagedArtifact stagedBook =
        SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    Path stagedBookRecordPath =
        ownedRecordPath(Objects.requireNonNull(stagedBookPath.getParent(), "stagedBook parent"));
    SqliteOwnedStagedArtifact stagedKey =
        SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);
    Files.delete(stagedBookPath);
    Files.delete(stagedBookRecordPath);
    Files.createDirectory(stagedBookRecordPath);
    Files.writeString(stagedBookRecordPath.resolve("cleanup-blocker"), "altered");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair stagedPair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    stagedBook, finalBookPath, stagedKey, finalKeyPath),
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                passphrase,
                VERIFICATION_SUPPORT)) {
      IllegalStateException failure = assertThrows(IllegalStateException.class, stagedPair::commit);
      assertTrue(String.valueOf(failure.getMessage()).contains("Failed to roll back"));
    }

    assertFalse(Files.exists(finalKeyPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertTrue(Files.isDirectory(stagedBookRecordPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
  }

  @Test
  void stagedPairs_rejectAlteredStageOwnershipBeforePublishingFinalArtifacts() throws Exception {
    Path backupStagedPath = writeArtifact("backup-committed/staged.sqlite", "backup");
    Path backupKeyStagedPath = writeArtifact("backup-committed/staged.key", "key");
    Path backupFinalPath = tempDirectory.resolve("backup-committed").resolve("backup.sqlite");
    Path backupKeyFinalPath = tempDirectory.resolve("backup-committed").resolve("backup.key");
    SqliteOwnedStagedArtifact backupStaged =
        SqliteOwnedStagedArtifact.recordExisting(backupFinalPath, backupStagedPath);
    SqliteOwnedStagedArtifact backupKeyStaged =
        SqliteOwnedStagedArtifact.recordExisting(backupKeyFinalPath, backupKeyStagedPath);
    Path backupKeyRecordPath = ownedRecordPathForStage(backupKeyStagedPath);
    replaceWithNonemptyDirectory(backupKeyRecordPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair backupPair =
            SqliteStagedBackupPairFactory.create(
                backupStaged,
                backupFinalPath,
                backupKeyStaged,
                backupKeyFinalPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      assertThrows(IllegalStateException.class, backupPair::commit);
    }

    assertFalse(Files.exists(backupFinalPath));
    assertFalse(Files.exists(backupKeyFinalPath));
    assertFalse(Files.exists(backupStagedPath));
    assertFalse(Files.exists(backupKeyStagedPath));
    assertTrue(Files.isDirectory(backupKeyRecordPath));

    Path restoredStagedPath = writeArtifact("restore-committed/staged.sqlite", "book");
    Path restoredKeyStagedPath = writeArtifact("restore-committed/staged.key", "key");
    Path restoredFinalPath = tempDirectory.resolve("restore-committed").resolve("book.sqlite");
    Path restoredKeyFinalPath = tempDirectory.resolve("restore-committed").resolve("book.key");
    SqliteOwnedStagedArtifact restoredStaged =
        SqliteOwnedStagedArtifact.recordExisting(restoredFinalPath, restoredStagedPath);
    SqliteOwnedStagedArtifact restoredKeyStaged =
        SqliteOwnedStagedArtifact.recordExisting(restoredKeyFinalPath, restoredKeyStagedPath);
    Path restoredKeyRecordPath = ownedRecordPathForStage(restoredKeyStagedPath);
    replaceWithNonemptyDirectory(restoredKeyRecordPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair restoredPair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    restoredStaged, restoredFinalPath, restoredKeyStaged, restoredKeyFinalPath),
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                passphrase,
                VERIFICATION_SUPPORT)) {
      assertThrows(IllegalStateException.class, restoredPair::commit);
    }

    assertFalse(Files.exists(restoredFinalPath));
    assertFalse(Files.exists(restoredKeyFinalPath));
    assertFalse(Files.exists(restoredStagedPath));
    assertFalse(Files.exists(restoredKeyStagedPath));
    assertTrue(Files.isDirectory(restoredKeyRecordPath));

    Path rekeyStagedBookPath =
        writeArtifact("rekey-published-cleanup/staged.sqlite", "rekeyed-book");
    Path rekeyStagedKeyPath = writeArtifact("rekey-published-cleanup/staged.key", "key");
    Path rekeyFinalBookPath = writeArtifact("rekey-published-cleanup/book.sqlite", "previous-book");
    Path rekeyFinalKeyPath = tempDirectory.resolve("rekey-published-cleanup").resolve("book.key");
    SqliteOwnedStagedArtifact rekeyStagedBook =
        SqliteOwnedStagedArtifact.recordExisting(rekeyFinalBookPath, rekeyStagedBookPath);
    SqliteOwnedStagedArtifact rekeyStagedKey =
        SqliteOwnedStagedArtifact.recordExisting(rekeyFinalKeyPath, rekeyStagedKeyPath);
    Path rekeyKeyRecordPath = ownedRecordPathForStage(rekeyStagedKeyPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair rekeyPair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    rekeyStagedBook, rekeyFinalBookPath, rekeyStagedKey, rekeyFinalKeyPath),
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                passphrase,
                VERIFICATION_SUPPORT,
                new SqliteRestoredBookPairPublication.Operators(
                    Files::createLink,
                    Files::createLink,
                    (stagedPath, finalPath) -> {
                      SqliteProtectedBookPublicationSupport.moveReplacing(stagedPath, finalPath);
                      replaceWithNonemptyDirectory(rekeyKeyRecordPath);
                    }))) {
      rekeyPair.commit();
    }

    assertEquals("rekeyed-book", Files.readString(rekeyFinalBookPath));
    assertTrue(Files.exists(rekeyFinalKeyPath));
    assertFalse(Files.exists(rekeyStagedBookPath));
    assertFalse(Files.exists(rekeyStagedKeyPath));
    assertTrue(Files.isDirectory(rekeyKeyRecordPath));
  }

  @Test
  void stagedPairs_preserveTheirSuccessfulPublicationWhenCommittedStageCleanupFails()
      throws Exception {
    Path backupStagedPath = writeArtifact("backup-published-cleanup/staged.sqlite", "backup");
    Path backupKeyStagedPath = writeArtifact("backup-published-cleanup/staged.key", "key");
    Path backupFinalPath =
        tempDirectory.resolve("backup-published-cleanup").resolve("backup.sqlite");
    Path backupKeyFinalPath =
        tempDirectory.resolve("backup-published-cleanup").resolve("backup.key");
    SqliteOwnedStagedArtifact backupStaged =
        SqliteOwnedStagedArtifact.recordExisting(backupFinalPath, backupStagedPath);
    SqliteOwnedStagedArtifact backupKeyStaged =
        SqliteOwnedStagedArtifact.recordExisting(backupKeyFinalPath, backupKeyStagedPath);
    Path backupKeyRecordPath = ownedRecordPathForStage(backupKeyStagedPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair backupPair =
            SqliteStagedBackupPairFactory.create(
                backupStaged,
                backupFinalPath,
                backupKeyStaged,
                backupKeyFinalPath,
                passphrase,
                VERIFICATION_SUPPORT,
                Files::createLink,
                (finalPath, stagedPath) -> {
                  Files.createLink(finalPath, stagedPath);
                  replaceWithNonemptyDirectory(backupKeyRecordPath);
                })) {
      backupPair.commit();
    }

    assertTrue(Files.exists(backupFinalPath));
    assertTrue(Files.exists(backupKeyFinalPath));
    assertFalse(Files.exists(backupStagedPath));
    assertFalse(Files.exists(backupKeyStagedPath));
    assertTrue(Files.isDirectory(backupKeyRecordPath));

    Path restoredStagedPath = writeArtifact("restore-published-cleanup/staged.sqlite", "book");
    Path restoredKeyStagedPath = writeArtifact("restore-published-cleanup/staged.key", "key");
    Path restoredFinalPath =
        tempDirectory.resolve("restore-published-cleanup").resolve("book.sqlite");
    Path restoredKeyFinalPath =
        tempDirectory.resolve("restore-published-cleanup").resolve("book.key");
    SqliteOwnedStagedArtifact restoredStaged =
        SqliteOwnedStagedArtifact.recordExisting(restoredFinalPath, restoredStagedPath);
    SqliteOwnedStagedArtifact restoredKeyStaged =
        SqliteOwnedStagedArtifact.recordExisting(restoredKeyFinalPath, restoredKeyStagedPath);
    Path restoredKeyRecordPath = ownedRecordPathForStage(restoredKeyStagedPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair restoredPair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    restoredStaged, restoredFinalPath, restoredKeyStaged, restoredKeyFinalPath),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                passphrase,
                VERIFICATION_SUPPORT,
                new SqliteRestoredBookPairPublication.Operators(
                    Files::createLink,
                    (finalPath, stagedPath) -> {
                      Files.createLink(finalPath, stagedPath);
                      replaceWithNonemptyDirectory(restoredKeyRecordPath);
                    },
                    SqliteProtectedBookPublicationSupport::moveReplacing))) {
      restoredPair.commit();
    }

    assertTrue(Files.exists(restoredFinalPath));
    assertTrue(Files.exists(restoredKeyFinalPath));
    assertFalse(Files.exists(restoredStagedPath));
    assertFalse(Files.exists(restoredKeyStagedPath));
    assertTrue(Files.isDirectory(restoredKeyRecordPath));
  }

  @Test
  void stagedRestoredBookPair_closesThePassphraseWhenFactoryValidationFails() throws Exception {
    Path stagedBookPath = writeArtifact("restore-factory/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-factory/staged.key", "key");
    try (SqliteBookPassphrase passphrase = testPassphrase()) {
      assertThrows(
          NullPointerException.class,
          () ->
              createWithNullVerificationSupport(
                  stagedBookPath,
                  tempDirectory.resolve("restore-factory").resolve("final.sqlite"),
                  stagedKeyPath,
                  tempDirectory.resolve("restore-factory").resolve("final.key"),
                  passphrase));
    }
  }

  @Test
  void stagedRestoredBookPair_cleansUpBeforeAndAfterKeyPublicationFailures() throws Exception {
    Path missingKeyStagedBook = writeArtifact("restore-before/staged.sqlite", "book");
    Path missingKeyPath = tempDirectory.resolve("restore-before").resolve("missing.key");
    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair stagedPair =
            newStagedRestoredBookPair(
                missingKeyStagedBook,
                tempDirectory.resolve("restore-before").resolve("final.sqlite"),
                missingKeyPath,
                tempDirectory.resolve("restore-before").resolve("final.key"),
                passphrase)) {
      assertThrows(IllegalStateException.class, stagedPair::commit);
    }
    assertFalse(Files.exists(missingKeyStagedBook));

    Path stagedBookPath = writeArtifact("restore-after/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-after/staged.key", "key");
    Path finalBookPath = tempDirectory.resolve("restore-after").resolve("final.sqlite");
    Files.createDirectories(finalBookPath);
    Path originalBookContent = finalBookPath.resolve("child");
    Files.writeString(originalBookContent, "occupied");
    Path finalKeyPath = tempDirectory.resolve("restore-after").resolve("final.key");
    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair stagedPair =
            newStagedRestoredBookPair(
                stagedBookPath, finalBookPath, stagedKeyPath, finalKeyPath, passphrase)) {
      assertThrows(IllegalStateException.class, stagedPair::commit);
    }
    assertFalse(Files.exists(stagedBookPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(finalKeyPath));
    assertEquals("occupied", Files.readString(originalBookContent));
  }

  @Test
  void stagedPairs_rejectUnsupportedAtomicSecretPublicationAndCleanTheirStages() throws Exception {
    Path zipArchive = tempDirectory.resolve("no-link.zip");
    try (FileSystem fileSystem =
        FileSystems.newFileSystem(
            URI.create("jar:" + zipArchive.toUri()), Map.of("create", "true"))) {
      Path stagedBackupPath = writeArtifact("unsupported-backup/staged.sqlite", "backup");
      Path finalBackupPath = tempDirectory.resolve("unsupported-backup").resolve("backup.sqlite");
      Path stagedBackupKeyPath = Files.writeString(fileSystem.getPath("/backup-stage.key"), "key");
      Path finalBackupKeyPath = fileSystem.getPath("/backup.key");

      try (SqliteBookPassphrase passphrase = testPassphrase();
          SqliteStagedBackupPair stagedPair =
              SqliteStagedBackupPairFactory.create(
                  SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                  finalBackupPath,
                  SqliteOwnedStagedArtifact.recordExisting(finalBackupKeyPath, stagedBackupKeyPath),
                  finalBackupKeyPath,
                  passphrase,
                  VERIFICATION_SUPPORT)) {
        ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
            assertInstanceOf(
                ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
                assertThrows(ProtectedBookMaintenanceRejectionException.class, stagedPair::commit)
                    .rejection());
        assertEquals(
            dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
                .BACKUP_KEY_TARGET,
            rejection.artifactRole());
        assertEquals(
            dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure
                .ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
            rejection.pathFailure());
      }
      assertFalse(Files.exists(stagedBackupPath));
      assertFalse(Files.exists(stagedBackupKeyPath));
      assertFalse(Files.exists(finalBackupKeyPath));

      Path stagedBookPath = writeArtifact("unsupported-restore/staged.sqlite", "book");
      Path finalBookPath = tempDirectory.resolve("unsupported-restore").resolve("final.sqlite");
      Path stagedBookKeyPath = Files.writeString(fileSystem.getPath("/restore-stage.key"), "key");
      Path finalBookKeyPath = fileSystem.getPath("/restore.key");

      try (SqliteBookPassphrase passphrase = testPassphrase();
          SqliteStagedRestoredBookPair stagedPair =
              newStagedRestoredBookPair(
                  stagedBookPath, finalBookPath, stagedBookKeyPath, finalBookKeyPath, passphrase)) {
        ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
            assertInstanceOf(
                ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
                assertThrows(ProtectedBookMaintenanceRejectionException.class, stagedPair::commit)
                    .rejection());
        assertEquals(
            dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
                .RESTORED_TARGET,
            rejection.artifactRole());
        assertEquals(
            dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure
                .ATOMIC_SECRET_PUBLICATION_UNSUPPORTED,
            rejection.pathFailure());
      }
      assertFalse(Files.exists(stagedBookPath));
      assertFalse(Files.exists(stagedBookKeyPath));
      assertFalse(Files.exists(finalBookKeyPath));
    }
  }

  private static SqliteBookPassphrase testPassphrase() {
    return SqliteBookPassphrase.fromUtf8Bytes(
        "staged protected-book pair", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
  }

  private static Path ownedRecordPath(Path parent) throws IOException {
    try (Stream<Path> children = Files.list(parent)) {
      return children
          .filter(path -> path.getFileName().toString().endsWith(".owner"))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Expected one owned-stage record fixture."));
    }
  }

  private static SqliteStagedRestoredBookPair createWithNullVerificationSupport(
      Path stagedBookPath,
      Path finalBookPath,
      Path stagedKeyPath,
      Path finalKeyPath,
      SqliteBookPassphrase passphrase) {
    try {
      MethodHandle factory =
          MethodHandles.lookup()
              .findStatic(
                  SqliteStagedRestoredBookPairFactory.class,
                  "create",
                  MethodType.methodType(
                      SqliteStagedRestoredBookPair.class,
                      SqliteStagedProtectedBookPairArtifacts.class,
                      RestoredBookTargetPolicy.class,
                      SqliteBookPassphrase.class,
                      SqliteProtectedBookVerificationSupport.class));
      return (SqliteStagedRestoredBookPair)
          factory.invokeWithArguments(
              new SqliteStagedProtectedBookPairArtifacts(
                  SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                  finalBookPath,
                  SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                  finalKeyPath),
              RestoredBookTargetPolicy.REPLACE_SELECTED,
              passphrase,
              null);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Throwable exception) {
      throw new LinkageError("Failed to invoke the staged restored-book factory.", exception);
    }
  }

  private static Path ownedRecordPathForStage(Path stagedPath) throws IOException {
    Path normalizedStagedPath = stagedPath.toAbsolutePath().normalize();
    String encodedStagedPath =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(normalizedStagedPath.toString().getBytes(StandardCharsets.UTF_8));
    Path parent = Objects.requireNonNull(normalizedStagedPath.getParent(), "stagedPath parent");
    try (Stream<Path> children = Files.list(parent)) {
      return children
          .filter(path -> path.getFileName().toString().endsWith(".owner"))
          .filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
          .filter(
              path -> {
                try {
                  return Files.readString(path).contains("stage=" + encodedStagedPath);
                } catch (IOException exception) {
                  throw new IllegalStateException(
                      "Unable to inspect one owned-stage record.", exception);
                }
              })
          .findFirst()
          .orElseThrow(() -> new AssertionError("Expected one owned-stage record fixture."));
    }
  }

  private static void replaceWithNonemptyDirectory(Path path) throws IOException {
    Files.delete(path);
    Files.createDirectory(path);
    Files.writeString(path.resolve("cleanup-blocker"), "altered");
  }

  private static void requirePosixPermissions(Path path) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        path.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "This filesystem cannot model POSIX access denial.");
  }
}
