package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenancePathFailure;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRecoveryOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ProtectedBookMaintenancePublishedLanguageTranslator}. */
class ProtectedBookMaintenancePublishedLanguageTranslatorTest {
  @Test
  void translator_projectsSuccessfulLocalMaintenanceOutcomes() {
    Path book = path("books/acme.sqlite");
    Path bookKey = path("books/acme.book-key");
    Path backup = path("backup/acme.sqlite");
    Path backupKey = path("backup/acme.book-key");
    Path rollback = path("books/acme.rekey-rollback-1.sqlite");

    BackupBookResult.BackedUp backedUp =
        assertInstanceOf(
            BackupBookResult.BackedUp.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookBackupOutcome.BackedUp(book, backup, backupKey)));
    assertEquals(hint(book), backedUp.bookFilePath());
    assertEquals(hint(backup), backedUp.backupFilePath());
    assertEquals(hint(backupKey), backedUp.backupBookKeyFilePath());

    RestoreBookResult.Restored restored =
        assertInstanceOf(
            RestoreBookResult.Restored.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookRestoreOutcome.Restored(book, bookKey)));
    assertEquals(hint(book), restored.bookFilePath());
    assertEquals(hint(bookKey), restored.bookKeyFilePath());

    RekeyRollbackResult.Inspected inspected =
        assertInstanceOf(
            RekeyRollbackResult.Inspected.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookRecoveryOutcome.Inspected(book, List.of(rollback))));
    assertEquals(hint(book), inspected.bookFilePath());
    assertEquals(List.of(hint(rollback)), inspected.rollbackArtifactPaths());

    RekeyRollbackResult.Restored rollbackRestored =
        assertInstanceOf(
            RekeyRollbackResult.Restored.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookRecoveryOutcome.Restored(book, rollback)));
    assertEquals(hint(rollback), rollbackRestored.rollbackArtifactPath());

    RekeyRollbackResult.Deleted deleted =
        assertInstanceOf(
            RekeyRollbackResult.Deleted.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookRecoveryOutcome.Deleted(book, rollback)));
    assertEquals(hint(rollback), deleted.rollbackArtifactPath());
  }

  @Test
  void translator_disambiguatesGroupedMaintenanceArtifactHints() {
    Path book = path("work-volume/books/main.sqlite");
    Path backup = path("work-volume/backup/books/main.sqlite");
    Path backupKey = path("work-volume/backup/secrets/main.book-key");

    BackupBookResult.BackedUp backedUp =
        assertInstanceOf(
            BackupBookResult.BackedUp.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookBackupOutcome.BackedUp(book, backup, backupKey)));

    assertEquals(
        new PublicPathHint("<redacted>/work-volume/books/main.sqlite"), backedUp.bookFilePath());
    assertEquals(
        new PublicPathHint("<redacted>/backup/books/main.sqlite"), backedUp.backupFilePath());
    assertEquals(
        new PublicPathHint("<redacted>/backup/secrets/main.book-key"),
        backedUp.backupBookKeyFilePath());
  }

  @Test
  void translator_projectsEveryLocalMaintenanceRejectionVariant() {
    Path book = path("books/acme.sqlite");
    Path backup = path("backup/acme.sqlite");
    Path backupKey = path("backup/acme.book-key");
    Path rollback = path("books/acme.rekey-rollback-1.sqlite");
    Path rollbackTwo = path("books/acme.rekey-rollback-2.sqlite");

    BookMaintenanceRejection.BookHasBlockingArtifacts blocking =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts(
                    book, List.of(rollback))));
    assertEquals(List.of(hint(rollback)), blocking.blockingArtifactPaths());

    BookMaintenanceRejection.BackupSourceHasBlockingArtifacts backupBlocking =
        assertInstanceOf(
            BookMaintenanceRejection.BackupSourceHasBlockingArtifacts.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                    backup, List.of(rollback))));
    assertEquals(List.of(hint(rollback)), backupBlocking.blockingArtifactPaths());

    BookMaintenanceRejection.BackupSourceMatchesLiveBook sourceMatchesLiveBook =
        assertInstanceOf(
            BookMaintenanceRejection.BackupSourceMatchesLiveBook.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook(book, backup)));
    assertEquals(hint(book), sourceMatchesLiveBook.bookFilePath());
    assertEquals(hint(backup), sourceMatchesLiveBook.backupFilePath());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactBusy.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.ArtifactBusy(
                    ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, backup)));
    assertEquals(BookMaintenanceArtifactRole.BACKUP_SOURCE, busy.artifactRole());
    assertEquals(hint(backup), busy.artifactPath());

    BookMaintenanceRejection.BackupDestinationAlreadyExists destinationExists =
        assertInstanceOf(
            BookMaintenanceRejection.BackupDestinationAlreadyExists.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(backup)));
    assertEquals(hint(backup), destinationExists.backupFilePath());

    BookMaintenanceRejection.BackupKeyFileAlreadyExists keyExists =
        assertInstanceOf(
            BookMaintenanceRejection.BackupKeyFileAlreadyExists.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.BackupKeyFileAlreadyExists(backupKey)));
    assertEquals(hint(backupKey), keyExists.backupBookKeyFilePath());

    for (ProtectedBookVerificationFailure localFailure :
        ProtectedBookVerificationFailure.values()) {
      assertVerificationFailureProjection(rollback, localFailure);
    }

    BookMaintenanceRejection.NoRollbackArtifactsFound noRollbackArtifactsFound =
        assertInstanceOf(
            BookMaintenanceRejection.NoRollbackArtifactsFound.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.NoRollbackArtifactsFound(book)));
    assertEquals(hint(book), noRollbackArtifactsFound.bookFilePath());

    BookMaintenanceRejection.RollbackArtifactSelectionRequired selectionRequired =
        assertInstanceOf(
            BookMaintenanceRejection.RollbackArtifactSelectionRequired.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.RollbackArtifactSelectionRequired(
                    book, List.of(rollback, rollbackTwo))));
    assertEquals(
        List.of(hint(rollback), hint(rollbackTwo)), selectionRequired.rollbackArtifactPaths());

    BookMaintenanceRejection.RollbackArtifactNotFound notFound =
        assertInstanceOf(
            BookMaintenanceRejection.RollbackArtifactNotFound.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.RollbackArtifactNotFound(rollback)));
    assertEquals(hint(rollback), notFound.rollbackArtifactPath());

    BookMaintenanceRejection.RollbackArtifactNotForBook notForBook =
        assertInstanceOf(
            BookMaintenanceRejection.RollbackArtifactNotForBook.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.RollbackArtifactNotForBook(book, rollback)));
    assertEquals(hint(book), notForBook.bookFilePath());
    assertEquals(hint(rollback), notForBook.rollbackArtifactPath());
  }

  @Test
  void translator_projectsArtifactPathFailuresAcrossCanonicalMaintenanceRoles() {
    Path backup = path("backup/acme.sqlite");
    Path backupKey = path("backup/acme.book-key");

    for (ProtectedBookMaintenancePathFailure localFailure :
        ProtectedBookMaintenancePathFailure.values()) {
      assertArtifactPathInvalidProjection(
          ProtectedBookMaintenanceArtifactRole.BACKUP_TARGET, backup, localFailure);
    }

    BookMaintenanceRejection.ArtifactPathInvalid backupKeyInvalid =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactPathInvalid.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
                    ProtectedBookMaintenanceArtifactRole.BACKUP_KEY_TARGET,
                    backupKey,
                    ProtectedBookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM)));
    assertEquals(BookMaintenanceArtifactRole.BACKUP_KEY_TARGET, backupKeyInvalid.artifactRole());
    assertEquals(hint(backupKey), backupKeyInvalid.artifactPath());
    assertEquals(
        BookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM, backupKeyInvalid.pathFailure());
  }

  private static Path path(String relativePath) {
    return Path.of(relativePath).toAbsolutePath().normalize();
  }

  private static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
  }

  private static void assertVerificationFailureProjection(
      Path rollback, ProtectedBookVerificationFailure localFailure) {
    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
                    ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, rollback, localFailure)));
    assertEquals(BookMaintenanceArtifactRole.RESTORED_TARGET, failed.artifactRole());
    assertEquals(hint(rollback), failed.artifactPath());
    assertEquals(
        BookMaintenanceVerificationFailure.valueOf(localFailure.name()),
        failed.verificationFailure());
  }

  private static void assertArtifactPathInvalidProjection(
      ProtectedBookMaintenanceArtifactRole localRole,
      Path artifactPath,
      ProtectedBookMaintenancePathFailure localFailure) {
    BookMaintenanceRejection.ArtifactPathInvalid invalid =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactPathInvalid.class,
            ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(
                new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
                    localRole, artifactPath, localFailure)));
    assertEquals(BookMaintenanceArtifactRole.valueOf(localRole.name()), invalid.artifactRole());
    assertEquals(hint(artifactPath), invalid.artifactPath());
    assertEquals(BookMaintenancePathFailure.valueOf(localFailure.name()), invalid.pathFailure());
  }
}
