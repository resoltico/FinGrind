package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public boundary for independently verifiable, manifest-attested backup artifacts. */
public final class AttestationBackupArtifact {
  private AttestationBackupArtifact() {}

  /**
   * Verifies the artifact's container, snapshot digest, internal chain, chain binding, and BACKUP
   * quorum resolved from the snapshot itself.
   *
   * @throws AttestationArtifactSnapshotReaderException when the supplied reader establishes a
   *     separately classified external-source failure
   */
  public static AttestationBackupArtifactVerification verify(
      byte[] artifact, AttestationArtifactSnapshotReader snapshotReader) {
    return AttestationArtifactVerifier.verifyBackupArtifact(artifact, snapshotReader);
  }

  /** Creates one canonical manifest-attested container from a consistent opaque snapshot. */
  static byte[] create(
      byte[] snapshot,
      UUID bookId,
      UUID backupId,
      BigInteger sourceOrder,
      byte[] sourceOperationHead,
      List<AttestationSigningCredential> signers) {
    byte[] checkedSnapshot = AttestationEncoding.copy(snapshot, "snapshot");
    AttestationBackupManifestPayload payload =
        new AttestationBackupManifestPayload(
            Objects.requireNonNull(bookId, "bookId"),
            Objects.requireNonNull(backupId, "backupId"),
            Objects.requireNonNull(sourceOrder, "sourceOrder"),
            AttestationHash.of(Objects.requireNonNull(sourceOperationHead, "sourceOperationHead")),
            AttestationHash.sha256(checkedSnapshot));
    List<AttestationSignatureEntry> entries =
        List.copyOf(Objects.requireNonNull(signers, "signers")).stream()
            .map(
                signer ->
                    Objects.requireNonNull(signer, "signers must not contain null")
                        .sign(payload.encoded()))
            .toList();
    return new AttestationArtifactContainer(
            checkedSnapshot, AttestationEnvelope.of(payload, entries))
        .encoded();
  }
}
