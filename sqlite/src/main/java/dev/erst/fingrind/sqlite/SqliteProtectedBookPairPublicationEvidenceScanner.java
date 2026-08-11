package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Detects retired sidecars as blocking evidence without parsing, repairing, or adopting them. */
final class SqliteProtectedBookPairPublicationEvidenceScanner {
  private SqliteProtectedBookPairPublicationEvidenceScanner() {}

  static boolean hasLegacyResidue(Path bookTargetPath, Path secretTargetPath) {
    Path checkedBookTarget = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Path checkedSecretTarget = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    if (SqliteOwnedStageRecord.hasUnsafeOwnerRecordResidue(checkedBookTarget, checkedSecretTarget)
        || !SqliteOwnedStageRecord.findFor(checkedBookTarget).isEmpty()
        || !SqliteOwnedStageRecord.findFor(checkedSecretTarget).isEmpty()) {
      return true;
    }
    for (Path parent :
        SqliteProtectedBookPairPublicationEvidencePaths.distinctParents(
            checkedBookTarget, checkedSecretTarget)) {
      if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
        continue;
      }
      if (containsRetiredEvidence(parent)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsRetiredEvidence(Path parent) {
    try {
      return SqliteDirectoryStreams.read(
          parent,
          children -> {
            for (Path candidate : children) {
              if (SqliteProtectedBookPairPublicationEvidencePaths.isRetiredEvidenceFile(
                  candidate)) {
                return true;
              }
            }
            return false;
          });
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect retired protected-book pair evidence beside "
              + SqliteMachinePaths.absoluteValue(parent)
              + ".",
          exception);
    }
  }
}
