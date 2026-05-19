package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRecoveryOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceEvent;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceEventKind;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceVerificationFailure;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Constructor and enum coverage tests for executor maintenance model types. */
class ProtectedBookMaintenanceModelTest {
  @Test
  void localMaintenanceTypes_validateConstructorStateAndExposeFields() {
    Path book = path("books/acme.sqlite");
    Path backup = path("backup/acme.sqlite");
    Path backupKey = path("backup/acme.book-key");
    Path rollback = path("books/acme.rekey-rollback-1.sqlite");
    Path rollbackTwo = path("books/acme.rekey-rollback-2.sqlite");
    ProtectedBookMaintenanceRejection rejection =
        new ProtectedBookMaintenanceRejection.ArtifactBusy(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, book);

    assertEquals(
        book, new ProtectedBookBackupOutcome.BackedUp(book, backup, backupKey).bookFilePath());
    assertEquals(rejection, new ProtectedBookBackupOutcome.Rejected(rejection).rejection());
    assertEquals(
        backup, new ProtectedBookRestoreOutcome.Restored(book, backup, backupKey).backupFilePath());
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

    assertEquals(
        List.of(rollback),
        new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(book, List.of(rollback))
            .blockingArtifactPaths());
    assertEquals(
        List.of(rollback),
        new ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                backup, List.of(rollback))
            .blockingArtifactPaths());
    assertEquals(
        ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
        new ProtectedBookMaintenanceRejection.ArtifactBusy(
                ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, rollback)
            .artifactRole());
    assertEquals(
        backup,
        new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(backup)
            .backupFilePath());
    assertEquals(
        backupKey,
        new ProtectedBookMaintenanceRejection.BackupKeyFileAlreadyExists(backupKey)
            .backupBookKeyFilePath());
    assertEquals(
        ProtectedBookMaintenanceVerificationFailure.FOREIGN_SQLITE,
        new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
                ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
                backup,
                ProtectedBookMaintenanceVerificationFailure.FOREIGN_SQLITE)
            .verificationFailure());
    assertEquals(
        book, new ProtectedBookMaintenanceRejection.NoRollbackArtifactsFound(book).bookFilePath());
    assertIterableEquals(
        List.of(rollback, rollbackTwo),
        new ProtectedBookMaintenanceRejection.RollbackArtifactSelectionRequired(
                book, List.of(rollback, rollbackTwo))
            .rollbackArtifactPaths());
    assertEquals(
        rollback,
        new ProtectedBookMaintenanceRejection.RollbackArtifactNotFound(rollback)
            .rollbackArtifactPath());
    assertEquals(
        rollback,
        new ProtectedBookMaintenanceRejection.RollbackArtifactNotForBook(book, rollback)
            .rollbackArtifactPath());

    assertEquals(book, new ProtectedBookMaintenanceStore.VerifiedBook(book).artifactPath());
    assertEquals(
        ProtectedBookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND,
        new ProtectedBookMaintenanceStore.VerificationFailure(
                book, ProtectedBookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND)
            .failure());
    assertEquals(book, new ProtectedBookMaintenanceStore.LeaseBusy(book).artifactPath());

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
        NullPointerException.class, () -> new ProtectedBookBackupOutcome.Rejected(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookRestoreOutcome.Restored(book, nullOf(), backupKey));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookRecoveryOutcome.Restored(book, nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookMaintenanceRejection.ArtifactBusy(nullOf(), rollback));
    assertThrows(
        NullPointerException.class,
        () ->
            new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, rollback, nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookMaintenanceStore.VerificationFailure(book, nullOf()));
  }

  @Test
  void maintenanceEvents_andEnumsExposeCanonicalVocabulary() {
    Instant recordedAt = Instant.parse("2026-05-18T16:30:00Z");
    Path book = path("books/acme.sqlite");
    Path backup = path("backup/acme.sqlite");
    Path backupKey = path("backup/acme.book-key");
    Path rollback = path("books/acme.rekey-rollback-1.sqlite");

    ProtectedBookMaintenanceEvent backupCreated =
        ProtectedBookMaintenanceEvent.backupCreated(recordedAt, book, backup, backupKey);
    ProtectedBookMaintenanceEvent backupRestored =
        ProtectedBookMaintenanceEvent.backupRestored(recordedAt, book, backup, backupKey);
    ProtectedBookMaintenanceEvent inspected =
        ProtectedBookMaintenanceEvent.rollbackArtifactsInspected(
            recordedAt, book, List.of(rollback));
    ProtectedBookMaintenanceEvent restored =
        ProtectedBookMaintenanceEvent.rollbackArtifactRestored(recordedAt, book, rollback);
    ProtectedBookMaintenanceEvent deleted =
        ProtectedBookMaintenanceEvent.rollbackArtifactDeleted(recordedAt, book, rollback);

    assertEquals(ProtectedBookMaintenanceEventKind.BACKUP_CREATED, backupCreated.kind());
    assertEquals(backupKey, backupCreated.backupBookKeyFilePath());
    assertEquals(ProtectedBookMaintenanceEventKind.BACKUP_RESTORED, backupRestored.kind());
    assertIterableEquals(List.of(rollback), inspected.rollbackArtifactPaths());
    assertEquals(ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_RESTORED, restored.kind());
    assertEquals(ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_DELETED, deleted.kind());

    assertIterableEquals(
        List.of(
            ProtectedBookMaintenanceEventKind.BACKUP_CREATED,
            ProtectedBookMaintenanceEventKind.BACKUP_RESTORED,
            ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_INSPECTED,
            ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_RESTORED,
            ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_DELETED),
        List.of(ProtectedBookMaintenanceEventKind.values()));
    assertEquals("backup-created", ProtectedBookMaintenanceEventKind.BACKUP_CREATED.wireValue());
    assertEquals("backup-restored", ProtectedBookMaintenanceEventKind.BACKUP_RESTORED.wireValue());
    assertEquals(
        "rekey-rollback-inspected",
        ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_INSPECTED.wireValue());
    assertEquals(
        "rekey-rollback-restored",
        ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_RESTORED.wireValue());
    assertEquals(
        "rekey-rollback-deleted",
        ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_DELETED.wireValue());

    assertIterableEquals(
        List.of(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE,
            ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT,
            ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET),
        List.of(ProtectedBookMaintenanceArtifactRole.values()));
    assertIterableEquals(
        List.of(
            ProtectedBookMaintenanceVerificationFailure.MISSING,
            ProtectedBookMaintenanceVerificationFailure.BLANK_SQLITE,
            ProtectedBookMaintenanceVerificationFailure.FOREIGN_SQLITE,
            ProtectedBookMaintenanceVerificationFailure.UNSUPPORTED_FORMAT_VERSION,
            ProtectedBookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND,
            ProtectedBookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED),
        List.of(ProtectedBookMaintenanceVerificationFailure.values()));
  }

  private static Path path(String relativePath) {
    return Path.of(relativePath).toAbsolutePath().normalize();
  }
}
