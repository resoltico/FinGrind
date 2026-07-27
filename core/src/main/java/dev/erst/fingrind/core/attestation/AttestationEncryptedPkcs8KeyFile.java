package dev.erst.fingrind.core.attestation;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Decodes, encodes, and clears the private byte material in FinGrind encrypted PKCS#8 files. */
final class AttestationEncryptedPkcs8KeyFile {
  private static final byte[] FORMAT_MAGIC = new byte[] {'F', 'G', 'A', 'T', 'K'};
  private static final byte FORMAT_VERSION = 2;
  static final int SALT_BYTE_COUNT = 16;
  static final int INITIALIZATION_VECTOR_BYTE_COUNT = 12;
  static final int ITERATION_COUNT = 600_000;
  private static final int AUTHENTICATION_TAG_BYTE_COUNT = 16;
  private static final int HEADER_BYTE_COUNT =
      FORMAT_MAGIC.length
          + Byte.BYTES
          + Integer.BYTES
          + SALT_BYTE_COUNT
          + INITIALIZATION_VECTOR_BYTE_COUNT
          + Short.BYTES;
  private static final int ENCRYPTED_PAYLOAD_HEADER_BYTE_COUNT =
      FORMAT_MAGIC.length
          + Byte.BYTES
          + Integer.BYTES
          + SALT_BYTE_COUNT
          + INITIALIZATION_VECTOR_BYTE_COUNT;

  private AttestationEncryptedPkcs8KeyFile() {}

  static byte[] encodeEncryptedPayload(
      byte[] salt, byte[] initializationVector, byte[] ciphertext) {
    byte[] checkedSalt = Objects.requireNonNull(salt, "salt");
    byte[] checkedInitializationVector =
        Objects.requireNonNull(initializationVector, "initializationVector");
    byte[] checkedCiphertext = Objects.requireNonNull(ciphertext, "ciphertext");
    if (checkedSalt.length != SALT_BYTE_COUNT
        || checkedInitializationVector.length != INITIALIZATION_VECTOR_BYTE_COUNT) {
      throw new IllegalArgumentException("Encrypted PKCS#8 requires its fixed salt and IV sizes.");
    }
    return ByteBuffer.allocate(ENCRYPTED_PAYLOAD_HEADER_BYTE_COUNT + checkedCiphertext.length)
        .put(FORMAT_MAGIC)
        .put(FORMAT_VERSION)
        .putInt(ITERATION_COUNT)
        .put(checkedSalt)
        .put(checkedInitializationVector)
        .put(checkedCiphertext)
        .array();
  }

  static byte[] encode(byte[] spki, byte[] encryptedPayload) {
    byte[] checkedSpki = AttestationSpki.of(Objects.requireNonNull(spki, "spki")).bytes();
    ByteBuffer payload =
        ByteBuffer.wrap(Objects.requireNonNull(encryptedPayload, "encryptedPayload"));
    payload.position(FORMAT_MAGIC.length + Byte.BYTES + Integer.BYTES);
    byte[] salt = next(payload, SALT_BYTE_COUNT);
    byte[] initializationVector = next(payload, INITIALIZATION_VECTOR_BYTE_COUNT);
    byte[] encryptedPrivateKey = next(payload, payload.remaining());
    try {
      return ByteBuffer.allocate(
              HEADER_BYTE_COUNT + checkedSpki.length + encryptedPrivateKey.length)
          .put(FORMAT_MAGIC)
          .put(FORMAT_VERSION)
          .putInt(ITERATION_COUNT)
          .put(salt)
          .put(initializationVector)
          .putShort((short) checkedSpki.length)
          .put(checkedSpki)
          .put(encryptedPrivateKey)
          .array();
    } finally {
      clear(checkedSpki);
      clear(salt);
      clear(initializationVector);
      clear(encryptedPrivateKey);
    }
  }

  static ParsedKeyFile parse(byte[] encodedKeyFile) {
    ByteBuffer encoded = ByteBuffer.wrap(Objects.requireNonNull(encodedKeyFile, "encodedKeyFile"));
    if (encoded.remaining() < HEADER_BYTE_COUNT + AUTHENTICATION_TAG_BYTE_COUNT
        || !java.util.Arrays.equals(next(encoded, FORMAT_MAGIC.length), FORMAT_MAGIC)
        || encoded.get() != FORMAT_VERSION) {
      throw new IllegalArgumentException("Attestation key file has an unsupported format.");
    }
    int iterations = encoded.getInt();
    if (iterations != ITERATION_COUNT) {
      throw new IllegalArgumentException("Attestation key file has an unsupported work factor.");
    }
    byte[] salt = null;
    byte[] initializationVector = null;
    byte[] spki = null;
    byte[] ciphertext = null;
    try {
      salt = next(encoded, SALT_BYTE_COUNT);
      initializationVector = next(encoded, INITIALIZATION_VECTOR_BYTE_COUNT);
      int spkiLength = Short.toUnsignedInt(encoded.getShort());
      if (spkiLength == 0 || spkiLength > encoded.remaining()) {
        throw new IllegalArgumentException("Attestation key file has an unsupported format.");
      }
      spki = next(encoded, spkiLength);
      ciphertext = next(encoded, encoded.remaining());
      ParsedKeyFile parsedKeyFile = new ParsedKeyFile(salt, initializationVector, spki, ciphertext);
      salt = null;
      initializationVector = null;
      spki = null;
      ciphertext = null;
      return parsedKeyFile;
    } finally {
      clear(salt);
      clear(initializationVector);
      clear(spki);
      clear(ciphertext);
    }
  }

  private static byte[] next(ByteBuffer source, int byteCount) {
    byte[] value = new byte[byteCount];
    source.get(value);
    return value;
  }

  private static void clear(byte @Nullable [] bytes) {
    if (bytes != null) {
      java.util.Arrays.fill(bytes, (byte) 0);
    }
  }

  /** Owns the sensitive decoded fields of one encrypted PKCS#8 file until they are cleared. */
  static final class ParsedKeyFile {
    private final byte[] salt;
    private final byte[] initializationVector;
    private final byte[] spki;
    private final byte[] ciphertext;

    private ParsedKeyFile(
        byte[] salt, byte[] initializationVector, byte[] spki, byte[] ciphertext) {
      this.salt = salt;
      this.initializationVector = initializationVector;
      this.spki = spki;
      this.ciphertext = ciphertext;
    }

    /** Creates an independent public credential before the parsed key file is cleared. */
    AttestationPublicCredential publicCredential() {
      return new AttestationPublicCredential(spki);
    }

    /**
     * Runs one cryptographic operation over the decoded private-key material without transferring
     * ownership of its arrays.
     */
    <T> T withPrivateKeyMaterial(PrivateKeyMaterialReader<T> reader)
        throws GeneralSecurityException {
      return Objects.requireNonNull(reader, "reader").read(salt, initializationVector, ciphertext);
    }

    void clear() {
      AttestationEncryptedPkcs8KeyFile.clear(salt);
      AttestationEncryptedPkcs8KeyFile.clear(initializationVector);
      AttestationEncryptedPkcs8KeyFile.clear(spki);
      AttestationEncryptedPkcs8KeyFile.clear(ciphertext);
    }

    boolean isCleared() {
      return isCleared(salt)
          && isCleared(initializationVector)
          && isCleared(spki)
          && isCleared(ciphertext);
    }

    private static boolean isCleared(byte[] bytes) {
      for (byte value : bytes) {
        if (value != 0) {
          return false;
        }
      }
      return true;
    }
  }

  /** Reads borrowed decoded private-key material that remains owned by its parsed key file. */
  @FunctionalInterface
  interface PrivateKeyMaterialReader<T> {
    /** Does not retain or mutate the supplied arrays. */
    T read(byte[] salt, byte[] initializationVector, byte[] ciphertext)
        throws GeneralSecurityException;
  }
}
