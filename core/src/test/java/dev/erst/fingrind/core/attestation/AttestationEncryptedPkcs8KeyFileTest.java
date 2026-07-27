package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Verifies the encoded encrypted-key boundary and ownership of its decoded material. */
class AttestationEncryptedPkcs8KeyFileTest {
  @Test
  void encodedPayloadRoundTripsPrivateMaterialAndTheParsedOwnerCanClearIt() throws Exception {
    byte[] spki = AttestationEd25519.generateKeyPair().getPublic().getEncoded();
    byte[] salt = nonzeroBytes(AttestationEncryptedPkcs8KeyFile.SALT_BYTE_COUNT);
    byte[] initializationVector =
        nonzeroBytes(AttestationEncryptedPkcs8KeyFile.INITIALIZATION_VECTOR_BYTE_COUNT);
    byte[] ciphertext = nonzeroBytes(spki.length);
    byte[] encryptedPayload =
        AttestationEncryptedPkcs8KeyFile.encodeEncryptedPayload(
            salt, initializationVector, ciphertext);
    byte[] encodedKeyFile = AttestationEncryptedPkcs8KeyFile.encode(spki, encryptedPayload);
    AttestationEncryptedPkcs8KeyFile.ParsedKeyFile parsedKeyFile =
        AttestationEncryptedPkcs8KeyFile.parse(encodedKeyFile);
    try {
      assertArrayEquals(spki, parsedKeyFile.publicCredential().spki());
      assertEquals(
          "read",
          parsedKeyFile.withPrivateKeyMaterial(
              (readSalt, readInitializationVector, readCiphertext) -> {
                assertArrayEquals(salt, readSalt);
                assertArrayEquals(initializationVector, readInitializationVector);
                assertArrayEquals(ciphertext, readCiphertext);
                return "read";
              }));
      assertFalse(parsedKeyFile.isCleared());

      parsedKeyFile.clear();

      assertTrue(parsedKeyFile.isCleared());
    } finally {
      parsedKeyFile.clear();
      Arrays.fill(spki, (byte) 0);
      Arrays.fill(salt, (byte) 0);
      Arrays.fill(initializationVector, (byte) 0);
      Arrays.fill(ciphertext, (byte) 0);
      Arrays.fill(encryptedPayload, (byte) 0);
      Arrays.fill(encodedKeyFile, (byte) 0);
    }
  }

  @Test
  void rejectsEitherIncorrectFixedEncryptionMaterialLength() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationEncryptedPkcs8KeyFile.encodeEncryptedPayload(
                new byte[AttestationEncryptedPkcs8KeyFile.SALT_BYTE_COUNT - 1],
                new byte[AttestationEncryptedPkcs8KeyFile.INITIALIZATION_VECTOR_BYTE_COUNT],
                new byte[1]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationEncryptedPkcs8KeyFile.encodeEncryptedPayload(
                new byte[AttestationEncryptedPkcs8KeyFile.SALT_BYTE_COUNT],
                new byte[AttestationEncryptedPkcs8KeyFile.INITIALIZATION_VECTOR_BYTE_COUNT - 1],
                new byte[1]));
  }

  @Test
  void clearStateChecksEverySensitiveFieldRatherThanOnlyTheFirstOne() {
    assertClearingState(true, false, false, false);
    assertClearingState(false, true, false, false);
    assertClearingState(false, false, true, false);
    assertClearingState(false, false, false, true);
  }

  private static void assertClearingState(
      boolean saltIsNonzero,
      boolean initializationVectorIsNonzero,
      boolean spkiIsNonzero,
      boolean ciphertextIsNonzero) {
    AttestationEncryptedPkcs8KeyFile.ParsedKeyFile parsedKeyFile =
        parsedKeyFile(
            saltIsNonzero, initializationVectorIsNonzero, spkiIsNonzero, ciphertextIsNonzero);
    try {
      assertFalse(parsedKeyFile.isCleared());
      parsedKeyFile.clear();
      assertTrue(parsedKeyFile.isCleared());
    } finally {
      parsedKeyFile.clear();
    }
  }

  private static AttestationEncryptedPkcs8KeyFile.ParsedKeyFile parsedKeyFile(
      boolean saltIsNonzero,
      boolean initializationVectorIsNonzero,
      boolean spkiIsNonzero,
      boolean ciphertextIsNonzero) {
    byte[] spki = AttestationEd25519.generateKeyPair().getPublic().getEncoded();
    byte[] salt = nonzeroBytes(AttestationEncryptedPkcs8KeyFile.SALT_BYTE_COUNT);
    byte[] initializationVector =
        nonzeroBytes(AttestationEncryptedPkcs8KeyFile.INITIALIZATION_VECTOR_BYTE_COUNT);
    byte[] ciphertext = nonzeroBytes(spki.length);
    byte[] encryptedPayload =
        AttestationEncryptedPkcs8KeyFile.encodeEncryptedPayload(
            salt, initializationVector, ciphertext);
    byte[] encodedKeyFile = AttestationEncryptedPkcs8KeyFile.encode(spki, encryptedPayload);
    try {
      int ciphertextOffset = encodedKeyFile.length - ciphertext.length;
      int spkiOffset = ciphertextOffset - spki.length;
      int initializationVectorOffset =
          spkiOffset
              - Short.BYTES
              - AttestationEncryptedPkcs8KeyFile.INITIALIZATION_VECTOR_BYTE_COUNT;
      int saltOffset =
          initializationVectorOffset - AttestationEncryptedPkcs8KeyFile.SALT_BYTE_COUNT;
      zeroWhenRequired(encodedKeyFile, saltOffset, salt.length, saltIsNonzero);
      zeroWhenRequired(
          encodedKeyFile,
          initializationVectorOffset,
          initializationVector.length,
          initializationVectorIsNonzero);
      zeroWhenRequired(encodedKeyFile, spkiOffset, spki.length, spkiIsNonzero);
      zeroWhenRequired(encodedKeyFile, ciphertextOffset, ciphertext.length, ciphertextIsNonzero);
      return AttestationEncryptedPkcs8KeyFile.parse(encodedKeyFile);
    } finally {
      Arrays.fill(spki, (byte) 0);
      Arrays.fill(salt, (byte) 0);
      Arrays.fill(initializationVector, (byte) 0);
      Arrays.fill(ciphertext, (byte) 0);
      Arrays.fill(encryptedPayload, (byte) 0);
      Arrays.fill(encodedKeyFile, (byte) 0);
    }
  }

  private static void zeroWhenRequired(byte[] bytes, int offset, int length, boolean isNonzero) {
    if (!isNonzero) {
      Arrays.fill(bytes, offset, offset + length, (byte) 0);
    }
  }

  private static byte[] nonzeroBytes(int length) {
    byte[] bytes = new byte[length];
    Arrays.fill(bytes, (byte) 1);
    return bytes;
  }
}
