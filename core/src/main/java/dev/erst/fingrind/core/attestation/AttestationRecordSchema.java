package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** One fixed record layout and canonical sort key from the immutable-preimage catalog. */
final class AttestationRecordSchema {
  private final int recordTypeTag;
  private final String name;
  private final String fieldSpecification;
  private final List<AttestationFieldSchema> fields;
  private final int[] sortKeyFieldIndexes;

  private AttestationRecordSchema(
      int recordTypeTag,
      String name,
      String fieldSpecification,
      List<AttestationFieldSchema> fields,
      int[] sortKeyFieldIndexes) {
    this.recordTypeTag = recordTypeTag;
    this.name = Objects.requireNonNull(name, "name");
    this.fieldSpecification = Objects.requireNonNull(fieldSpecification, "fieldSpecification");
    this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    this.sortKeyFieldIndexes =
        Objects.requireNonNull(sortKeyFieldIndexes, "sortKeyFieldIndexes").clone();
  }

  static AttestationRecordSchema record(
      int recordTypeTag, String name, String fieldSpecification, int... sortKeyFieldIndexes) {
    return new AttestationRecordSchema(
        recordTypeTag, name, fieldSpecification, fields(fieldSpecification), sortKeyFieldIndexes);
  }

  int recordTypeTag() {
    return recordTypeTag;
  }

  String name() {
    return name;
  }

  String fieldSpecification() {
    return fieldSpecification;
  }

  int[] sortKeyFieldIndexes() {
    return sortKeyFieldIndexes.clone();
  }

  int fieldCount() {
    return fields.size();
  }

  AttestationFieldSchema fieldSchema(int index) {
    return fields.get(index);
  }

  void requireValidFields(List<AttestationField> values) {
    if (values.size() != fields.size()) {
      throw new IllegalArgumentException(
          "Attestation record " + name + " must contain its catalog-defined field count.");
    }
    for (int index = 0; index < fields.size(); index++) {
      if (!values.get(index).matches(fields.get(index))) {
        throw new IllegalArgumentException(
            "Attestation record "
                + name
                + " field "
                + fields.get(index).name()
                + " violates its catalog type or presence rule.");
      }
    }
  }

  byte[] encodedSortKey(List<AttestationField> values) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (int index : sortKeyFieldIndexes) {
      values.get(index).appendTo(output);
    }
    return output.toByteArray();
  }

  private static List<AttestationFieldSchema> fields(String fieldSpecification) {
    return Arrays.stream(fieldSpecification.split(", "))
        .map(AttestationRecordSchema::field)
        .toList();
  }

  private static AttestationFieldSchema field(String specification) {
    int separator = specification.indexOf(':');
    String name = specification.substring(0, separator);
    String typeAndRequirement = specification.substring(separator + 1);
    boolean required = typeAndRequirement.endsWith("!");
    String type = typeAndRequirement.substring(0, typeAndRequirement.length() - 1);
    return new AttestationFieldSchema(name, fieldType(name, type), required);
  }

  private static AttestationFieldType fieldType(String name, String type) {
    if ("mutation".equals(name)) {
      return AttestationFieldType.MUTATION;
    }
    return Objects.requireNonNull(
        AttestationFieldType.WIRE_TYPES.get(type),
        "Attestation preimage catalog has an unknown field type.");
  }
}

/** One named field in a fixed immutable-preimage record layout. */
record AttestationFieldSchema(String name, AttestationFieldType type, boolean required) {
  AttestationFieldSchema {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
  }
}
