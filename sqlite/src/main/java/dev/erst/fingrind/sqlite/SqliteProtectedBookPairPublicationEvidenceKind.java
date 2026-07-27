package dev.erst.fingrind.sqlite;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** Closed v3 namespaces for immutable protected-book pair evidence. */
enum SqliteProtectedBookPairPublicationEvidenceKind {
  CLAIM(".fingrind-protected-book-pair-claim-", ".claim", "claim"),
  INTENT(".fingrind-protected-book-pair-intent-", ".intent", "intent"),
  RECOVERY(".fingrind-protected-book-pair-recovery-v3-", ".recovery", "recovery"),
  RETAINED(".fingrind-protected-book-pair-retained-v3-", ".retained", "prepublication-retained"),
  COMPLETED(".fingrind-protected-book-pair-completed-v3-", ".completed", "completed");

  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  private final String prefix;
  private final String suffix;
  private final String wireValue;

  SqliteProtectedBookPairPublicationEvidenceKind(String prefix, String suffix, String wireValue) {
    this.prefix = prefix;
    this.suffix = suffix;
    this.wireValue = wireValue;
  }

  String recordFileName(UUID pairId) {
    return prefix + pairId + suffix;
  }

  String wireValue() {
    return wireValue;
  }

  boolean isMandatoryRecoveryEvidence() {
    return this != RETAINED && this != COMPLETED;
  }

  static boolean hasCurrentNamespace(String fileName) {
    return Arrays.stream(values()).anyMatch(kind -> fileName.startsWith(kind.prefix));
  }

  static Optional<SqliteProtectedBookPairPublicationEvidenceKind> fromCurrentFileName(
      String fileName) {
    for (SqliteProtectedBookPairPublicationEvidenceKind kind : values()) {
      if (!fileName.startsWith(kind.prefix) || !fileName.endsWith(kind.suffix)) {
        continue;
      }
      String token =
          fileName.substring(kind.prefix.length(), fileName.length() - kind.suffix.length());
      if (token.matches(UUID_PATTERN)) {
        return Optional.of(kind);
      }
    }
    return Optional.empty();
  }

  static SqliteProtectedBookPairPublicationEvidenceKind fromWireValue(String value) {
    return Arrays.stream(values())
        .filter(kind -> kind.wireValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown pair evidence kind."));
  }
}
