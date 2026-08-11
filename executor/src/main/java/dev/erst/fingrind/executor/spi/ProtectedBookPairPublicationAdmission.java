package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.core.PublicationTransactionResult;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Atomic protected-book pair admission held under both final-target leases.
 *
 * <p>It deliberately unifies recovery reconciliation and new staging admission: a caller cannot
 * observe an absent record, release the leases, and then create a competing staged publication.
 */
public sealed interface ProtectedBookPairPublicationAdmission
    permits ProtectedBookPairPublicationAdmission.Prepared,
        ProtectedBookPairPublicationAdmission.Recovered,
        ProtectedBookPairPublicationAdmission.ExistingCompleteBackup,
        ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete,
        ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked {

  /** Both targets are cleanly reserved for exactly one new staged pair. */
  record Prepared(ProtectedBookMaintenanceStore.PreparedPairPublication publication)
      implements ProtectedBookPairPublicationAdmission {
    public Prepared {
      Objects.requireNonNull(publication, "publication");
    }
  }

  /** An earlier exact operation was recovered by its authenticated transaction journal. */
  record Recovered(ProtectedBookPairPublication publication)
      implements ProtectedBookPairPublicationAdmission {
    public Recovered {
      Objects.requireNonNull(publication, "publication");
    }
  }

  /** A completed external backup pair remains authoritative for its selected artifact path. */
  record ExistingCompleteBackup(Path backupArtifactPath, Path backupKeyPath)
      implements ProtectedBookPairPublicationAdmission {
    public ExistingCompleteBackup {
      backupArtifactPath = normalized(backupArtifactPath, "backupArtifactPath");
      backupKeyPath = normalized(backupKeyPath, "backupKeyPath");
      if (backupArtifactPath.equals(backupKeyPath)) {
        throw new IllegalArgumentException("Backup artifact and backup key paths must differ.");
      }
    }
  }

  /**
   * An exact journal was found but could not prove complete publication after recovery.
   *
   * <p>The caller must report the transaction-specific safe failure and must never begin another
   * publication for the same operation context.
   */
  record PublicationTransactionIncomplete(
      Path candidateArtifactPath, PublicationTransactionResult transactionResult)
      implements ProtectedBookPairPublicationAdmission {
    public PublicationTransactionIncomplete {
      candidateArtifactPath = normalized(candidateArtifactPath, "candidateArtifactPath");
      Objects.requireNonNull(transactionResult, "transactionResult");
      if (transactionResult.successful()) {
        throw new IllegalArgumentException(
            "A completed publication transaction cannot be admitted as incomplete.");
      }
    }
  }

  private static Path normalized(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }
}
