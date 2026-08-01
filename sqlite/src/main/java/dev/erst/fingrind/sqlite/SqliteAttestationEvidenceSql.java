package dev.erst.fingrind.sqlite;

/** Canonical SQL literals for immutable attestation-operation evidence. */
final class SqliteAttestationEvidenceSql {
  static final String LOAD_ALL =
      """
      select
          operation_order_hex,
          operation_envelope_base64,
          request_preimage_base64,
          effect_preimage_base64
      from attestation_operation
      order by operation_order_hex asc
      """;

  static final String INSERT =
      """
      insert into attestation_operation (
          operation_order_hex,
          operation_envelope_base64,
          request_preimage_base64,
          effect_preimage_base64,
          operation_head_hex
      ) values (?, ?, ?, ?, ?)
      """;

  private SqliteAttestationEvidenceSql() {}
}
