package dev.erst.fingrind.core.attestation;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Public routing metadata for one encrypted file-backed attestation credential.
 *
 * <p>This value deliberately contains no passphrase or private-key material. Its paths are consumed
 * only by the file-custody signing seam when a protected-book mutation reaches its transactional
 * compare-and-swap boundary.
 */
public record AttestationCredentialSource(
    AttestationCustodian custodian,
    UUID principalId,
    Path encryptedKeyFilePath,
    Path passphraseFilePath) {
  /** Normalizes credential-source paths and prevents an ambiguous one-file selection. */
  public AttestationCredentialSource {
    Objects.requireNonNull(custodian, "custodian");
    Objects.requireNonNull(principalId, "principalId");
    encryptedKeyFilePath = normalize(encryptedKeyFilePath, "encryptedKeyFilePath");
    passphraseFilePath = normalize(passphraseFilePath, "passphraseFilePath");
    if (encryptedKeyFilePath.equals(passphraseFilePath)) {
      throw new IllegalArgumentException(
          "Attestation encrypted key and passphrase files must be distinct.");
    }
  }

  private static Path normalize(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }
}
