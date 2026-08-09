package dev.erst.fingrind.core;

import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical 128-bit identifier for one publication transaction journal record. */
public record PublicationTransactionId(String value) {
  private static final int ENTROPY_BYTES = 16;
  private static final Pattern CANONICAL_VALUE = Pattern.compile("[0-9a-f]{32}");

  /** Rejects every representation except exactly 32 lowercase hexadecimal characters. */
  public PublicationTransactionId {
    Objects.requireNonNull(value, "value");
    if (!CANONICAL_VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Publication transaction id must be exactly 32 lowercase hexadecimal characters.");
    }
  }

  /** Creates one transaction identifier from fresh cryptographically secure 128-bit entropy. */
  public static PublicationTransactionId fresh() {
    return fromEntropy(CryptographicPrimitives.secureBytes(ENTROPY_BYTES));
  }

  /** Encodes exactly 128 bits of supplied entropy for deterministic package-level tests. */
  static PublicationTransactionId fromEntropy(byte[] entropy) {
    byte[] checkedEntropy = Objects.requireNonNull(entropy, "entropy");
    if (checkedEntropy.length != ENTROPY_BYTES) {
      throw new IllegalArgumentException(
          "Publication transaction entropy must contain exactly 16 bytes.");
    }
    return new PublicationTransactionId(HexFormat.of().formatHex(checkedEntropy));
  }
}
