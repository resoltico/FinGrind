package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates the version-one encrypted-PKCS#8 file custodian and returns only public credential data.
 */
public final class AttestationKeyFiles {
  private AttestationKeyFiles() {}

  /**
   * Creates one new no-clobber encrypted Ed25519 private-key file and returns its public
   * credential.
   */
  public static AttestationKeyFileCreation create(Path path, char[] passphrase) throws IOException {
    char[] ownedPassphrase = Objects.requireNonNull(passphrase, "passphrase").clone();
    try {
      return AttestationFilePkcs8Custodian.createCredential(
          Objects.requireNonNull(path, "path"), ownedPassphrase);
    } finally {
      Arrays.fill(ownedPassphrase, '\0');
    }
  }

  /** Creates one new encrypted credential using the secret decoded from one passphrase file. */
  public static AttestationKeyFileCreation create(Path path, Path passphraseFilePath)
      throws IOException {
    char[] passphrase =
        readPassphraseFile(Objects.requireNonNull(passphraseFilePath, "passphraseFilePath"));
    try {
      return create(path, passphrase);
    } finally {
      Arrays.fill(passphrase, '\0');
    }
  }

  /**
   * Validates one passphrase-file source without creating, opening, or replacing a credential.
   *
   * <p>This enables callers to reject malformed founder input before they create another protected
   * artifact. The decoded secret is cleared before this method returns.
   */
  public static void validatePassphraseFile(Path passphraseFilePath) throws IOException {
    char[] passphrase =
        readPassphraseFile(Objects.requireNonNull(passphraseFilePath, "passphraseFilePath"));
    Arrays.fill(passphrase, '\0');
  }

  /** Reads the public credential published with an encrypted attestation key file. */
  public static AttestationPublicCredential loadPublicCredential(Path path) throws IOException {
    return AttestationFilePkcs8Custodian.readPublicCredential(Objects.requireNonNull(path, "path"));
  }

  /**
   * Opens one existing encrypted key or creates it atomically, then binds it to one principal.
   *
   * <p>The passphrase source is read, decoded, and cleared entirely inside the file-custody
   * boundary. A missing encrypted-key path is deliberately creation-only; an existing key is never
   * replaced.
   */
  public static AttestationSigningCredentialOpening openOrCreateCredential(
      UUID principalId, Path encryptedKeyFilePath, Path passphraseFilePath) throws IOException {
    UUID checkedPrincipalId = Objects.requireNonNull(principalId, "principalId");
    Path checkedKeyPath = Objects.requireNonNull(encryptedKeyFilePath, "encryptedKeyFilePath");
    AttestationPublicCredential existingCredential =
        Files.exists(checkedKeyPath, LinkOption.NOFOLLOW_LINKS)
            ? loadPublicCredential(checkedKeyPath)
            : null;
    char[] passphrase =
        readPassphraseFile(Objects.requireNonNull(passphraseFilePath, "passphraseFilePath"));
    try {
      if (existingCredential != null) {
        return new AttestationSigningCredentialOpening(
            new AttestationSigningCredential(
                checkedPrincipalId, existingCredential, checkedKeyPath, passphrase),
            null);
      }
      AttestationKeyFileCreation created = create(checkedKeyPath, passphrase);
      return new AttestationSigningCredentialOpening(
          new AttestationSigningCredential(
              checkedPrincipalId, created.credential(), created.keyFilePath(), passphrase),
          created.publication());
    } finally {
      Arrays.fill(passphrase, '\0');
    }
  }

  /**
   * Opens one existing encrypted credential bound to its declared principal.
   *
   * <p>Unlike genesis provisioning, this method refuses a missing credential path. Mutations must
   * never silently create a key that has not first been enrolled by an attested operation.
   */
  public static AttestationSigningCredential openExistingCredential(
      UUID principalId, Path encryptedKeyFilePath, Path passphraseFilePath) throws IOException {
    UUID checkedPrincipalId = Objects.requireNonNull(principalId, "principalId");
    Path checkedKeyPath = Objects.requireNonNull(encryptedKeyFilePath, "encryptedKeyFilePath");
    AttestationPublicCredential existingCredential = loadPublicCredential(checkedKeyPath);
    char[] passphrase =
        readPassphraseFile(Objects.requireNonNull(passphraseFilePath, "passphraseFilePath"));
    try {
      return new AttestationSigningCredential(
          checkedPrincipalId, existingCredential, checkedKeyPath, passphrase);
    } finally {
      Arrays.fill(passphrase, '\0');
    }
  }

  private static char[] readPassphraseFile(Path passphraseFilePath) throws IOException {
    byte[] encoded = readBounded(passphraseFilePath);
    CharBuffer decoded = CharBuffer.allocate(encoded.length);
    try {
      decodeUtf8(encoded, decoded);
      decoded.flip();
      int length = normalizedPassphraseLength(decoded);
      if (length == 0) {
        throw new IllegalArgumentException("Attestation key passphrase must not be empty.");
      }
      char[] passphrase = new char[length];
      decoded.get(passphrase);
      return passphrase;
    } finally {
      clear(decoded);
      Arrays.fill(encoded, (byte) 0);
    }
  }

  private static void decodeUtf8(byte[] encoded, CharBuffer decoded) {
    var decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    var decodeResult = decoder.decode(ByteBuffer.wrap(encoded), decoded, true);
    if (decodeResult.isError()) {
      throw new IllegalArgumentException("Attestation passphrase file is not valid UTF-8.");
    }
    decoder.flush(decoded);
  }

  private static void clear(CharBuffer decoded) {
    decoded.clear();
    while (decoded.hasRemaining()) {
      decoded.put('\0');
    }
  }

  private static byte[] readBounded(Path passphraseFilePath) throws IOException {
    try (PrivateOutputFile.OpenedFile channel =
            PrivateOutputFile.openExisting(passphraseFilePath, PrivateOutputFile.Access.READ_ONLY);
        InputStream input = Channels.newInputStream(channel)) {
      byte[] bytes = input.readNBytes(4_097);
      if (bytes.length <= 4_096) {
        return bytes;
      }
      Arrays.fill(bytes, (byte) 0);
      throw new IllegalArgumentException("Attestation passphrase file exceeds 4096 UTF-8 bytes.");
    }
  }

  static FileChannel openPassphraseFileNoFollow(
      Path passphraseFilePath, PassphraseChannelOpener passphraseChannelOpener) throws IOException {
    Path checkedPassphraseFilePath =
        Objects.requireNonNull(passphraseFilePath, "passphraseFilePath");
    PassphraseChannelOpener checkedPassphraseChannelOpener =
        Objects.requireNonNull(passphraseChannelOpener, "passphraseChannelOpener");
    try {
      return checkedPassphraseChannelOpener.open(checkedPassphraseFilePath);
    } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
      throw new IOException(
          "The selected filesystem cannot enforce nofollow access for the attestation passphrase file.",
          unsupported);
    }
  }

  /** Opens the caller-selected passphrase file through the required nofollow channel primitive. */
  @FunctionalInterface
  interface PassphraseChannelOpener {
    /** Opens the supplied passphrase file without following a symbolic link. */
    FileChannel open(Path passphraseFilePath) throws IOException;
  }

  private static int normalizedPassphraseLength(CharBuffer characters) {
    int length = characters.remaining();
    if (length > 0 && characters.get(length - 1) == '\n') {
      length--;
      if (length > 0 && characters.get(length - 1) == '\r') {
        length--;
      }
    }
    return length;
  }
}
