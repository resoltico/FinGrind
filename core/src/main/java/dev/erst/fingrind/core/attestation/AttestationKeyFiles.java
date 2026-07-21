package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Creates the version-one encrypted-PKCS#8 file custodian and returns only public credential data. */
public final class AttestationKeyFiles {
  private AttestationKeyFiles() {}

  /**
   * Creates one new no-clobber encrypted Ed25519 private-key file and returns its public credential.
   */
  public static AttestationPublicCredential create(Path path, char[] passphrase) throws IOException {
    char[] ownedPassphrase = Objects.requireNonNull(passphrase, "passphrase").clone();
    return AttestationFilePkcs8Custodian.createCredential(
        Objects.requireNonNull(path, "path"), ownedPassphrase);
  }
}
