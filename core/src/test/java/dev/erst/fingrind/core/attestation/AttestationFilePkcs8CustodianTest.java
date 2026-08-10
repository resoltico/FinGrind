package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.contains;
import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.signingFailure;
import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.writeOwnerOnlyFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Exercises encrypted PKCS#8 custody, including its secure key-file publication boundary. */
class AttestationFilePkcs8CustodianTest extends AttestationKeyFileTestFixture {

  @Test
  void acceptsOnlyTheShippedCustodian() {
    assertEquals(AttestationCustodian.FILE_PKCS8, AttestationCustodian.require("file-pkcs8"));
    assertEquals("file-pkcs8", AttestationCustodian.FILE_PKCS8.wireValue());
    AttestationCustodianNotSupportedException rejection =
        assertThrows(
            AttestationCustodianNotSupportedException.class,
            () -> AttestationCustodian.require("pkcs11"));
    assertEquals("custodian-not-supported: pkcs11", rejection.getMessage());
    assertEquals("pkcs11", rejection.custodian());
  }

  @Test
  void fileCustodianRoundTripsEncryptedPkcs8AndRejectsWrongPassphrases() throws Exception {
    Path keyPath = temporaryDirectory.resolve("signing.pk8");
    char[] createPassphrase = "correct horse battery staple".toCharArray();
    PublicKey publicKey = AttestationFilePkcs8Custodian.create(keyPath, createPassphrase);
    byte[] persisted = Files.readAllBytes(keyPath);
    byte[] payload = new byte[] {4, 5, 6};

    byte[] signature =
        AttestationFilePkcs8Custodian.sign(
            keyPath, "correct horse battery staple".toCharArray(), payload);
    assertTrue(AttestationEd25519.verifies(publicKey, payload, signature));
    assertFalse(Arrays.equals(publicKey.getEncoded(), persisted));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationFilePkcs8Custodian.sign(keyPath, "wrong passphrase".toCharArray(), payload));

    persisted[persisted.length - 1] ^= 1;
    Files.write(keyPath, persisted);
    assertEquals(
        "Attestation key file cannot be decrypted with this passphrase.",
        signingFailure(keyPath).getMessage());
  }

  @Test
  void decryptClearsParsedKeyMaterialWhenAuthenticationFails() throws Exception {
    Path keyPath = temporaryDirectory.resolve("signing.pk8");
    AttestationFilePkcs8Custodian.create(keyPath, "correct horse battery staple".toCharArray());
    byte[] persisted = Files.readAllBytes(keyPath);
    AtomicReference<AttestationEncryptedPkcs8KeyFile.ParsedKeyFile> observed =
        new AtomicReference<>();

    try {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              AttestationFilePkcs8Custodian.decrypt(
                  persisted, "wrong passphrase".toCharArray(), observed::set));

      var parsedKeyFile = observed.get();
      assertNotNull(parsedKeyFile, "parsed key material was observed");
      assertTrue(
          Objects.requireNonNull(parsedKeyFile).isCleared(),
          "authentication failure must clear all parsed key material");
    } finally {
      Arrays.fill(persisted, (byte) 0);
    }
  }

  @Test
  void keyFilePublishesTheCredentialNeededToReuseItsCustodian() throws Exception {
    Path keyPath = temporaryDirectory.resolve("signing.pk8");
    AttestationPublicCredential created =
        AttestationKeyFiles.create(keyPath, "correct horse battery staple".toCharArray())
            .credential();

    AttestationPublicCredential loaded = AttestationKeyFiles.loadPublicCredential(keyPath);

    assertArrayEquals(created.spki(), loaded.spki());
    assertArrayEquals(created.keyId(), loaded.keyId());
  }

  @Test
  void fileCustodianDoesNotPersistPlaintextPrivateKeysAndUsesFreshEncryptionMaterial() {
    var pair = AttestationEd25519.generateKeyPair();
    byte[] privateKeyEncoding = pair.getPrivate().getEncoded();
    byte[] firstEncryption =
        AttestationFilePkcs8Custodian.encrypt(
            pair.getPrivate(), "correct horse battery staple".toCharArray(), Cipher::getInstance);
    byte[] secondEncryption =
        AttestationFilePkcs8Custodian.encrypt(
            pair.getPrivate(), "correct horse battery staple".toCharArray(), Cipher::getInstance);
    try {
      assertFalse(contains(firstEncryption, privateKeyEncoding));
      assertFalse(contains(secondEncryption, privateKeyEncoding));
      assertFalse(Arrays.equals(firstEncryption, secondEncryption));
    } finally {
      Arrays.fill(privateKeyEncoding, (byte) 0);
      Arrays.fill(firstEncryption, (byte) 0);
      Arrays.fill(secondEncryption, (byte) 0);
    }
  }

  @Test
  void fileCustodianRejectsEveryUnsupportedKeyFileShape() throws Exception {
    Path keyPath = temporaryDirectory.resolve("signing.pk8");
    AttestationFilePkcs8Custodian.create(keyPath, "correct horse battery staple".toCharArray());
    byte[] persisted = Files.readAllBytes(keyPath);

    Files.write(keyPath, new byte[0]);
    assertEquals(
        "Attestation key file has an unsupported format.", signingFailure(keyPath).getMessage());

    byte[] wrongMagic = persisted.clone();
    wrongMagic[0] ^= 1;
    Files.write(keyPath, wrongMagic);
    assertEquals(
        "Attestation key file has an unsupported format.", signingFailure(keyPath).getMessage());

    byte[] wrongVersion = persisted.clone();
    wrongVersion[5] = 3;
    Files.write(keyPath, wrongVersion);
    assertEquals(
        "Attestation key file has an unsupported format.", signingFailure(keyPath).getMessage());

    byte[] wrongWorkFactor = persisted.clone();
    ByteBuffer.wrap(wrongWorkFactor).putInt(6, 1);
    Files.write(keyPath, wrongWorkFactor);
    assertEquals(
        "Attestation key file has an unsupported work factor.",
        signingFailure(keyPath).getMessage());

    int spkiLength = Short.toUnsignedInt(ByteBuffer.wrap(persisted).getShort(38));
    Files.write(keyPath, Arrays.copyOf(persisted, 40 + spkiLength));
    assertEquals(
        "Attestation key file has no encrypted private key.", signingFailure(keyPath).getMessage());

    char[] emptyPassphrase = new char[0];
    assertEquals(
        "Attestation key passphrase must not be empty.",
        assertThrows(
                IllegalArgumentException.class,
                () -> AttestationFilePkcs8Custodian.sign(keyPath, emptyPassphrase, new byte[0]))
            .getMessage());
    assertArrayEquals(new char[0], emptyPassphrase);
  }

  @Test
  void fileCustodianRejectsOversizedKeyFilesBeforeDecryption() throws Exception {
    Path keyPath = temporaryDirectory.resolve("signing.pk8");
    writeOwnerOnlyFile(
        keyPath, new byte[AttestationFilePkcs8Custodian.MAXIMUM_KEY_FILE_BYTE_COUNT + 1]);

    assertEquals(
        "Attestation key file exceeds the maximum size of 1 KiB.",
        signingFailure(keyPath).getMessage());
  }

  @Test
  void fileCustodianDoesNotOverwriteKeysAndClearsPassedPassphrases() throws Exception {
    Path keyPath = temporaryDirectory.resolve("signing.pk8");
    char[] passphrase = "correct horse battery staple".toCharArray();
    AttestationFilePkcs8Custodian.create(keyPath, passphrase);

    assertArrayEquals(new char[passphrase.length], passphrase);
    char[] collisionPassphrase = "another passphrase".toCharArray();
    java.nio.file.FileAlreadyExistsException collision =
        assertThrows(
            java.nio.file.FileAlreadyExistsException.class,
            () -> AttestationFilePkcs8Custodian.create(keyPath, collisionPassphrase));
    assertArrayEquals(new char[collisionPassphrase.length], collisionPassphrase);
    assertEquals(keyPath.toAbsolutePath().normalize().toString(), collision.getFile());

    Assumptions.assumeTrue(
        FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
        "POSIX permissions are unavailable on this filesystem.");
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(keyPath));
  }

  @Test
  void fileCustodianClearsPassedPassphrasesWhenPathValidationFails() {
    Path[] absentPath = new Path[1];
    char[] createPassphrase = "correct horse battery staple".toCharArray();
    assertThrows(
        NullPointerException.class,
        () -> AttestationFilePkcs8Custodian.create(absentPath[0], createPassphrase));
    assertArrayEquals(new char[createPassphrase.length], createPassphrase);

    char[] signingPassphrase = "correct horse battery staple".toCharArray();
    assertThrows(
        NullPointerException.class,
        () -> AttestationFilePkcs8Custodian.sign(absentPath[0], signingPassphrase, new byte[0]));
    assertArrayEquals(new char[signingPassphrase.length], signingPassphrase);
  }

  @Test
  void custodySupportFailsClosedWhenItsCipherDependencyIsUnavailable() {
    var keyPair = AttestationEd25519.generateKeyPair();
    assertEquals(
        "Encrypted PKCS#8 is unavailable in this Java runtime.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    AttestationFilePkcs8Custodian.encrypt(
                        keyPair.getPrivate(),
                        "correct horse battery staple".toCharArray(),
                        ignored -> {
                          throw new NoSuchAlgorithmException("test");
                        }))
            .getMessage());
  }
}
