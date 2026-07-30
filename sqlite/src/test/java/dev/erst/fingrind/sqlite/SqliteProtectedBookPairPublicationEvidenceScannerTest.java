package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            SqliteProtectedBookPairPublicationEvidenceKind.CLAIM.recordFileName(UUID.randomUUID())),
        "not immutable FinGrind pair evidence");

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqliteProtectedBookPairPublicationEvidenceScanner.scan(
            parent.resolve("book.sqlite"), parent.resolve("book.key")));
  }

  @Test
  void scannerAcceptsTheMirroredImmutableCopiesOfOnePairRecord() throws Exception {
    Path bookTarget = tempDirectory.resolve("mirrored-pair-evidence/book.sqlite");
    Path secretTarget = tempDirectory.resolve("mirrored-pair-evidence/book.key");
    Path bookStage = writeArtifact("mirrored-pair-evidence/.book.stage", "book stage");
    Path secretStage = writeArtifact("mirrored-pair-evidence/.secret.stage", "secret stage");
    SqliteOwnedStagedArtifact.recordExisting(bookTarget, bookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretTarget, secretStage);

    SqliteProtectedBookPairPublicationRecord.create(
        bookTarget,
        secretTarget,
        bookStage,
        secretStage,
        dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy
            .REQUIRE_ABSENT,
        backupBinding(bookTarget.resolveSibling("source.sqlite")),
        (ignoredStep, ignoredParent) -> {});

    assertFalse(
        SqliteProtectedBookPairPublicationEvidenceScanner.scan(bookTarget, secretTarget)
            instanceof SqlitePairPublicationEvidenceUnsafe);
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

  @Test
  void unboundOwnerStageResidueDistinguishesNoResidueFromAnUnboundOrCompletedPairStage()
      throws Exception {
    Path absentBookTarget = tempDirectory.resolve("no-owner-residue/book.sqlite");
    Path absentSecretTarget = tempDirectory.resolve("no-owner-residue/book.key");

    assertFalse(
        SqliteProtectedBookPairPublicationEvidenceScanner.hasUnboundOwnerStageResidue(
            absentBookTarget, absentSecretTarget));

    Path unsafeBookTarget = tempDirectory.resolve("unsafe-owner-residue/book.sqlite");
    Path unsafeSecretTarget = tempDirectory.resolve("unsafe-owner-residue/book.key");
    Path unsafeParent = unsafeBookTarget.getParent();
    if (unsafeParent == null) {
      throw new AssertionError("Fixture target requires one parent.");
    }
    Files.createDirectories(unsafeParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(unsafeParent);
    Files.writeString(
        unsafeParent.resolve(
            ".book.sqlite.fingrind-maintenance-stage-" + UUID.randomUUID() + ".owner"),
        "retired owner record");

    assertTrue(
        SqliteProtectedBookPairPublicationEvidenceScanner.hasUnboundOwnerStageResidue(
            unsafeBookTarget, unsafeSecretTarget));

    Path unboundBookTarget = tempDirectory.resolve("unbound-owner-residue/book.sqlite");
    Path unboundSecretTarget = tempDirectory.resolve("unbound-owner-residue/book.key");
    Path unboundBookStage = writeArtifact("unbound-owner-residue/.book.stage", "book stage");
    SqliteOwnedStagedArtifact.recordExisting(unboundBookTarget, unboundBookStage);

    assertTrue(
        SqliteProtectedBookPairPublicationEvidenceScanner.hasUnboundOwnerStageResidue(
            unboundBookTarget, unboundSecretTarget));

    Path incompleteBookTarget = tempDirectory.resolve("incomplete-owner-residue/book.sqlite");
    Path incompleteSecretTarget = tempDirectory.resolve("incomplete-owner-residue/book.key");
    Path incompleteBookStage =
        writeArtifact("incomplete-owner-residue/.book.stage", "incomplete book stage");
    Path incompleteSecretStage =
        writeArtifact("incomplete-owner-residue/.secret.stage", "incomplete secret stage");
    SqliteOwnedStagedArtifact.recordExisting(incompleteBookTarget, incompleteBookStage);
    SqliteOwnedStagedArtifact.recordExisting(incompleteSecretTarget, incompleteSecretStage);
    SqliteProtectedBookPairPublicationRecord.create(
        incompleteBookTarget,
        incompleteSecretTarget,
        incompleteBookStage,
        incompleteSecretStage,
        dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy
            .REQUIRE_ABSENT,
        backupBinding(incompleteBookTarget.resolveSibling("source.sqlite")),
        (ignoredStep, ignoredParent) -> {});

    assertTrue(
        SqliteProtectedBookPairPublicationEvidenceScanner.hasUnboundOwnerStageResidue(
            incompleteBookTarget, incompleteSecretTarget));

    Path completedBookTarget = tempDirectory.resolve("completed-owner-residue/book.sqlite");
    Path completedSecretTarget = tempDirectory.resolve("completed-owner-residue/book.key");
    Path completedBookStage =
        writeArtifact("completed-owner-residue/.book.stage", "completed book stage");
    Path completedSecretStage =
        writeArtifact("completed-owner-residue/.secret.stage", "completed secret stage");
    SqliteOwnedStagedArtifact.recordExisting(completedBookTarget, completedBookStage);
    SqliteOwnedStagedArtifact.recordExisting(completedSecretTarget, completedSecretStage);
    SqliteProtectedBookPairPublicationRecord completed =
        SqliteProtectedBookPairPublicationRecord.create(
            completedBookTarget,
            completedSecretTarget,
            completedBookStage,
            completedSecretStage,
            dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy
                .REQUIRE_ABSENT,
            backupBinding(completedBookTarget.resolveSibling("source.sqlite")),
            (ignoredStep, ignoredParent) -> {});
    Files.createLink(completedBookTarget, completedBookStage);
    Files.createLink(completedSecretTarget, completedSecretStage);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        completed, (ignoredStep, ignoredParent) -> {});

    assertFalse(
        SqliteProtectedBookPairPublicationEvidenceScanner.hasUnboundOwnerStageResidue(
            completedBookTarget, completedSecretTarget));

    Path mismatchedBookStage =
        writeArtifact("completed-owner-residue/.mismatched-book.stage", "mismatched book stage");
    SqliteOwnedStagedArtifact.recordExisting(completedBookTarget, mismatchedBookStage);

    assertTrue(
        SqliteProtectedBookPairPublicationEvidenceScanner.hasUnboundOwnerStageResidue(
            completedBookTarget, completedSecretTarget));

    Path secretMismatchBookTarget =
        tempDirectory.resolve("completed-secret-owner-residue/book.sqlite");
    Path secretMismatchSecretTarget =
        tempDirectory.resolve("completed-secret-owner-residue/book.key");
    Path secretMismatchBookStage =
        writeArtifact("completed-secret-owner-residue/.book.stage", "completed book stage");
    Path secretMismatchSecretStage =
        writeArtifact("completed-secret-owner-residue/.secret.stage", "completed secret stage");
    SqliteOwnedStagedArtifact.recordExisting(secretMismatchBookTarget, secretMismatchBookStage);
    SqliteOwnedStagedArtifact.recordExisting(secretMismatchSecretTarget, secretMismatchSecretStage);
    SqliteProtectedBookPairPublicationRecord secretMismatchCompleted =
        SqliteProtectedBookPairPublicationRecord.create(
            secretMismatchBookTarget,
            secretMismatchSecretTarget,
            secretMismatchBookStage,
            secretMismatchSecretStage,
            dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy
                .REQUIRE_ABSENT,
            backupBinding(secretMismatchBookTarget.resolveSibling("source.sqlite")),
            (ignoredStep, ignoredParent) -> {});
    Files.createLink(secretMismatchBookTarget, secretMismatchBookStage);
    Files.createLink(secretMismatchSecretTarget, secretMismatchSecretStage);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        secretMismatchCompleted, (ignoredStep, ignoredParent) -> {});
    Path mismatchedSecretStage =
        writeArtifact(
            "completed-secret-owner-residue/.mismatched-secret.stage", "mismatched secret stage");
    SqliteOwnedStagedArtifact.recordExisting(secretMismatchSecretTarget, mismatchedSecretStage);

    assertTrue(
        SqliteProtectedBookPairPublicationEvidenceScanner.hasUnboundOwnerStageResidue(
            secretMismatchBookTarget, secretMismatchSecretTarget));
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
