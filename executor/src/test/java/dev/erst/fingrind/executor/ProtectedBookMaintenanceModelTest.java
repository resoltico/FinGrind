package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookPassphraseSource;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRecoveryOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Constructor and enum coverage tests for executor maintenance model types. */
class ProtectedBookMaintenanceModelTest {
  @Test
  void localMaintenanceTypes_validateConstructorStateAndExposeFields() {
    Path book = path("books/acme.sqlite");
    Path bookKey = path("books/acme.book-key");
    Path backup = path("backup/acme.sqlite");
    Path backupKey = path("backup/acme.book-key");
    Path rollback = path("books/acme.rekey-rollback-1.sqlite");
    Path rollbackTwo = path("books/acme.rekey-rollback-2.sqlite");
    ProtectedBookMaintenanceRejection rejection =
        new ProtectedBookMaintenanceRejection.ArtifactBusy(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, book);
    ProtectedBookAccess localAccess =
        new ProtectedBookAccess(book, new ProtectedBookPassphraseSource.KeyFile(backupKey));

    assertEquals(
        book, new ProtectedBookBackupOutcome.BackedUp(book, backup, backupKey).bookFilePath());
    assertEquals(rejection, new ProtectedBookBackupOutcome.Rejected(rejection).rejection());
    assertEquals(
        bookKey, new ProtectedBookRestoreOutcome.Restored(book, bookKey).bookKeyFilePath());
    assertEquals(rejection, new ProtectedBookRestoreOutcome.Rejected(rejection).rejection());
    assertIterableEquals(
        List.of(rollback),
        new ProtectedBookRecoveryOutcome.Inspected(book, List.of(rollback))
            .rollbackArtifactPaths());
    assertEquals(
        rollback, new ProtectedBookRecoveryOutcome.Restored(book, rollback).rollbackArtifactPath());
    assertEquals(
        rollback, new ProtectedBookRecoveryOutcome.Deleted(book, rollback).rollbackArtifactPath());
    assertEquals(rejection, new ProtectedBookRecoveryOutcome.Rejected(rejection).rejection());
    ProtectedBookMaintenanceRejectionException rejectionException =
        new ProtectedBookMaintenanceRejectionException(rejection);
    assertEquals(rejection, rejectionException.rejection());
    assertEquals(rejection.toString(), rejectionException.getMessage());

    assertEquals(book, localAccess.toPublished().bookFilePath());
    assertEquals(localAccess, ProtectedBookAccess.fromPublished(localAccess.toPublished()));
    assertEquals(
        BookAccess.PassphraseSource.StandardInput.INSTANCE,
        ProtectedBookPassphraseSource.StandardInput.INSTANCE.toPublished());
    assertEquals(
        ProtectedBookPassphraseSource.StandardInput.INSTANCE,
        ProtectedBookPassphraseSource.fromPublished(
            BookAccess.PassphraseSource.StandardInput.INSTANCE));
    assertEquals(
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
        ProtectedBookPassphraseSource.InteractivePrompt.INSTANCE.toPublished());
    assertEquals(
        ProtectedBookPassphraseSource.InteractivePrompt.INSTANCE,
        ProtectedBookPassphraseSource.fromPublished(
            BookAccess.PassphraseSource.InteractivePrompt.INSTANCE));
    assertEquals(
        book,
        new ProtectedBookMaintenanceStore.VerifiedBook() {
          @Override
          public Path artifactPath() {
            return book;
          }

          @Override
          public void close() {}
        }.artifactPath());
    assertEquals(book, new ProtectedBookMaintenanceStore.LeaseBusy(book).artifactPath());
    assertEquals(
        ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND,
        new ProtectedBookMaintenanceStore.VerificationFailure(
                book, ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND)
            .failure());

    assertEquals(
        backup,
        new ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook(book, backup)
            .backupFilePath());
    assertEquals(
        ProtectedBookVerificationFailure.FOREIGN_SQLITE,
        new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
                ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
                backup,
                ProtectedBookVerificationFailure.FOREIGN_SQLITE)
            .verificationFailure());
    assertEquals(
        List.of(rollback, rollbackTwo),
        new ProtectedBookMaintenanceRejection.RollbackArtifactSelectionRequired(
                book, List.of(rollback, rollbackTwo))
            .rollbackArtifactPaths());

    assertThrows(
        IllegalArgumentException.class,
        () -> new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(book, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                backup, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProtectedBookMaintenanceRejection.RollbackArtifactSelectionRequired(
                book, List.of(rollback)));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookBackupOutcome.BackedUp(nullOf(), backup, backupKey));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook(nullOf(), backup));
    assertThrows(
        NullPointerException.class, () -> new ProtectedBookMaintenanceRejectionException(nullOf()));
    assertThrows(NullPointerException.class, () -> new ProtectedBookAccess(book, nullOf()));
  }

  @Test
  void maintenanceEnumsExposeCanonicalVocabulary() {
    assertIterableEquals(
        List.of(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
            ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET,
            ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
            ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET),
        List.of(ProtectedBookMaintenanceArtifactRole.values()));
    assertIterableEquals(
        List.of(
            ProtectedBookMaintenancePathFailure.MISSING_PARENT_DIRECTORY,
            ProtectedBookMaintenancePathFailure.PARENT_PATH_COLLISION,
            ProtectedBookMaintenancePathFailure.PARENT_OWNER_ACCESS_REQUIRED,
            ProtectedBookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED,
            ProtectedBookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE,
            ProtectedBookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM),
        List.of(ProtectedBookMaintenancePathFailure.values()));
    assertIterableEquals(
        List.of(
            ProtectedBookVerificationFailure.MISSING,
            ProtectedBookVerificationFailure.BLANK_SQLITE,
            ProtectedBookVerificationFailure.FOREIGN_SQLITE,
            ProtectedBookVerificationFailure.UNSUPPORTED_FORMAT_VERSION,
            ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND,
            ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED),
        List.of(ProtectedBookVerificationFailure.values()));
    assertIterableEquals(
        List.of(
            ProtectedBookMaintenanceAuditKind.BACKUP_CREATED,
            ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED,
            ProtectedBookMaintenanceAuditKind.REKEY_ROLLBACK_RESTORED,
            ProtectedBookMaintenanceAuditKind.REKEY_ROLLBACK_DELETED),
        List.of(ProtectedBookMaintenanceAuditKind.values()));
    assertIterableEquals(
        List.of(
            ProtectedBookMaintenanceAuditCompensationKind.BACKUP_CREATED,
            ProtectedBookMaintenanceAuditCompensationKind.REKEY_ROLLBACK_DELETED),
        List.of(ProtectedBookMaintenanceAuditCompensationKind.values()));
  }

  private static Path path(String relativePath) {
    return Path.of(relativePath).toAbsolutePath().normalize();
  }
}
