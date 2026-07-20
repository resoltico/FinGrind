package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** File-backed encrypted PKCS#8 custodian; private keys exist only inside its signing seam. */
final class AttestationFilePkcs8Custodian {
  private static final byte[] FORMAT_MAGIC = new byte[] {'F', 'G', 'A', 'T', 'K'};
  private static final byte FORMAT_VERSION = 1;
  private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
  private static final int SALT_BYTE_COUNT = 16;
  private static final int INITIALIZATION_VECTOR_BYTE_COUNT = 12;
  private static final int ITERATION_COUNT = 600_000;
  private static final int KEY_BIT_COUNT = 256;
  private static final int AUTHENTICATION_TAG_BIT_COUNT = 128;
  private static final int HEADER_BYTE_COUNT =
      FORMAT_MAGIC.length
          + Byte.BYTES
          + Integer.BYTES
          + SALT_BYTE_COUNT
          + INITIALIZATION_VECTOR_BYTE_COUNT;
  static final int MAXIMUM_KEY_FILE_BYTE_COUNT = 1_024;
  private static final SecureRandom RANDOM = new SecureRandom();

  private AttestationFilePkcs8Custodian() {}

  static PublicKey create(Path path, char[] passphrase) throws IOException {
    char[] ownedPassphrase = Objects.requireNonNull(passphrase, "passphrase");
    try {
      Objects.requireNonNull(path, "path");
      KeyPair keyPair = AttestationEd25519.generateKeyPair();
      byte[] encryptedPrivateKey = encrypt(keyPair.getPrivate(), ownedPassphrase);
      try {
        AttestationKeyFilePublication.writeNewKeyFile(path, encryptedPrivateKey);
      } finally {
        java.util.Arrays.fill(encryptedPrivateKey, (byte) 0);
      }
      return keyPair.getPublic();
    } finally {
      java.util.Arrays.fill(ownedPassphrase, '\0');
    }
  }

  static byte[] sign(Path path, char[] passphrase, byte[] payload) throws IOException {
    char[] ownedPassphrase = Objects.requireNonNull(passphrase, "passphrase");
    try {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(payload, "payload");
      byte[] encryptedPrivateKey = readEncryptedPrivateKey(path);
      try {
        return AttestationEd25519.sign(decrypt(encryptedPrivateKey, ownedPassphrase), payload);
      } finally {
        java.util.Arrays.fill(encryptedPrivateKey, (byte) 0);
      }
    } finally {
      java.util.Arrays.fill(ownedPassphrase, '\0');
    }
  }

  private static byte[] encrypt(PrivateKey privateKey, char[] passphrase) {
    return encrypt(privateKey, passphrase, Cipher::getInstance);
  }

  private static byte[] readEncryptedPrivateKey(Path path) throws IOException {
    byte[] encryptedPrivateKey;
    try (var input = Files.newInputStream(path)) {
      encryptedPrivateKey = input.readNBytes(MAXIMUM_KEY_FILE_BYTE_COUNT + 1);
    }
    if (encryptedPrivateKey.length <= MAXIMUM_KEY_FILE_BYTE_COUNT) {
      return encryptedPrivateKey;
    }
    java.util.Arrays.fill(encryptedPrivateKey, (byte) 0);
    throw new IllegalArgumentException("Attestation key file exceeds the maximum size of 1 KiB.");
  }

  static byte[] encrypt(PrivateKey privateKey, char[] passphrase, CipherFactory cipherFactory) {
    requirePassphrase(passphrase);
    try {
      byte[] salt = new byte[SALT_BYTE_COUNT];
      RANDOM.nextBytes(salt);
      byte[] initializationVector = new byte[INITIALIZATION_VECTOR_BYTE_COUNT];
      RANDOM.nextBytes(initializationVector);
      Cipher cipher = cipherFactory.create(CIPHER_ALGORITHM);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          derivedAesKey(passphrase, salt),
          new GCMParameterSpec(AUTHENTICATION_TAG_BIT_COUNT, initializationVector));
      byte[] encodedPrivateKey = privateKey.getEncoded();
      byte[] ciphertext;
      try {
        ciphertext = cipher.doFinal(encodedPrivateKey);
      } finally {
        java.util.Arrays.fill(encodedPrivateKey, (byte) 0);
      }
      return ByteBuffer.allocate(HEADER_BYTE_COUNT + ciphertext.length)
          .put(FORMAT_MAGIC)
          .put(FORMAT_VERSION)
          .putInt(ITERATION_COUNT)
          .put(salt)
          .put(initializationVector)
          .put(ciphertext)
          .array();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Encrypted PKCS#8 is unavailable in this Java runtime.", exception);
    }
  }

  private static PrivateKey decrypt(byte[] encryptedPkcs8, char[] passphrase) {
    requirePassphrase(passphrase);
    try {
      ByteBuffer encoded = ByteBuffer.wrap(encryptedPkcs8);
      requireFormat(encoded);
      int iterations = encoded.getInt();
      if (iterations != ITERATION_COUNT) {
        throw new IllegalArgumentException("Attestation key file has an unsupported work factor.");
      }
      byte[] salt = next(encoded, SALT_BYTE_COUNT);
      byte[] initializationVector = next(encoded, INITIALIZATION_VECTOR_BYTE_COUNT);
      byte[] ciphertext = next(encoded, encoded.remaining());
      if (ciphertext.length <= AUTHENTICATION_TAG_BIT_COUNT / Byte.SIZE) {
        throw new IllegalArgumentException("Attestation key file has no encrypted private key.");
      }
      Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
      cipher.init(
          Cipher.DECRYPT_MODE,
          derivedAesKey(passphrase, salt),
          new GCMParameterSpec(AUTHENTICATION_TAG_BIT_COUNT, initializationVector));
      byte[] encodedPrivateKey = cipher.doFinal(ciphertext);
      try {
        return KeyFactory.getInstance(AttestationAlgorithm.ED25519.jcaName())
            .generatePrivate(new PKCS8EncodedKeySpec(encodedPrivateKey));
      } finally {
        java.util.Arrays.fill(encodedPrivateKey, (byte) 0);
      }
    } catch (GeneralSecurityException exception) {
      throw new IllegalArgumentException(
          "Attestation key file cannot be decrypted with this passphrase.", exception);
    }
  }

  private static SecretKeySpec derivedAesKey(char[] passphrase, byte[] salt)
      throws GeneralSecurityException {
    PBEKeySpec specification = new PBEKeySpec(passphrase, salt, ITERATION_COUNT, KEY_BIT_COUNT);
    try {
      byte[] encodedKey =
          SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
              .generateSecret(specification)
              .getEncoded();
      try {
        return new SecretKeySpec(encodedKey, "AES");
      } finally {
        java.util.Arrays.fill(encodedKey, (byte) 0);
      }
    } finally {
      specification.clearPassword();
    }
  }

  private static void requireFormat(ByteBuffer encoded) {
    if (encoded.remaining() < HEADER_BYTE_COUNT + AUTHENTICATION_TAG_BIT_COUNT / Byte.SIZE
        || !java.util.Arrays.equals(next(encoded, FORMAT_MAGIC.length), FORMAT_MAGIC)
        || encoded.get() != FORMAT_VERSION) {
      throw new IllegalArgumentException("Attestation key file has an unsupported format.");
    }
  }

  private static byte[] next(ByteBuffer source, int byteCount) {
    byte[] value = new byte[byteCount];
    source.get(value);
    return value;
  }

  private static void requirePassphrase(char[] passphrase) {
    if (Objects.requireNonNull(passphrase, "passphrase").length == 0) {
      throw new IllegalArgumentException("Attestation key passphrase must not be empty.");
    }
  }

  /** Produces a JCA cipher for deterministic provider-failure testing. */
  @FunctionalInterface
  interface CipherFactory {
    /** Creates the cipher identified by its JCA transformation. */
    Cipher create(String algorithm) throws GeneralSecurityException;
  }
}
