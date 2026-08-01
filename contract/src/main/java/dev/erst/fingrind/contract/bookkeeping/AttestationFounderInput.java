package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.attestation.AttestationCustodian;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * One explicit founder credential source supplied when an attested book is created.
 *
 * <p>This value names public routing metadata only. The encrypted key and its passphrase remain
 * unread until the core file-custody boundary creates the signed genesis operation.
 */
public record AttestationFounderInput(
    AttestationCustodian custodian,
    UUID principalId,
    Path encryptedKeyFilePath,
    Path passphraseFilePath) {
  /** Normalizes filesystem inputs while retaining no secret material in the command contract. */
  public AttestationFounderInput {
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
