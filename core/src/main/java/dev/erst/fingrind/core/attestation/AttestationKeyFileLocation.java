package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves and revalidates the private canonical directory that owns attestation-key names. */
final class AttestationKeyFileLocation {
  private AttestationKeyFileLocation() {}

  static AttestationKeyFileDestination publicationDestination(Path normalizedPath)
      throws IOException {
    Path checkedPath = Objects.requireNonNull(normalizedPath, "path");
    Path parent = canonicalPrivateParent(checkedPath);
    return new AttestationKeyFileDestination(
        parent, parent.resolve(Objects.requireNonNull(checkedPath.getFileName(), "key file name")));
  }

  static Path canonicalPrivateParent(Path path) throws IOException {
    Path parent = path.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("Attestation key file must have a parent directory.");
    }
    PrivateOutputDirectory.requireExistingOwnerOnly(parent);
    Path canonicalParent = parent.toRealPath();
    PrivateOutputDirectory.requireExistingOwnerOnly(canonicalParent);
    return canonicalParent;
  }
}
