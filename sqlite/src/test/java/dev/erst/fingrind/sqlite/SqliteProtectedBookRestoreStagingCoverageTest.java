package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Focused coverage for SQLite restore staging, staged restore publication, and cleanup paths. */
class SqliteProtectedBookRestoreStagingCoverageTest
    extends SqliteProtectedBookMaintenanceStoreCoverageTestSupport {

  @Test
  void stageRestoredBookPair_translatesManagedTargetPathRejections() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path parentBlocker = tempDirectory.resolve("restore-translation").resolve("parent-blocker");
    writeArtifact("restore-translation/parent-ready", "ready");
    Files.writeString(parentBlocker, "not-a-directory");
    Path invalidRestoredBookPath = parentBlocker.resolve("restored.sqlite");
    Path restoredBookKeyPath = tempDirectory.resolve("restore-translation").resolve("restored.key");

    ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
        assertInstanceOf(
            ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
            assertThrows(
                    ProtectedBookMaintenanceRejectionException.class,
                    () ->
                        store.preparePairPublication(
                            restoredBookKeyPath,
                            invalidRestoredBookPath,
                            RestoredBookTargetPolicy.REPLACE_SELECTED,
                            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
                            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET))
                .rejection());

    assertEquals(ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, rejection.artifactRole());
    assertEquals(
        ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION, rejection.pathFailure());
  }

  @Test
  void stageResolvedRestoredBookPair_cleansUpArtifactsOnCopyAndRekeyFailures() throws Exception {
    Path missingSourceBookPath =
        tempDirectory.resolve("restore-stage-io").resolve("missing-source.sqlite");
    Path ioTargetBookPath = tempDirectory.resolve("restore-stage-io").resolve("restored.sqlite");
    Path ioTargetKeyPath = tempDirectory.resolve("restore-stage-io").resolve("restored.key");

    try (SqliteBookPassphrase sourcePassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "missing restore source", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8))) {
      MaintenanceFailure ioFailure =
          failedValue(
              SqliteProtectedBookStagingSupport.stageResolvedRestoredBookPair(
                  missingSourceBookPath,
                  ioTargetBookPath,
                  ioTargetKeyPath,
                  RestoredBookTargetPolicy.REPLACE_SELECTED,
                  sourcePassphrase,
                  VERIFICATION_SUPPORT,
                  checkpoint -> {},
                  SqliteBookKeyFileGenerator::generate));

      assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, ioFailure.descriptor());
      assertEquals(
          SqliteProtectedBookStagingSupport.StagingCheckpoint.RESTORE_COPY.failureMessage(),
          ioFailure.message());
      assertEquals("bookFilePath", ioFailure.argument());
      assertEquals(
          ioTargetBookPath.toAbsolutePath().normalize(),
          java.util.Objects.requireNonNull(ioFailure.paths(), "failure paths").path());
    }
    assertNoRestoreStageArtifacts(ioTargetBookPath, ioTargetKeyPath);

    Path bogusSourceBookPath =
        writeArtifact("restore-stage-runtime/source.sqlite", "not-a-protected-sqlite-book");
    Path runtimeTargetBookPath =
        tempDirectory.resolve("restore-stage-runtime").resolve("restored.sqlite");
    Path runtimeTargetKeyPath =
        tempDirectory.resolve("restore-stage-runtime").resolve("restored.key");

    try (SqliteBookPassphrase sourcePassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "bogus restore source", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8))) {
      MaintenanceFailure runtimeFailure =
          failedValue(
              SqliteProtectedBookStagingSupport.stageResolvedRestoredBookPair(
                  bogusSourceBookPath,
                  runtimeTargetBookPath,
                  runtimeTargetKeyPath,
                  RestoredBookTargetPolicy.REPLACE_SELECTED,
                  sourcePassphrase,
                  VERIFICATION_SUPPORT,
                  checkpoint -> {},
                  SqliteBookKeyFileGenerator::generate));
      assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, runtimeFailure.descriptor());
      assertEquals(
          SqliteProtectedBookStagingSupport.StagingCheckpoint.RESTORE_REKEY.failureMessage(),
          runtimeFailure.message());
      assertEquals("bookFilePath", runtimeFailure.argument());
      assertEquals(
          runtimeTargetBookPath.toAbsolutePath().normalize(),
          java.util.Objects.requireNonNull(runtimeFailure.paths(), "failure paths").path());
    }
    assertNoRestoreStageArtifacts(runtimeTargetBookPath, runtimeTargetKeyPath);
  }

  @Test
  void directStagingSetupFailures_rethrowWithoutCreatingOwnedArtifacts() throws Exception {
    Path sourceBookPath = tempDirectory.resolve("setup-failure").resolve("source.sqlite");
    Path backupKeyPath = tempDirectory.resolve("setup-failure").resolve("backup.key");
    Path restoredKeyPath = tempDirectory.resolve("setup-failure").resolve("restored.key");

    try (SqliteBookPassphrase backupPassphrase =
            SqliteBookPassphrase.fromUtf8Bytes(
                "backup setup failure", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
        SqliteBookPassphrase restorePassphrase =
            SqliteBookPassphrase.fromUtf8Bytes(
                "restore setup failure", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8))) {
      assertThrows(
          NullPointerException.class,
          () ->
              SqliteProtectedBookStagingSupport.stageResolvedBackupPair(
                  sourceBookPath,
                  NullTestSupport.nullOf(Path.class),
                  backupKeyPath,
                  backupPassphrase,
                  VERIFICATION_SUPPORT,
                  checkpoint -> {},
                  SqliteBookKeyFileGenerator::generate,
                  null));
      assertThrows(
          NullPointerException.class,
          () ->
              SqliteProtectedBookStagingSupport.stageResolvedRestoredBookPair(
                  sourceBookPath,
                  NullTestSupport.nullOf(Path.class),
                  restoredKeyPath,
                  RestoredBookTargetPolicy.REPLACE_SELECTED,
                  restorePassphrase,
                  VERIFICATION_SUPPORT,
                  checkpoint -> {},
                  SqliteBookKeyFileGenerator::generate,
                  null));
    }

    assertFalse(Files.exists(backupKeyPath));
    assertFalse(Files.exists(restoredKeyPath));
  }

  @Test
  void stagedRestoredBookPair_verifiesPublishesAndReencryptsOverAnExistingBook() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("restore-success").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path restoredBookPath = writeArtifact("restore-success/restored.sqlite", "legacy-book");
    Path restoredBookKeyPath = tempDirectory.resolve("restore-success").resolve("restored.key");
    Path restoredParent =
        java.util.Objects.requireNonNull(restoredBookPath.getParent(), "restoredBookPath parent");

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareRestoredBookPair(
                store,
                restoredBookPath,
                restoredBookKeyPath,
                RestoredBookTargetPolicy.REPLACE_SELECTED);
        StagedRestoredBookPair stagedRestoredBookPair =
            acceptedValue(
                store.stageRestoredBookPair(verifiedSourceBook, preparedPairPublication))) {
      assertEquals(0L, countMatchingChildren(restoredParent, ".previous-"));
      assertInstanceOf(
          ProtectedBookMaintenanceStore.VerifiedBook.class,
          acceptedValue(stagedRestoredBookPair.verifyInitializedRestoredBook()));

      stagedRestoredBookPair.commit();
      stagedRestoredBookPair.commit();
      stagedRestoredBookPair.close();
    }

    assertTrue(Files.exists(restoredBookPath));
    assertTrue(Files.exists(restoredBookKeyPath));
    assertEquals(0L, countMatchingChildren(restoredParent, ".previous-"));
    assertInstanceOf(
        ProtectedBookMaintenanceStore.VerifiedBook.class,
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(keyedAccess(restoredBookPath, restoredBookKeyPath)),
                ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET)));
    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(keyedAccess(restoredBookPath, sourceKeyFilePath(sourceAccess))),
                ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET)),
        restoredBookPath,
        ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED);
  }

  @Test
  void stagedRestoredBookPair_closeRollsBackAndRollbackRemainsIdempotent() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("restore-close").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path restoredBookPath = tempDirectory.resolve("restore-close").resolve("restored.sqlite");
    Path restoredBookKeyPath = tempDirectory.resolve("restore-close").resolve("restored.key");
    Path restoredParent =
        java.util.Objects.requireNonNull(restoredBookPath.getParent(), "restoredBookPath parent");

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareRestoredBookPair(
                store,
                restoredBookPath,
                restoredBookKeyPath,
                RestoredBookTargetPolicy.REPLACE_SELECTED);
        StagedRestoredBookPair stagedRestoredBookPair =
            acceptedValue(
                store.stageRestoredBookPair(verifiedSourceBook, preparedPairPublication))) {
      assertTrue(countMatchingChildren(restoredParent, ".restore-") > 0L);
      assertTrue(countMatchingChildren(restoredParent, ".restore-key-") > 0L);
      stagedRestoredBookPair.close();
      stagedRestoredBookPair.rollback();
      stagedRestoredBookPair.close();
    }

    assertFalse(Files.exists(restoredBookPath));
    assertFalse(Files.exists(restoredBookKeyPath));
    assertEquals(0L, countMatchingChildren(restoredParent, ".restore-"));
    assertEquals(0L, countMatchingChildren(restoredParent, ".restore-key-"));
  }

  @Test
  void stagedRestoredBookPair_rollbackHandlesAlreadyClearedPassphrases() throws Exception {
    Path stagedBookPath = writeArtifact("restore-null-passphrase/staged.sqlite", "staged-book");
    Path stagedBookKeyPath = writeArtifact("restore-null-passphrase/staged.key", "staged-key");
    Path finalBookPath = tempDirectory.resolve("restore-null-passphrase").resolve("final.sqlite");
    Path finalBookKeyPath = tempDirectory.resolve("restore-null-passphrase").resolve("final.key");
    try (SqliteBookPassphrase restoredPassphrase =
            SqliteBookPassphrase.fromUtf8Bytes(
                "already-cleared passphrase", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
        SqliteStagedRestoredBookPair stagedRestoredBookPair =
            newStagedRestoredBookPair(
                stagedBookPath,
                finalBookPath,
                stagedBookKeyPath,
                finalBookKeyPath,
                restoredPassphrase)) {
      setPrivateField(stagedRestoredBookPair, "restoredPassphrase", null);
      stagedRestoredBookPair.rollback();
    }

    assertFalse(Files.exists(stagedBookPath));
    assertFalse(Files.exists(stagedBookKeyPath));
  }

  @Test
  void stagedRestoredBookPair_refusesAKeyTargetThatBecomesOccupiedBeforeCommit() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("restore-race").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path restoredBookPath = writeArtifact("restore-race/restored.sqlite", "live-book");
    byte[] originalBook = Files.readAllBytes(restoredBookPath);
    Path restoredBookKeyPath = tempDirectory.resolve("restore-race").resolve("restored.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareRestoredBookPair(
                store,
                restoredBookPath,
                restoredBookKeyPath,
                RestoredBookTargetPolicy.REPLACE_SELECTED);
        StagedRestoredBookPair stagedRestoredBookPair =
            acceptedValue(
                store.stageRestoredBookPair(verifiedSourceBook, preparedPairPublication))) {
      Files.writeString(restoredBookKeyPath, "occupied-secret");
      byte[] originalKey = Files.readAllBytes(restoredBookKeyPath);

      ProtectedBookMaintenanceRejectionException rejection =
          assertThrows(
              ProtectedBookMaintenanceRejectionException.class, stagedRestoredBookPair::commit);
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.SecretTargetOccupied.class, rejection.rejection());
      assertArrayEquals(originalBook, Files.readAllBytes(restoredBookPath));
      assertArrayEquals(originalKey, Files.readAllBytes(restoredBookKeyPath));
    }
    assertNoRestoreStageArtifacts(restoredBookPath, restoredBookKeyPath);
  }

  @Test
  void stagedRestoredBookPair_preservesADestinationThatBecomesOccupiedBeforeNoReplaceCommit()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("restore-book-race").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path restoredBookPath = tempDirectory.resolve("restore-book-race").resolve("restored.sqlite");
    Path restoredBookKeyPath = tempDirectory.resolve("restore-book-race").resolve("restored.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareRestoredBookPair(
                store,
                restoredBookPath,
                restoredBookKeyPath,
                RestoredBookTargetPolicy.REQUIRE_ABSENT);
        StagedRestoredBookPair stagedRestoredBookPair =
            acceptedValue(
                store.stageRestoredBookPair(verifiedSourceBook, preparedPairPublication))) {
      Files.writeString(restoredBookPath, "unacknowledged live book");
      byte[] originalBook = Files.readAllBytes(restoredBookPath);

      ProtectedBookMaintenanceRejectionException rejection =
          assertThrows(
              ProtectedBookMaintenanceRejectionException.class, stagedRestoredBookPair::commit);
      ProtectedBookMaintenanceRejection.BookDestinationOccupied occupied =
          assertInstanceOf(
              ProtectedBookMaintenanceRejection.BookDestinationOccupied.class,
              rejection.rejection());
      assertEquals(restoredBookPath, occupied.bookFilePath());
      assertArrayEquals(originalBook, Files.readAllBytes(restoredBookPath));
      assertFalse(Files.exists(restoredBookKeyPath));
    }
    assertNoRestoreStageArtifacts(restoredBookPath, restoredBookKeyPath);
  }

  @Test
  void stagedRestoredBookPair_publishesNoReplacementDestinationWhenItRemainsAbsent()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("restore-no-replace").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path restoredBookPath = tempDirectory.resolve("restore-no-replace").resolve("restored.sqlite");
    Path restoredBookKeyPath = tempDirectory.resolve("restore-no-replace").resolve("restored.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
            prepareRestoredBookPair(
                store,
                restoredBookPath,
                restoredBookKeyPath,
                RestoredBookTargetPolicy.REQUIRE_ABSENT);
        StagedRestoredBookPair stagedRestoredBookPair =
            acceptedValue(
                store.stageRestoredBookPair(verifiedSourceBook, preparedPairPublication))) {
      assertInstanceOf(
          ProtectedBookMaintenanceStore.VerifiedBook.class,
          acceptedValue(stagedRestoredBookPair.verifyInitializedRestoredBook()));

      stagedRestoredBookPair.commit();
    }

    assertTrue(Files.exists(restoredBookPath));
    assertTrue(Files.exists(restoredBookKeyPath));
    assertInstanceOf(
        ProtectedBookMaintenanceStore.VerifiedBook.class,
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(keyedAccess(restoredBookPath, restoredBookKeyPath)),
                ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET)));
    assertNoRestoreStageArtifacts(restoredBookPath, restoredBookKeyPath);
  }

  @Test
  void recoverInterruptedPairPublication_removesOneOwnedKeyPublishedBeforeItsCompanionBook()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path finalBookPath = tempDirectory.resolve("recover-interrupted").resolve("book.sqlite");
    Path finalKeyPath = tempDirectory.resolve("recover-interrupted").resolve("book.key");
    Path stagedBookPath = writeArtifact("recover-interrupted/book.stage", "staged-book");
    Path stagedKeyPath = writeArtifact("recover-interrupted/key.stage", TEST_BOOK_KEY);
    SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);
    Files.createLink(finalKeyPath, stagedKeyPath);

    store.recoverInterruptedPairPublication(finalKeyPath, finalBookPath);

    assertFalse(Files.exists(finalKeyPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(stagedBookPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
    assertTrue(SqliteOwnedStageRecord.findFor(finalBookPath).isEmpty());
  }

  @Test
  void recoverInterruptedPairPublication_retainsOneCompletedPairAndClearsOnlyItsStages()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("recover-completed").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path finalBookPath = tempDirectory.resolve("recover-completed").resolve("restored.sqlite");
    Path finalKeyPath = tempDirectory.resolve("recover-completed").resolve("restored.key");
    Files.copy(sourceBookPath, finalBookPath);
    SqliteBookFileSecurity.hardenBookArtifacts(finalBookPath);

    Path stagedBookPath = writeArtifact("recover-completed/book.stage", "staged-book");
    Path stagedKeyPath = tempDirectory.resolve("recover-completed").resolve("key.stage");
    Files.copy(sourceKeyFilePath(sourceAccess), stagedKeyPath);
    SqliteBookArtifactSecurity.hardenOwnerOnlyFile(stagedKeyPath);
    SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);
    Files.createLink(finalKeyPath, stagedKeyPath);

    store.recoverInterruptedPairPublication(finalKeyPath, finalBookPath);

    assertTrue(Files.exists(finalBookPath));
    assertTrue(Files.exists(finalKeyPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(stagedBookPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
    assertTrue(SqliteOwnedStageRecord.findFor(finalBookPath).isEmpty());
    try (ProtectedBookMaintenanceStore.VerifiedBook ignored =
        verifiedBook(store, keyedAccess(finalBookPath, finalKeyPath))) {
      assertEquals(finalBookPath.toAbsolutePath().normalize(), ignored.artifactPath());
    }
  }

  @Test
  void recoverInterruptedPairPublication_discardsStagesWhenTheGeneratedKeyWasNeverPublished()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path finalBookPath = tempDirectory.resolve("recover-unpublished").resolve("book.sqlite");
    Path finalKeyPath = tempDirectory.resolve("recover-unpublished").resolve("book.key");
    Path stagedBookPath = writeArtifact("recover-unpublished/book.stage", "staged-book");
    Path stagedKeyPath = writeArtifact("recover-unpublished/key.stage", TEST_BOOK_KEY);
    SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);

    store.recoverInterruptedPairPublication(finalKeyPath, finalBookPath);

    assertFalse(Files.exists(finalKeyPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(stagedBookPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
    assertTrue(SqliteOwnedStageRecord.findFor(finalBookPath).isEmpty());
  }

  @Test
  void recoverInterruptedPairPublication_preservesAnUnownedGeneratedKeyAndClearsStaleStages()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path finalBookPath = tempDirectory.resolve("recover-unowned").resolve("book.sqlite");
    Path finalKeyPath = writeArtifact("recover-unowned/book.key", "unowned-key");
    Path stagedBookPath = writeArtifact("recover-unowned/book.stage", "staged-book");
    Path stagedKeyPath = writeArtifact("recover-unowned/key.stage", TEST_BOOK_KEY);
    SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);

    store.recoverInterruptedPairPublication(finalKeyPath, finalBookPath);

    assertEquals("unowned-key", Files.readString(finalKeyPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(stagedBookPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalKeyPath).isEmpty());
    assertTrue(SqliteOwnedStageRecord.findFor(finalBookPath).isEmpty());
  }

  @Test
  void recoverInterruptedPairPublication_rollsBackAKeyWhoseRegularBookDoesNotVerify()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("recover-unverified").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path finalBookPath = writeArtifact("recover-unverified/book.sqlite", "not-a-book");
    Path finalKeyPath = tempDirectory.resolve("recover-unverified").resolve("book.key");
    Path stagedBookPath = writeArtifact("recover-unverified/book.stage", "staged-book");
    Path stagedKeyPath = tempDirectory.resolve("recover-unverified").resolve("key.stage");
    Files.copy(sourceKeyFilePath(sourceAccess), stagedKeyPath);
    SqliteBookArtifactSecurity.hardenOwnerOnlyFile(stagedKeyPath);
    SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);
    Files.createLink(finalKeyPath, stagedKeyPath);

    store.recoverInterruptedPairPublication(finalKeyPath, finalBookPath);

    assertEquals("not-a-book", Files.readString(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(stagedBookPath));
  }

  @Test
  void recoverInterruptedPairPublication_rollsBackAnInsecureKeyFile() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("recover-malformed-key").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path finalBookPath = tempDirectory.resolve("recover-malformed-key").resolve("book.sqlite");
    Files.copy(sourceBookPath, finalBookPath);
    SqliteBookFileSecurity.hardenBookArtifacts(finalBookPath);
    Path finalKeyPath = tempDirectory.resolve("recover-malformed-key").resolve("book.key");
    Path stagedBookPath = writeArtifact("recover-malformed-key/book.stage", "staged-book");
    Path stagedKeyPath = writeArtifact("recover-malformed-key/key.stage", "not-a-key-file");
    SqliteBookArtifactSecurity.hardenOwnerOnlyFile(stagedKeyPath);
    Files.setPosixFilePermissions(
        stagedKeyPath,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ));
    SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);
    Files.createLink(finalKeyPath, stagedKeyPath);

    store.recoverInterruptedPairPublication(finalKeyPath, finalBookPath);

    assertTrue(Files.exists(finalBookPath));
    assertFalse(Files.exists(finalKeyPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(stagedBookPath));
  }

  @Test
  void stagedRestoredBookPair_rollbackPreservesAnUnownedKeyAndDiscardsItsOwnStage()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-unowned-key/staged.sqlite", "staged-book");
    Path stagedKeyPath = writeArtifact("restore-unowned-key/staged.key", "staged-key");
    Path finalBookPath = tempDirectory.resolve("restore-unowned-key").resolve("book.sqlite");
    Path finalKeyPath = writeArtifact("restore-unowned-key/book.key", "unowned-key");

    try (SqliteBookPassphrase restoredPassphrase =
            SqliteBookPassphrase.fromUtf8Bytes(
                "restore-unowned-key", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
        SqliteStagedRestoredBookPair stagedPair =
            newStagedRestoredBookPair(
                stagedBookPath, finalBookPath, stagedKeyPath, finalKeyPath, restoredPassphrase)) {
      setPrivateField(stagedPair, "restoredPassphrase", null);
      setPrivateField(stagedPair, "bookKeyFilePublished", true);
      stagedPair.rollback();
    }

    assertEquals("unowned-key", Files.readString(finalKeyPath));
    assertFalse(Files.exists(stagedBookPath));
    assertFalse(Files.exists(stagedKeyPath));
  }

  @Test
  void recoverInterruptedPairPublication_ignoresTargetsWithoutARecoverableOwnerRecord()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path parentlessKeyPath = Path.of("recover-parentless.key");
    Path parentlessBookPath = Path.of("recover-parentless.sqlite");
    Path absentKeyPath = tempDirectory.resolve("recover-no-record").resolve("book.key");
    Path absentBookPath = tempDirectory.resolve("recover-no-record").resolve("book.sqlite");

    store.recoverInterruptedPairPublication(parentlessKeyPath, parentlessBookPath);
    store.recoverInterruptedPairPublication(absentKeyPath, absentBookPath);

    assertFalse(Files.exists(parentlessKeyPath));
    assertFalse(Files.exists(parentlessBookPath));
    assertFalse(Files.exists(absentKeyPath));
    assertFalse(Files.exists(absentBookPath));
  }

  @Test
  void recoverInterruptedPairPublication_preservesOneDirectoryKeyTargetAndClearsStaleStages()
      throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("recover-directory-key").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path finalBookPath = tempDirectory.resolve("recover-directory-key").resolve("book.sqlite");
    Files.copy(sourceBookPath, finalBookPath);
    SqliteBookFileSecurity.hardenBookArtifacts(finalBookPath);
    Path finalKeyPath = tempDirectory.resolve("recover-directory-key").resolve("book.key");
    Files.createDirectory(finalKeyPath);
    Path stagedBookPath = writeArtifact("recover-directory-key/book.stage", "staged-book");
    Path stagedKeyPath = writeArtifact("recover-directory-key/key.stage", TEST_BOOK_KEY);
    SqliteOwnedStagedArtifact.recordExisting(finalBookPath, stagedBookPath);
    SqliteOwnedStagedArtifact.recordExisting(finalKeyPath, stagedKeyPath);

    store.recoverInterruptedPairPublication(finalKeyPath, finalBookPath);

    assertTrue(Files.isDirectory(finalKeyPath));
    assertFalse(Files.exists(stagedKeyPath));
    assertFalse(Files.exists(stagedBookPath));
  }

  private static BookAccess keyedAccess(Path bookPath, Path keyFilePath) {
    return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyFilePath));
  }

  private static Path sourceKeyFilePath(BookAccess bookAccess) {
    return switch (bookAccess.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> keyFile.bookKeyFilePath();
      case BookAccess.PassphraseSource.StandardInput _ ->
          throw new AssertionError("Expected one key-file-backed test access tuple.");
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          throw new AssertionError("Expected one key-file-backed test access tuple.");
    };
  }

  private void assertNoRestoreStageArtifacts(Path finalBookPath, Path finalBookKeyFilePath)
      throws IOException {
    Path parent =
        java.util.Objects.requireNonNullElse(
            finalBookPath.getParent(), finalBookKeyFilePath.getParent());
    if (parent == null || Files.notExists(parent)) {
      return;
    }
    assertEquals(0L, countMatchingChildren(parent, ".restore-"));
    assertEquals(0L, countMatchingChildren(parent, ".restore-key-"));
  }

  private static long countMatchingChildren(Path parentDirectory, String infix) throws IOException {
    if (Files.notExists(parentDirectory)) {
      return 0L;
    }
    try (Stream<Path> children = Files.list(parentDirectory)) {
      return children.filter(path -> path.getFileName().toString().contains(infix)).count();
    }
  }
}
