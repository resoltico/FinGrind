package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Focused coverage for SQLite restore staging, staged restore publication, and cleanup paths. */
class SqliteProtectedBookRestoreStagingCoverageTest
    extends SqliteProtectedBookMaintenanceStoreCoverageTestSupport {

  @Test
  void stageRestoredBookPair_translatesManagedTargetPathRejections() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("restore-translation").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path parentBlocker = tempDirectory.resolve("restore-translation").resolve("parent-blocker");
    Files.writeString(parentBlocker, "not-a-directory");
    Path invalidRestoredBookPath = parentBlocker.resolve("restored.sqlite");
    Path restoredBookKeyPath = tempDirectory.resolve("restore-translation").resolve("restored.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
        verifiedBook(store, sourceAccess)) {
      ProtectedBookMaintenanceRejection.ArtifactPathInvalid rejection =
          assertInstanceOf(
              ProtectedBookMaintenanceRejection.ArtifactPathInvalid.class,
              assertThrows(
                      ProtectedBookMaintenanceRejectionException.class,
                      () ->
                          store.stageRestoredBookPair(
                              verifiedSourceBook, invalidRestoredBookPath, restoredBookKeyPath))
                  .rejection());

      assertEquals(ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, rejection.artifactRole());
      assertEquals(
          ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION, rejection.pathFailure());
    }
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
      IllegalStateException ioFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookStagingSupport.stageResolvedRestoredBookPair(
                      missingSourceBookPath,
                      ioTargetBookPath,
                      ioTargetKeyPath,
                      sourcePassphrase,
                      VERIFICATION_SUPPORT));

      assertTrue(
          NullTestSupport.messageOf(ioFailure)
              .contains("Failed to stage the restored FinGrind live-book pair"));
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
      assertThrows(
          RuntimeException.class,
          () ->
              SqliteProtectedBookStagingSupport.stageResolvedRestoredBookPair(
                  bogusSourceBookPath,
                  runtimeTargetBookPath,
                  runtimeTargetKeyPath,
                  sourcePassphrase,
                  VERIFICATION_SUPPORT));
    }
    assertNoRestoreStageArtifacts(runtimeTargetBookPath, runtimeTargetKeyPath);
  }

  @Test
  void stagedRestoredBookPair_verifiesPublishesAndReencryptsOverExistingTargets() throws Exception {
    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("restore-success").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    Path restoredBookPath = writeArtifact("restore-success/restored.sqlite", "legacy-book");
    Path restoredBookKeyPath = tempDirectory.resolve("restore-success").resolve("restored.key");
    writeSecureKeyFile(restoredBookKeyPath, "legacy-secret");
    Path restoredParent =
        java.util.Objects.requireNonNull(restoredBookPath.getParent(), "restoredBookPath parent");

    try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
            verifiedBook(store, sourceAccess);
        StagedRestoredBookPair stagedRestoredBookPair =
            acceptedValue(
                store.stageRestoredBookPair(
                    verifiedSourceBook, restoredBookPath, restoredBookKeyPath))) {
      assertTrue(countMatchingChildren(restoredParent, ".previous-") > 0L);
      assertTrue(countMatchingChildren(restoredParent, ".previous-key-") > 0L);
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
    assertEquals(0L, countMatchingChildren(restoredParent, ".previous-key-"));
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
        StagedRestoredBookPair stagedRestoredBookPair =
            acceptedValue(
                store.stageRestoredBookPair(
                    verifiedSourceBook, restoredBookPath, restoredBookKeyPath))) {
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
    Path previousBookPath =
        writeArtifact("restore-null-passphrase/previous.sqlite", "previous-book");
    Path previousBookKeyPath =
        writeArtifact("restore-null-passphrase/previous.key", "previous-key");
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
                previousBookPath,
                previousBookKeyPath,
                restoredPassphrase)) {
      setPrivateField(stagedRestoredBookPair, "restoredPassphrase", null);
      stagedRestoredBookPair.rollback();
    }

    assertFalse(Files.exists(stagedBookPath));
    assertFalse(Files.exists(stagedBookKeyPath));
    assertFalse(Files.exists(previousBookPath));
    assertFalse(Files.exists(previousBookKeyPath));
  }

  @Test
  void stagedRestoredBookPair_createFailureClosesPassphraseAndDeletesPreviousArtifacts()
      throws Exception {
    Path stagedBookPath = writeArtifact("restore-create-failure/staged.sqlite", "staged-book");
    Path finalBookPath = writeArtifact("restore-create-failure/final.sqlite", "final-book");
    Path stagedBookKeyPath = writeArtifact("restore-create-failure/staged.key", "staged-key");
    Path finalBookKeyDirectory =
        tempDirectory.resolve("restore-create-failure").resolve("final-key-as-directory");
    Files.createDirectories(finalBookKeyDirectory);
    try (SqliteBookPassphrase restoredPassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "create failure passphrase", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8))) {
      assertThrows(
          SqliteCallerPathContractException.class,
          () ->
              SqliteStagedRestoredBookPair.create(
                  stagedBookPath,
                  finalBookPath,
                  stagedBookKeyPath,
                  finalBookKeyDirectory,
                  restoredPassphrase,
                  VERIFICATION_SUPPORT));
      assertArrayEquals(
          new byte[restoredPassphrase.byteLength()], restoredPassphrase.utf8BytesCopy());
      assertEquals(
          0L,
          countMatchingChildren(
              java.util.Objects.requireNonNull(finalBookPath.getParent(), "finalBookPath parent"),
              ".previous-"));
    }
  }

  @Test
  void stagedRestoredBookPair_commitFailureRestoresOrDeletesTargetsBestEffort() {
    try (AclFixtureFileSystem fileSystem =
        AclFixtureFileSystem.withViews(java.util.Set.of("basic"))) {
      AclFixturePath stagedBookPath =
          existingRegularFixture(fileSystem, "\\restore\\staged.sqlite");
      AclFixturePath finalBookPath = existingRegularFixture(fileSystem, "\\restore\\final.sqlite");
      AclFixturePath stagedBookKeyPath =
          existingRegularFixture(fileSystem, "\\restore\\staged.key");
      stagedBookKeyPath.failMoveWith(new IOException("publish-key-boom"));
      AclFixturePath finalBookKeyPath = existingRegularFixture(fileSystem, "\\restore\\final.key");
      AclFixturePath previousBookPath = fileSystem.path("\\restore\\previous.sqlite");
      previousBookPath.exists = false;
      previousBookPath.regularFile = true;
      AclFixturePath previousBookKeyPath = fileSystem.path("\\restore\\previous.key");
      previousBookKeyPath.exists = false;
      previousBookKeyPath.regularFile = true;
      previousBookKeyPath.failMoveWith(new IOException("restore-key-boom"));
      try (SqliteBookPassphrase restoredPassphrase =
              SqliteBookPassphrase.fromUtf8Bytes(
                  "fixture restore passphrase", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
          SqliteStagedRestoredBookPair stagedRestoredBookPair =
              newStagedRestoredBookPair(
                  stagedBookPath,
                  finalBookPath,
                  stagedBookKeyPath,
                  finalBookKeyPath,
                  previousBookPath,
                  previousBookKeyPath,
                  restoredPassphrase)) {
        IllegalStateException failure =
            assertThrows(IllegalStateException.class, stagedRestoredBookPair::commit);
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to publish the restored FinGrind live-book pair"));
        assertTrue(finalBookPath.exists);
        assertTrue(previousBookKeyPath.exists);
        assertFalse(finalBookKeyPath.exists);
      }
    }

    try (AclFixtureFileSystem fileSystem =
        AclFixtureFileSystem.withViews(java.util.Set.of("basic"))) {
      AclFixturePath stagedBookPath = existingRegularFixture(fileSystem, "\\delete\\staged.sqlite");
      AclFixturePath finalBookPath = fileSystem.path("\\delete\\final.sqlite");
      finalBookPath.exists = false;
      finalBookPath.regularFile = true;
      AclFixturePath stagedBookKeyPath = existingRegularFixture(fileSystem, "\\delete\\staged.key");
      stagedBookKeyPath.failMoveWith(new IOException("publish-key-boom"));
      AclFixturePath finalBookKeyPath = fileSystem.path("\\delete\\final.key");
      finalBookKeyPath.exists = false;
      finalBookKeyPath.regularFile = true;
      AclFixturePath previousBookPath = fileSystem.path("\\delete\\previous.sqlite");
      previousBookPath.exists = false;
      previousBookPath.regularFile = true;
      AclFixturePath previousBookKeyPath = fileSystem.path("\\delete\\previous.key");
      previousBookKeyPath.exists = false;
      previousBookKeyPath.regularFile = true;
      try (SqliteBookPassphrase restoredPassphrase =
              SqliteBookPassphrase.fromUtf8Bytes(
                  "delete restore passphrase", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
          SqliteStagedRestoredBookPair stagedRestoredBookPair =
              newStagedRestoredBookPair(
                  stagedBookPath,
                  finalBookPath,
                  stagedBookKeyPath,
                  finalBookKeyPath,
                  previousBookPath,
                  previousBookKeyPath,
                  restoredPassphrase)) {
        assertThrows(IllegalStateException.class, stagedRestoredBookPair::commit);
        assertFalse(finalBookPath.exists);
        assertFalse(finalBookKeyPath.exists);
      }
    }

    try (AclFixtureFileSystem fileSystem =
        AclFixtureFileSystem.withViews(java.util.Set.of("basic"))) {
      AclFixturePath stagedBookPath =
          existingRegularFixture(fileSystem, "\\delete-null\\staged.sqlite");
      AclFixturePath finalBookPath = fileSystem.path("\\delete-null\\final.sqlite");
      finalBookPath.exists = false;
      finalBookPath.regularFile = true;
      AclFixturePath stagedBookKeyPath =
          existingRegularFixture(fileSystem, "\\delete-null\\staged.key");
      stagedBookKeyPath.failMoveWith(new IOException("publish-key-boom"));
      AclFixturePath finalBookKeyPath = fileSystem.path("\\delete-null\\final.key");
      finalBookKeyPath.exists = false;
      finalBookKeyPath.regularFile = true;
      try (SqliteBookPassphrase restoredPassphrase =
              SqliteBookPassphrase.fromUtf8Bytes(
                  "null previous restore passphrase",
                  TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
          SqliteStagedRestoredBookPair stagedRestoredBookPair =
              newStagedRestoredBookPair(
                  stagedBookPath,
                  finalBookPath,
                  stagedBookKeyPath,
                  finalBookKeyPath,
                  null,
                  null,
                  restoredPassphrase)) {
        assertThrows(IllegalStateException.class, stagedRestoredBookPair::commit);
        assertFalse(finalBookPath.exists);
        assertFalse(finalBookKeyPath.exists);
      }
    }
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

  private static AclFixturePath existingRegularFixture(
      AclFixtureFileSystem fileSystem, String pathText) {
    AclFixturePath path = fileSystem.path(pathText);
    path.exists = true;
    path.regularFile = true;
    return path;
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
