package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Exercises journal-stage failure boundaries that must retain journal recovery authority. */
class SqliteJournaledStagingBoundaryTest extends SqliteArtifactPublicationTestSupport {
  @Test
  void restoreCopyRejectsZeroProgressFromItsSourceAndDestination() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = privateParent(fileSystem, "\\restore");
      AclFixturePath zeroRead = regularFile(fileSystem, "\\restore\\zero-read.sqlite");
      zeroRead.replaceContent("source bytes".getBytes(StandardCharsets.UTF_8));
      zeroRead.returnZeroProgressFromNextRead();
      AclFixturePath readStage = regularFile(fileSystem, "\\restore\\read-stage.sqlite");
      IOException readFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqliteProtectedBookRestoreStaging.copySourceIntoExistingOwnedStage(
                      zeroRead, readStage));
      assertEquals(
          "Failed to read the complete protected-book restore source.", readFailure.getMessage());

      AclFixturePath source = regularFile(fileSystem, "\\restore\\source.sqlite");
      source.replaceContent("source bytes".getBytes(StandardCharsets.UTF_8));
      AclFixturePath zeroWrite = regularFile(fileSystem, "\\restore\\zero-write.sqlite");
      zeroWrite.returnZeroProgressFromNextWrite();
      IOException writeFailure =
          assertThrows(
              IOException.class,
              () ->
                  SqliteProtectedBookRestoreStaging.copySourceIntoExistingOwnedStage(
                      source, zeroWrite));
      assertEquals(
          "Failed to write the complete protected-book restore stage.", writeFailure.getMessage());
      assertEquals(parent, source.getParent());
    }
  }

  @Test
  void backupSealingRejectsAStageThatCannotMakeWriteProgress() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      privateParent(fileSystem, "\\backup");
      AclFixturePath stage = regularFile(fileSystem, "\\backup\\zero-write.sqlite");
      stage.returnZeroProgressFromNextWrite();

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteJournaledStagedBackupPair.writeExactly(stage, new byte[] {1, 2, 3}));

      assertEquals("Failed to seal the journal-owned backup artifact.", failure.getMessage());
      assertEquals(
          "Failed to write the complete journal-owned backup artifact.",
          java.util.Objects.requireNonNull(failure.getCause()).getMessage());
    }
  }

  @Test
  void restoreStagingRecordsTheJournalOutcomeWhenItsSourceCannotBeRead() throws Exception {
    Path targetBook = privateTarget("restore-source-read-failure", "restored.sqlite");
    Path targetSecret = targetBook.resolveSibling("restored.key");
    Path missingSource = tempDirectory.resolve("missing-restore-source.sqlite");

    try (ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareRestoredBookPair(
                maintenanceStore(),
                targetBook,
                targetSecret,
                ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REQUIRE_ABSENT);
        SqliteBookPassphrase sourcePassphrase =
            SqliteBookPassphrase.fromUtf8Bytes(
                "restore source failure", "source passphrase".getBytes(StandardCharsets.UTF_8))) {
      assertThrows(
          ContractFailureException.class,
          () ->
              acceptedValue(
                  SqliteProtectedBookRestoreStaging.stageResolvedPair(
                      missingSource,
                      fixturePreparedPublication(prepared),
                      sourcePassphrase,
                      VERIFICATION_SUPPORT)));
    }
  }

  @Test
  void backupSealingRejectsMismatchedAndRepeatedArtifacts() throws Exception {
    SourceBook source = initializedSourceBook("backup-seal-validation-source");
    Path targetBook = privateTarget("backup-seal-validation-target", "backup.sqlite");
    Path targetSecret = targetBook.resolveSibling("backup.key");

    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(maintenanceStore(), source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareBackupPair(maintenanceStore(), targetBook, targetSecret);
        StagedBackupPair staged = acceptedValue(stageBackupPairForFixture(verified, prepared))) {
      byte[] snapshot = staged.snapshot();
      byte[] mismatched = Arrays.copyOf(snapshot, snapshot.length + 1);
      mismatched[0] ^= 1;
      byte[] sealedArtifact = Arrays.copyOf(snapshot, snapshot.length + 1);
      sealedArtifact[sealedArtifact.length - 1] = 1;
      try {
        assertThrows(IllegalArgumentException.class, () -> staged.sealArtifact(mismatched));
        staged.sealArtifact(sealedArtifact);
        assertThrows(IllegalStateException.class, () -> staged.sealArtifact(sealedArtifact));
      } finally {
        Arrays.fill(snapshot, (byte) 0);
        Arrays.fill(mismatched, (byte) 0);
        Arrays.fill(sealedArtifact, (byte) 0);
      }
    }
  }

  @Test
  void closedStagesRejectFurtherReadsOrVerificationWithoutLocalCleanup() throws Exception {
    SourceBook source = initializedSourceBook("closed-stage-source");
    Path targetBook = privateTarget("closed-stage-target", "backup.sqlite");
    Path targetSecret = targetBook.resolveSibling("backup.key");
    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(maintenanceStore(), source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareBackupPair(maintenanceStore(), targetBook, targetSecret);
        StagedBackupPair staged = acceptedValue(stageBackupPairForFixture(verified, prepared))) {
      Files.delete(fixturePreparedPublication(prepared).journaledPair().bookStagePath());
      assertThrows(SqliteCallerPathContractException.class, staged::snapshot);
    }

    Path restoreBook = privateTarget("closed-restore-stage-target", "restored.sqlite");
    Path restoreSecret = restoreBook.resolveSibling("restored.key");
    try (ProtectedBookMaintenanceStore.VerifiedBook verified =
            verifiedBook(maintenanceStore(), source.access());
        ProtectedBookMaintenanceStore.PreparedPairPublication prepared =
            prepareRestoredBookPair(
                maintenanceStore(),
                restoreBook,
                restoreSecret,
                ProtectedBookMaintenanceStore.RestoredBookTargetPolicy.REQUIRE_ABSENT);
        StagedRestoredBookPair staged =
            acceptedValue(stageRestoredBookPairForFixture(verified, prepared))) {
      staged.retainUnpublishedArtifacts();
      assertThrows(IllegalStateException.class, staged::verifyInitializedRestoredBook);
    }
  }

  private SourceBook initializedSourceBook(String name) {
    Path book = tempDirectory.resolve(name).resolve("source.sqlite");
    BookAccess access = bookAccess(book);
    initializeBook(access);
    return new SourceBook(access);
  }

  private Path privateTarget(String directory, String fileName) throws IOException {
    Path target = tempDirectory.resolve(directory).resolve(fileName);
    Path parent = java.util.Objects.requireNonNull(target.getParent(), "target parent");
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    return target;
  }

  private static AclFixturePath privateParent(AclFixtureFileSystem fileSystem, String path) {
    AclFixturePath parent = fileSystem.path(path);
    parent.exists = true;
    parent.regularFile = false;
    parent.posixPermissions =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    return parent;
  }

  private static AclFixturePath regularFile(AclFixtureFileSystem fileSystem, String path) {
    AclFixturePath file = fileSystem.path(path);
    file.exists = true;
    file.regularFile = true;
    file.posixPermissions = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    return file;
  }

  private record SourceBook(BookAccess access) {}
}
