package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Protected-book maintenance storage with the mandatory attestation transaction and artifact
 * verification boundaries.
 */
public interface AttestedProtectedBookMaintenanceStore extends ProtectedBookMaintenanceStore {
  /** Reads the complete immutable operation chain from one already verified protected book. */
  List<AttestationEvidence> loadAttestationEvidence(VerifiedBook verifiedBook);

  /**
   * Appends one exact attested operation inside the selected book's immediate write transaction.
   *
   * <p>When {@code backupAcknowledgement} is supplied, the store must apply exact-tuple replay and
   * conflicting-backup-ID admission before invoking the authorizer.
   */
  AttestationVerification appendAttestedOperation(
      VerifiedBook verifiedBook,
      AttestationOperationKind operationKind,
      Instant recordedAt,
      AttestationOperationPreimages preimages,
      AttestationOperationAuthorizer authorizer,
      @Nullable AttestationBackupAcknowledgement backupAcknowledgement);

  /**
   * Admits one credential-registry mutation after validating its target against the authenticated
   * current authority state in the same write transaction.
   */
  AttestationVerification appendAttestedRegistryMutation(
      VerifiedBook verifiedBook,
      AttestationRegistryMutation mutation,
      Instant recordedAt,
      AttestationOperationAuthorizer authorizer);

  /**
   * Verifies a manifest-attested backup artifact and opens a temporary verified snapshot source.
   *
   * <p>The result owns its decrypted staging material and must be closed after restoration staging
   * completes.
   */
  VerifiedBackupArtifact verifyBackupArtifact(
      Path normalizedBackupArtifactPath, Path normalizedBackupKeyFilePath);

  /** One verified artifact with its independently authenticated snapshot source. */
  interface VerifiedBackupArtifact extends AutoCloseable {
    /** Returns the verified artifact metadata and authenticated source chain. */
    dev.erst.fingrind.core.attestation.AttestationBackupArtifactVerification verification();

    /** Returns the verified temporary snapshot used only to stage the restored book. */
    VerifiedBook snapshotBook();

    @Override
    void close();
  }

  /** Rejects accidental use of an old unaudited maintenance store at an attested boundary. */
  static AttestedProtectedBookMaintenanceStore require(ProtectedBookMaintenanceStore store) {
    Objects.requireNonNull(store, "store");
    if (store instanceof AttestedProtectedBookMaintenanceStore attestedStore) {
      return attestedStore;
    }
    throw new IllegalArgumentException(
        "Protected-book lifecycle mutation requires an attested maintenance store.");
  }
}
