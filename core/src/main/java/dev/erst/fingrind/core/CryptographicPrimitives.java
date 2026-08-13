package dev.erst.fingrind.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.random.RandomGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * Owns the application's fixed cryptographic primitives outside attestation-specific operations.
 */
public final class CryptographicPrimitives {
  private static final String HMAC_SHA_256 = "HmacSHA256";

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

  /** Returns the SHA-256 digest of all bytes read from the supplied stream. */
  public static byte[] sha256(InputStream inputStream) throws IOException {
    return sha256(inputStream, CryptographicPrimitives::sha256Digest);
  }

  /** Returns the lowercase SHA-256 hex digest of all bytes read from the supplied stream. */
  public static String sha256Hex(InputStream inputStream) throws IOException {
    return sha256Hex(inputStream, CryptographicPrimitives::sha256Digest);
  }

  static String sha256Hex(InputStream inputStream, DigestFactory factory) throws IOException {
    return HexFormat.of().formatHex(sha256(inputStream, factory));
  }

  static byte[] sha256(InputStream inputStream, DigestFactory factory) throws IOException {
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
        return digest.digest();
      }
      if (read == 0) {
        throw new IOException("Cryptographic digest input did not make read progress.");
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

  /**
   * Returns the HMAC-SHA-256 authentication tag for the supplied runtime key and canonical bytes.
   */
  public static byte[] hmacSha256(byte[] key, byte[] value) {
    return hmacSha256(key, value, HMAC_SHA_256);
  }

  static byte[] hmacSha256(byte[] key, byte[] value, String algorithm) {
    byte[] checkedKey = Objects.requireNonNull(key, "key");
    return hmacSha256(new RuntimeHmacKey(algorithm, checkedKey), value, algorithm);
  }

  static byte[] hmacSha256(SecretKey key, byte[] value, String algorithm) {
    SecretKey checkedKey = Objects.requireNonNull(key, "key");
    byte[] checkedValue = Objects.requireNonNull(value, "value");
    String checkedAlgorithm = Objects.requireNonNull(algorithm, "algorithm");
    try {
      Mac mac = Mac.getInstance(checkedAlgorithm);
      mac.init(checkedKey);
      return mac.doFinal(checkedValue);
    } catch (java.security.InvalidKeyException exception) {
      throw new IllegalArgumentException("HMAC-SHA-256 key is not usable.", exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(
          "HMAC-SHA-256 is unavailable in this Java runtime.", exception);
    }
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

  static MessageDigest sha256Digest() throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-256");
  }

  static IllegalStateException unavailableSha256(NoSuchAlgorithmException exception) {
    return new IllegalStateException("SHA-256 is unavailable in this Java runtime.", exception);
  }

  /** Supplies one SHA-256 message digest for deterministic provider-failure testing. */
  @FunctionalInterface
  interface DigestFactory {
    /** Creates one SHA-256 message digest. */
    MessageDigest create() throws NoSuchAlgorithmException;
  }
}

/** Retains one caller-supplied HMAC key without storing a shared application secret. */
final class RuntimeHmacKey implements SecretKey {
  private static final long serialVersionUID = 1L;
  private final String algorithm;
  private final byte[] bytes;

  RuntimeHmacKey(String algorithm, byte[] bytes) {
    this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
    this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
  }

  @Override
  public String getAlgorithm() {
    return algorithm;
  }

  @Override
  public String getFormat() {
    return "RAW";
  }

  @Override
  public byte[] getEncoded() {
    return bytes.clone();
  }
}

/** Owns SHA-256 channel consumption for the application's non-attestation crypto seam. */
final class CryptographicChannelDigest {
  private static final int BUFFER_BYTES = 16 * 1024;

  private CryptographicChannelDigest() {}

  static String sha256Hex(ReadableByteChannel channel) throws IOException {
    return sha256Hex(channel, CryptographicPrimitives::sha256Digest);
  }

  static String sha256Hex(
      ReadableByteChannel channel, CryptographicPrimitives.DigestFactory factory)
      throws IOException {
    ReadableByteChannel checkedChannel = Objects.requireNonNull(channel, "channel");
    CryptographicPrimitives.DigestFactory checkedFactory =
        Objects.requireNonNull(factory, "factory");
    MessageDigest digest;
    try {
      digest = checkedFactory.create();
    } catch (NoSuchAlgorithmException exception) {
      throw CryptographicPrimitives.unavailableSha256(exception);
    }
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_BYTES);
    while (true) {
      int read = checkedChannel.read(buffer);
      if (read < 0) {
        return HexFormat.of().formatHex(digest.digest());
      }
      if (read == 0) {
        throw new IOException("Cryptographic digest input did not make read progress.");
      }
      buffer.flip();
      digest.update(buffer);
      buffer.clear();
    }
  }
}
