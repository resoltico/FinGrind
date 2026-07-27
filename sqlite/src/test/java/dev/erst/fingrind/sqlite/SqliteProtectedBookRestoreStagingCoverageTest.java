package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Focused coverage for SQLite restore staging, publication, and retained-stage evidence. */
class SqliteProtectedBookRestoreStagingCoverageTest extends SqliteArtifactPublicationTestSupport {

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
                        admitRestoredBookPair(
                            store,
                            invalidRestoredBookPath,
                            restoredBookKeyPath,
                            RestoredBookTargetPolicy.REPLACE_SELECTED))
                .rejection());

    assertEquals(ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, rejection.artifactRole());
    assertEquals(
        ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION, rejection.pathFailure());
  }

  @Test
  void stageResolvedRestoredBookPairRetainsArtifactsAfterRekeyFailure() throws Exception {
    Path missingSourceBookPath =
        tempDirectory.resolve("restore-stage-io").resolve("missing-source.sqlite");
    Path ioTargetBookPath = tempDirectory.resolve("restore-stage-io").resolve("restored.sqlite");
    Path ioTargetKeyPath = tempDirectory.resolve("restore-stage-io").resolve("restored.key");
    Path ioTargetParent =
        java.util.Objects.requireNonNull(ioTargetBookPath.getParent(), "ioTargetBookPath parent");
    Files.createDirectories(ioTargetParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(ioTargetParent);

    try (SqliteBookPassphrase sourcePassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "missing restore source", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8))) {
      MaintenanceFailure ioFailure =
          failedValue(
              SqliteProtectedBookRestoreStaging.stageResolvedPair(
                  missingSourceBookPath,
                  ioTargetBookPath,
                  ioTargetKeyPath,
                  RestoredBookTargetPolicy.REPLACE_SELECTED,
                  sourcePassphrase,
                  VERIFICATION_SUPPORT,
                  checkpoint -> {},
                  SqliteBookKeyFileGenerator::generateIntoExistingOwnedStage));

      assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, ioFailure.descriptor());
      assertEquals(
          SqliteProtectedBookStagingCheckpoint.RESTORE_COPY.failureMessage(), ioFailure.message());
      assertEquals("bookFilePath", ioFailure.argument());
      assertEquals(
          ioTargetBookPath.toAbsolutePath().normalize(),
          java.util.Objects.requireNonNull(ioFailure.paths(), "failure paths").path());
    }
    assertNoRestoreStageArtifactsCreated(ioTargetBookPath, ioTargetKeyPath);

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
              SqliteProtectedBookRestoreStaging.stageResolvedPair(
                  bogusSourceBookPath,
                  runtimeTargetBookPath,
                  runtimeTargetKeyPath,
                  RestoredBookTargetPolicy.REPLACE_SELECTED,
                  sourcePassphrase,
                  VERIFICATION_SUPPORT,
                  checkpoint -> {},
                  SqliteBookKeyFileGenerator::generateIntoExistingOwnedStage));
      assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, runtimeFailure.descriptor());
      assertEquals(
          SqliteProtectedBookStagingCheckpoint.RESTORE_REKEY.failureMessage(),
          runtimeFailure.message());
      assertEquals("bookFilePath", runtimeFailure.argument());
      assertEquals(
          runtimeTargetBookPath.toAbsolutePath().normalize(),
          java.util.Objects.requireNonNull(runtimeFailure.paths(), "failure paths").path());
    }
    assertRestoreStageArtifactsRetained(runtimeTargetBookPath, runtimeTargetKeyPath);
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
              SqliteProtectedBookBackupStaging.stageResolvedPair(
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
              SqliteProtectedBookRestoreStaging.stageResolvedPair(
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
  void closedPreparedPublicationIsRejectedBeforeRestoreStagingBegins() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("closed-prepared").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path restoredBookPath = tempDirectory.resolve("closed-prepared").resolve("restored.sqlite");
    Path restoredKeyPath = tempDirectory.resolve("closed-prepared").resolve("restored.key");
    try (ProtectedBookMaintenanceStore.PreparedPairPublication preparedPublication =
            prepareRestoredBookPair(
                store, restoredBookPath, restoredKeyPath, RestoredBookTargetPolicy.REQUIRE_ABSENT);
        SqliteBookPassphrase sourcePassphrase =
            SqliteBookKeyFile.load(sourceKeyFilePath(sourceAccess))) {
      preparedPublication.close();
      MaintenanceFailure failure =
          failedValue(
              SqliteProtectedBookRestoreStaging.stageResolvedPair(
                  sourceBookPath,
                  fixturePreparedPublication(preparedPublication),
                  sourcePassphrase,
                  VERIFICATION_SUPPORT));
      assertEquals(
          SqliteProtectedBookStagingCheckpoint.RESTORE_COPY.failureMessage(), failure.message());
    }

    assertFalse(Files.exists(restoredBookPath));
    assertFalse(Files.exists(restoredKeyPath));
    assertNoRestoreStageArtifactsCreated(restoredBookPath, restoredKeyPath);
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
                stageRestoredBookPairForFixture(verifiedSourceBook, preparedPairPublication))) {
      assertEquals(0L, countMatchingChildren(restoredParent, ".previous-"));
      assertInstanceOf(
          ProtectedBookMaintenanceStore.VerifiedBook.class,
          acceptedValue(stagedRestoredBookPair.verifyInitializedRestoredBook()));

      StagedPairPublicationCommitOutcome firstCommit =
          stagedRestoredBookPair.commit(
              restoreBinding(sourceBookPath, sourceKeyFilePath(sourceAccess)));
      StagedPairPublicationCommitOutcome secondCommit =
          stagedRestoredBookPair.commit(
              restoreBinding(sourceBookPath, sourceKeyFilePath(sourceAccess)));
      assertInstanceOf(StagedPairPublicationCommitOutcome.Published.class, firstCommit);
      assertSame(firstCommit, secondCommit);
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
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)));
    assertVerificationFailure(
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(keyedAccess(restoredBookPath, sourceKeyFilePath(sourceAccess))),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)),
        restoredBookPath,
        ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED);
  }

  @Test
  void stagedRestoredBookPair_closeRetainsArtifactsAndRetentionRemainsIdempotent()
      throws Exception {
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
                stageRestoredBookPairForFixture(verifiedSourceBook, preparedPairPublication))) {
      assertTrue(countMatchingChildren(restoredParent, ".restore-") > 0L);
      assertTrue(countMatchingChildren(restoredParent, ".restore-key-") > 0L);
      stagedRestoredBookPair.close();
      stagedRestoredBookPair.retainUnpublishedArtifacts();
      stagedRestoredBookPair.close();
    }

    assertFalse(Files.exists(restoredBookPath));
    assertFalse(Files.exists(restoredBookKeyPath));
    assertTrue(countMatchingChildren(restoredParent, ".restore-") > 0L);
    assertTrue(countMatchingChildren(restoredParent, ".restore-key-") > 0L);
  }

  @Test
  void stagedRestoredBookPair_retentionHandlesAlreadyClearedPassphrases() throws Exception {
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
      stagedRestoredBookPair.retainUnpublishedArtifacts();
    }

    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedBookKeyPath));
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
                stageRestoredBookPairForFixture(verifiedSourceBook, preparedPairPublication))) {
      Files.writeString(restoredBookKeyPath, "occupied-secret");
      byte[] originalKey = Files.readAllBytes(restoredBookKeyPath);

      ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired recoveryRequired =
          assertInstanceOf(
              ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired.class,
              stagedRestoredBookPair.commit(
                  restoreBinding(sourceBookPath, sourceKeyFilePath(sourceAccess))));
      assertEquals(restoredBookPath, recoveryRequired.bookArtifactPath());
      assertEquals(restoredBookKeyPath, recoveryRequired.secretArtifactPath());
      assertArrayEquals(originalBook, Files.readAllBytes(restoredBookPath));
      assertArrayEquals(originalKey, Files.readAllBytes(restoredBookKeyPath));
    }
    assertFalse(SqliteOwnedStageRecord.findFor(restoredBookPath).isEmpty());
    assertFalse(SqliteOwnedStageRecord.findFor(restoredBookKeyPath).isEmpty());
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
                stageRestoredBookPairForFixture(verifiedSourceBook, preparedPairPublication))) {
      Files.writeString(restoredBookPath, "unacknowledged live book");
      byte[] originalBook = Files.readAllBytes(restoredBookPath);

      ProtectedBookPairPublicationFailureOutcome.CompletionUncertain uncertain =
          assertInstanceOf(
              ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
              stagedRestoredBookPair.commit(
                  restoreBinding(sourceBookPath, sourceKeyFilePath(sourceAccess))));
      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState
              .NOT_ATTEMPTED,
          uncertain.bookArtifactState());
      assertEquals(
          dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState
              .PUBLISHED_DURABLE,
          uncertain.secretArtifactState());
      assertArrayEquals(originalBook, Files.readAllBytes(restoredBookPath));
      assertTrue(Files.exists(restoredBookKeyPath));
    }
    assertFalse(SqliteOwnedStageRecord.findFor(restoredBookPath).isEmpty());
    assertFalse(SqliteOwnedStageRecord.findFor(restoredBookKeyPath).isEmpty());
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
                stageRestoredBookPairForFixture(verifiedSourceBook, preparedPairPublication))) {
      assertInstanceOf(
          ProtectedBookMaintenanceStore.VerifiedBook.class,
          acceptedValue(stagedRestoredBookPair.verifyInitializedRestoredBook()));

      assertInstanceOf(
          StagedPairPublicationCommitOutcome.Published.class,
          stagedRestoredBookPair.commit(
              restoreBinding(sourceBookPath, sourceKeyFilePath(sourceAccess))));
    }

    assertTrue(Files.exists(restoredBookPath));
    assertTrue(Files.exists(restoredBookKeyPath));
    assertInstanceOf(
        ProtectedBookMaintenanceStore.VerifiedBook.class,
        acceptedValue(
            store.verifyInitializedBook(
                localAccess(keyedAccess(restoredBookPath, restoredBookKeyPath)),
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)));
    assertRestoreStageArtifactsRetained(restoredBookPath, restoredBookKeyPath);
  }

  @Test
  void stagedRestoredBookPair_retentionPreservesAnUnownedKeyAndItsOwnStage() throws Exception {
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
      stagedPair.retainUnpublishedArtifacts();
    }

    assertEquals("unowned-key", Files.readString(finalKeyPath));
    assertTrue(Files.exists(stagedBookPath));
    assertTrue(Files.exists(stagedKeyPath));
  }

  private static BookAccess keyedAccess(Path bookPath, Path keyFilePath) {
    return new BookAccess(
        bookPath, new BookAccess.PassphraseSource.KeyFile(keyFilePath), java.util.List.of());
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

  private void assertNoRestoreStageArtifactsCreated(Path finalBookPath, Path finalBookKeyFilePath)
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

  private void assertRestoreStageArtifactsRetained(Path finalBookPath, Path finalBookKeyFilePath)
      throws IOException {
    Path parent =
        java.util.Objects.requireNonNullElse(
            finalBookPath.getParent(), finalBookKeyFilePath.getParent());
    if (parent == null) {
      throw new AssertionError("Restore targets must name one parent directory.");
    }
    assertTrue(countMatchingChildren(parent, ".restore-") > 0L);
    assertTrue(countMatchingChildren(parent, ".restore-key-") > 0L);
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
