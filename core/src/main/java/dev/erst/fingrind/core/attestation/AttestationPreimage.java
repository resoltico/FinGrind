package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Canonically ordered immutable semantic facts committed by one attested operation. */
final class AttestationPreimage {
  private static final int MAX_RECORD_COUNT = 1_000_000;
  private static final int MAX_ENCODED_BYTE_COUNT = 16 * 1024 * 1024;
  private final List<Fact> records;
  private final int encodedByteCount;

  private AttestationPreimage(List<Fact> records, int encodedByteCount) {
    this.records = records;
    this.encodedByteCount = encodedByteCount;
  }

  static AttestationPreimage of(List<Fact> records) {
    Objects.requireNonNull(records, "records");
    if (records.size() > MAX_RECORD_COUNT) {
      throw new IllegalArgumentException(
          "Attestation preimage may contain at most 1000000 records.");
    }
    List<Fact> canonicalRecords = new ArrayList<>(records.size());
    long encodedByteCount = Integer.BYTES;
    for (Fact fact : records) {
      Fact requiredFact = Objects.requireNonNull(fact, "records must not contain null");
      encodedByteCount = Math.addExact(encodedByteCount, requiredFact.encodedByteCount());
      if (encodedByteCount > MAX_ENCODED_BYTE_COUNT) {
        throw new IllegalArgumentException("Attestation preimage must be at most 16777216 bytes.");
      }
      canonicalRecords.add(requiredFact);
    }
    canonicalRecords.sort(AttestationPreimage::compareRecords);
    for (int index = 1; index < canonicalRecords.size(); index++) {
      Fact previous = canonicalRecords.get(index - 1);
      Fact current = canonicalRecords.get(index);
      if (previous.recordTypeTag == current.recordTypeTag
          && Arrays.equals(previous.encodedSortKey, current.encodedSortKey)) {
        throw new IllegalArgumentException(
            "Attestation preimage must not contain duplicate complete sort keys.");
      }
    }
    return new AttestationPreimage(List.copyOf(canonicalRecords), (int) encodedByteCount);
  }

  /** Decodes and rechecks one complete canonical preimage without consulting mutable book state. */
  static AttestationPreimage decode(byte[] encoded, AttestationAuthorizationFailure failure) {
    try {
      AttestationByteReader input = new AttestationByteReader(encoded, failure);
      int recordCount = input.readUnsigned(Integer.BYTES).intValueExact();
      if (recordCount > MAX_RECORD_COUNT) {
        throw input.failure();
      }
      List<Fact> decodedRecords = new ArrayList<>(recordCount);
      for (int recordIndex = 0; recordIndex < recordCount; recordIndex++) {
        int recordTypeTag = input.readUnsigned(Short.BYTES).intValueExact();
        AttestationRecordSchema schema = AttestationPreimageCatalog.require(recordTypeTag);
        int fieldCount = input.readUnsigned(Short.BYTES).intValueExact();
        if (fieldCount != schema.fieldCount()) {
          throw input.failure();
        }
        List<AttestationField> fields = new ArrayList<>(fieldCount);
        for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
          int presence = input.readUnsigned(Byte.BYTES).intValueExact();
          if (presence == 0) {
            fields.add(AttestationField.absent());
          } else if (presence == 1) {
            fields.add(
                AttestationField.present(
                    input.readFieldValue(schema.fieldSchema(fieldIndex).type())));
          } else {
            throw input.failure();
          }
        }
        decodedRecords.add(new Fact(recordTypeTag, fields));
      }
      input.requireAtEnd();
      AttestationPreimage decoded = of(decodedRecords);
      if (!Arrays.equals(decoded.encoded(), encoded)) {
        throw input.failure();
      }
      return decoded;
    } catch (AttestationAuthorizationException exception) {
      throw exception;
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new AttestationAuthorizationException(failure);
    }
  }

  List<Fact> records() {
    return records;
  }

  byte[] encoded() {
    ByteArrayOutputStream output = new ByteArrayOutputStream(encodedByteCount);
    AttestationUnsignedEncoding.appendUnsigned(
        output, BigInteger.valueOf(records.size()), Integer.BYTES, "recordCount");
    records.forEach(record -> record.appendTo(output));
    return output.toByteArray();
  }

  private static int compareRecords(Fact left, Fact right) {
    int tagComparison = Integer.compare(left.recordTypeTag, right.recordTypeTag);
    return tagComparison != 0
        ? tagComparison
        : compareUnsignedBytes(left.encodedSortKey, right.encodedSortKey);
  }

  private static int compareUnsignedBytes(byte[] left, byte[] right) {
    int commonLength = Math.min(left.length, right.length);
    for (int index = 0; index < commonLength; index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  /** One catalog-defined record with a complete ordered field sequence and per-type sort key. */
  static final class Fact {
    private final int recordTypeTag;
    private final List<AttestationField> fields;
    private final byte[] encodedSortKey;
    private final int encodedByteCount;

    Fact(int recordTypeTag, List<AttestationField> fields) {
      if (recordTypeTag < 0
          || recordTypeTag >= AttestationUnsignedEncoding.uint16Limit().intValueExact()) {
        throw new IllegalArgumentException("recordTypeTag must fit an unsigned 2-byte integer.");
      }
      AttestationRecordSchema schema = AttestationPreimageCatalog.require(recordTypeTag);
      Objects.requireNonNull(fields, "fields");
      List<AttestationField> copiedFields = new ArrayList<>(fields.size());
      for (AttestationField field : fields) {
        copiedFields.add(Objects.requireNonNull(field, "fields must not contain null"));
      }
      this.fields = List.copyOf(copiedFields);
      schema.requireValidFields(this.fields);
      this.recordTypeTag = schema.recordTypeTag();
      this.encodedSortKey = schema.encodedSortKey(this.fields);
      long encodedByteCount = Short.BYTES + Short.BYTES;
      for (AttestationField field : this.fields) {
        encodedByteCount = Math.addExact(encodedByteCount, field.encodedByteCount());
      }
      this.encodedByteCount = Math.toIntExact(encodedByteCount);
    }

    int recordTypeTag() {
      return recordTypeTag;
    }

    List<AttestationField> fields() {
      return fields;
    }

    byte[] encodedSortKey() {
      return encodedSortKey.clone();
    }

    private int encodedByteCount() {
      return encodedByteCount;
    }

    private void appendTo(ByteArrayOutputStream output) {
      AttestationUnsignedEncoding.appendUnsigned(
          output, BigInteger.valueOf(recordTypeTag), Short.BYTES, "recordTypeTag");
      AttestationUnsignedEncoding.appendUnsigned(
          output, BigInteger.valueOf(fields.size()), Short.BYTES, "fieldCount");
      fields.forEach(field -> field.appendTo(output));
    }
  }
}
