package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behavioral coverage for fail-closed discovery of protected-book pair evidence. */
class SqliteProtectedBookPairPublicationEvidenceScannerTest
    extends SqliteArtifactPublicationTestSupport {

  @Test
  void absentSharedParentContributesNoEvidenceInsteadOfCreatingOrInspectingIt() {
    Path absentParent = tempDirectory.resolve("absent-evidence-parent");

    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqliteProtectedBookPairPublicationEvidenceScanner.scan(
            absentParent.resolve("book.sqlite"), absentParent.resolve("book.key")));
  }

  @Test
  void scannerIgnoresOrdinaryDirectoryEntriesWhileClassifyingAnExistingParentAsAbsent()
      throws Exception {
    Path parent = tempDirectory.resolve("ordinary-entry-evidence");
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    Files.writeString(parent.resolve("operator-notes.txt"), "not recovery evidence");

    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqliteProtectedBookPairPublicationEvidenceScanner.scan(
            parent.resolve("book.sqlite"), parent.resolve("book.key")));
  }

  @Test
  void scannerRejectsMalformedCurrentNamespaceEvidence() throws Exception {
    Path parent = tempDirectory.resolve("malformed-evidence");
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    Files.writeString(
        parent.resolve(
            SqliteProtectedBookPairPublicationEvidenceKind.CLAIM.recordFileName(
                UUID.randomUUID())),
        "not immutable FinGrind pair evidence");

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqliteProtectedBookPairPublicationEvidenceScanner.scan(
            parent.resolve("book.sqlite"), parent.resolve("book.key")));
  }

  @Test
  void scannerReportsCandidateEnumerationFailureAfterCheckingForUnsafeOwnerResidue() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\evidence");
      parent.exists = true;
      parent.regularFile = false;
      IOException injected = new IOException("candidate evidence enumeration failure");
      parent.failNewDirectoryStreamAfterSuccessfulCallsWith(1, injected);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookPairPublicationEvidenceScanner.scan(
                      fileSystem.path("\\evidence\\book.sqlite"),
                      fileSystem.path("\\evidence\\book.key")));

      assertEquals(
          "Failed to inspect protected-book pair recovery evidence beside \\evidence.",
          failure.getMessage());
      assertSame(injected, failure.getCause());
    }
  }

  @Test
  void scannerReportsCandidateEnumerationCloseFailureAfterCheckingForUnsafeOwnerResidue() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\close-failure-evidence");
      parent.exists = true;
      parent.regularFile = false;
      IOException injected = new IOException("candidate evidence close failure");
      parent.failDirectoryStreamCloseAfterSuccessfulCallsWith(1, injected);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteProtectedBookPairPublicationEvidenceScanner.scan(
                      fileSystem.path("\\close-failure-evidence\\book.sqlite"),
                      fileSystem.path("\\close-failure-evidence\\book.key")));

      assertEquals(
          "Failed to inspect protected-book pair recovery evidence beside \\close-failure-evidence.",
          failure.getMessage());
      assertSame(injected, failure.getCause());
    }
  }

  @Test
  void scannerRejectsConflictingImmutableEvidenceForTheSamePairIdentity() throws Exception {
    Path bookTarget = tempDirectory.resolve("conflicting-pair-evidence/book.sqlite");
    Path secretTarget = tempDirectory.resolve("conflicting-pair-evidence/book.key");
    Path bookStage = writeArtifact("conflicting-pair-evidence/.book.stage", "book stage");
    Path secretStage = writeArtifact("conflicting-pair-evidence/.secret.stage", "secret stage");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);
    SqliteProtectedBookPairPublicationRecord record =
        SqliteProtectedBookPairPublicationRecord.create(
            bookTarget,
            secretTarget,
            bookStage,
            secretStage,
            dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy
                .REQUIRE_ABSENT,
            backupBinding(bookTarget.resolveSibling("source.sqlite")),
            (ignoredStep, ignoredParent) -> {});
    SqliteProtectedBookPairPublicationRecord conflicting = withChangedBookDigest(record);
    Path conflictingEvidence =
        conflicting
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.RETAINED)
            .getFirst();
    java.nio.file.Files.writeString(
        conflictingEvidence,
        SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
            conflicting, SqliteProtectedBookPairPublicationEvidenceKind.RETAINED));

    assertFalse(SqliteOwnedStageRecord.hasUnsafeOwnerRecordResidue(bookTarget, secretTarget));
    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqliteProtectedBookPairPublicationEvidenceScanner.scan(bookTarget, secretTarget));
  }

  private static SqliteProtectedBookPairPublicationRecord withChangedBookDigest(
      SqliteProtectedBookPairPublicationRecord original) {
    byte[] changedBookDigest = original.bookDigest.clone();
    changedBookDigest[0] ^= 1;
    return new SqliteProtectedBookPairPublicationRecord(
        new SqliteProtectedBookPairPublicationRecord.Components(
            original.pairId,
            new SqliteProtectedBookPairPublicationRecord.PairPaths(
                original.bookTargetPath,
                original.secretTargetPath,
                original.bookStagePath,
                original.secretStagePath),
            new SqliteProtectedBookPairPublicationRecord.PairDigests(
                changedBookDigest, original.secretDigest, original.replaceTargetDigest),
            original.bookTargetPolicy,
            original.binding));
  }
}
