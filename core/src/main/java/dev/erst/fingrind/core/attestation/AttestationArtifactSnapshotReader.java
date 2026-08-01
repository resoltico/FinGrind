package dev.erst.fingrind.core.attestation;

import java.util.List;

/**
 * Decodes one backup artifact's opaque snapshot into its complete immutable attestation chain.
 *
 * <p>The reader belongs at the encrypted-storage boundary: it may decrypt or otherwise open the
 * snapshot, but it returns only immutable evidence to the pure core verifier.
 */
@FunctionalInterface
public interface AttestationArtifactSnapshotReader {
  /**
   * Returns the complete genesis-through-head evidence reconstructed from one raw snapshot.
   *
   * @throws AttestationArtifactSnapshotReaderException when the adapter has established a
   *     separately classified external-source failure
   */
  List<AttestationEvidence> read(byte[] snapshot);
}
