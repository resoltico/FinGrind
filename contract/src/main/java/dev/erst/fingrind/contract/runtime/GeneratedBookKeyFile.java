package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.util.Objects;

/** Non-secret metadata and completed transaction evidence for one generated protected-book key. */
public record GeneratedBookKeyFile(
    PublicationTransactionArtifact publication,
    String encoding,
    int entropyBits,
    String permissions) {
  public GeneratedBookKeyFile {
    Objects.requireNonNull(publication, "publication");
    Objects.requireNonNull(encoding, "encoding");
    if (encoding.isBlank()) {
      throw new IllegalArgumentException("encoding must not be blank.");
    }
    if (entropyBits <= 0) {
      throw new IllegalArgumentException("entropyBits must be positive.");
    }
    Objects.requireNonNull(permissions, "permissions");
    if (permissions.isBlank()) {
      throw new IllegalArgumentException("permissions must not be blank.");
    }
  }
}
