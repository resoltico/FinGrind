package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.regex.Pattern;

/** Captures the final-member identity and digest after a durable publication attempt. */
record PublicationTransactionFinalizedArtifact(String physicalIdentity, String sha256Hex) {
  private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

  PublicationTransactionFinalizedArtifact {
    physicalIdentity =
        PublicationTransactionStagedArtifact.requireNonBlank(physicalIdentity, "physicalIdentity");
    Objects.requireNonNull(sha256Hex, "sha256Hex");
    if (!SHA_256_HEX.matcher(sha256Hex).matches()) {
      throw new IllegalArgumentException(
          "sha256Hex must contain 64 lowercase hexadecimal characters.");
    }
  }
}
