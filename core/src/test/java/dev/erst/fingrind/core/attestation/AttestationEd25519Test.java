package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the JDK-only Ed25519 and encrypted-file custody seam. */
class AttestationEd25519Test {
  @TempDir Path temporaryDirectory;

  @Test
  void signsAndVerifiesOnlyTheExactPayloadWithAnEd25519Key() {
    var pair = AttestationEd25519.generateKeyPair();
    var wrongPair = AttestationEd25519.generateKeyPair();
    byte[] payload = new byte[] {1, 2, 3};
    byte[] signature = AttestationEd25519.sign(pair.getPrivate(), payload);

    assertTrue(AttestationEd25519.verifies(pair.getPublic(), payload, signature));
    assertFalse(AttestationEd25519.verifies(wrongPair.getPublic(), payload, signature));
    assertFalse(AttestationEd25519.verifies(pair.getPublic(), new byte[] {1, 2, 4}, signature));
    assertFalse(AttestationEd25519.verifies(pair.getPublic(), payload, new byte[63]));
    assertFalse(
        AttestationEd25519.verifies(
            new EncodedOnlyPublicKey(pair.getPublic().getEncoded()), payload, signature));
    assertArrayEquals(
        pair.getPublic().getEncoded(),
        AttestationEd25519.publicKey(pair.getPublic().getEncoded()).getEncoded());
    assertEquals(
        AttestationHash.sha256(pair.getPublic().getEncoded()),
        AttestationEd25519.keyId(pair.getPublic()));
    assertEquals("ed25519", AttestationAlgorithm.ED25519.id());
  }

  @Test
  void rejectsNonEd25519KeysAndExposesProviderFailuresPrecisely() throws Exception {
    var rsaPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    PublicKey rsa = rsaPair.getPublic();
    assertFalse(AttestationEd25519.isEd25519(rsa));
    assertFalse(AttestationEd25519.verifies(rsa, new byte[0], new byte[64]));
    assertThrows(IllegalArgumentException.class, () -> AttestationEd25519.keyId(rsa));
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationEd25519.sign(rsaPair.getPrivate(), new byte[0]));
    assertThrows(IllegalArgumentException.class, () -> AttestationEd25519.publicKey(new byte[0]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationEd25519.requireCanonicalSpki(
                new byte[] {1}, new EncodedOnlyPublicKey(new byte[] {2})));
    assertEquals(
        "Ed25519 is unavailable in this Java runtime.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    AttestationEd25519.generateKeyPair(
                        ignored -> {
                          throw new NoSuchAlgorithmException("test");
                        }))
            .getMessage());
    assertEquals(
        "SHA-256 is unavailable in this Java runtime.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    AttestationEd25519.sha256(
                        new byte[0],
                        ignored -> {
                          throw new NoSuchAlgorithmException("test");
                        }))
            .getMessage());
  }

  @Test
  void acceptsOnlyTheShippedCustodian() {
    assertEquals(AttestationCustodian.FILE_PKCS8, AttestationCustodian.require("file-pkcs8"));
    AttestationCustodianNotSupportedException rejection =
        assertThrows(
            AttestationCustodianNotSupportedException.class,
            () -> AttestationCustodian.require("pkcs11"));
    assertEquals("custodian-not-supported: pkcs11", rejection.getMessage());
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
    wrongVersion[5] = 2;
    Files.write(keyPath, wrongVersion);
    assertEquals(
        "Attestation key file has an unsupported format.", signingFailure(keyPath).getMessage());

    byte[] wrongWorkFactor = persisted.clone();
    ByteBuffer.wrap(wrongWorkFactor).putInt(6, 1);
    Files.write(keyPath, wrongWorkFactor);
    assertEquals(
        "Attestation key file has an unsupported work factor.",
        signingFailure(keyPath).getMessage());

    Files.write(keyPath, Arrays.copyOf(persisted, 54));
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
  void fileCustodianDoesNotOverwriteKeysAndClearsPassedPassphrases() throws Exception {
    Path keyPath = temporaryDirectory.resolve("signing.pk8");
    char[] passphrase = "correct horse battery staple".toCharArray();
    AttestationFilePkcs8Custodian.create(keyPath, passphrase);

    assertArrayEquals(new char[passphrase.length], passphrase);
    assertThrows(
        java.nio.file.FileAlreadyExistsException.class,
        () -> AttestationFilePkcs8Custodian.create(keyPath, "another passphrase".toCharArray()));

    Assumptions.assumeTrue(
        FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
        "POSIX permissions are unavailable on this filesystem.");
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(keyPath));
  }

  @Test
  void custodySupportFailsClosedWhenItsDependenciesCannotProvideSafeOutput() throws Exception {
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

    assertEquals(
        "Failed to write the complete encrypted attestation key file.",
        assertThrows(
                java.io.IOException.class,
                () ->
                    AttestationFilePkcs8Custodian.writeFully(
                        ByteBuffer.wrap(new byte[] {1}), ignored -> 0))
            .getMessage());

    Path fallbackPath = temporaryDirectory.resolve("fallback.pk8");
    AttestationFilePkcs8Custodian.createOwnerOnlyFile(
        fallbackPath,
        ignored -> {
          throw new UnsupportedOperationException("test");
        });
    assertTrue(Files.isRegularFile(fallbackPath));
  }

  private IllegalArgumentException signingFailure(Path keyPath) {
    return assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationFilePkcs8Custodian.sign(
                keyPath, "correct horse battery staple".toCharArray(), new byte[] {4, 5, 6}));
  }

  /** Deliberately non-JCA public-key implementation carrying otherwise valid Ed25519 bytes. */
  private static final class EncodedOnlyPublicKey implements PublicKey {
    private static final long serialVersionUID = 1L;

    private final byte[] encoded;

    private EncodedOnlyPublicKey(byte[] encoded) {
      this.encoded = encoded.clone();
    }

    @Override
    public String getAlgorithm() {
      return "Ed25519";
    }

    @Override
    public String getFormat() {
      return "X.509";
    }

    @Override
    public byte[] getEncoded() {
      return encoded.clone();
    }
  }
}
