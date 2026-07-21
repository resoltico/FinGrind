package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Proves native backup staging needs only read access to its protected-book source. */
class SqliteProtectedBookReadOnlyBackupSourceTest extends SqliteArtifactPublicationTestSupport {

  @Test
  void stageBackupPair_readsSourceBookWithoutWriteAccess() throws Exception {
    assumeTrue(tempDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"));

    SqliteProtectedBookMaintenanceStore store = maintenanceStore();
    Path sourceBookPath = tempDirectory.resolve("source").resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    byte[] sourceBefore = Files.readAllBytes(sourceBookPath);
    Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(sourceBookPath);

    try {
      Files.setPosixFilePermissions(sourceBookPath, Set.of(PosixFilePermission.OWNER_READ));
      Path backupFilePath = tempDirectory.resolve("backup").resolve("source.sqlite");
      Path backupKeyFilePath = tempDirectory.resolve("backup").resolve("source.key");
      try (ProtectedBookMaintenanceStore.VerifiedBook verifiedSourceBook =
              verifiedBook(store, sourceAccess);
          ProtectedBookMaintenanceStore.PreparedPairPublication preparedPairPublication =
              prepareBackupPair(store, backupFilePath, backupKeyFilePath);
          StagedBackupPair stagedBackupPair =
              acceptedValue(store.stageBackupPair(verifiedSourceBook, preparedPairPublication))) {
        assertInstanceOf(
            ProtectedBookMaintenanceStore.VerifiedBook.class,
            acceptedValue(stagedBackupPair.verifyInitializedBackup()));
        stagedBackupPair.commit();
      }
    } finally {
      Files.setPosixFilePermissions(sourceBookPath, originalPermissions);
    }

    assertArrayEquals(sourceBefore, Files.readAllBytes(sourceBookPath));
  }
}
