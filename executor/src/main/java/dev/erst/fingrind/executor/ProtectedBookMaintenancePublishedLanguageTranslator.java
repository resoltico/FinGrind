package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenancePathFailure;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import java.util.Objects;

/** Projects local protected-book maintenance outcomes into the published contract. */
public final class ProtectedBookMaintenancePublishedLanguageTranslator {
  private ProtectedBookMaintenancePublishedLanguageTranslator() {}

  /** Projects one local backup outcome into the public contract. */
  public static BackupBookResult toPublished(ProtectedBookBackupOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case ProtectedBookBackupOutcome.BackedUp backedUp ->
          new BackupBookResult.BackedUp(
              backedUp.bookFilePath(), backedUp.backupFilePath(), backedUp.backupBookKeyFilePath());
      case ProtectedBookBackupOutcome.Rejected rejected ->
          new BackupBookResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Projects one local restore outcome into the public contract. */
  public static RestoreBookResult toPublished(ProtectedBookRestoreOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case ProtectedBookRestoreOutcome.Restored restored ->
          new RestoreBookResult.Restored(restored.bookFilePath(), restored.bookKeyFilePath());
      case ProtectedBookRestoreOutcome.Rejected rejected ->
          new RestoreBookResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Projects one local rekey outcome into the public contract. */
  public static RekeyBookResult toPublished(ProtectedBookRekeyOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case ProtectedBookRekeyOutcome.Rekeyed rekeyed ->
          new RekeyBookResult.Rekeyed(rekeyed.bookFilePath());
      case ProtectedBookRekeyOutcome.Rejected rejected ->
          new RekeyBookResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Projects one local maintenance rejection into the public contract. */
  public static BookMaintenanceRejection toPublished(ProtectedBookMaintenanceRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts ->
          new BookMaintenanceRejection.BookHasBlockingArtifacts(
              blockingArtifacts.bookFilePath(), blockingArtifacts.blockingArtifactPaths());
      case ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts ->
          new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
              blockingArtifacts.backupFilePath(), blockingArtifacts.blockingArtifactPaths());
      case ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook sourceMatchesLiveBook ->
          backupSourceMatchesLiveBook(sourceMatchesLiveBook);
      case ProtectedBookMaintenanceRejection.ArtifactPathInvalid invalidArtifactPath ->
          new BookMaintenanceRejection.ArtifactPathInvalid(
              toPublished(invalidArtifactPath.artifactRole()),
              invalidArtifactPath.artifactPath(),
              toPublishedPathFailure(invalidArtifactPath.pathFailure()));
      case ProtectedBookMaintenanceRejection.ArtifactBusy artifactBusy ->
          new BookMaintenanceRejection.ArtifactBusy(
              toPublished(artifactBusy.artifactRole()), artifactBusy.artifactPath());
      case ProtectedBookMaintenanceRejection.BackupAcknowledgementConflict conflict ->
          new BookMaintenanceRejection.BackupAcknowledgementConflict(conflict.backupId());
      case ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists
              destinationAlreadyExists ->
          new BookMaintenanceRejection.BackupDestinationAlreadyExists(
              destinationAlreadyExists.backupFilePath());
      case ProtectedBookMaintenanceRejection.SecretTargetOccupied targetOccupied ->
          new BookMaintenanceRejection.SecretTargetOccupied(targetOccupied.secretTargetPath());
      case ProtectedBookMaintenanceRejection.BookDestinationOccupied destinationOccupied ->
          new BookMaintenanceRejection.BookDestinationOccupied(destinationOccupied.bookFilePath());
      case ProtectedBookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          new BookMaintenanceRejection.ArtifactVerificationFailed(
              toPublished(verificationFailed.artifactRole()),
              verificationFailed.artifactPath(),
              toPublished(verificationFailed.verificationFailure()));
    };
  }

  private static BookMaintenanceRejection.BackupSourceMatchesLiveBook backupSourceMatchesLiveBook(
      ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook sourceMatchesLiveBook) {
    return new BookMaintenanceRejection.BackupSourceMatchesLiveBook(
        sourceMatchesLiveBook.bookFilePath(), sourceMatchesLiveBook.backupFilePath());
  }

  private static BookMaintenanceArtifactRole toPublished(
      ProtectedBookMaintenanceArtifactRole artifactRole) {
    Objects.requireNonNull(artifactRole, "artifactRole");
    return switch (artifactRole) {
      case LIVE_BOOK -> BookMaintenanceArtifactRole.LIVE_BOOK;
      case BACKUP_SOURCE -> BookMaintenanceArtifactRole.BACKUP_SOURCE;
      case BACKUP_TARGET -> BookMaintenanceArtifactRole.BACKUP_TARGET;
      case BACKUP_KEY_TARGET -> BookMaintenanceArtifactRole.BACKUP_KEY_TARGET;
      case ROLLBACK_ARTIFACT -> BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT;
      case RESTORED_TARGET -> BookMaintenanceArtifactRole.RESTORED_TARGET;
    };
  }

  private static BookMaintenancePathFailure toPublishedPathFailure(
      ProtectedBookMaintenancePathFailure pathFailure) {
    Objects.requireNonNull(pathFailure, "pathFailure");
    return switch (pathFailure) {
      case MISSING_PARENT_DIRECTORY -> BookMaintenancePathFailure.MISSING_PARENT_DIRECTORY;
      case PARENT_PATH_COLLISION -> BookMaintenancePathFailure.PARENT_PATH_COLLISION;
      case PARENT_OWNER_ACCESS_REQUIRED -> BookMaintenancePathFailure.PARENT_OWNER_ACCESS_REQUIRED;
      case PARENT_OWNER_ONLY_REQUIRED -> BookMaintenancePathFailure.PARENT_OWNER_ONLY_REQUIRED;
      case TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE ->
          BookMaintenancePathFailure.TARGET_MUST_BE_REGULAR_NON_SYMLINK_FILE;
      case UNSUPPORTED_SECURE_FILESYSTEM ->
          BookMaintenancePathFailure.UNSUPPORTED_SECURE_FILESYSTEM;
      case ATOMIC_SECRET_PUBLICATION_UNSUPPORTED ->
          BookMaintenancePathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED;
    };
  }

  private static BookMaintenanceVerificationFailure toPublished(
      ProtectedBookVerificationFailure verificationFailure) {
    Objects.requireNonNull(verificationFailure, "verificationFailure");
    return switch (verificationFailure) {
      case MISSING -> BookMaintenanceVerificationFailure.MISSING;
      case BLANK_SQLITE -> BookMaintenanceVerificationFailure.BLANK_SQLITE;
      case FOREIGN_SQLITE -> BookMaintenanceVerificationFailure.FOREIGN_SQLITE;
      case UNSUPPORTED_FORMAT_VERSION ->
          BookMaintenanceVerificationFailure.UNSUPPORTED_FORMAT_VERSION;
      case INCOMPLETE_FINGRIND -> BookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND;
      case PROTECTED_BOOK_VERIFICATION_FAILED ->
          BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED;
    };
  }
}
