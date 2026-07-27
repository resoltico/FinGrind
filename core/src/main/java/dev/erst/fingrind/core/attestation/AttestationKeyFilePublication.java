package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Coordinates exact-channel staging and no-clobber encrypted-key publication. */
final class AttestationKeyFilePublication {
  private AttestationKeyFilePublication() {}

  /**
   * Creates one fresh encrypted-key stage, links its final name without replacement, and retains
   * the stage as publication evidence.
   */
  static ArtifactPublicationResult writeNewKeyFile(Path path, byte[] encryptedPrivateKey)
      throws IOException {
    return AttestationKeyFilePublisher.publish(
        Objects.requireNonNull(path, "path"),
        Objects.requireNonNull(encryptedPrivateKey, "encryptedPrivateKey"));
  }
}
