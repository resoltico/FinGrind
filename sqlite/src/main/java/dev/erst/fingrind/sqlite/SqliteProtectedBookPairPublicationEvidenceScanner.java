package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Classifies v3 evidence from both requested target parents without adopting retired residue. */
final class SqliteProtectedBookPairPublicationEvidenceScanner {
  private SqliteProtectedBookPairPublicationEvidenceScanner() {}

  static SqlitePairPublicationEvidenceScan scan(Path bookTargetPath, Path secretTargetPath) {
    Path checkedBookTarget =
        SqlitePairPublicationRecordIntegrity.normalized(bookTargetPath, "bookTargetPath");
    Path checkedSecretTarget =
        SqlitePairPublicationRecordIntegrity.normalized(secretTargetPath, "secretTargetPath");
    Optional<Map<UUID, SqliteProtectedBookPairPublicationRecord>> records =
        collectedRecords(checkedBookTarget, checkedSecretTarget);
    if (records.isEmpty()) {
      return SqlitePairPublicationEvidenceUnsafe.INSTANCE;
    }
    return SqlitePairPublicationEvidenceClassifier.classify(
        checkedBookTarget, checkedSecretTarget, records.orElseThrow());
  }

  private static Optional<Map<UUID, SqliteProtectedBookPairPublicationRecord>> collectedRecords(
      Path bookTargetPath, Path secretTargetPath) {
    if (SqliteOwnedStageRecord.hasUnsafeOwnerRecordResidue(bookTargetPath, secretTargetPath)) {
      return Optional.empty();
    }
    Map<UUID, SqliteProtectedBookPairPublicationRecord> records = new ConcurrentHashMap<>();
    for (Path parent :
        SqliteProtectedBookPairPublicationEvidencePaths.distinctParents(
            bookTargetPath, secretTargetPath)) {
      if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
        continue;
      }
      if (!collect(parent, records)) {
        return Optional.empty();
      }
    }
    return Optional.of(records);
  }

  private static boolean collect(
      Path parent, Map<UUID, SqliteProtectedBookPairPublicationRecord> records) {
    try {
      return SqliteDirectoryStreams.read(parent, children -> collectEvidence(children, records));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect protected-book pair recovery evidence beside "
              + SqliteMachinePaths.absoluteValue(parent)
              + ".",
          exception);
    }
  }

  private static boolean collectEvidence(
      DirectoryStream<Path> children, Map<UUID, SqliteProtectedBookPairPublicationRecord> records) {
    for (Path candidate : children) {
      if (!SqliteProtectedBookPairPublicationEvidencePaths.isEvidenceShapedFile(candidate)) {
        continue;
      }
      Optional<SqliteProtectedBookPairPublicationEvidenceCodec.DecodedEvidence> decoded =
          SqliteProtectedBookPairPublicationEvidenceCodec.read(candidate);
      if (decoded.isEmpty()) {
        return false;
      }
      SqliteProtectedBookPairPublicationEvidenceCodec.DecodedEvidence evidence =
          decoded.orElseThrow();
      SqliteProtectedBookPairPublicationRecord previous =
          records.putIfAbsent(evidence.record().pairId, evidence.record());
      // The strict codec already established that this candidate is an exact canonical spelling
      // of the decoded record's evidence path. Scanning only needs to reject a second, divergent
      // immutable record for the same pair identity.
      if (previous != null && !previous.sameImmutableRecord(evidence.record())) {
        return false;
      }
    }
    return true;
  }
}
