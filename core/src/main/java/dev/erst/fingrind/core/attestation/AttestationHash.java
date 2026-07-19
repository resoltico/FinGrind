package dev.erst.fingrind.core.attestation;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable SHA-256 value used by attestation payloads, heads, and artifact digests. */
final class AttestationHash implements Comparable<AttestationHash> {
  static final int BYTE_LENGTH = 32;
  private final byte[] bytes;

  private AttestationHash(byte[] bytes) {
    this.bytes = bytes;
  }

  static AttestationHash of(byte[] value) {
    byte[] copiedValue = AttestationEncoding.copy(value, "value");
    if (copiedValue.length != BYTE_LENGTH) {
      throw new IllegalArgumentException("Attestation hash must contain exactly 32 bytes.");
    }
    return new AttestationHash(copiedValue);
  }

  static AttestationHash sha256(byte[] value) {
    return AttestationEd25519.sha256(value);
  }

  byte[] bytes() {
    return bytes.clone();
  }

  String hex() {
    return HexFormat.of().formatHex(bytes);
  }

  @Override
  public int compareTo(AttestationHash other) {
    Objects.requireNonNull(other, "other");
    for (int index = 0; index < BYTE_LENGTH; index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(bytes[index]), Byte.toUnsignedInt(other.bytes[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AttestationHash hash && Arrays.equals(bytes, hash.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return hex();
  }
}
