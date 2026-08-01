package dev.erst.fingrind.contract.runtime;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Non-secret public identity metadata for one encrypted Ed25519 attestation credential file. */
public record AttestationKeyFileMetadata(
    Path attestationKeyFilePath, String credentialSpki, String keyId) {
  public AttestationKeyFileMetadata {
    Objects.requireNonNull(attestationKeyFilePath, "attestationKeyFilePath");
    if (Files.exists(attestationKeyFilePath, LinkOption.NOFOLLOW_LINKS)
        && !Files.isRegularFile(attestationKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("attestationKeyFilePath must identify a regular file.");
    }
    credentialSpki = requireText(credentialSpki, "credentialSpki");
    keyId = requireText(keyId, "keyId");
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
