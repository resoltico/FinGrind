package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.ArtifactPublicationStages;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Creates and writes retained owner-only attestation-key stages through one exact channel. */
final class AttestationKeyFileStaging {
  private static final String STAGED_KEY_FILE_PREFIX = ".fingrind-attestation-key-";
  private static final String STAGED_KEY_FILE_SUFFIX = ".tmp";

  private AttestationKeyFileStaging() {}

  /**
   * Creates a fresh {@code 0600} stage, writes all encrypted bytes, and forces the exact channel.
   *
   * <p>ACL-only filesystems are deliberately refused: Java has no handle-bound ACL creation
   * primitive, and create-then-repair would let a same-owner replacement receive the repair.
   */
  static Path createAndWriteOwnerOnlyStage(Path parent, byte[] encryptedPrivateKey)
      throws IOException {
    return ArtifactPublicationStages.createAndWrite(
        Objects.requireNonNull(parent, "parent"),
        STAGED_KEY_FILE_PREFIX,
        STAGED_KEY_FILE_SUFFIX,
        Objects.requireNonNull(encryptedPrivateKey, "encryptedPrivateKey"));
  }
}
