package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Decodes one catalog-typed immutable-preimage field without consulting mutable book state. */
final class AttestationPreimageValueReader {
  private AttestationPreimageValueReader() {}

  static String token(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    byte[] encoded = value(fact, fieldIndex, failure);
    int length = Byte.toUnsignedInt(encoded[0]);
    return new String(encoded, 1, length, StandardCharsets.US_ASCII);
  }

  static UUID uuid(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    byte[] encoded = value(fact, fieldIndex, failure);
    ByteBuffer buffer = ByteBuffer.wrap(encoded);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  static AttestationHash hash(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    byte[] encoded = value(fact, fieldIndex, failure);
    return AttestationHash.of(encoded);
  }

  static @Nullable AttestationHash optionalHash(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return AttestationPreimageFields.requireField(fact, fieldIndex).isPresent()
        ? hash(fact, fieldIndex, failure)
        : null;
  }

  static AttestationSpki spki(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    byte[] encoded = value(fact, fieldIndex, failure);
    return AttestationSpki.of(Arrays.copyOfRange(encoded, Short.BYTES, encoded.length));
  }

  static String text(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    byte[] encoded = value(fact, fieldIndex, failure);
    return new String(
        encoded, Integer.BYTES, encoded.length - Integer.BYTES, StandardCharsets.UTF_8);
  }

  static @Nullable String optionalText(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return AttestationPreimageFields.requireField(fact, fieldIndex).isPresent()
        ? text(fact, fieldIndex, failure)
        : null;
  }

  static boolean booleanValue(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    byte[] encoded = value(fact, fieldIndex, failure);
    return encoded[0] == 1;
  }

  static LocalDate date(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return LocalDate.parse(new String(value(fact, fieldIndex, failure), StandardCharsets.US_ASCII));
  }

  static BigInteger unsigned64(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return new BigInteger(1, value(fact, fieldIndex, failure));
  }

  static int mutation(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return unsigned(fact, fieldIndex, Byte.BYTES, failure);
  }

  static int unsigned16(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return unsigned(fact, fieldIndex, Short.BYTES, failure);
  }

  private static int unsigned(
      AttestationPreimage.Fact fact,
      int fieldIndex,
      int byteCount,
      AttestationAuthorizationFailure failure) {
    byte[] encoded = value(fact, fieldIndex, failure);
    return byteCount == Byte.BYTES
        ? Byte.toUnsignedInt(encoded[0])
        : Short.toUnsignedInt(ByteBuffer.wrap(encoded).getShort());
  }

  private static byte[] value(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return AttestationPreimageFields.requireValue(fact, fieldIndex, failure).encoded();
  }
}
