package dev.erst.fingrind.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Owns the application's fixed cryptographic primitives outside attestation-specific operations.
 */
public final class CryptographicPrimitives {
  private CryptographicPrimitives() {}

  /** Returns the SHA-256 digest of the supplied bytes. */
  public static byte[] sha256(byte[] value) {
    return sha256(value, CryptographicPrimitives::sha256Digest);
  }

  static byte[] sha256(byte[] value, DigestFactory factory) {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(factory, "factory");
    try {
      return factory.create().digest(value);
    } catch (NoSuchAlgorithmException exception) {
      throw unavailableSha256(exception);
    }
  }

  /** Returns the lowercase SHA-256 hex digest of the supplied bytes. */
  public static String sha256Hex(byte[] value) {
    return HexFormat.of().formatHex(sha256(value));
  }

  /** Returns the lowercase SHA-256 hex digest of the supplied UTF-8 text. */
  public static String sha256HexUtf8(String value) {
    Objects.requireNonNull(value, "value");
    return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
  }

  /** Returns the lowercase SHA-256 hex digest of all bytes read from the supplied stream. */
  public static String sha256Hex(InputStream inputStream) throws IOException {
    return sha256Hex(inputStream, CryptographicPrimitives::sha256Digest);
  }

  static String sha256Hex(InputStream inputStream, DigestFactory factory) throws IOException {
    Objects.requireNonNull(inputStream, "inputStream");
    Objects.requireNonNull(factory, "factory");
    MessageDigest digest;
    try {
      digest = factory.create();
    } catch (NoSuchAlgorithmException exception) {
      throw unavailableSha256(exception);
    }
    byte[] buffer = new byte[16 * 1024];
    while (true) {
      int read = inputStream.read(buffer);
      if (read < 0) {
        return HexFormat.of().formatHex(digest.digest());
      }
      digest.update(buffer, 0, read);
    }
  }

  /** Compares two byte arrays without an early-exit equality check. */
  public static boolean constantTimeEquals(byte[] left, byte[] right) {
    Objects.requireNonNull(left, "left");
    Objects.requireNonNull(right, "right");
    return MessageDigest.isEqual(left, right);
  }

  /** Returns a cryptographically secure source for non-deterministic application values. */
  public static RandomGenerator secureRandom() {
    return new SecureRandom();
  }

  /** Returns newly generated cryptographically secure bytes. */
  public static byte[] secureBytes(int byteCount) {
    if (byteCount < 0) {
      throw new IllegalArgumentException("byteCount must not be negative.");
    }
    byte[] bytes = new byte[byteCount];
    secureRandom().nextBytes(bytes);
    return bytes;
  }

  private static MessageDigest sha256Digest() throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-256");
  }

  private static IllegalStateException unavailableSha256(NoSuchAlgorithmException exception) {
    return new IllegalStateException("SHA-256 is unavailable in this Java runtime.", exception);
  }

  /** Supplies one SHA-256 message digest for deterministic provider-failure testing. */
  @FunctionalInterface
  interface DigestFactory {
    /** Creates one SHA-256 message digest. */
    MessageDigest create() throws NoSuchAlgorithmException;
  }
}
