package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Identifies paths that were owned by the retired sidecar publication protocol. */
final class SqliteProtectedBookPairPublicationEvidencePaths {
  private static final String RETIRED_PREFIX = ".fingrind-protected-book-pair-";

  private SqliteProtectedBookPairPublicationEvidencePaths() {}

  static List<Path> distinctParents(Path bookTargetPath, Path secretTargetPath) {
    return SqliteProtectedBookPathIdentity.distinctPhysicalParents(
        bookTargetPath, secretTargetPath);
  }

  static boolean isRetiredEvidenceFile(Path candidate) {
    String fileName =
        Objects.requireNonNull(candidate.getFileName(), "candidate fileName").toString();
    return fileName.startsWith(RETIRED_PREFIX);
  }
}
