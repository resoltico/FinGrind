package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/** Captures the admitted stage identity and digest required before automatic residue removal. */
record PublicationTransactionStagedArtifact(
    Path stagePath, String physicalIdentity, String sha256Hex) {
  private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

  PublicationTransactionStagedArtifact {
    stagePath = normalizedArtifactPath(stagePath, "stagePath");
    physicalIdentity = requireNonBlank(physicalIdentity, "physicalIdentity");
    Objects.requireNonNull(sha256Hex, "sha256Hex");
    if (!SHA_256_HEX.matcher(sha256Hex).matches()) {
      throw new IllegalArgumentException(
          "sha256Hex must contain 64 lowercase hexadecimal characters.");
    }
  }

  static Path normalizedArtifactPath(Path path, String parameterName) {
    Path normalizedPath = Objects.requireNonNull(path, parameterName).toAbsolutePath().normalize();
    if (normalizedPath.getParent() == null) {
      throw new IllegalArgumentException(
          parameterName + " must name an artifact in a parent directory.");
    }
    return normalizedPath;
  }

  static String requireNonBlank(String value, String parameterName) {
    String checkedValue = Objects.requireNonNull(value, parameterName);
    if (checkedValue.isBlank()) {
      throw new IllegalArgumentException(parameterName + " must not be blank.");
    }
    return checkedValue;
  }
}
