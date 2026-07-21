package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies public-safe failures emitted while SQLite constructs protected-book backup stages. */
class SqliteProtectedBookStagingFilesTest extends SqliteNativeBridgeTestSupport {

  @Test
  void backupExportFailure_preservesTheCheckpointAndExposesOnlyNativeResultNames() {
    SqliteProtectedBookStagingFiles.BackupExportFailure nativeFailure =
        new SqliteProtectedBookStagingFiles.BackupExportFailure(
            SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_COPY,
            new SqliteNativeException(
                SqliteNativeResultCode.code("BUSY"), "private native detail"));

    assertEquals(
        SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_COPY,
        nativeFailure.checkpoint());
    assertEquals(
        SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_COPY.failureMessage()
            + " SQLite reported SQLITE_BUSY.",
        nativeFailure.publicFailureMessage());

    SqliteProtectedBookStagingFiles.BackupExportFailure filesystemFailure =
        new SqliteProtectedBookStagingFiles.BackupExportFailure(
            SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_HARDEN,
            new IllegalStateException("private filesystem detail"));

    assertEquals(
        SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_HARDEN.failureMessage(),
        filesystemFailure.publicFailureMessage());
  }

  @Test
  void stagingFileHelpers_enforceRegularInputsAndSecureTheirOwnedArtifacts() throws Exception {
    Path regularBook = tempDirectory.resolve("regular-book.sqlite");
    Files.writeString(regularBook, "protected-book-fixture");
    assertDoesNotThrow(
        () -> SqliteProtectedBookStagingFiles.requireRegularNonSymlinkFile(regularBook));
    assertThrows(
        SqliteCallerPathContractException.class,
        () -> SqliteProtectedBookStagingFiles.requireRegularNonSymlinkFile(tempDirectory));

    Path backupArtifact = tempDirectory.resolve("backup-parent").resolve("backup.fgba");
    Path backupKey = tempDirectory.resolve("backup-key-parent").resolve("backup.key");
    SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(backupArtifact);
    SqliteProtectedBookStagingFiles.ensureSecureBackupKeyFileParentDirectory(backupKey);
    assertDoesNotThrow(() -> SqliteProtectedBookStagingFiles.hardenBookArtifacts(regularBook));

    Path stagedSecret = tempDirectory.resolve("staged.key");
    Files.writeString(stagedSecret, "transient secret");
    SqliteProtectedBookStagingFiles.resetStagedSecretFile(stagedSecret);
    assertFalse(Files.exists(stagedSecret));
  }

  @Test
  void stagingFileHelpers_wrapNativeAndFilesystemFailuresAtTheirPublicBoundaries()
      throws Exception {
    Path missingBook = tempDirectory.resolve("missing-source.sqlite");
    Path stagedBackup = tempDirectory.resolve("staged-backup.sqlite");
    try (SqliteBookPassphrase sourcePassphrase =
        SqliteBookPassphrase.fromCharacters(
            "missing staging source", TEST_BOOK_KEY.toCharArray())) {
      SqliteProtectedBookStagingFiles.BackupExportFailure exportFailure =
          assertThrows(
              SqliteProtectedBookStagingFiles.BackupExportFailure.class,
              () ->
                  SqliteProtectedBookStagingFiles.exportBackupUsingSqlite(
                      missingBook, stagedBackup, sourcePassphrase));
      assertEquals(
          SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_SOURCE_OPEN,
          exportFailure.checkpoint());
    }

    Path nonDirectoryParent = tempDirectory.resolve("not-a-directory");
    Files.writeString(nonDirectoryParent, "regular file");
    Path impossibleChild = nonDirectoryParent.resolve("child.sqlite");
    assertThrows(
        SqliteCallerPathContractException.class,
        () ->
            SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(impossibleChild));
    assertThrows(
        SqliteCallerPathContractException.class,
        () ->
            SqliteProtectedBookStagingFiles.ensureSecureBackupKeyFileParentDirectory(
                impossibleChild));
    assertThrows(
        SqliteCallerPathContractException.class,
        () -> SqliteProtectedBookStagingFiles.hardenBookArtifacts(impossibleChild));
  }

  @Test
  void stagingFileHelpers_wrapAclMetadataIoFailuresAtTheirPublicBoundaries() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath backupArtifact = fileSystem.path("\\backup\\book.fgba");
      AclFixturePath backupParent =
          assertInstanceOf(AclFixturePath.class, backupArtifact.getParent());
      backupParent.overrideAclView = failingAclView();
      IllegalStateException backupFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(
                      backupArtifact));
      assertEquals(
          "Failed to secure the parent directory for \\backup\\book.fgba.",
          backupFailure.getMessage());
      assertInstanceOf(IOException.class, backupFailure.getCause());

      AclFixturePath backupKey = fileSystem.path("\\backup-key\\book.key");
      AclFixturePath backupKeyParent =
          assertInstanceOf(AclFixturePath.class, backupKey.getParent());
      backupKeyParent.overrideAclView = failingAclView();
      IllegalStateException backupKeyFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookStagingFiles.ensureSecureBackupKeyFileParentDirectory(
                      backupKey));
      assertEquals(
          "Failed to secure the parent directory for \\backup-key\\book.key.",
          backupKeyFailure.getMessage());
      assertInstanceOf(IOException.class, backupKeyFailure.getCause());

      AclFixturePath stagedBook = fileSystem.path("\\staged\\book.sqlite");
      SqliteBookFileSecurity.ensureSecureParentDirectory(stagedBook);
      stagedBook.exists = true;
      stagedBook.regularFile = true;
      stagedBook.overrideAclView = failingAclView();
      IllegalStateException hardeningFailure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteProtectedBookStagingFiles.hardenBookArtifacts(stagedBook));
      assertEquals(
          "Failed to harden the FinGrind protected-book artifacts for \\staged\\book.sqlite.",
          hardeningFailure.getMessage());
      assertInstanceOf(IOException.class, hardeningFailure.getCause());
    }
  }

  private static AclFileAttributeView failingAclView() {
    return new AclFileAttributeView() {
      @Override
      public String name() {
        return "acl";
      }

      @Override
      public List<AclEntry> getAcl() throws IOException {
        throw new IOException("simulated ACL metadata failure");
      }

      @Override
      public void setAcl(List<AclEntry> acl) throws IOException {
        throw new IOException("simulated ACL metadata failure");
      }

      @Override
      public UserPrincipal getOwner() throws IOException {
        throw new IOException("simulated ACL metadata failure");
      }

      @Override
      public void setOwner(UserPrincipal owner) throws IOException {
        throw new IOException("simulated ACL metadata failure");
      }
    };
  }
}
