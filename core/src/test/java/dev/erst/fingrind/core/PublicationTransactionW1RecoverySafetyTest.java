package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Verifies W1 recovery remains conservative across schemas and mixed publication modes. */
class PublicationTransactionW1RecoverySafetyTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-08-10T12:34:56Z");

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void admitsAbsentReplacementCollisionEvidenceOnlyFromTheCurrentJournalSchema(
      @TempDir Path temporaryDirectory) throws Exception {
    PublicationTransactionPublisherTest.TestPublication publication =
        PublicationTransactionPublisherTest.publication(
            temporaryDirectory, PublicationTransactionFaultInjector.NONE);

    assertFalse(
        PublicationTransactionCleaner.hasVerifiedNoReplaceCollision(
            stagedAbsentReplacementJournal(
                publication, 3, ".schema-three-stage", PublicationMode.REPLACE)));
    assertTrue(
        PublicationTransactionCleaner.hasVerifiedNoReplaceCollision(
            stagedAbsentReplacementJournal(
                publication,
                PublicationTransactionJournal.CURRENT_SCHEMA_VERSION,
                ".schema-four-stage",
                PublicationMode.REPLACE)));
    assertTrue(
        PublicationTransactionCleaner.hasVerifiedNoReplaceCollision(
            stagedAbsentReplacementJournal(
                publication,
                PublicationTransactionJournal.CURRENT_SCHEMA_VERSION,
                ".no-replace-stage",
                PublicationMode.NO_REPLACE_LINK)));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void commitsNoReplaceMembersBeforeReplacementMembersInOneMixedTransaction(
      @TempDir Path temporaryDirectory) throws Exception {
    PublicationTransactionPublisherTest.TestPublication publication =
        PublicationTransactionPublisherTest.publication(
            temporaryDirectory, PublicationTransactionFaultInjector.NONE);
    Path replacement = publication.outputDirectory().resolve("replaced.pdf");
    PublicationTransactionArtifactFiles.createStage(
        replacement, "old".getBytes(StandardCharsets.UTF_8));
    Path fresh = publication.outputDirectory().resolve("fresh.key");

    PublicationTransactionResult result =
        publication
            .publisher()
            .publish(
                new PublicationTransactionRequest(
                    List.of(
                        new PublicationTransactionMemberRequest(
                            "replace-report",
                            PublicationTransactionMemberRole.PDF_REPORT,
                            replacement,
                            PublicationMode.REPLACE,
                            "new".getBytes(StandardCharsets.UTF_8)),
                        new PublicationTransactionMemberRequest(
                            "fresh-key",
                            PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                            fresh,
                            PublicationMode.NO_REPLACE_LINK,
                            "key".getBytes(StandardCharsets.UTF_8)))));

    assertTrue(result.successful());
    assertTrue(java.nio.file.Files.exists(replacement));
    assertTrue(java.nio.file.Files.exists(fresh));
  }

  private static PublicationTransactionJournal stagedAbsentReplacementJournal(
      PublicationTransactionPublisherTest.TestPublication publication,
      int schemaVersion,
      String stageName,
      PublicationMode publicationMode)
      throws IOException {
    Path finalPath = publication.outputDirectory().resolve(stageName + ".final");
    Path stagePath = publication.outputDirectory().resolve(stageName);
    PublicationTransactionStagedArtifact stage =
        PublicationTransactionArtifactFiles.createStage(
            stagePath, "stage".getBytes(StandardCharsets.UTF_8));
    PublicationTransactionArtifactFiles.createStage(
        finalPath, "external".getBytes(StandardCharsets.UTF_8));
    PublicationTransactionMember member =
        new PublicationTransactionMember(
            "replacement",
            PublicationTransactionMemberRole.PDF_REPORT,
            finalPath,
            stagePath,
            PrivateOutputDirectory.physicalObjectIdentity(publication.outputDirectory()),
            publicationMode,
            Optional.empty(),
            PublicationTransactionMemberProgress.STAGED,
            Optional.of(stage),
            Optional.empty());
    PublicationTransactionOutcome stagedOutcome =
        new PublicationTransactionOutcome(
            PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.INCOMPLETE);

    return new PublicationTransactionJournal(
        schemaVersion,
        PublicationTransactionId.fresh(),
        "0123456789abcdef0123456789abcdef",
        publication.repository().ownerKeyFingerprint(),
        Optional.empty(),
        RECORDED_AT,
        List.of(member),
        List.of(
            PublicationTransactionTransition.prepared(RECORDED_AT),
            new PublicationTransactionTransition(
                PublicationTransactionState.STAGED, RECORDED_AT, stagedOutcome)));
  }
}
