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

  private AttestationPreimage(List<Fact> records) {
    this.records = records;
  }

  static AttestationPreimage of(List<Fact> records) {
    Objects.requireNonNull(records, "records");
    if (records.size() > MAX_RECORD_COUNT) {
      throw new IllegalArgumentException(
          "Attestation preimage may contain at most 1000000 records.");
    }
    List<Fact> canonicalRecords = new ArrayList<>(records.size());
    for (Fact fact : records) {
      canonicalRecords.add(Objects.requireNonNull(fact, "records must not contain null"));
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
    return new AttestationPreimage(List.copyOf(canonicalRecords));
  }

  List<Fact> records() {
    return records;
  }

  byte[] encoded() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    AttestationUnsignedEncoding.appendUnsigned(
        output, BigInteger.valueOf(records.size()), Integer.BYTES, "recordCount");
    records.forEach(record -> record.appendTo(output));
    if (output.size() > MAX_ENCODED_BYTE_COUNT) {
      throw new IllegalArgumentException("Attestation preimage must be at most 16777216 bytes.");
    }
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

    private void appendTo(ByteArrayOutputStream output) {
      AttestationUnsignedEncoding.appendUnsigned(
          output, BigInteger.valueOf(recordTypeTag), Short.BYTES, "recordTypeTag");
      AttestationUnsignedEncoding.appendUnsigned(
          output, BigInteger.valueOf(fields.size()), Short.BYTES, "fieldCount");
      fields.forEach(field -> field.appendTo(output));
    }
  }
}
