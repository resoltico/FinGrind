package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.access;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.hint;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.path;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.service;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.touch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenancePathFailure;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contract tests for deterministic maintenance path refusals across workflow seams. */
class ProtectedBookMaintenancePathContractPropagationTest {
  @TempDir Path tempDirectory;

  @Test
  void backupBook_rejectsManagedLiveBookLeasePathViolationsDeterministically() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.managedLeaseRejection =
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            store.normalized(book),
            ProtectedBookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED);

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    assertArtifactPathInvalid(
        rejected.rejection(),
        BookMaintenanceArtifactRole.LIVE_BOOK,
        hint(book),
        BookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED);
  }

  @Test
  void restoreBook_rejectsBackupSourcePathViolationsRaisedDuringVerification() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.verifyInitializedBookRejection =
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
            store.normalized(backup),
            ProtectedBookMaintenancePathFailure.MISSING_PARENT_DIRECTORY);

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey).requireAccepted());

    assertArtifactPathInvalid(
        rejected.rejection(),
        BookMaintenanceArtifactRole.BACKUP_SOURCE,
        hint(backup),
        BookMaintenancePathFailure.MISSING_PARENT_DIRECTORY);
  }

  @Test
  void restoreBook_rejectsRestoredTargetPathViolationsRaisedDuringReplacement() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.stageReplacementRejection =
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
            store.normalized(book),
            ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION);

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey).requireAccepted());

    assertArtifactPathInvalid(
        rejected.rejection(),
        BookMaintenanceArtifactRole.RESTORED_TARGET,
        hint(book),
        BookMaintenancePathFailure.PARENT_PATH_COLLISION);
  }

  @Test
  void restoreRekeyRollback_rejectsRestoredTargetPathViolationsRaisedDuringReplacement() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(
        store.normalized(book), java.util.List.of(store.normalized(rollback)));
    store.stageReplacementRejection =
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET,
            store.normalized(book),
            ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION);

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book,
                    rollback,
                    new BookAccess.PassphraseSource.KeyFile(path(tempDirectory, "book.key")))
                .requireAccepted());

    assertArtifactPathInvalid(
        rejected.rejection(),
        BookMaintenanceArtifactRole.RESTORED_TARGET,
        hint(book),
        BookMaintenancePathFailure.PARENT_PATH_COLLISION);
  }

  @Test
  void deleteRekeyRollback_rejectsRollbackArtifactPathViolationsRaisedDuringDeletionStaging() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(book);
    touch(rollback);
    store.rollbackArtifacts.put(
        store.normalized(book), java.util.List.of(store.normalized(rollback)));
    store.stageRollbackDeletionRejection =
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
            store.normalized(rollback),
            ProtectedBookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE);

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), rollback).requireAccepted());

    assertArtifactPathInvalid(
        rejected.rejection(),
        BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
        hint(rollback),
        BookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE);
    assertTrue(store.recordedAudits.isEmpty());
  }

  @Test
  void restoreBook_failsFastWhenTheStoreThrowsANonPathMaintenanceRejection() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.verifyInitializedBookRejection =
        new ProtectedBookMaintenanceRejection.ArtifactBusy(
            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, store.normalized(backup));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> service(store).restoreBook(book, bookKey, backup, backupKey));

    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage())
            .contains("Expected one maintenance artifact-path rejection"));
  }

  @Test
  void restoreBook_failsFastWhenReplacementStagingThrowsANonPathMaintenanceRejection() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.stageReplacementRejection =
        new ProtectedBookMaintenanceRejection.ArtifactBusy(
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, store.normalized(book));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> service(store).restoreBook(book, bookKey, backup, backupKey));

    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage())
            .contains("Expected one maintenance artifact-path rejection"));
  }

  private static void assertArtifactPathInvalid(
      BookMaintenanceRejection rejection,
      BookMaintenanceArtifactRole expectedRole,
      dev.erst.fingrind.contract.runtime.PublicPathHint expectedPathHint,
      BookMaintenancePathFailure expectedPathFailure) {
    BookMaintenanceRejection.ArtifactPathInvalid invalid =
        assertInstanceOf(BookMaintenanceRejection.ArtifactPathInvalid.class, rejection);
    assertEquals(expectedRole, invalid.artifactRole());
    assertEquals(expectedPathHint, invalid.artifactPath());
    assertEquals(expectedPathFailure, invalid.pathFailure());
  }

  private static Path restoreBookKey(Path bookFilePath) {
    return bookFilePath
        .resolveSibling(bookFilePath.getFileName().toString() + ".book-key")
        .toAbsolutePath()
        .normalize();
  }
}
