package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Reads and validates immutable protected-book pair evidence. */
final class SqlitePairPublicationEvidenceStatus {
  private SqlitePairPublicationEvidenceStatus() {}

  static boolean hasComplete(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind) {
    for (Path evidencePath : Objects.requireNonNull(record, "record").evidencePaths(kind)) {
      Optional<SqliteProtectedBookPairPublicationEvidenceCodec.DecodedEvidence> decoded =
          SqliteProtectedBookPairPublicationEvidenceCodec.read(evidencePath);
      // The codec admits evidence only when its wire kind is bound to this exact filename.
      if (decoded.isEmpty() || !record.sameImmutableRecord(decoded.orElseThrow().record())) {
        return false;
      }
    }
    return true;
  }

  static boolean hasObserved(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind) {
    return Objects.requireNonNull(record, "record").evidencePaths(kind).stream()
        .anyMatch(path -> Files.exists(path, LinkOption.NOFOLLOW_LINKS));
  }

  static void requireComplete(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind)
      throws IOException {
    if (!hasComplete(record, kind)) {
      throw new IOException(
          "The protected-book pair "
              + kind.wireValue()
              + " evidence changed before recovered publication.");
    }
  }

  static void requireExact(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind,
      Path evidencePath)
      throws IOException {
    Optional<SqliteProtectedBookPairPublicationEvidenceCodec.DecodedEvidence> decoded =
        SqliteProtectedBookPairPublicationEvidenceCodec.read(evidencePath);
    // The codec admits evidence only when its wire kind is bound to this exact filename.
    if (decoded.isEmpty() || !record.sameImmutableRecord(decoded.orElseThrow().record())) {
      throw new IOException("Protected-book pair evidence changed while completing recovery.");
    }
  }

  static SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep durabilityStep(
      SqliteProtectedBookPairPublicationEvidenceKind kind) {
    return switch (Objects.requireNonNull(kind, "kind")) {
      case CLAIM ->
          SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.PAIR_STAGE_CLAIM;
      case INTENT ->
          SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_INTENT;
      case RECOVERY ->
          SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_RECORD;
      case RETAINED ->
          SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
              .PREPUBLICATION_RETENTION;
      case COMPLETED ->
          SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
              .RECOVERY_TERMINAL_RETENTION;
    };
  }
}
