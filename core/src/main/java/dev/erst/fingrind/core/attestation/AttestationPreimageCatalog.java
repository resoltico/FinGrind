package dev.erst.fingrind.core.attestation;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves the complete, immutable record catalog that gives preimage fields their wire meaning.
 */
final class AttestationPreimageCatalog {
  private static final Map<Integer, AttestationRecordSchema> RECORDS =
      Stream.concat(
              AttestationRequestRecordCatalog.schemas().stream(),
              AttestationEffectRecordCatalog.schemas().stream())
          .collect(
              Collectors.toUnmodifiableMap(
                  AttestationRecordSchema::recordTypeTag, schema -> schema));

  private AttestationPreimageCatalog() {}

  static AttestationRecordSchema require(int recordTypeTag) {
    AttestationRecordSchema schema = RECORDS.get(recordTypeTag);
    if (schema == null) {
      throw new IllegalArgumentException("Attestation preimage record type is unknown.");
    }
    return schema;
  }

  static int recordCount() {
    return RECORDS.size();
  }
}
