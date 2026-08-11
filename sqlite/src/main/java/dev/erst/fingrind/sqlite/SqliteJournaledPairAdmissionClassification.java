package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Classifies only clean target facts after legacy evidence has already been rejected. */
final class SqliteJournaledPairAdmissionClassification {
  private SqliteJournaledPairAdmissionClassification() {}

  /**
   * Classifies final-target occupancy without granting recovery authority to an old sidecar.
   *
   * <p>An unbound old owner-stage record is evidence, not an ordinary occupied destination. The
   * caller therefore receives an evidence-blocked outcome rather than a classification that could
   * cause a later operation to overwrite, delete, or reinterpret private residue.
   */
  static SqlitePairPublicationReconciliation classifyCleanTargets(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy expectedBookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request) {
    Path checkedBookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Path checkedSecretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    RestoredBookTargetPolicy checkedPolicy =
        Objects.requireNonNull(expectedBookTargetPolicy, "expectedBookTargetPolicy");
    ProtectedBookPairPublicationRecoveryRequest checkedRequest =
        Objects.requireNonNull(request, "request");
    PairTargetPresence targets =
        PairTargetPresence.observe(checkedBookTargetPath, checkedSecretTargetPath);
    return switch (targets.occupancy()) {
      case BOTH_ABSENT -> SqlitePairPublicationReconciliationAbsent.INSTANCE;
      case BOTH_PRESENT -> {
        if (checkedPolicy == RestoredBookTargetPolicy.REPLACE_SELECTED) {
          throw secretTargetOccupied(checkedSecretTargetPath);
        }
        yield existingCompleteBackupOrEvidenceBlocked(
            checkedRequest, checkedBookTargetPath, checkedSecretTargetPath);
      }
      case BOOK_ONLY -> {
        if (checkedPolicy == RestoredBookTargetPolicy.REPLACE_SELECTED) {
          yield SqlitePairPublicationReconciliationAbsent.INSTANCE;
        }
        throw occupiedBookTarget(checkedRequest, checkedBookTargetPath);
      }
      case SECRET_ONLY -> throw secretTargetOccupied(checkedSecretTargetPath);
    };
  }

  /** Creates the public-safe refusal for any legacy or otherwise untrusted pair evidence. */
  static ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked evidenceBlocked(
      Path bookPath, Path secretPath) {
    return new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
        Objects.requireNonNull(bookPath, "bookPath"),
        ProtectedBookPairPublicationMemberState.UNESTABLISHED,
        Objects.requireNonNull(secretPath, "secretPath"),
        ProtectedBookPairPublicationMemberState.UNESTABLISHED);
  }

  private static ProtectedBookMaintenanceRejectionException occupiedBookTarget(
      ProtectedBookPairPublicationRecoveryRequest request, Path bookTargetPath) {
    return new ProtectedBookMaintenanceRejectionException(
        switch (request) {
          case ProtectedBookPairPublicationRecoveryRequest.Backup _ ->
              new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(bookTargetPath);
          case ProtectedBookPairPublicationRecoveryRequest.Restore _ ->
              new ProtectedBookMaintenanceRejection.BookDestinationOccupied(bookTargetPath);
          case ProtectedBookPairPublicationRecoveryRequest.Rekey _ ->
              throw new IllegalStateException(
                  "A rekey pair publication must replace its selected live book target.");
        });
  }

  private static ProtectedBookMaintenanceRejectionException secretTargetOccupied(
      Path secretTargetPath) {
    return new ProtectedBookMaintenanceRejectionException(
        new ProtectedBookMaintenanceRejection.SecretTargetOccupied(secretTargetPath));
  }

  private static SqlitePairPublicationReconciliation existingCompleteBackupOrEvidenceBlocked(
      ProtectedBookPairPublicationRecoveryRequest request,
      Path bookTargetPath,
      Path secretTargetPath) {
    if (request instanceof ProtectedBookPairPublicationRecoveryRequest.Backup
        && Files.isRegularFile(bookTargetPath, LinkOption.NOFOLLOW_LINKS)
        && Files.isRegularFile(secretTargetPath, LinkOption.NOFOLLOW_LINKS)) {
      return new SqlitePairPublicationReconciliationExistingCompleteBackup(
          bookTargetPath, secretTargetPath);
    }
    return new SqlitePairPublicationReconciliationEvidenceBlocked(bookTargetPath, secretTargetPath);
  }

  /** Holds non-following target-presence facts observed after evidence scanning. */
  private record PairTargetPresence(boolean bookExists, boolean secretExists) {
    private static PairTargetPresence observe(Path bookTargetPath, Path secretTargetPath) {
      return new PairTargetPresence(
          Files.exists(bookTargetPath, LinkOption.NOFOLLOW_LINKS),
          Files.exists(secretTargetPath, LinkOption.NOFOLLOW_LINKS));
    }

    private Occupancy occupancy() {
      if (bookExists) {
        return secretExists ? Occupancy.BOTH_PRESENT : Occupancy.BOOK_ONLY;
      }
      return secretExists ? Occupancy.SECRET_ONLY : Occupancy.BOTH_ABSENT;
    }

    /** Closed target-presence states observed after journal-evidence admission. */
    private enum Occupancy {
      /** Neither final target exists. */
      BOTH_ABSENT,
      /** Only the final protected-book target exists. */
      BOOK_ONLY,
      /** Only the final generated-secret target exists. */
      SECRET_ONLY,
      /** Both final targets exist. */
      BOTH_PRESENT
    }
  }
}
