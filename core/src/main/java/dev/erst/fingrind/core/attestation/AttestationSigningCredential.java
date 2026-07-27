package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * One principal-bound encrypted-PKCS#8 signing input.
 *
 * <p>The decrypted private key remains inside the file-custody signing seam. Closing this value
 * clears its in-memory passphrase copy.
 */
public final class AttestationSigningCredential implements AutoCloseable {
  private final UUID principalId;
  private final AttestationPublicCredential publicCredential;
  private final Path encryptedPkcs8Path;
  private final char[] passphrase;
  private boolean closed;

  /** Creates one signing input bound to its declared principal and public credential. */
  public AttestationSigningCredential(
      UUID principalId,
      AttestationPublicCredential publicCredential,
      Path encryptedPkcs8Path,
      char[] passphrase) {
    this.principalId = Objects.requireNonNull(principalId, "principalId");
    this.publicCredential = Objects.requireNonNull(publicCredential, "publicCredential");
    this.encryptedPkcs8Path = Objects.requireNonNull(encryptedPkcs8Path, "encryptedPkcs8Path");
    this.passphrase = Objects.requireNonNull(passphrase, "passphrase").clone();
    if (this.passphrase.length == 0) {
      throw new IllegalArgumentException("Attestation key passphrase must not be empty.");
    }
  }

  /** Returns the recognized principal that this credential represents. */
  public UUID principalId() {
    return principalId;
  }

  /** Returns the public credential metadata without exposing key-custody internals. */
  public AttestationPublicCredential publicCredential() {
    return publicCredential;
  }

  @Override
  public void close() {
    if (!closed) {
      java.util.Arrays.fill(passphrase, '\0');
      closed = true;
    }
  }

  AttestationSignatureEntry sign(byte[] payload) {
    if (closed) {
      throw new IllegalStateException("Attestation signing credential is closed.");
    }
    byte[] checkedPayload = Objects.requireNonNull(payload, "payload").clone();
    try {
      char[] signingPassphrase = passphrase.clone();
      byte[] signature;
      try {
        signature =
            AttestationFilePkcs8Custodian.sign(
                encryptedPkcs8Path, signingPassphrase, checkedPayload);
      } catch (IOException | IllegalArgumentException exception) {
        throw new AttestationCredentialUseException(
            encryptedPkcs8Path, "Attestation key file could not be used for signing.", exception);
      }
      byte[] spki = publicCredential.spki();
      try {
        if (!AttestationEd25519.verifies(spki, checkedPayload, signature)) {
          throw new AttestationCredentialUseException(
              encryptedPkcs8Path,
              "Attestation key file does not match the declared public credential.");
        }
        return new AttestationSignatureEntry(
            principalId, AttestationHash.of(publicCredential.keyId()), signature);
      } finally {
        java.util.Arrays.fill(spki, (byte) 0);
        java.util.Arrays.fill(signature, (byte) 0);
      }
    } finally {
      java.util.Arrays.fill(checkedPayload, (byte) 0);
    }
  }
}
