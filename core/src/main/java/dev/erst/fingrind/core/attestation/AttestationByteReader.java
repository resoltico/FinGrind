package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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

  String readToken() {
    int start = offset;
    int length = readUnsigned(Byte.BYTES).intValueExact();
    String value = new String(readBytes(length), StandardCharsets.US_ASCII);
    requireCanonical(AttestationTextFieldValue.token(value).encoded(), start);
    return value;
  }

  String readText() {
    int start = offset;
    int length = readUnsigned(Integer.BYTES).intValueExact();
    String value = new String(readBytes(length), StandardCharsets.UTF_8);
    requireCanonical(AttestationTextFieldValue.text(value).encoded(), start);
    return value;
  }

  String readCurrency() {
    int start = offset;
    String value = new String(readBytes(3), StandardCharsets.US_ASCII);
    requireCanonical(AttestationTextFieldValue.currency(value).encoded(), start);
    return value;
  }

  LocalDate readDate() {
    int start = offset;
    LocalDate value = LocalDate.parse(new String(readBytes(10), StandardCharsets.US_ASCII));
    requireCanonical(AttestationTextFieldValue.date(value).encoded(), start);
    return value;
  }

  Instant readInstant() {
    int start = offset;
    Instant value = Instant.parse(new String(readBytes(24), StandardCharsets.US_ASCII));
    requireCanonical(AttestationTextFieldValue.instant(value).encoded(), start);
    return value;
  }

  AttestationSpki readSpki() {
    int start = offset;
    int length = readUnsigned(Short.BYTES).intValueExact();
    byte[] value = readBytes(length);
    requireCanonical(AttestationBinaryFieldValue.spki(value).encoded(), start);
    return AttestationSpki.of(value);
  }

  AttestationFieldValue readFieldValue(AttestationFieldType type) {
    return switch (type) {
      case UNSIGNED_8 ->
          AttestationNumericFieldValue.unsigned8(readUnsigned(Byte.BYTES).intValueExact());
      case UNSIGNED_16 ->
          AttestationNumericFieldValue.unsigned16(readUnsigned(Short.BYTES).intValueExact());
      case UNSIGNED_32 -> AttestationNumericFieldValue.unsigned32(readUnsigned(Integer.BYTES));
      case UNSIGNED_64 -> AttestationNumericFieldValue.unsigned64(readUnsigned(Long.BYTES));
      case SIGNED_64 -> AttestationNumericFieldValue.signed64(readSigned(Long.BYTES));
      case SIGNED_128 -> AttestationNumericFieldValue.signed128(readSigned(16));
      case UUID -> AttestationBinaryFieldValue.uuid(readUuid());
      case HASH -> AttestationBinaryFieldValue.hash(readHash());
      case SPKI -> AttestationBinaryFieldValue.spki(readSpki().bytes());
      case BYTES -> AttestationBinaryFieldValue.bytes(readSizedBytes());
      case TOKEN -> AttestationTextFieldValue.token(readToken());
      case TEXT -> AttestationTextFieldValue.text(readText());
      case CURRENCY -> AttestationTextFieldValue.currency(readCurrency());
      case DATE -> AttestationTextFieldValue.date(readDate());
      case INSTANT -> AttestationTextFieldValue.instant(readInstant());
      case MONEY -> readMoney();
      case SCALED -> readScaled();
      case BOOLEAN -> readBoolean();
      case MUTATION ->
          AttestationNumericFieldValue.mutation(readUnsigned(Byte.BYTES).intValueExact());
    };
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

  private byte[] readSizedBytes() {
    int length = readUnsigned(Integer.BYTES).intValueExact();
    return readBytes(length);
  }

  private AttestationFieldValue readMoney() {
    String currency = readCurrency();
    int sign = readUnsigned(Byte.BYTES).intValueExact();
    if (sign > 1) {
      throw failure();
    }
    return AttestationNumericFieldValue.money(currency, sign == 1, readUnsigned(16));
  }

  private AttestationFieldValue readScaled() {
    int scale = readUnsigned(Byte.BYTES).intValueExact();
    int sign = readUnsigned(Byte.BYTES).intValueExact();
    if (sign > 1) {
      throw failure();
    }
    return AttestationNumericFieldValue.scaled(scale, sign == 1, readUnsigned(16));
  }

  private AttestationFieldValue readBoolean() {
    int encoded = readUnsigned(Byte.BYTES).intValueExact();
    if (encoded > 1) {
      throw failure();
    }
    return AttestationNumericFieldValue.booleanValue(encoded == 1);
  }

  private void requireCanonical(byte[] canonical, int start) {
    if (!Arrays.equals(canonical, Arrays.copyOfRange(source, start, offset))) {
      throw failure();
    }
  }
}
