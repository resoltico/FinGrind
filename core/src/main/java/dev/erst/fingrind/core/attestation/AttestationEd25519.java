package dev.erst.fingrind.core.attestation;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

/** Ed25519 and SHA-256 JCA operations within the version-one attestation crypto seam. */
final class AttestationEd25519 {
  private static final AttestationAlgorithm ALGORITHM = AttestationAlgorithm.ED25519;

  private AttestationEd25519() {}

  static KeyPair generateKeyPair() {
    return generateKeyPair(KeyPairGenerator::getInstance);
  }

  static KeyPair generateKeyPair(KeyPairGeneratorFactory factory) {
    try {
      return factory.create(ALGORITHM.jcaName()).generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw unavailable(exception);
    }
  }

  static byte[] sign(PrivateKey privateKey, byte[] payload) {
    Objects.requireNonNull(privateKey, "privateKey");
    Objects.requireNonNull(payload, "payload");
    try {
      Signature signer = Signature.getInstance(ALGORITHM.jcaName());
      signer.initSign(privateKey);
      signer.update(payload);
      return signer.sign();
    } catch (GeneralSecurityException exception) {
      throw new IllegalArgumentException(
          "Attestation signing key must be an Ed25519 private key.", exception);
    }
  }

  static boolean verifies(PublicKey publicKey, byte[] payload, byte[] signature) {
    return verifies(publicKey, payload, signature, Signature::getInstance);
  }

  static boolean verifies(
      PublicKey publicKey, byte[] payload, byte[] signature, SignatureFactory signatureFactory) {
    Objects.requireNonNull(publicKey, "publicKey");
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(signature, "signature");
    Objects.requireNonNull(signatureFactory, "signatureFactory");
    PublicKey canonicalPublicKey;
    try {
      canonicalPublicKey = canonicalEd25519PublicKey(publicKey);
    } catch (IllegalArgumentException exception) {
      return false;
    }
    if (signature.length != ALGORITHM.signatureByteLength()) {
      return false;
    }
    try {
      Signature verifier = signatureFactory.create(ALGORITHM.jcaName());
      verifier.initVerify(canonicalPublicKey);
      verifier.update(payload);
      return verifier.verify(signature);
    } catch (GeneralSecurityException exception) {
      return false;
    }
  }

  static PublicKey publicKey(byte[] encodedSpki) {
    Objects.requireNonNull(encodedSpki, "encodedSpki");
    try {
      PublicKey publicKey =
          KeyFactory.getInstance(ALGORITHM.jcaName())
              .generatePublic(new X509EncodedKeySpec(encodedSpki));
      requireCanonicalSpki(encodedSpki, publicKey);
      return publicKey;
    } catch (GeneralSecurityException exception) {
      throw new IllegalArgumentException(
          "Attestation public key must be an Ed25519 DER SPKI.", exception);
    }
  }

  static boolean isEd25519(PublicKey publicKey) {
    try {
      canonicalEd25519PublicKey(publicKey);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  static AttestationHash keyId(PublicKey publicKey) {
    try {
      return AttestationHash.sha256(canonicalEd25519PublicKey(publicKey).getEncoded());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Attestation public key must be Ed25519.", exception);
    }
  }

  static AttestationHash sha256(byte[] value) {
    return sha256(value, MessageDigest::getInstance);
  }

  static AttestationHash sha256(byte[] value, DigestFactory factory) {
    Objects.requireNonNull(value, "value");
    try {
      return AttestationHash.of(factory.create("SHA-256").digest(value));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("SHA-256 is unavailable in this Java runtime.", exception);
    }
  }

  private static IllegalStateException unavailable(GeneralSecurityException exception) {
    return new IllegalStateException("Ed25519 is unavailable in this Java runtime.", exception);
  }

  static void requireCanonicalSpki(byte[] encodedSpki, PublicKey publicKey) {
    if (!java.util.Arrays.equals(encodedSpki, publicKey.getEncoded())) {
      throw new IllegalArgumentException("Attestation public key must use canonical DER SPKI.");
    }
  }

  private static PublicKey canonicalEd25519PublicKey(PublicKey publicKey) {
    byte[] encodedSpki = Objects.requireNonNull(publicKey, "publicKey").getEncoded();
    if (encodedSpki == null) {
      throw new IllegalArgumentException("Attestation public key must be an Ed25519 DER SPKI.");
    }
    return publicKey(encodedSpki.clone());
  }

  /** Produces a JCA digest for deterministic provider-failure testing. */
  @FunctionalInterface
  interface DigestFactory {
    /** Creates the digest identified by its JCA algorithm name. */
    MessageDigest create(String algorithm) throws GeneralSecurityException;
  }

  /** Produces an Ed25519 JCA key-pair generator for deterministic provider-failure testing. */
  @FunctionalInterface
  interface KeyPairGeneratorFactory {
    /** Creates the key-pair generator identified by its JCA algorithm name. */
    KeyPairGenerator create(String algorithm) throws GeneralSecurityException;
  }

  /** Produces an Ed25519 JCA signature engine for deterministic verification-failure testing. */
  @FunctionalInterface
  interface SignatureFactory {
    /** Creates the signature engine identified by its JCA algorithm name. */
    Signature create(String algorithm) throws GeneralSecurityException;
  }
}
