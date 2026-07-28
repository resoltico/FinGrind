package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Exercises retained staged backup and restore evidence when publication cannot complete
 * atomically.
 */
class SqliteStagedProtectedBookPairFailureTest extends SqliteArtifactPublicationTestSupport {
  private static final String BACKUP_READ_FAILURE_DIRECTORY = "backup-read-failure";
  private static final String BACKUP_SEAL_FAILURE_DIRECTORY = "backup-seal-failure";
  private static final String STAGED_BACKUP_FILE_NAME = "staged.sqlite";
  private static final String STAGED_KEY_FILE_NAME = "staged.key";
  private static final String FINAL_BACKUP_FILE_NAME = "backup.sqlite";
  private static final String FINAL_KEY_FILE_NAME = "backup.key";

  @Test
  void stagedBackupArtifact_requiresItsSealBeforePublicationAndRejectsResealing() throws Exception {
    Path finalBackupPath = tempDirectory.resolve("backup-artifact").resolve("backup.sqlite");
    Path backupParent = Objects.requireNonNull(finalBackupPath.getParent());
    Files.createDirectories(backupParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(backupParent);
    SqliteOwnedStagedArtifact stage =
        SqliteOwnedStagedArtifact.create(finalBackupPath, ".backup-artifact-", ".sqlite");
    try {
      Files.writeString(stage.stagedPath(), "encrypted snapshot", StandardCharsets.UTF_8);
      SqliteStagedBackupArtifact artifact = new SqliteStagedBackupArtifact(stage, finalBackupPath);

      assertThrows(IllegalStateException.class, artifact::requireSealed);
      byte[] snapshot = artifact.snapshot();
      byte[] sealed = Arrays.copyOf(snapshot, snapshot.length + 1);
      sealed[sealed.length - 1] = 1;
      artifact.seal(sealed);

      assertDoesNotThrow(artifact::requireSealed);
      assertThrows(IllegalStateException.class, artifact::requireUnsealed);
    } finally {
      stage.releaseRetained();
    }
  }

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

      stagedPair.retainUnpublishedArtifacts();
      assertThrows(
          IllegalStateException.class, () -> stagedPair.commit(backupBinding(finalBackupPath)));
    }

    assertTrue(Files.exists(stagedBackupPath));
    assertTrue(Files.exists(stagedKeyPath));
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

    assertTrue(Files.exists(stagedBackupPath));
    assertTrue(Files.exists(stagedKeyPath));
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
  void untransferredVerifiedBackupSnapshot_closesItsBookAndRetainsItsStage() throws Exception {
    Path finalArtifactPath = tempDirectory.resolve("backup-snapshot").resolve("backup.sqlite");
    Path artifactParent = Objects.requireNonNull(finalArtifactPath.getParent(), "artifact parent");
    Files.createDirectories(artifactParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(artifactParent);
    SqliteOwnedStagedArtifact stage =
        SqliteOwnedStagedArtifact.create(finalArtifactPath, ".snapshot-", ".sqlite");
    Path stagePath = stage.stagedPath();
    try (SqliteVerifiedBackupSnapshot snapshot = new SqliteVerifiedBackupSnapshot(stage)) {
      snapshot.attachBook(new SqliteVerifiedBook(finalArtifactPath, testPassphrase()));
      snapshot.close();
    }

    assertTrue(Files.exists(stagePath));
  }

  @Test
  void stagedBackupPair_retainsItsStagesWhenTheGeneratedKeyTargetIsOccupied() throws Exception {
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
      sealBackupForPublication(stagedPair);
      ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired outcome =
          assertInstanceOf(
              ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
              stagedPair.commit(backupBinding(finalBackupPath)));
      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState
              .DURABLY_RETAINED,
          outcome.recoveryRecordState());
      assertEquals(
          finalBackupPath.toAbsolutePath().normalize(),
          outcome.pairPublicationRetention().bookPublication().publishedArtifactPath());
      assertEquals(
          stagedBackupPath.toAbsolutePath().normalize(),
          outcome.pairPublicationRetention().bookPublication().retention().retainedStagePath());
      assertEquals(
          finalKeyPath.toAbsolutePath().normalize(),
          outcome.pairPublicationRetention().generatedSecretPublication().publishedArtifactPath());
      assertEquals(
          stagedKeyPath.toAbsolutePath().normalize(),
          outcome
              .pairPublicationRetention()
              .generatedSecretPublication()
              .retention()
              .retainedStagePath());
    }

    assertTrue(Files.exists(stagedBackupPath));
    assertTrue(Files.exists(stagedKeyPath));
    assertArrayEquals(occupiedKeyBefore, Files.readAllBytes(finalKeyPath));
  }

  @Test
  void stagedBackupPair_retainsItsAvailableStageWhenTheKeyStageDisappearsBeforePublication()
      throws Exception {
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
      sealBackupForPublication(stagedPair);
      assertThrows(
          IllegalStateException.class, () -> stagedPair.commit(backupBinding(finalBackupPath)));
    }

    assertTrue(Files.exists(stagedBackupPath));
    assertFalse(Files.exists(finalBackupPath));
    assertFalse(Files.exists(finalKeyPath));
  }

  @Test
  void stagedBackupPair_retentionClosesBothPassphraseStatesAndRetainsEveryOwnedStage()
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
      sealBackupForPublication(stagedPair);
      assertThrows(
          IllegalStateException.class,
          () -> stagedPair.commit(backupBinding(missingKeyFinalBackupPath)));
    }
    assertTrue(Files.exists(missingKeyBackupPath));

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
      stagedPair.retainUnpublishedArtifacts();
    }

    assertTrue(Files.exists(publishedStagePath));
    assertTrue(Files.exists(publishedKeyStagePath));
  }

  @Test
  void stagedBackupPair_passphraseClosureIsIdempotentBeforeRetentionTakesOwnershipOfTheStages()
      throws Exception {
    Path stagedBackupPath = writeArtifact("backup-passphrase-retention/staged.sqlite", "backup");
    Path stagedKeyPath = writeArtifact("backup-passphrase-retention/staged.key", "key");
    Path finalBackupPath =
        tempDirectory.resolve("backup-passphrase-retention").resolve("backup.sqlite");
    Path finalKeyPath = tempDirectory.resolve("backup-passphrase-retention").resolve("backup.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      closeUnusedBackupPassphrase(stagedPair);
      closeUnusedBackupPassphrase(stagedPair);
    }

    assertTrue(Files.exists(stagedBackupPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedBackupPair_retainsStageEvidenceWhenTheOtherOwnershipRecordIsCorrupted()
      throws Exception {
    Path stagedBackupPath =
        writeArtifact("backup-retention-record-failure/staged.sqlite", "backup");
    Path stagedKeyPath = writeArtifact("backup-retention-record-failure/staged.key", "key");
    Path finalBackupPath =
        writeArtifact("backup-retention-record-failure/backup.sqlite", "occupied");
    Path finalKeyPath =
        tempDirectory.resolve("backup-retention-record-failure").resolve("backup.key");
    SqliteOwnedStagedArtifact stagedBackup =
        SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath);
    Path stagedBackupRecordPath =
        ownedRecordPath(
            Objects.requireNonNull(stagedBackupPath.getParent(), "stagedBackup parent"));
    SqliteOwnedStagedArtifact stagedKey =
        SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair stagedPair =
            SqliteStagedBackupPairFactory.create(
                stagedBackup,
                finalBackupPath,
                stagedKey,
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      sealBackupForPublication(stagedPair);
      Files.delete(stagedBackupRecordPath);
      Files.createDirectory(stagedBackupRecordPath);
      Files.writeString(stagedBackupRecordPath.resolve("tamper-blocker"), "altered");
      assertThrows(
          IllegalStateException.class, () -> stagedPair.commit(backupBinding(finalBackupPath)));
    }

    assertEquals("occupied", Files.readString(finalBackupPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBackupPath));
    assertTrue(Files.exists(stagedKeyPath));
    assertTrue(Files.isDirectory(stagedBackupRecordPath));
    assertFalse(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
  }

  @Test
  void stagedRestoredBookPair_retainsItsKeyStageWhenBookStageEvidenceIsCorrupted()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-retention-record-failure/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-retention-record-failure/staged.key", "key");
    Path finalBookPath =
        tempDirectory.resolve("restore-retention-record-failure").resolve("book.sqlite");
    Path finalKeyPath =
        tempDirectory.resolve("restore-retention-record-failure").resolve("book.key");
    SqliteOwnedStagedArtifact stagedBook =
        SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    Path stagedBookRecordPath =
        ownedRecordPath(Objects.requireNonNull(stagedBookPath.getParent(), "stagedBook parent"));
    SqliteOwnedStagedArtifact stagedKey =
        SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair stagedPair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    stagedBook, finalBookPath, stagedKey, finalKeyPath),
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                passphrase,
                VERIFICATION_SUPPORT)) {
      Files.delete(stagedBookPath);
      Files.delete(stagedBookRecordPath);
      Files.createDirectory(stagedBookRecordPath);
      Files.writeString(stagedBookRecordPath.resolve("tamper-blocker"), "altered");
      assertThrows(
          IllegalStateException.class,
          () -> stagedPair.commit(restoreBinding(stagedBookPath, stagedKeyPath)));
    }

    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedKeyPath));
    assertTrue(Files.isDirectory(stagedBookRecordPath));
    assertFalse(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
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

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair backupPair =
            SqliteStagedBackupPairFactory.create(
                backupStaged,
                backupFinalPath,
                backupKeyStaged,
                backupKeyFinalPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      sealBackupForPublication(backupPair);
      replaceWithNonemptyDirectory(backupKeyRecordPath);
      assertThrows(
          IllegalStateException.class, () -> backupPair.commit(backupBinding(backupFinalPath)));
    }

    assertFalse(Files.exists(backupFinalPath));
    assertFalse(Files.exists(backupKeyFinalPath));
    assertTrue(Files.exists(backupStagedPath));
    assertTrue(Files.exists(backupKeyStagedPath));
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

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair restoredPair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    restoredStaged, restoredFinalPath, restoredKeyStaged, restoredKeyFinalPath),
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                passphrase,
                VERIFICATION_SUPPORT)) {
      replaceWithNonemptyDirectory(restoredKeyRecordPath);
      assertThrows(
          IllegalStateException.class,
          () -> restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath)));
    }

    assertFalse(Files.exists(restoredFinalPath));
    assertFalse(Files.exists(restoredKeyFinalPath));
    assertTrue(Files.exists(restoredStagedPath));
    assertTrue(Files.exists(restoredKeyStagedPath));
    assertTrue(Files.isDirectory(restoredKeyRecordPath));

    Path rekeyStagedBookPath =
        writeArtifact("rekey-published-retention/staged.sqlite", "rekeyed-book");
    Path rekeyStagedKeyPath = writeArtifact("rekey-published-retention/staged.key", "key");
    Path rekeyFinalBookPath =
        writeArtifact("rekey-published-retention/book.sqlite", "previous-book");
    Path rekeyFinalKeyPath = tempDirectory.resolve("rekey-published-retention").resolve("book.key");
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
      rekeyPair.commit(
          rekeyBinding(rekeyFinalBookPath, rekeyFinalBookPath.resolveSibling("source.key")));
    }

    assertEquals("rekeyed-book", Files.readString(rekeyFinalBookPath));
    assertTrue(Files.exists(rekeyFinalKeyPath));
    assertTrue(Files.exists(rekeyStagedBookPath));
    assertTrue(Files.exists(rekeyStagedKeyPath));
    assertTrue(Files.isDirectory(rekeyKeyRecordPath));
  }

  @Test
  void rekeyBoundaryChangeBeforeSecretPublicationLeavesTheSecretUnpublished() throws Exception {
    Path stagedBookPath = writeArtifact("rekey-boundary/staged.sqlite", "rekeyed book");
    Path stagedKeyPath = writeArtifact("rekey-boundary/staged.key", "generated key");
    Path finalBookPath = writeArtifact("rekey-boundary/book.sqlite", "selected book");
    Path finalKeyPath = tempDirectory.resolve("rekey-boundary/book.key");
    SqliteOwnedStagedArtifact stagedBook =
        SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    SqliteOwnedStagedArtifact stagedKey =
        SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);
    boolean[] externallyChanged = {false};

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair pair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    stagedBook, finalBookPath, stagedKey, finalKeyPath),
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                passphrase,
                VERIFICATION_SUPPORT,
                SqliteRestoredBookPairPublication.defaultOperators(),
                null,
                null,
                (step, ignoredParent) -> {
                  if (step
                          == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                              .RECOVERY_RECORD
                      && !externallyChanged[0]) {
                    Files.writeString(finalBookPath, "externally changed selected book");
                    externallyChanged[0] = true;
                  }
                })) {
      ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired outcome =
          assertInstanceOf(
              ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
              pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));

      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState
              .DURABILITY_UNCONFIRMED,
          outcome.recoveryRecordState());
    }

    assertTrue(externallyChanged[0]);
    assertEquals("externally changed selected book", Files.readString(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void recoveryRecordForceFailure_preventsEveryBackupFinalPrimitive() throws Exception {
    Path stagedBackupPath = writeArtifact("record-forcer-throws/staged.sqlite", "backup");
    Path stagedKeyPath = writeArtifact("record-forcer-throws/staged.key", "backup key");
    Path finalBackupPath = tempDirectory.resolve("record-forcer-throws").resolve("backup.sqlite");
    Path finalKeyPath = tempDirectory.resolve("record-forcer-throws").resolve("backup.key");
    AtomicInteger finalPrimitiveCalls = new AtomicInteger();

    try (SqliteStagedBackupPair pair =
        SqliteStagedBackupPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            (finalPath, stagedPath) -> {
              finalPrimitiveCalls.incrementAndGet();
              Files.createLink(finalPath, stagedPath);
            },
            (finalPath, stagedPath) -> {
              finalPrimitiveCalls.incrementAndGet();
              Files.createLink(finalPath, stagedPath);
            },
            null,
            null,
            (step, parentDirectory) -> {},
            evidencePath -> {
              throw new IOException("simulated recovery-evidence force failure");
            })) {
      // Witness acquisition exercises the injected book primitive once; only commit boundaries
      // count for this proof.
      finalPrimitiveCalls.set(0);
      sealBackupForPublication(pair);

      ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired outcome =
          assertInstanceOf(
              ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
              pair.commit(backupBinding(finalBackupPath)));

      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState
              .DURABLY_RETAINED,
          outcome.recoveryRecordState());
    }

    assertEquals(0, finalPrimitiveCalls.get());
    assertFalse(Files.exists(finalBackupPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBackupPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedBackupPairWrapsACheckedFailureBeforeTheRecoveryBoundary() throws Exception {
    Path stagedBackupPath = writeArtifact("backup-pre-boundary-io/staged.sqlite", "backup");
    Path stagedKeyPath = writeArtifact("backup-pre-boundary-io/staged.key", "backup key");
    Path finalBackupPath =
        tempDirectory.resolve("backup-pre-boundary-io").resolve("backup.sqlite");
    Path finalKeyPath = tempDirectory.resolve("backup-pre-boundary-io").resolve("backup.key");
    IOException injected = new IOException("staged-member durability failed");

    try (SqliteStagedBackupPair pair =
        SqliteStagedBackupPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            Files::createLink,
            Files::createLink,
            null,
            null,
            (step, ignoredParent) -> {
              if (step
                  == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                      .STAGED_MEMBER_DURABILITY) {
                throw injected;
              }
            },
            SqliteSecureRegularFileAccess::forceFile)) {
      sealBackupForPublication(pair);

      IllegalStateException failure =
          assertThrows(IllegalStateException.class, () -> pair.commit(backupBinding(finalBackupPath)));

      assertEquals("Failed to publish the staged FinGrind backup pair.", failure.getMessage());
      assertSame(injected, failure.getCause());
    }

    assertFalse(Files.exists(finalBackupPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBackupPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedRestoredBookPairWrapsACheckedFailureBeforeTheRecoveryBoundary() throws Exception {
    Path stagedBookPath = writeArtifact("restore-pre-boundary-io/staged.sqlite", "restored book");
    Path stagedKeyPath = writeArtifact("restore-pre-boundary-io/staged.key", "restored key");
    Path finalBookPath =
        tempDirectory.resolve("restore-pre-boundary-io").resolve("restored.sqlite");
    Path finalKeyPath = tempDirectory.resolve("restore-pre-boundary-io").resolve("restored.key");
    IOException injected = new IOException("staged-member durability failed");

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (step, ignoredParent) -> {
              if (step
                  == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                      .STAGED_MEMBER_DURABILITY) {
                throw injected;
              }
            },
            SqliteSecureRegularFileAccess::forceFile)) {
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> pair.commit(restoreBinding(stagedBookPath, stagedKeyPath)));

      assertEquals(
          "Failed to publish the restored FinGrind live-book pair at "
              + finalBookPath.toAbsolutePath()
              + ".",
          failure.getMessage());
      assertSame(injected, failure.getCause());
    }

    assertFalse(Files.exists(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void recoveryRecordPromotionFailureStopsBackupAndRestoreBeforeAnyFinalMember() throws Exception {
    Path backupStagedPath = writeArtifact("record-promotion-failure/backup.stage", "backup");
    Path backupKeyStagedPath =
        writeArtifact("record-promotion-failure/backup.key.stage", "backup key");
    Path backupFinalPath =
        tempDirectory.resolve("record-promotion-failure").resolve("backup.sqlite");
    Path backupKeyFinalPath =
        tempDirectory.resolve("record-promotion-failure").resolve("backup.key");

    try (SqliteStagedBackupPair backupPair =
        SqliteStagedBackupPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(backupFinalPath, backupStagedPath),
                backupFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(backupKeyFinalPath, backupKeyStagedPath),
                backupKeyFinalPath),
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            Files::createLink,
            Files::createLink,
            null,
            null,
            recoveryRecordFailingDirectoryForcer(),
            SqliteSecureRegularFileAccess::forceFile)) {
      sealBackupForPublication(backupPair);
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
          backupPair.commit(backupBinding(backupFinalPath)));
    }

    assertFalse(Files.exists(backupFinalPath));
    assertFalse(Files.exists(backupKeyFinalPath));

    Path restoredStagedPath =
        writeArtifact("record-promotion-failure/restore.stage", "restored book");
    Path restoredKeyStagedPath =
        writeArtifact("record-promotion-failure/restore.key.stage", "restored key");
    Path restoredFinalPath =
        tempDirectory.resolve("record-promotion-failure").resolve("restored.sqlite");
    Path restoredKeyFinalPath =
        tempDirectory.resolve("record-promotion-failure").resolve("restored.key");

    try (SqliteStagedRestoredBookPair restoredPair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(restoredFinalPath, restoredStagedPath),
                restoredFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(
                    restoredKeyFinalPath, restoredKeyStagedPath),
                restoredKeyFinalPath),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            recoveryRecordFailingDirectoryForcer(),
            SqliteSecureRegularFileAccess::forceFile)) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
          restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath)));
    }

    assertFalse(Files.exists(restoredFinalPath));
    assertFalse(Files.exists(restoredKeyFinalPath));
  }

  @Test
  void recordForceFailureAfterSecretPublicationLeavesBothPairKindsRecoveryBound() throws Exception {
    Path backupStagedPath = writeArtifact("post-secret-force-failure/backup.stage", "backup");
    Path backupKeyStagedPath =
        writeArtifact("post-secret-force-failure/backup.key.stage", "backup key");
    Path backupFinalPath =
        tempDirectory.resolve("post-secret-force-failure").resolve("backup.sqlite");
    Path backupKeyFinalPath =
        tempDirectory.resolve("post-secret-force-failure").resolve("backup.key");
    AtomicInteger backupForceCalls = new AtomicInteger();

    try (SqliteStagedBackupPair backupPair =
        SqliteStagedBackupPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(backupFinalPath, backupStagedPath),
                backupFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(backupKeyFinalPath, backupKeyStagedPath),
                backupKeyFinalPath),
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            Files::createLink,
            Files::createLink,
            null,
            null,
            (ignoredStep, ignoredParent) -> {},
            recordForcerFailingAtBookBoundary(backupForceCalls))) {
      sealBackupForPublication(backupPair);
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          backupPair.commit(backupBinding(backupFinalPath)));
    }

    assertEquals(4, backupForceCalls.get());
    assertFalse(Files.exists(backupFinalPath));
    assertTrue(Files.exists(backupKeyFinalPath));

    Path restoredStagedPath =
        writeArtifact("post-secret-force-failure/restore.stage", "restored book");
    Path restoredKeyStagedPath =
        writeArtifact("post-secret-force-failure/restore.key.stage", "restored key");
    Path restoredFinalPath =
        tempDirectory.resolve("post-secret-force-failure").resolve("restored.sqlite");
    Path restoredKeyFinalPath =
        tempDirectory.resolve("post-secret-force-failure").resolve("restored.key");
    AtomicInteger restoredForceCalls = new AtomicInteger();

    try (SqliteStagedRestoredBookPair restoredPair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(restoredFinalPath, restoredStagedPath),
                restoredFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(
                    restoredKeyFinalPath, restoredKeyStagedPath),
                restoredKeyFinalPath),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (ignoredStep, ignoredParent) -> {},
            recordForcerFailingAtBookBoundary(restoredForceCalls))) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath)));
    }

    assertEquals(4, restoredForceCalls.get());
    assertFalse(Files.exists(restoredFinalPath));
    assertTrue(Files.exists(restoredKeyFinalPath));
  }

  @Test
  void recoveryRecordForceMutation_blocksEveryRestoredFinalPrimitive() throws Exception {
    Path stagedBookPath = writeArtifact("record-forcer-mutates/staged.sqlite", "restored book");
    Path stagedKeyPath = writeArtifact("record-forcer-mutates/staged.key", "restored key");
    Path finalBookPath = writeArtifact("record-forcer-mutates/book.sqlite", "previous book");
    Path finalKeyPath = tempDirectory.resolve("record-forcer-mutates").resolve("book.key");
    AtomicInteger finalLinkCalls = new AtomicInteger();
    AtomicInteger finalMoveCalls = new AtomicInteger();

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            new SqliteRestoredBookPairPublication.Operators(
                (finalPath, stagedPath) -> {
                  finalLinkCalls.incrementAndGet();
                  Files.createLink(finalPath, stagedPath);
                },
                (finalPath, stagedPath) -> {
                  finalLinkCalls.incrementAndGet();
                  Files.createLink(finalPath, stagedPath);
                },
                (stagedPath, finalPath) -> {
                  finalMoveCalls.incrementAndGet();
                  SqliteProtectedBookPublicationSupport.moveReplacing(stagedPath, finalPath);
                }),
            null,
            null,
            (step, parentDirectory) -> {},
            evidencePath -> Files.writeString(evidencePath, "mutated recovery evidence"))) {
      // Witness acquisition exercises the injected book primitive once; only commit boundaries
      // count for this proof.
      finalLinkCalls.set(0);
      finalMoveCalls.set(0);

      ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired outcome =
          assertInstanceOf(
              ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
              pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState
              .DURABILITY_UNCONFIRMED,
          outcome.recoveryRecordState());
    }

    assertEquals(0, finalLinkCalls.get());
    assertEquals(0, finalMoveCalls.get());
    assertEquals("previous book", Files.readString(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedPairs_retainTheirStageEvidenceAfterSuccessfulPublicationWhenOwnerRecordsAreAltered()
      throws Exception {
    Path backupStagedPath = writeArtifact("backup-published-retention/staged.sqlite", "backup");
    Path backupKeyStagedPath = writeArtifact("backup-published-retention/staged.key", "key");
    Path backupFinalPath =
        tempDirectory.resolve("backup-published-retention").resolve("backup.sqlite");
    Path backupKeyFinalPath =
        tempDirectory.resolve("backup-published-retention").resolve("backup.key");
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
                },
                (step, parentDirectory) -> {})) {
      sealBackupForPublication(backupPair);
      backupPair.commit(backupBinding(backupFinalPath));
    }

    assertTrue(Files.exists(backupFinalPath));
    assertTrue(Files.exists(backupKeyFinalPath));
    assertTrue(Files.exists(backupStagedPath));
    assertTrue(Files.exists(backupKeyStagedPath));
    assertTrue(Files.isDirectory(backupKeyRecordPath));

    Path restoredStagedPath = writeArtifact("restore-published-retention/staged.sqlite", "book");
    Path restoredKeyStagedPath = writeArtifact("restore-published-retention/staged.key", "key");
    Path restoredFinalPath =
        tempDirectory.resolve("restore-published-retention").resolve("book.sqlite");
    Path restoredKeyFinalPath =
        tempDirectory.resolve("restore-published-retention").resolve("book.key");
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
      restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath));
    }

    assertTrue(Files.exists(restoredFinalPath));
    assertTrue(Files.exists(restoredKeyFinalPath));
    assertTrue(Files.exists(restoredStagedPath));
    assertTrue(Files.exists(restoredKeyStagedPath));
    assertTrue(Files.isDirectory(restoredKeyRecordPath));
  }

  @Test
  void stagedPairCommitReturnsItsExactSuccessfulOutcomeOnReplay() throws Exception {
    Path backupStagedPath = writeArtifact("replay-success/backup.stage", "backup");
    Path backupKeyStagedPath = writeArtifact("replay-success/backup.key.stage", "key");
    Path backupFinalPath = tempDirectory.resolve("replay-success").resolve("backup.sqlite");
    Path backupKeyFinalPath = tempDirectory.resolve("replay-success").resolve("backup.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair backupPair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(backupFinalPath, backupStagedPath),
                backupFinalPath,
                SqliteOwnedStagedArtifact.recordExisting(backupKeyFinalPath, backupKeyStagedPath),
                backupKeyFinalPath,
                passphrase,
                VERIFICATION_SUPPORT)) {
      sealBackupForPublication(backupPair);
      var published = backupPair.commit(backupBinding(backupFinalPath));
      assertInstanceOf(
          dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome.Published.class,
          published);
      assertSame(published, backupPair.commit(backupBinding(backupFinalPath)));
    }

    Path restoredStagedPath = writeArtifact("replay-success/restore.stage", "restored book");
    Path restoredKeyStagedPath = writeArtifact("replay-success/restore.key.stage", "restored key");
    Path restoredFinalPath = tempDirectory.resolve("replay-success").resolve("restored.sqlite");
    Path restoredKeyFinalPath = tempDirectory.resolve("replay-success").resolve("restored.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair restoredPair =
            SqliteStagedRestoredBookPairFactory.create(
                new SqliteStagedProtectedBookPairArtifacts(
                    SqliteOwnedStagedArtifact.recordExisting(restoredFinalPath, restoredStagedPath),
                    restoredFinalPath,
                    SqliteOwnedStagedArtifact.recordExisting(
                        restoredKeyFinalPath, restoredKeyStagedPath),
                    restoredKeyFinalPath),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                passphrase,
                VERIFICATION_SUPPORT)) {
      var published =
          restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath));
      assertInstanceOf(
          dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome.Published.class,
          published);
      assertSame(
          published,
          restoredPair.commit(restoreBinding(restoredStagedPath, restoredKeyStagedPath)));
    }
  }

  @Test
  void stagedRestoredBookPair_refusesASelectedTargetChangedAfterRecoveryEvidence()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-selected-target-change/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-selected-target-change/staged.key", "key");
    Path finalBookPath =
        writeArtifact("restore-selected-target-change/book.sqlite", "selected original book");
    Path finalKeyPath = tempDirectory.resolve("restore-selected-target-change").resolve("book.key");
    AtomicBoolean changedAfterRecoveryEvidence = new AtomicBoolean();

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (step, parentDirectory) -> {
              if (step
                      == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                          .RECOVERY_RECORD
                  && changedAfterRecoveryEvidence.compareAndSet(false, true)) {
                Files.writeString(finalBookPath, "selected replacement by another writer");
              }
            })) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
          pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
    }

    assertTrue(changedAfterRecoveryEvidence.get());
    assertEquals("selected replacement by another writer", Files.readString(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void stagedRestoredBookPair_keepsItsPublicationRecoverableWhenTheTargetChangesAfterSecret()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-post-secret-change/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-post-secret-change/staged.key", "key");
    Path finalBookPath =
        writeArtifact("restore-post-secret-change/book.sqlite", "selected original book");
    Path finalKeyPath = tempDirectory.resolve("restore-post-secret-change").resolve("book.key");
    AtomicBoolean changedAfterSecretPublication = new AtomicBoolean();

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            SqliteRestoredBookPairPublication.defaultOperators(),
            null,
            null,
            (step, parentDirectory) -> {
              if (step
                      == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
                          .GENERATED_SECRET_PUBLICATION
                  && changedAfterSecretPublication.compareAndSet(false, true)) {
                Files.writeString(
                    finalBookPath, "selected target changed after secret publication");
              }
            })) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
    }

    assertTrue(changedAfterSecretPublication.get());
    assertEquals(
        "selected target changed after secret publication", Files.readString(finalBookPath));
    assertTrue(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void restoredBookMoveFailureAfterItsAttemptIsCompletionUncertain() throws Exception {
    Path stagedBookPath = writeArtifact("restore-book-move-failure/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-book-move-failure/staged.key", "key");
    Path finalBookPath =
        writeArtifact("restore-book-move-failure/book.sqlite", "selected original book");
    Path finalKeyPath = tempDirectory.resolve("restore-book-move-failure").resolve("book.key");

    try (SqliteStagedRestoredBookPair pair =
        SqliteStagedRestoredBookPairFactory.create(
            new SqliteStagedProtectedBookPairArtifacts(
                SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath),
                finalBookPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8),
            VERIFICATION_SUPPORT,
            new SqliteRestoredBookPairPublication.Operators(
                Files::createLink,
                Files::createLink,
                (stagedPath, targetPath) -> {
                  throw new IOException("simulated final book move failure");
                }))) {
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          pair.commit(rekeyBinding(finalBookPath, finalBookPath.resolveSibling("source.key"))));
    }

    assertEquals("selected original book", Files.readString(finalBookPath));
    assertTrue(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void backupBookLinkFailureAfterItsAttemptIsCompletionUncertain() throws Exception {
    Path stagedBackupPath = writeArtifact("backup-book-link-failure/staged.sqlite", "backup");
    Path stagedKeyPath = writeArtifact("backup-book-link-failure/staged.key", "key");
    Path finalBackupPath =
        tempDirectory.resolve("backup-book-link-failure").resolve("backup.sqlite");
    Path finalKeyPath = tempDirectory.resolve("backup-book-link-failure").resolve("backup.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedBackupPair pair =
            SqliteStagedBackupPairFactory.create(
                SqliteOwnedStagedArtifact.recordExisting(finalBackupPath, stagedBackupPath),
                finalBackupPath,
                SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath),
                finalKeyPath,
                passphrase,
                VERIFICATION_SUPPORT,
                Files::createLink,
                (finalPath, stagedPath) -> {
                  throw new IOException("simulated final backup link failure");
                },
                (ignoredStep, ignoredParent) -> {})) {
      sealBackupForPublication(pair);
      assertInstanceOf(
          ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
          pair.commit(backupBinding(finalBackupPath)));
    }

    assertFalse(Files.exists(finalBackupPath));
    assertTrue(Files.exists(finalKeyPath));
    assertTrue(Files.exists(stagedBackupPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  @Test
  void retainedRestoredPairCannotBeCommittedAfterItIsFinishedWithoutAPublicationOutcome()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-finished/staged.sqlite", "book");
    Path stagedKeyPath = writeArtifact("restore-finished/staged.key", "key");
    Path finalBookPath = tempDirectory.resolve("restore-finished").resolve("book.sqlite");
    Path finalKeyPath = tempDirectory.resolve("restore-finished").resolve("book.key");

    try (SqliteBookPassphrase passphrase = testPassphrase();
        SqliteStagedRestoredBookPair pair =
            newStagedRestoredBookPair(
                stagedBookPath, finalBookPath, stagedKeyPath, finalKeyPath, passphrase)) {
      pair.retainUnpublishedArtifacts();
      assertThrows(
          IllegalStateException.class,
          () -> pair.commit(restoreBinding(stagedBookPath, stagedKeyPath)));
    }

    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
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
  void stagedRestoredBookPair_retainsStagesBeforeAndAfterKeyPublicationFailures() throws Exception {
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
      assertThrows(
          IllegalStateException.class,
          () -> stagedPair.commit(restoreBinding(missingKeyStagedBook, missingKeyPath)));
    }
    assertTrue(Files.exists(missingKeyStagedBook));

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
      assertThrows(
          IllegalStateException.class,
          () -> stagedPair.commit(restoreBinding(stagedBookPath, stagedKeyPath)));
    }
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(finalKeyPath));
    assertEquals("occupied", Files.readString(originalBookContent));
  }

  private static SqliteBookPassphrase testPassphrase() {
    return SqliteBookPassphrase.fromUtf8Bytes(
        "staged protected-book pair", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
  }

  private static SqliteProtectedBookPublicationSupport.PairDirectoryForcer
      recoveryRecordFailingDirectoryForcer() {
    return (step, parentDirectory) -> {
      if (step
          == SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_RECORD) {
        throw new IOException("simulated recovery-record promotion failure");
      }
    };
  }

  private static SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer
      recordForcerFailingAtBookBoundary(AtomicInteger forceCalls) {
    return evidencePath -> {
      if (forceCalls.incrementAndGet() > 3) {
        throw new IOException("simulated record force failure before book publication");
      }
      SqliteSecureRegularFileAccess.forceFile(evidencePath);
    };
  }

  private static void closeUnusedBackupPassphrase(SqliteStagedBackupPair stagedPair) {
    try {
      MethodHandle closeUnusedBackupPassphrase =
          MethodHandles.privateLookupIn(SqliteStagedBackupPair.class, MethodHandles.lookup())
              .findVirtual(
                  SqliteStagedBackupPair.class,
                  "closeUnusedBackupPassphrase",
                  MethodType.methodType(void.class));
      closeUnusedBackupPassphrase.invoke(stagedPair);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("Failed to invoke staged backup passphrase closure.", throwable);
    }
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
    Files.writeString(path.resolve("tamper-blocker"), "altered");
  }

  private static void requirePosixPermissions(Path path) {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        path.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "This filesystem cannot model POSIX access denial.");
  }
}
