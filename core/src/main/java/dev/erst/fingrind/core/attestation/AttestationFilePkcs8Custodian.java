package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.io.IOException;
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
  private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
  private static final int KEY_BIT_COUNT = 256;
  private static final int AUTHENTICATION_TAG_BIT_COUNT = 128;
  static final int MAXIMUM_KEY_FILE_BYTE_COUNT = 1_024;
  private static final SecureRandom RANDOM = new SecureRandom();

  private AttestationFilePkcs8Custodian() {}

  static AttestationKeyFileCreation createCredential(Path path, char[] passphrase)
      throws IOException {
    PublishedKeyPair created = createAndPublish(path, passphrase);
    byte[] spki = created.keyPair().getPublic().getEncoded();
    try {
      return new AttestationKeyFileCreation(
          created.publication(), new AttestationPublicCredential(spki));
    } finally {
      java.util.Arrays.fill(spki, (byte) 0);
    }
  }

  static AttestationPublicCredential readPublicCredential(Path path) throws IOException {
    byte[] encodedKeyFile = readEncryptedPrivateKey(Objects.requireNonNull(path, "path"));
    AttestationEncryptedPkcs8KeyFile.ParsedKeyFile parsedKeyFile = null;
    try {
      parsedKeyFile = AttestationEncryptedPkcs8KeyFile.parse(encodedKeyFile);
      return parsedKeyFile.publicCredential();
    } finally {
      if (parsedKeyFile != null) {
        parsedKeyFile.clear();
      }
      java.util.Arrays.fill(encodedKeyFile, (byte) 0);
    }
  }

  static PublicKey create(Path path, char[] passphrase) throws IOException {
    return createAndPublish(path, passphrase).keyPair().getPublic();
  }

  private static PublishedKeyPair createAndPublish(Path path, char[] passphrase)
      throws IOException {
    char[] ownedPassphrase = Objects.requireNonNull(passphrase, "passphrase");
    try {
      Objects.requireNonNull(path, "path");
      KeyPair keyPair = AttestationEd25519.generateKeyPair();
      byte[] spki = keyPair.getPublic().getEncoded();
      byte[] encryptedPrivateKey = encrypt(keyPair.getPrivate(), ownedPassphrase);
      try {
        byte[] encodedKeyFile = AttestationEncryptedPkcs8KeyFile.encode(spki, encryptedPrivateKey);
        try {
          ArtifactPublicationResult publication =
              AttestationKeyFilePublication.writeNewKeyFile(path, encodedKeyFile);
          return new PublishedKeyPair(keyPair, publication);
        } finally {
          java.util.Arrays.fill(encodedKeyFile, (byte) 0);
        }
      } finally {
        java.util.Arrays.fill(spki, (byte) 0);
        java.util.Arrays.fill(encryptedPrivateKey, (byte) 0);
      }
    } finally {
      java.util.Arrays.fill(ownedPassphrase, '\0');
    }
  }

  private record PublishedKeyPair(KeyPair keyPair, ArtifactPublicationResult publication) {}

  static byte[] sign(Path path, char[] passphrase, byte[] payload) throws IOException {
    char[] ownedPassphrase = Objects.requireNonNull(passphrase, "passphrase");
    try {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(payload, "payload");
      byte[] encodedKeyFile = readEncryptedPrivateKey(path);
      try {
        return AttestationEd25519.sign(decrypt(encodedKeyFile, ownedPassphrase), payload);
      } finally {
        java.util.Arrays.fill(encodedKeyFile, (byte) 0);
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
    byte[] salt = new byte[AttestationEncryptedPkcs8KeyFile.SALT_BYTE_COUNT];
    byte[] initializationVector =
        new byte[AttestationEncryptedPkcs8KeyFile.INITIALIZATION_VECTOR_BYTE_COUNT];
    try {
      RANDOM.nextBytes(salt);
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
      try {
        return AttestationEncryptedPkcs8KeyFile.encodeEncryptedPayload(
            salt, initializationVector, ciphertext);
      } finally {
        java.util.Arrays.fill(ciphertext, (byte) 0);
      }
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Encrypted PKCS#8 is unavailable in this Java runtime.", exception);
    } finally {
      java.util.Arrays.fill(salt, (byte) 0);
      java.util.Arrays.fill(initializationVector, (byte) 0);
    }
  }

  private static PrivateKey decrypt(byte[] encryptedPkcs8, char[] passphrase) {
    return decrypt(encryptedPkcs8, passphrase, ignored -> {});
  }

  static PrivateKey decrypt(
      byte[] encryptedPkcs8, char[] passphrase, ParsedKeyFileObserver parsedKeyFileObserver) {
    requirePassphrase(passphrase);
    AttestationEncryptedPkcs8KeyFile.ParsedKeyFile parsedKeyFile = null;
    try {
      parsedKeyFile = AttestationEncryptedPkcs8KeyFile.parse(encryptedPkcs8);
      Objects.requireNonNull(parsedKeyFileObserver, "parsedKeyFileObserver").observe(parsedKeyFile);
      return parsedKeyFile.withPrivateKeyMaterial(
          (salt, initializationVector, ciphertext) ->
              decryptPrivateKey(passphrase, salt, initializationVector, ciphertext));
    } catch (GeneralSecurityException exception) {
      throw new IllegalArgumentException(
          "Attestation key file cannot be decrypted with this passphrase.", exception);
    } finally {
      if (parsedKeyFile != null) {
        parsedKeyFile.clear();
      }
    }
  }

  private static PrivateKey decryptPrivateKey(
      char[] passphrase, byte[] salt, byte[] initializationVector, byte[] ciphertext)
      throws GeneralSecurityException {
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
  }

  private static SecretKeySpec derivedAesKey(char[] passphrase, byte[] salt)
      throws GeneralSecurityException {
    PBEKeySpec specification =
        new PBEKeySpec(
            passphrase, salt, AttestationEncryptedPkcs8KeyFile.ITERATION_COUNT, KEY_BIT_COUNT);
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

  private static void requirePassphrase(char[] passphrase) {
    if (Objects.requireNonNull(passphrase, "passphrase").length == 0) {
      throw new IllegalArgumentException("Attestation key passphrase must not be empty.");
    }
  }

  /** Observes parsed key-file fields only in package-level custody regression tests. */
  @FunctionalInterface
  interface ParsedKeyFileObserver {
    /** Receives fields that will be cleared before decrypt returns or fails. */
    void observe(AttestationEncryptedPkcs8KeyFile.ParsedKeyFile parsedKeyFile);
  }

  /** Produces a JCA cipher for deterministic provider-failure testing. */
  @FunctionalInterface
  interface CipherFactory {
    /** Creates the cipher identified by its JCA transformation. */
    Cipher create(String algorithm) throws GeneralSecurityException;
  }
}
