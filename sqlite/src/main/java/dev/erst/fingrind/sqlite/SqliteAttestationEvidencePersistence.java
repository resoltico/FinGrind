package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** SQLite row codec for immutable attestation evidence. */
final class SqliteAttestationEvidencePersistence {
  private SqliteAttestationEvidencePersistence() {}

  static List<AttestationEvidence> loadAll(SqliteNativeDatabase activeDatabase) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteAttestationEvidenceSql.LOAD_ALL)) {
      List<AttestationEvidence> evidence = new ArrayList<>();
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
        String persistedOrder = SqlitePostingMapper.requiredText(statement, 0);
        String expectedOrder = orderHex(BigInteger.valueOf(evidence.size()));
        if (!persistedOrder.equals(expectedOrder)) {
          throw new IllegalStateException(
              "Persisted attestation operation order is not a canonical contiguous sequence.");
        }
        evidence.add(
            new AttestationEvidence(
                decode(SqlitePostingMapper.requiredText(statement, 1)),
                decode(SqlitePostingMapper.requiredText(statement, 2)),
                decode(SqlitePostingMapper.requiredText(statement, 3))));
      }
      return List.copyOf(evidence);
    }
  }

  static void insert(
      SqliteNativeDatabase activeDatabase,
      AttestationVerification verification,
      AttestationEvidence evidence) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteAttestationEvidenceSql.INSERT)) {
      statement.bindText(1, orderHex(verification.headOrder()));
      statement.bindText(2, encode(evidence.operationEnvelope()));
      statement.bindText(3, encode(evidence.requestPreimage()));
      statement.bindText(4, encode(evidence.effectPreimage()));
      statement.bindText(5, operationHeadHex(verification.operationHead()));
      statement.step();
    }
  }

  private static byte[] decode(String encoded) {
    try {
      return Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "Persisted attestation evidence is not valid base64.", exception);
    }
  }

  private static String encode(byte[] value) {
    return Base64.getEncoder().encodeToString(value);
  }

  static String orderHex(BigInteger order) {
    BigInteger checkedOrder = Objects.requireNonNull(order, "order");
    if (checkedOrder.signum() < 0 || checkedOrder.bitLength() > Long.SIZE) {
      throw new IllegalArgumentException("operation order must fit an unsigned 64-bit value.");
    }
    return "%016x".formatted(checkedOrder);
  }

  private static String operationHeadHex(byte[] operationHead) {
    byte[] checkedOperationHead = Objects.requireNonNull(operationHead, "operationHead");
    return java.util.HexFormat.of().formatHex(checkedOperationHead);
  }
}
