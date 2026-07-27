package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliAttestationRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliBookLifecycleJsonModels;
import dev.erst.fingrind.cli.json.CliBookLifecycleRejectionJsonModels;
import dev.erst.fingrind.cli.json.CliBookPairPublicationJsonModels;
import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels;
import dev.erst.fingrind.cli.json.CliOpenBookErrorJsonModels;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Validates attestation-chain facts carried by JSON transport payloads. */
class CliAttestationJsonModelValidationTest {
  @Test
  void closedFiscalYearPayload_keepsReplayAndAttestationCommitCoupled() {
    CliAttestationJsonModels.AttestationCommitPayload commit =
        new CliAttestationJsonModels.AttestationCommitPayload("12", "a".repeat(64));

    assertEquals(commit, closedFiscalYearPayload(false, commit).attestationCommit());
    assertEquals(null, closedFiscalYearPayload(true, null).attestationCommit());
    assertEquals(
        "An idempotent fiscal year close replay must not report a newly appended attestation operation.",
        assertThrows(IllegalArgumentException.class, () -> closedFiscalYearPayload(true, commit))
            .getMessage());
    assertEquals(
        "A newly closed fiscal year must report its attestation operation.",
        assertThrows(IllegalArgumentException.class, () -> closedFiscalYearPayload(false, null))
            .getMessage());
  }

  @Test
  void backupBookPayload_keepsAcknowledgementStateAndAttestationCommitCoupled() {
    CliAttestationJsonModels.AttestationCommitPayload commit =
        new CliAttestationJsonModels.AttestationCommitPayload("12", "a".repeat(64));

    assertEquals(
        commit,
        new CliBookPairPublicationJsonModels.BackupBookPayload(
                "book.sqlite",
                "backup-1",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.PUBLISHED,
                pairPublicationRetention(),
                CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload.ACKNOWLEDGED,
                commit)
            .attestationCommit());
    assertEquals(
        null,
        new CliBookPairPublicationJsonModels.BackupBookPayload(
                "book.sqlite",
                "backup-1",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.ALREADY_PUBLISHED,
                null,
                CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload.PENDING,
                null)
            .attestationCommit());
    assertEquals(
        "An acknowledged backup must report its attestation operation.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new CliBookPairPublicationJsonModels.BackupBookPayload(
                        "book.sqlite",
                        "backup-1",
                        CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.PUBLISHED,
                        pairPublicationRetention(),
                        CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload
                            .ACKNOWLEDGED,
                        null))
            .getMessage());
    assertEquals(
        "ALREADY_PRESENT backup acknowledgement must not report a newly appended operation.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new CliBookPairPublicationJsonModels.BackupBookPayload(
                        "book.sqlite",
                        "backup-1",
                        CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.PUBLISHED,
                        pairPublicationRetention(),
                        CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload
                            .ALREADY_PRESENT,
                        commit))
            .getMessage());

    for (CompletionAndAcknowledgement invalid :
        List.of(
            new CompletionAndAcknowledgement(
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.PUBLISHED,
                CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload.RESUMED),
            new CompletionAndAcknowledgement(
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.RECOVERED,
                CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload.ACKNOWLEDGED),
            new CompletionAndAcknowledgement(
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.RECOVERED,
                CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload.ALREADY_PRESENT),
            new CompletionAndAcknowledgement(
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.ALREADY_PUBLISHED,
                CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload.ACKNOWLEDGED),
            new CompletionAndAcknowledgement(
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.ALREADY_PUBLISHED,
                CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload
                    .ALREADY_PRESENT))) {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              new CliBookPairPublicationJsonModels.BackupBookPayload(
                  "book.sqlite",
                  "backup-1",
                  invalid.completion(),
                  invalid.completion()
                          == CliBookPairPublicationJsonModels.PairPublicationCompletionPayload
                              .ALREADY_PUBLISHED
                      ? null
                      : pairPublicationRetention(),
                  invalid.state(),
                  null));
    }
  }

  @Test
  void restoreAndRekeyPayloads_reserveAlreadyPublishedForBackupAcknowledgementReplay() {
    CliAttestationJsonModels.AttestationCommitPayload commit =
        new CliAttestationJsonModels.AttestationCommitPayload("12", "a".repeat(64));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliBookPairPublicationJsonModels.RestoreBookPayload(
                "book.sqlite",
                "book.key",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.ALREADY_PUBLISHED,
                null,
                commit));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliBookPairPublicationJsonModels.RekeyBookPayload(
                "book.sqlite",
                "book.key",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.ALREADY_PUBLISHED,
                null,
                commit));
  }

  @Test
  void retainedPairPublicationFacts_bindEveryReportedLifecycleTarget() {
    CliAttestationJsonModels.AttestationCommitPayload commit =
        new CliAttestationJsonModels.AttestationCommitPayload("12", "a".repeat(64));
    CliBookPairPublicationJsonModels.PairPublicationRetentionPayload retention =
        pairPublicationRetention();

    assertEquals(
        retention,
        new CliBookPairPublicationJsonModels.RekeyBookPayload(
                "book.sqlite",
                "book.key",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.PUBLISHED,
                retention,
                commit)
            .pairPublicationRetention());
    assertEquals(
        retention,
        new CliBookPairPublicationJsonModels.RestoreBookPayload(
                "book.sqlite",
                "book.key",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.RECOVERED,
                retention,
                commit)
            .pairPublicationRetention());
    assertEquals(
        retention,
        new CliBookLifecycleRejectionJsonModels.BackupAcknowledgementAuthorizationRejectedDetails(
                "source-book.sqlite",
                "book.sqlite",
                "book.key",
                "backup-1",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.PUBLISHED,
                retention)
            .pairPublicationRetention());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliBookPairPublicationJsonModels.RekeyBookPayload(
                "other-book.sqlite",
                "book.key",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.PUBLISHED,
                retention,
                commit));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliBookPairPublicationJsonModels.RestoreBookPayload(
                "book.sqlite",
                "other-book.key",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.RECOVERED,
                retention,
                commit));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliBookLifecycleRejectionJsonModels
                .BackupAcknowledgementAuthorizationRejectedDetails(
                "source-book.sqlite",
                "other-backup.sqlite",
                "book.key",
                "backup-1",
                CliBookPairPublicationJsonModels.PairPublicationCompletionPayload.PUBLISHED,
                retention));
  }

  @Test
  void pairPublicationRetentionCarriesTwoAuthoritativeMemberFacts() {
    CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload bookPublication =
        new CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload(
            "/books/backup.fgba", "/books/.backup.fgba.stage");
    CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload
        generatedSecretPublication =
            new CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload(
                "/books/backup.key", "/books/.backup.key.stage");

    CliBookPairPublicationJsonModels.PairPublicationRetentionPayload retention =
        new CliBookPairPublicationJsonModels.PairPublicationRetentionPayload(
            bookPublication, generatedSecretPublication);

    assertEquals(bookPublication, retention.bookPublication());
    assertEquals(generatedSecretPublication, retention.generatedSecretPublication());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload(
                "/books/backup.fgba", "/books/backup.fgba"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliBookPairPublicationJsonModels.PairPublicationRetentionPayload(
                bookPublication,
                new CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload(
                    "/books/backup.fgba", "/books/.other.stage")));
  }

  @Test
  void protectedBookPairUncertaintyDetails_acceptOnlyMaintenanceOperationsAndEstablishedMembers() {
    CliMaintenanceErrorJsonModels.PairPublication recoverablePair = recoverablePairPublication();

    assertEquals(
        "backup-book",
        new CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails(
                "backup-book", recoverablePair)
            .operation());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails(
                "open-book", recoverablePair));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails(
                "backup", recoverablePair));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliMaintenanceErrorJsonModels.ProtectedBookPairPublicationUncertainDetails(
                "restore-book", unestablishedPairPublication()));
  }

  @Test
  void retainedOpenBookPreparationArtifact_acceptsOnlyCurrentRoleVocabulary() {
    for (String role :
        List.of(
            "attestation-founder-key",
            "attestation-founder-key-stage",
            "book-file",
            "book-sidecar")) {
      assertEquals(
          role,
          new CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact(
                  role, "/books/opening-artifact", null)
              .role());
    }

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliOpenBookErrorJsonModels.RetainedOpenBookPreparationArtifact(
                "founder-key", "/books/opening-artifact", null));
  }

  @Test
  void attestationCredentialAndReviewFacts_preserveOptionalDigestAndEqualWidthOrderComparisons() {
    String keyId = "a".repeat(64);
    String predecessorKeyId = "b".repeat(64);
    CliAttestationJsonModels.AttestationCredentialPayload credential =
        new CliAttestationJsonModels.AttestationCredentialPayload(
            "principal-1", keyId, "spki", "operator", "enrolled", "12", predecessorKeyId, "active");
    CliAttestationJsonModels.AttestationReviewFindingPayload finding =
        new CliAttestationJsonModels.AttestationReviewFindingPayload(keyId, "10", "12", "11");

    assertEquals(predecessorKeyId, credential.predecessorKeyId());
    assertEquals("11", finding.operationOrder());
  }

  @Test
  void attestationReviewPayloads_andStrictRejectionDetails_bindOneVerifiedHead() {
    CliAttestationJsonModels.AttestationHeadPayload verifiedHead =
        new CliAttestationJsonModels.AttestationHeadPayload("12", "a".repeat(64));
    CliAttestationJsonModels.AttestationReviewFindingPayload finding =
        new CliAttestationJsonModels.AttestationReviewFindingPayload(
            "b".repeat(64), "10", "12", "12");
    CliAttestationJsonModels.AttestationReviewPayload review =
        new CliAttestationJsonModels.AttestationReviewPayload(
            "book-1", verifiedHead, List.of(finding));
    CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails details =
        new CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails(
            "book-1", verifiedHead, "0".repeat(64), List.of(finding));

    assertEquals(verifiedHead, review.verifiedAttestationHead());
    assertEquals(verifiedHead, details.verifiedAttestationHead());
    assertEquals(List.of(finding), details.reviewFindings());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliAttestationJsonModels.AttestationHeadPayload("12", "A".repeat(64)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails(
                "book-1", verifiedHead, "0".repeat(64), List.of()));
  }

  @Test
  void attestationTransportFacts_areCanonicalAndReviewFindingsAreCoherent() {
    String digest = "a".repeat(64);
    String otherDigest = "b".repeat(64);
    String maximumUnsigned64Order = "18446744073709551615";

    assertEquals(
        maximumUnsigned64Order,
        new CliAttestationJsonModels.AttestationHeadPayload(maximumUnsigned64Order, digest)
            .operationOrder());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliAttestationJsonModels.AttestationHeadPayload("01", digest));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.AttestationCommitPayload("18446744073709551616", digest));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliAttestationJsonModels.AttestationCommitPayload("1", "A".repeat(64)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.AttestationCredentialPayload(
                "principal-1",
                "A".repeat(64),
                "spki",
                "operator",
                "enrolled",
                "1",
                null,
                "active"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.AttestationCredentialPayload(
                "principal-1", digest, "spki", "operator", "enrolled", "01", null, "active"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.AttestationCredentialPayload(
                "principal-1",
                digest,
                "spki",
                "operator",
                "enrolled",
                "1",
                "B".repeat(64),
                "active"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.AttestationSystemWorkflowPolicyPayload(
                "workflow-1", "interim-result-sweep", "3900", null, null, true, "01"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliBookLifecycleJsonModels.AttestationKeyFilePayload("spki", "c".repeat(63)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.StaleHeadDetails(digest, otherDigest, "01"));
    assertEquals(
        "8",
        new CliErrorJsonModels.AttestationReviewWindowDetails(digest, "6", "8", "7")
            .lastAffectedOrder());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.AttestationReviewWindowDetails(digest, "6", "7", "7"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.AttestationReviewWindowDetails(digest, "6", "5", "7"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.UnsupportedBookFormatVersionDetails(-1, 8));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.UnsupportedBookFormatVersionDetails(54, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.UnsupportedBookFormatVersionDetails(8, 8));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.AttestationReviewFindingPayload(
                "C".repeat(64), "6", "7", "6"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliAttestationJsonModels.AttestationReviewFindingPayload(digest, "7", "6", "7"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliAttestationJsonModels.AttestationReviewFindingPayload(digest, "7", "8", "6"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliAttestationJsonModels.AttestationReviewFindingPayload(digest, "7", "8", "9"));

    CliAttestationJsonModels.AttestationHeadPayload verifiedHead =
        new CliAttestationJsonModels.AttestationHeadPayload("7", digest);
    CliAttestationJsonModels.AttestationReviewFindingPayload boundedFindingPastHead =
        new CliAttestationJsonModels.AttestationReviewFindingPayload(otherDigest, "6", "8", "6");
    CliAttestationJsonModels.AttestationReviewFindingPayload openFindingPastHead =
        new CliAttestationJsonModels.AttestationReviewFindingPayload(otherDigest, "8", null, "8");
    CliAttestationJsonModels.AttestationRegistryPayload registry =
        new CliAttestationJsonModels.AttestationRegistryPayload(
            List.of(), List.of(), List.of(), List.of());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.AttestationReviewPayload(
                "book-1", verifiedHead, List.of(boundedFindingPastHead)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationRejectionJsonModels.AttestationReviewRequiredDetails(
                "book-1", verifiedHead, "0".repeat(64), List.of(boundedFindingPastHead)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.VerifyBookPayload(
                "book-1",
                verifiedHead,
                "0".repeat(64),
                true,
                List.of(openFindingPastHead),
                registry));

    CliAttestationJsonModels.AttestationHeadPayload acceptedHead =
        new CliAttestationJsonModels.AttestationHeadPayload("8", digest);
    CliAttestationJsonModels.AttestationReviewFindingPayload acceptedFinding =
        new CliAttestationJsonModels.AttestationReviewFindingPayload(otherDigest, "6", "8", "8");
    assertEquals(
        List.of(acceptedFinding),
        new CliAttestationJsonModels.AttestationReviewPayload(
                "book-1", acceptedHead, List.of(acceptedFinding))
            .findings());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAttestationJsonModels.AttestationReviewPayload(
                "book-1", acceptedHead, List.of(acceptedFinding, acceptedFinding)));
  }

  private static CliBookLifecycleJsonModels.ClosedFiscalYearPayload closedFiscalYearPayload(
      boolean idempotentReplay,
      CliAttestationJsonModels.@Nullable AttestationCommitPayload attestationCommit) {
    return new CliBookLifecycleJsonModels.ClosedFiscalYearPayload(
        1,
        "2026-01-01",
        "2026-12-31",
        "3000",
        "3900",
        "3200",
        "2027-01-01T00:00:00Z",
        idempotentReplay,
        List.of("posting-1"),
        attestationCommit);
  }

  private static CliBookPairPublicationJsonModels.PairPublicationRetentionPayload
      pairPublicationRetention() {
    return new CliBookPairPublicationJsonModels.PairPublicationRetentionPayload(
        new CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload(
            "book.sqlite", "retained-book.stage"),
        new CliBookPairPublicationJsonModels.PairPublicationMemberPublicationPayload(
            "book.key", "retained-secret.stage"));
  }

  private static CliMaintenanceErrorJsonModels.PairPublication recoverablePairPublication() {
    return new CliMaintenanceErrorJsonModels.PairPublication(
        new CliMaintenanceErrorJsonModels.PairPublicationMember(
            "/books/restore.sqlite",
            CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload.NOT_ATTEMPTED),
        new CliMaintenanceErrorJsonModels.PairPublicationMember(
            "/keys/restore.book-key",
            CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload.NOT_ATTEMPTED),
        CliMaintenanceErrorJsonModels.PairPublicationRecoveryRecordStatePayload.DURABLY_RETAINED,
        new CliMaintenanceErrorJsonModels.PairPublicationRetention(
            new CliMaintenanceErrorJsonModels.PublishedArtifact(
                "/books/restore.sqlite", "/books/.restore-stage"),
            new CliMaintenanceErrorJsonModels.PublishedArtifact(
                "/keys/restore.book-key", "/keys/.restore-key-stage")));
  }

  private static CliMaintenanceErrorJsonModels.PairPublication unestablishedPairPublication() {
    return new CliMaintenanceErrorJsonModels.PairPublication(
        new CliMaintenanceErrorJsonModels.PairPublicationMember(
            "/books/restore.sqlite",
            CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload.UNESTABLISHED),
        new CliMaintenanceErrorJsonModels.PairPublicationMember(
            "/keys/restore.book-key",
            CliMaintenanceErrorJsonModels.PairPublicationMemberStatePayload.UNESTABLISHED),
        null,
        null);
  }

  private record CompletionAndAcknowledgement(
      CliBookPairPublicationJsonModels.PairPublicationCompletionPayload completion,
      CliBookPairPublicationJsonModels.BackupAcknowledgementStatePayload state) {}
}
