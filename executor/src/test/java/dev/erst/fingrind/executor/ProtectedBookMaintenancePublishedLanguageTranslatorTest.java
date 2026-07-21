package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the total local-to-published vocabulary projection for maintenance outcomes. */
class ProtectedBookMaintenancePublishedLanguageTranslatorTest {
  private static final Path BOOK_PATH = Path.of("book.sqlite");
  private static final Path BACKUP_PATH = Path.of("backup.fgba");
  private static final Path KEY_PATH = Path.of("backup.key");
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @Test
  void mapsEveryOutcomeAlternativeWithoutLosingTheSelectedPaths() {
    BackupBookResult.BackedUp backedUp =
        assertInstanceOf(
            BackupBookResult.BackedUp.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookBackupOutcome.BackedUp(
                    BOOK_PATH, BACKUP_PATH, KEY_PATH, BACKUP_ID, true)));
    assertEquals(BACKUP_ID, backedUp.backupId());
    assertTrue(backedUp.acknowledgementResumed());

    assertInstanceOf(
        BackupBookResult.AcknowledgementPending.class,
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
            new ProtectedBookBackupOutcome.AcknowledgementPending(
                BOOK_PATH, BACKUP_PATH, KEY_PATH, BACKUP_ID)));
    assertInstanceOf(
        RestoreBookResult.Restored.class,
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
            new ProtectedBookRestoreOutcome.Restored(BOOK_PATH, KEY_PATH)));
    assertInstanceOf(
        RekeyBookResult.Rekeyed.class,
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
            new ProtectedBookRekeyOutcome.Rekeyed(BOOK_PATH, KEY_PATH)));
  }

  @Test
  void mapsEveryDeterministicRejectionAndEveryClosedVocabularyMember() {
    List<ProtectedBookMaintenanceRejection> rejections =
        List.of(
            new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                BOOK_PATH, List.of(Path.of("book.sqlite-wal"))),
            new ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                BACKUP_PATH, List.of(Path.of("backup.fgba-wal"))),
            new ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook(
                BOOK_PATH, BACKUP_PATH),
            new ProtectedBookMaintenanceRejection.BackupAcknowledgementConflict(BACKUP_ID),
            new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(BACKUP_PATH),
            new ProtectedBookMaintenanceRejection.SecretTargetOccupied(KEY_PATH),
            new ProtectedBookMaintenanceRejection.BookDestinationOccupied(BOOK_PATH));

    for (ProtectedBookMaintenanceRejection rejection : rejections) {
      assertRejectionProjection(rejection);
    }

    for (ProtectedBookMaintenanceArtifactRole role :
        ProtectedBookMaintenanceArtifactRole.values()) {
      assertArtifactBusyProjection(role);
    }
    for (ProtectedBookMaintenancePathFailure failure :
        ProtectedBookMaintenancePathFailure.values()) {
      assertArtifactPathFailureProjection(failure);
    }
    for (ProtectedBookVerificationFailure failure : ProtectedBookVerificationFailure.values()) {
      assertArtifactVerificationFailureProjection(failure);
    }
  }

  private static void assertRejectionProjection(ProtectedBookMaintenanceRejection rejection) {
    BookMaintenanceRejection published =
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(rejection);
    assertEquals(rejection.getClass().getSimpleName(), published.getClass().getSimpleName());
    assertInstanceOf(
        BackupBookResult.Rejected.class,
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
            new ProtectedBookBackupOutcome.Rejected(rejection)));
    assertInstanceOf(
        RestoreBookResult.Rejected.class,
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
            new ProtectedBookRestoreOutcome.Rejected(rejection)));
    assertInstanceOf(
        RekeyBookResult.Rejected.class,
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
            new ProtectedBookRekeyOutcome.Rejected(rejection)));
  }

  private static void assertArtifactBusyProjection(ProtectedBookMaintenanceArtifactRole role) {
    assertInstanceOf(
        BookMaintenanceRejection.ArtifactBusy.class,
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
            new ProtectedBookMaintenanceRejection.ArtifactBusy(role, BOOK_PATH)));
  }

  private static void assertArtifactPathFailureProjection(
      ProtectedBookMaintenancePathFailure failure) {
    assertInstanceOf(
        BookMaintenanceRejection.ArtifactPathInvalid.class,
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
            new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
                ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, BOOK_PATH, failure)));
  }

  private static void assertArtifactVerificationFailureProjection(
      ProtectedBookVerificationFailure failure) {
    assertInstanceOf(
        BookMaintenanceRejection.ArtifactVerificationFailed.class,
        ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
            new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
                ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, BACKUP_PATH, failure)));
  }
}
