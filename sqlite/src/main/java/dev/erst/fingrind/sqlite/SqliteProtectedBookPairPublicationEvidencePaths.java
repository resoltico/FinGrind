package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Owns deterministic evidence locations for durable pair-publication evidence. */
final class SqliteProtectedBookPairPublicationEvidencePaths {
  private static final String RETIRED_PREFIX = ".fingrind-protected-book-pair-";

  private SqliteProtectedBookPairPublicationEvidencePaths() {}

  static List<Path> paths(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    SqliteProtectedBookPairPublicationEvidenceKind checkedKind =
        Objects.requireNonNull(kind, "kind");
    return distinctParents(checkedRecord.bookTargetPath, checkedRecord.secretTargetPath).stream()
        .map(parent -> parent.resolve(checkedKind.recordFileName(checkedRecord.pairId)))
        .toList();
  }

  static List<Path> distinctParents(Path bookTargetPath, Path secretTargetPath) {
    return SqliteProtectedBookPathIdentity.distinctPhysicalParents(
        bookTargetPath, secretTargetPath);
  }

  static boolean isEvidenceShapedFile(Path candidate) {
    String fileName =
        Objects.requireNonNull(candidate.getFileName(), "candidate fileName").toString();
    return SqliteProtectedBookPairPublicationEvidenceKind.hasCurrentNamespace(fileName)
        || fileName.startsWith(RETIRED_PREFIX);
  }

  static Path temporaryPath(Path recordPath, UUID token) {
    return SqlitePairPublicationRecordIntegrity.parentOf(recordPath)
        .resolve(".fingrind-pair-evidence-write-" + token + ".tmp");
  }
}
