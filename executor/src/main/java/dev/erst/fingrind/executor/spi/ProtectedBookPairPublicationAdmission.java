package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
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
        ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired,
        ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked,
        ProtectedBookPairPublicationFailureOutcome.CompletionUncertain {

  /** Both targets are cleanly reserved for exactly one new staged pair. */
  record Prepared(ProtectedBookMaintenanceStore.PreparedPairPublication publication)
      implements ProtectedBookPairPublicationAdmission {
    public Prepared {
      Objects.requireNonNull(publication, "publication");
    }
  }

  /** An earlier exact operation was fully reconciled without another append or staging attempt. */
  record Recovered(
      ProtectedBookPairPublicationBinding binding, ProtectedBookPairPublicationRetention retention)
      implements ProtectedBookPairPublicationAdmission {
    public Recovered {
      Objects.requireNonNull(binding, "binding");
      Objects.requireNonNull(retention, "retention");
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

  private static Path normalized(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }
}
