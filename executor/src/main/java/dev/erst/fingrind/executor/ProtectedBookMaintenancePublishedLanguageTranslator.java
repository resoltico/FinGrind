package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.PublicationPathFailure;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedPublicationPathFailure;
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
              backedUp.bookFilePath(),
              backedUp.backupFilePath(),
              backedUp.backupBookKeyFilePath(),
              backedUp.backupId(),
              backedUp.pairPublicationCompletion(),
              backedUp.pairPublication(),
              backedUp.acknowledgementState(),
              backedUp.attestationCommit());
      case ProtectedBookBackupOutcome.AcknowledgementPending pending ->
          new BackupBookResult.AcknowledgementPending(
              pending.bookFilePath(),
              pending.backupFilePath(),
              pending.backupBookKeyFilePath(),
              pending.backupId(),
              pending.pairPublicationCompletion(),
              pending.pairPublication());
      case ProtectedBookBackupOutcome.AcknowledgementAuthorizationRejected rejected ->
          new BackupBookResult.AcknowledgementAuthorizationRejected(
              rejected.bookFilePath(),
              rejected.backupFilePath(),
              rejected.backupBookKeyFilePath(),
              rejected.backupId(),
              rejected.pairPublicationCompletion(),
              rejected.pairPublication(),
              dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure.fromWireCode(
                  rejected.failure().code()));
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
              restored.bookFilePath(),
              restored.bookKeyFilePath(),
              restored.attestationCommit(),
              restored.pairPublicationCompletion(),
              restored.pairPublication());
      case ProtectedBookRestoreOutcome.Rejected rejected ->
          new RestoreBookResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Projects one local rekey outcome into the public contract. */
  public static RekeyBookResult toPublished(ProtectedBookRekeyOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case ProtectedBookRekeyOutcome.Rekeyed rekeyed ->
          new RekeyBookResult.Rekeyed(
              rekeyed.bookFilePath(),
              rekeyed.newBookKeyFilePath(),
              rekeyed.attestationCommit(),
              rekeyed.pairPublicationCompletion(),
              rekeyed.pairPublication());
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
      case ProtectedBookMaintenanceRejection.PairTargetsConflict conflict ->
          new BookMaintenanceRejection.PairTargetsConflict(
              conflict.bookTargetPath(), conflict.generatedSecretTargetPath());
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
      case ProtectedBookMaintenanceRejection.RecoveryPending recoveryPending ->
          new BookMaintenanceRejection.RecoveryPending(
              recoveryPending.recoveryOperation(),
              recoveryPending.bookTargetPath(),
              recoveryPending.generatedSecretTargetPath());
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
      case LIVE_BOOK_KEY_SOURCE -> BookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE;
      case BACKUP_SOURCE -> BookMaintenanceArtifactRole.BACKUP_SOURCE;
      case BACKUP_KEY_SOURCE -> BookMaintenanceArtifactRole.BACKUP_KEY_SOURCE;
      case BACKUP_TARGET -> BookMaintenanceArtifactRole.BACKUP_TARGET;
      case BACKUP_KEY_TARGET -> BookMaintenanceArtifactRole.BACKUP_KEY_TARGET;
      case RESTORED_TARGET -> BookMaintenanceArtifactRole.RESTORED_TARGET;
      case NEW_BOOK_KEY_TARGET -> BookMaintenanceArtifactRole.NEW_BOOK_KEY_TARGET;
    };
  }

  private static PublicationPathFailure toPublishedPathFailure(
      ProtectedPublicationPathFailure pathFailure) {
    return Objects.requireNonNull(pathFailure, "pathFailure").publishedFailure();
  }

  private static BookMaintenanceVerificationFailure toPublished(
      ProtectedBookVerificationFailure verificationFailure) {
    Objects.requireNonNull(verificationFailure, "verificationFailure");
    return switch (verificationFailure) {
      case MISSING -> BookMaintenanceVerificationFailure.MISSING;
      case BLANK_SQLITE -> BookMaintenanceVerificationFailure.BLANK_SQLITE;
      case FOREIGN_SQLITE -> BookMaintenanceVerificationFailure.FOREIGN_SQLITE;
      case INCOMPLETE_FINGRIND -> BookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND;
      case PROTECTED_BOOK_VERIFICATION_FAILED ->
          BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED;
    };
  }
}
