package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import org.junit.jupiter.api.Test;

/** Exercises the JDK-only Ed25519 primitive and canonical public-key validation. */
class AttestationEd25519Test {
  @Test
  void signsAndVerifiesOnlyTheExactPayloadWithAnEd25519Key() {
    var pair = AttestationEd25519.generateKeyPair();
    var wrongPair = AttestationEd25519.generateKeyPair();
    byte[] payload = new byte[] {1, 2, 3};
    byte[] signature = AttestationEd25519.sign(pair.getPrivate(), payload);

    assertTrue(AttestationEd25519.verifies(pair.getPublic(), payload, signature));
    assertTrue(AttestationEd25519.isEd25519(pair.getPublic()));
    assertFalse(AttestationEd25519.verifies(wrongPair.getPublic(), payload, signature));
    assertFalse(AttestationEd25519.verifies(pair.getPublic(), new byte[] {1, 2, 4}, signature));
    byte[] tamperedSignature = signature.clone();
    tamperedSignature[0] ^= 1;
    assertFalse(AttestationEd25519.verifies(pair.getPublic(), payload, tamperedSignature));
    assertFalse(AttestationEd25519.verifies(pair.getPublic(), payload, new byte[63]));
    assertTrue(
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
    var pair = AttestationEd25519.generateKeyPair();
    assertFalse(
        AttestationEd25519.verifies(
            pair.getPublic(),
            new byte[0],
            AttestationEd25519.sign(pair.getPrivate(), new byte[0]),
            ignored -> {
              throw new NoSuchAlgorithmException("test");
            }));
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
  void rejectsUnencodableKeysAndHashesOnlyTheCanonicalSpkiItValidated() {
    var pair = AttestationEd25519.generateKeyPair();
    assertFalse(AttestationEd25519.isEd25519(new UnencodablePublicKey()));
    assertFalse(
        AttestationEd25519.verifies(
            new UnencodablePublicKey(),
            new byte[0],
            AttestationEd25519.sign(pair.getPrivate(), new byte[0])));
    assertThrows(
        IllegalArgumentException.class, () -> AttestationEd25519.keyId(new UnencodablePublicKey()));

    PublicKey changingKey =
        new ChangingEncodedPublicKey(pair.getPublic().getEncoded(), new byte[] {1, 2, 3});
    assertEquals(
        AttestationHash.sha256(pair.getPublic().getEncoded()),
        AttestationEd25519.keyId(changingKey));

    byte[] payload = new byte[] {1, 2, 3};
    byte[] signature = AttestationEd25519.sign(pair.getPrivate(), payload);
    assertTrue(
        AttestationEd25519.verifies(
            new ChangingEncodedPublicKey(pair.getPublic().getEncoded(), new byte[] {1, 2, 3}),
            payload,
            signature));
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

  /** Deliberately unencodable public key used to prove boolean validation fails closed. */
  private static final class UnencodablePublicKey implements PublicKey {
    private static final long serialVersionUID = 1L;

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
      return nullOf();
    }
  }

  /** Deliberately changes its encoding to prove the key ID uses the one validated SPKI. */
  private static final class ChangingEncodedPublicKey implements PublicKey {
    private static final long serialVersionUID = 1L;

    private final byte[] firstEncoding;
    private final byte[] laterEncoding;
    private boolean firstRead = true;

    private ChangingEncodedPublicKey(byte[] firstEncoding, byte[] laterEncoding) {
      this.firstEncoding = firstEncoding.clone();
      this.laterEncoding = laterEncoding.clone();
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
      if (firstRead) {
        firstRead = false;
        return firstEncoding.clone();
      }
      return laterEncoding.clone();
    }
  }
}
