package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Bounded canonical byte reader used only at the untrusted attestation-format boundary. */
final class AttestationByteReader {
  private final byte[] source;
  private final AttestationAuthorizationFailure failure;
  private int offset;

  AttestationByteReader(byte[] source, AttestationAuthorizationFailure failure) {
    this.source = AttestationEncoding.copy(source, "source");
    this.failure = Objects.requireNonNull(failure, "failure");
  }

  int offset() {
    return offset;
  }

  byte[] sourceSlice(int start, int end) {
    return Arrays.copyOfRange(source, start, end);
  }

  boolean hasRemaining(int byteCount) {
    return byteCount >= 0 && byteCount <= source.length - offset;
  }

  byte[] readBytes(int byteCount) {
    if (!hasRemaining(byteCount)) {
      throw failure();
    }
    byte[] value = Arrays.copyOfRange(source, offset, offset + byteCount);
    offset += byteCount;
    return value;
  }

  BigInteger readUnsigned(int byteCount) {
    return new BigInteger(1, readBytes(byteCount));
  }

  BigInteger readSigned(int byteCount) {
    return new BigInteger(readBytes(byteCount));
  }

  UUID readUuid() {
    ByteBuffer buffer = ByteBuffer.wrap(readBytes(16));
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  AttestationHash readHash() {
    return AttestationHash.of(readBytes(AttestationHash.BYTE_LENGTH));
  }

  void requireAscii(String expected) {
    if (!Arrays.equals(
        readBytes(expected.length()), expected.getBytes(StandardCharsets.US_ASCII))) {
      throw failure();
    }
  }

  void requireAtEnd() {
    if (offset != source.length) {
      throw failure();
    }
  }

  AttestationAuthorizationException failure() {
    return new AttestationAuthorizationException(failure);
  }
}
