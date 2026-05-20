package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRecoveryOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import java.nio.file.Path;
import java.util.List;
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
              publicHint(backedUp.bookFilePath()),
              publicHint(backedUp.backupFilePath()),
              publicHint(backedUp.backupBookKeyFilePath()));
      case ProtectedBookBackupOutcome.Rejected rejected ->
          new BackupBookResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Projects one local restore outcome into the public contract. */
  public static RestoreBookResult toPublished(ProtectedBookRestoreOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case ProtectedBookRestoreOutcome.Restored restored ->
          new RestoreBookResult.Restored(
              publicHint(restored.bookFilePath()),
              publicHint(restored.backupFilePath()),
              publicHint(restored.backupBookKeyFilePath()));
      case ProtectedBookRestoreOutcome.Rejected rejected ->
          new RestoreBookResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Projects one local rekey-rollback outcome into the public contract. */
  public static RekeyRollbackResult toPublished(ProtectedBookRecoveryOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case ProtectedBookRecoveryOutcome.Inspected inspected ->
          new RekeyRollbackResult.Inspected(
              publicHint(inspected.bookFilePath()), publicHints(inspected.rollbackArtifactPaths()));
      case ProtectedBookRecoveryOutcome.Restored restored ->
          new RekeyRollbackResult.Restored(
              publicHint(restored.bookFilePath()), publicHint(restored.rollbackArtifactPath()));
      case ProtectedBookRecoveryOutcome.Deleted deleted ->
          new RekeyRollbackResult.Deleted(
              publicHint(deleted.bookFilePath()), publicHint(deleted.rollbackArtifactPath()));
      case ProtectedBookRecoveryOutcome.Rejected rejected ->
          new RekeyRollbackResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Projects one local maintenance rejection into the public contract. */
  public static BookMaintenanceRejection toPublished(ProtectedBookMaintenanceRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts ->
          new BookMaintenanceRejection.BookHasBlockingArtifacts(
              publicHint(blockingArtifacts.bookFilePath()),
              publicHints(blockingArtifacts.blockingArtifactPaths()));
      case ProtectedBookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts ->
          new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
              publicHint(blockingArtifacts.backupFilePath()),
              publicHints(blockingArtifacts.blockingArtifactPaths()));
      case ProtectedBookMaintenanceRejection.BackupSourceMatchesLiveBook sourceMatchesLiveBook ->
          new BookMaintenanceRejection.BackupSourceMatchesLiveBook(
              publicHint(sourceMatchesLiveBook.bookFilePath()),
              publicHint(sourceMatchesLiveBook.backupFilePath()));
      case ProtectedBookMaintenanceRejection.ArtifactBusy artifactBusy ->
          new BookMaintenanceRejection.ArtifactBusy(
              toPublished(artifactBusy.artifactRole()), publicHint(artifactBusy.artifactPath()));
      case ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists
              destinationAlreadyExists ->
          new BookMaintenanceRejection.BackupDestinationAlreadyExists(
              publicHint(destinationAlreadyExists.backupFilePath()));
      case ProtectedBookMaintenanceRejection.BackupKeyFileAlreadyExists keyFileAlreadyExists ->
          new BookMaintenanceRejection.BackupKeyFileAlreadyExists(
              publicHint(keyFileAlreadyExists.backupBookKeyFilePath()));
      case ProtectedBookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          new BookMaintenanceRejection.ArtifactVerificationFailed(
              toPublished(verificationFailed.artifactRole()),
              publicHint(verificationFailed.artifactPath()),
              toPublished(verificationFailed.verificationFailure()));
      case ProtectedBookMaintenanceRejection.NoRollbackArtifactsFound noRollbackArtifactsFound ->
          new BookMaintenanceRejection.NoRollbackArtifactsFound(
              publicHint(noRollbackArtifactsFound.bookFilePath()));
      case ProtectedBookMaintenanceRejection.RollbackArtifactSelectionRequired selectionRequired ->
          new BookMaintenanceRejection.RollbackArtifactSelectionRequired(
              publicHint(selectionRequired.bookFilePath()),
              publicHints(selectionRequired.rollbackArtifactPaths()));
      case ProtectedBookMaintenanceRejection.RollbackArtifactNotFound artifactNotFound ->
          new BookMaintenanceRejection.RollbackArtifactNotFound(
              publicHint(artifactNotFound.rollbackArtifactPath()));
      case ProtectedBookMaintenanceRejection.RollbackArtifactNotForBook artifactNotForBook ->
          new BookMaintenanceRejection.RollbackArtifactNotForBook(
              publicHint(artifactNotForBook.bookFilePath()),
              publicHint(artifactNotForBook.rollbackArtifactPath()));
    };
  }

  private static List<PublicPathHint> publicHints(List<Path> paths) {
    return Objects.requireNonNull(paths, "paths").stream().map(PublicPathHint::fromPath).toList();
  }

  private static PublicPathHint publicHint(Path path) {
    return PublicPathHint.fromPath(path);
  }

  private static BookMaintenanceArtifactRole toPublished(
      ProtectedBookMaintenanceArtifactRole artifactRole) {
    Objects.requireNonNull(artifactRole, "artifactRole");
    return switch (artifactRole) {
      case LIVE_BOOK -> BookMaintenanceArtifactRole.LIVE_BOOK;
      case BACKUP_SOURCE -> BookMaintenanceArtifactRole.BACKUP_SOURCE;
      case ROLLBACK_ARTIFACT -> BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT;
      case RESTORED_TARGET -> BookMaintenanceArtifactRole.RESTORED_TARGET;
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
