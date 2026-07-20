package dev.erst.fingrind.core.attestation;

import java.util.Map;
import java.util.Objects;

/** Decodes one catalog-typed preimage field from the bounded raw byte reader. */
final class AttestationFieldValueDecoder {
  private static final Map<AttestationFieldType, Decoder> DECODERS =
      Map.ofEntries(
          Map.entry(
              AttestationFieldType.UNSIGNED_8,
              input ->
                  AttestationNumericFieldValue.unsigned8(
                      input.readUnsigned(Byte.BYTES).intValueExact())),
          Map.entry(
              AttestationFieldType.UNSIGNED_16,
              input ->
                  AttestationNumericFieldValue.unsigned16(
                      input.readUnsigned(Short.BYTES).intValueExact())),
          Map.entry(
              AttestationFieldType.UNSIGNED_32,
              input -> AttestationNumericFieldValue.unsigned32(input.readUnsigned(Integer.BYTES))),
          Map.entry(
              AttestationFieldType.UNSIGNED_64,
              input -> AttestationNumericFieldValue.unsigned64(input.readUnsigned(Long.BYTES))),
          Map.entry(
              AttestationFieldType.SIGNED_64,
              input -> AttestationNumericFieldValue.signed64(input.readSigned(Long.BYTES))),
          Map.entry(
              AttestationFieldType.SIGNED_128,
              input -> AttestationNumericFieldValue.signed128(input.readSigned(16))),
          Map.entry(
              AttestationFieldType.UUID,
              input -> AttestationBinaryFieldValue.uuid(input.readUuid())),
          Map.entry(
              AttestationFieldType.HASH,
              input -> AttestationBinaryFieldValue.hash(input.readHash())),
          Map.entry(
              AttestationFieldType.SPKI,
              input ->
                  AttestationBinaryFieldValue.spki(
                      AttestationCanonicalValueReader.spki(input).bytes())),
          Map.entry(
              AttestationFieldType.BYTES,
              input ->
                  AttestationBinaryFieldValue.bytes(
                      input.readBytes(input.readUnsigned(Integer.BYTES).intValueExact()))),
          Map.entry(
              AttestationFieldType.TOKEN,
              input ->
                  AttestationTextFieldValue.token(AttestationCanonicalValueReader.token(input))),
          Map.entry(
              AttestationFieldType.TEXT,
              input -> AttestationTextFieldValue.text(AttestationCanonicalValueReader.text(input))),
          Map.entry(
              AttestationFieldType.CURRENCY,
              input ->
                  AttestationTextFieldValue.currency(
                      AttestationCanonicalValueReader.currency(input))),
          Map.entry(
              AttestationFieldType.DATE,
              input -> AttestationTextFieldValue.date(AttestationCanonicalValueReader.date(input))),
          Map.entry(
              AttestationFieldType.INSTANT,
              input ->
                  AttestationTextFieldValue.instant(
                      AttestationCanonicalValueReader.instant(input))),
          Map.entry(AttestationFieldType.MONEY, AttestationFieldValueDecoder::money),
          Map.entry(AttestationFieldType.SCALED, AttestationFieldValueDecoder::scaled),
          Map.entry(AttestationFieldType.BOOLEAN, AttestationFieldValueDecoder::booleanValue),
          Map.entry(
              AttestationFieldType.MUTATION,
              input ->
                  AttestationNumericFieldValue.mutation(
                      input.readUnsigned(Byte.BYTES).intValueExact())));

  private AttestationFieldValueDecoder() {}

  static AttestationFieldValue decode(AttestationByteReader input, AttestationFieldType type) {
    return Objects.requireNonNull(
            DECODERS.get(Objects.requireNonNull(type, "type")), "missing field type decoder")
        .decode(input);
  }

  private static AttestationFieldValue money(AttestationByteReader input) {
    return AttestationNumericFieldValue.money(
        AttestationCanonicalValueReader.currency(input), sign(input), input.readUnsigned(16));
  }

  private static AttestationFieldValue scaled(AttestationByteReader input) {
    int scale = input.readUnsigned(Byte.BYTES).intValueExact();
    return AttestationNumericFieldValue.scaled(scale, sign(input), input.readUnsigned(16));
  }

  private static AttestationFieldValue booleanValue(AttestationByteReader input) {
    int encoded = input.readUnsigned(Byte.BYTES).intValueExact();
    if (encoded > 1) {
      throw input.failure();
    }
    return AttestationNumericFieldValue.booleanValue(encoded == 1);
  }

  private static boolean sign(AttestationByteReader input) {
    int sign = input.readUnsigned(Byte.BYTES).intValueExact();
    if (sign > 1) {
      throw input.failure();
    }
    return sign == 1;
  }

  /** Decodes one known field type from the shared bounded source reader. */
  @FunctionalInterface
  private interface Decoder {
    /** Decodes the next canonical value for this field type. */
    AttestationFieldValue decode(AttestationByteReader input);
  }
}
