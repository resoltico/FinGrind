package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Forces and revalidates the durable boundaries shared by staged pair-publication lifecycles. */
final class SqlitePairPublicationDurability {
  private SqlitePairPublicationDurability() {}

  static void forcePublishedDirectory(
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep step,
      Path path)
      throws IOException {
    Objects.requireNonNull(directoryForcer, "directoryForcer")
        .force(
            Objects.requireNonNull(step, "step"),
            Objects.requireNonNull(
                Objects.requireNonNull(path, "path").toAbsolutePath().normalize().getParent(),
                "published artifact parent"));
  }

  static void forceStagedRecoveryMembers(
      SqliteOwnedStagedArtifact bookStage,
      Path bookTarget,
      SqliteOwnedStagedArtifact secretStage,
      Path secretTarget,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer)
      throws IOException {
    Objects.requireNonNull(bookStage, "bookStage")
        .forceForPairPublicationRecoveryBoundary(bookTarget, directoryForcer);
    Objects.requireNonNull(secretStage, "secretStage")
        .forceForPairPublicationRecoveryBoundary(secretTarget, directoryForcer);
  }

  static void forceAndRequireRecoveryBoundary(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteOwnedStagedArtifact stagedArtifact,
      Path finalPath,
      boolean bookMember,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer)
      throws IOException {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "recoveryRecord");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.forceForRecoveredPublication(
        checkedRecord,
        Objects.requireNonNull(directoryForcer, "directoryForcer"),
        Objects.requireNonNull(recoveryRecordFileForcer, "recoveryRecordFileForcer"));
    Objects.requireNonNull(stagedArtifact, "stagedArtifact").requireIntactFor(finalPath);
    boolean matches =
        bookMember ? checkedRecord.stagedBookMatches() : checkedRecord.stagedSecretMatches();
    if (!matches) {
      throw new IOException(
          "The staged protected-book pair member changed after durable recovery evidence was recorded.");
    }
  }
}
