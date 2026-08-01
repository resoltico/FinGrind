package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Exercises retained pair-publication evidence classification before recovery admission. */
class SqliteProtectedBookPairPublicationEvidenceClassificationTest
    extends SqliteProtectedBookPairPublicationRecoveryTestSupport {
  @Test
  void evidenceClassifierDistinguishesExactIncompleteAndUnsafeRecoveryEvidence() throws Exception {
    SqliteProtectedBookPairPublicationRecord exact = retainedRecord("classifier-exact");

    assertInstanceOf(
        SqlitePairPublicationEvidenceExact.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath, exact.secretTargetPath, Map.of(exact.pairId, exact)));

    SqliteProtectedBookPairPublicationRecord duplicate =
        SqliteProtectedBookPairPublicationRecord.create(
            exact.bookTargetPath,
            exact.secretTargetPath,
            writeArtifact("classifier-exact/.other-book.stage", "other retained book stage"),
            writeArtifact("classifier-exact/.other-secret.stage", "other retained secret stage"),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            backupBinding(exact.bookTargetPath.resolveSibling("source.sqlite")),
            (ignoredStep, ignoredParent) -> {});
    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath,
            exact.secretTargetPath,
            Map.of(exact.pairId, exact, duplicate.pairId, duplicate)));

    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        exact, (ignoredStep, ignoredParent) -> {});
    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath, exact.secretTargetPath, Map.of(exact.pairId, exact)));

    SqliteProtectedBookPairPublicationRecord retainedWithOnlySecretVisible =
        retainedRecord("classifier-retained-only-secret-visible");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        retainedWithOnlySecretVisible, (ignoredStep, ignoredParent) -> {});
    Files.createLink(
        retainedWithOnlySecretVisible.secretTargetPath,
        retainedWithOnlySecretVisible.secretStagePath);
    assertInstanceOf(
        SqlitePairPublicationEvidenceExact.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            retainedWithOnlySecretVisible.bookTargetPath,
            retainedWithOnlySecretVisible.secretTargetPath,
            Map.of(retainedWithOnlySecretVisible.pairId, retainedWithOnlySecretVisible)));

    Files.writeString(
        exact.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.RETAINED).getFirst(),
        "changed retained evidence");
    assertInstanceOf(
        SqlitePairPublicationEvidenceExactIncomplete.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath, exact.secretTargetPath, Map.of(exact.pairId, exact)));
    assertInstanceOf(
        SqlitePairPublicationEvidenceOtherPending.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            exact.bookTargetPath.resolveSibling("other-book.sqlite"),
            exact.secretTargetPath.resolveSibling("other-book.key"),
            Map.of(exact.pairId, exact)));
  }

  @Test
  void evidenceClassifierTreatsDurablyCompletedBackupAsAuthoritativeAndIncompleteClaimsAsUnsafe()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord completed = retainedRecord("classifier-completed");
    Files.createLink(completed.bookTargetPath, completed.bookStagePath);
    Files.createLink(completed.secretTargetPath, completed.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        completed, (ignoredStep, ignoredParent) -> {});

    assertInstanceOf(
        SqlitePairPublicationEvidenceExact.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            completed.bookTargetPath,
            completed.secretTargetPath.resolveSibling("unrelated.key"),
            Map.of(completed.pairId, completed)));

    SqliteProtectedBookPairPublicationRecord partialClaim = retainedRecord("classifier-claim");
    Files.writeString(
        partialClaim.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst(),
        "changed claim evidence");
    for (Path evidencePath :
        partialClaim.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.INTENT)) {
      Files.delete(evidencePath);
    }
    for (Path evidencePath :
        partialClaim.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY)) {
      Files.delete(evidencePath);
    }

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            partialClaim.bookTargetPath,
            partialClaim.secretTargetPath,
            Map.of(partialClaim.pairId, partialClaim)));
  }

  @Test
  void evidenceClassifierIgnoresInertClaimResidueAndCompletedNonBackupPublications()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord claimOnly = retainedRecord("classifier-claim-only");
    deleteEvidence(claimOnly, SqliteProtectedBookPairPublicationEvidenceKind.INTENT);
    deleteEvidence(claimOnly, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY);

    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            claimOnly.bookTargetPath,
            claimOnly.secretTargetPath,
            Map.of(claimOnly.pairId, claimOnly)));

    SqliteProtectedBookPairPublicationRecord claimOnlyWithVisibleSecret =
        retainedRecord("classifier-claim-only-visible-secret");
    deleteEvidence(
        claimOnlyWithVisibleSecret, SqliteProtectedBookPairPublicationEvidenceKind.INTENT);
    deleteEvidence(
        claimOnlyWithVisibleSecret, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY);
    Files.createLink(
        claimOnlyWithVisibleSecret.secretTargetPath, claimOnlyWithVisibleSecret.secretStagePath);
    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            claimOnlyWithVisibleSecret.bookTargetPath,
            claimOnlyWithVisibleSecret.secretTargetPath,
            Map.of(claimOnlyWithVisibleSecret.pairId, claimOnlyWithVisibleSecret)));

    SqliteProtectedBookPairPublicationRecord completedRekey =
        rekeyRecord("classifier-completed-rekey");
    Files.delete(completedRekey.bookTargetPath);
    Files.createLink(completedRekey.bookTargetPath, completedRekey.bookStagePath);
    Files.createLink(completedRekey.secretTargetPath, completedRekey.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        completedRekey, (ignoredStep, ignoredParent) -> {});

    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            completedRekey.bookTargetPath,
            completedRekey.secretTargetPath,
            Map.of(completedRekey.pairId, completedRekey)));

    SqliteProtectedBookPairPublicationRecord completedBackup =
        retainedRecord("classifier-completed-unrelated-backup");
    Files.createLink(completedBackup.bookTargetPath, completedBackup.bookStagePath);
    Files.createLink(completedBackup.secretTargetPath, completedBackup.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        completedBackup, (ignoredStep, ignoredParent) -> {});

    assertEquals(
        SqlitePairPublicationEvidenceAbsent.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            completedBackup.bookTargetPath.resolveSibling("unrelated.sqlite"),
            completedBackup.secretTargetPath,
            Map.of(completedBackup.pairId, completedBackup)));
  }

  @Test
  void evidenceClassifierDistinguishesPendingIncompleteAndCompletionUncertainEvidence()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord pending =
        pairRecord("classifier-pending-other-target", false);

    assertInstanceOf(
        SqlitePairPublicationEvidenceOtherPending.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            pending.bookTargetPath.resolveSibling("other-book.sqlite"),
            pending.secretTargetPath.resolveSibling("other-book.key"),
            Map.of(pending.pairId, pending)));

    SqliteProtectedBookPairPublicationRecord incompleteClaim =
        retainedRecord("classifier-incomplete-claim-terminal");
    Files.writeString(
        incompleteClaim
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM)
            .getFirst(),
        "changed claim evidence");

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            incompleteClaim.bookTargetPath,
            incompleteClaim.secretTargetPath,
            Map.of(incompleteClaim.pairId, incompleteClaim)));

    SqliteProtectedBookPairPublicationRecord incompleteCompletion =
        retainedRecord("classifier-incomplete-completion");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        incompleteCompletion, (ignoredStep, ignoredParent) -> {});
    Files.createLink(incompleteCompletion.bookTargetPath, incompleteCompletion.bookStagePath);
    Files.createLink(incompleteCompletion.secretTargetPath, incompleteCompletion.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        incompleteCompletion, (ignoredStep, ignoredParent) -> {});
    Files.writeString(
        incompleteCompletion
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED)
            .getFirst(),
        "changed completed evidence");
    Files.delete(incompleteCompletion.secretTargetPath);

    assertInstanceOf(
        SqlitePairPublicationEvidenceExactIncomplete.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            incompleteCompletion.bookTargetPath,
            incompleteCompletion.secretTargetPath,
            Map.of(incompleteCompletion.pairId, incompleteCompletion)));

    SqliteProtectedBookPairPublicationRecord partialCompleted =
        retainedRecord("classifier-partial-completed");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        partialCompleted, (ignoredStep, ignoredParent) -> {});
    Files.createLink(partialCompleted.bookTargetPath, partialCompleted.bookStagePath);

    assertInstanceOf(
        SqlitePairPublicationEvidenceExact.class,
        SqlitePairPublicationEvidenceClassifier.classify(
            partialCompleted.bookTargetPath,
            partialCompleted.secretTargetPath,
            Map.of(partialCompleted.pairId, partialCompleted)));

    SqliteProtectedBookPairPublicationRecord claimOnlyWithVisibleBook =
        retainedRecord("classifier-claim-only-visible-book");
    deleteEvidence(claimOnlyWithVisibleBook, SqliteProtectedBookPairPublicationEvidenceKind.INTENT);
    deleteEvidence(
        claimOnlyWithVisibleBook, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY);
    Files.createLink(
        claimOnlyWithVisibleBook.bookTargetPath, claimOnlyWithVisibleBook.bookStagePath);

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            claimOnlyWithVisibleBook.bookTargetPath,
            claimOnlyWithVisibleBook.secretTargetPath,
            Map.of(claimOnlyWithVisibleBook.pairId, claimOnlyWithVisibleBook)));

    SqliteProtectedBookPairPublicationRecord incompleteIntent =
        retainedRecord("classifier-incomplete-intent-terminal");
    Files.writeString(
        incompleteIntent
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.INTENT)
            .getFirst(),
        "changed intent evidence");

    assertEquals(
        SqlitePairPublicationEvidenceUnsafe.INSTANCE,
        SqlitePairPublicationEvidenceClassifier.classify(
            incompleteIntent.bookTargetPath,
            incompleteIntent.secretTargetPath,
            Map.of(incompleteIntent.pairId, incompleteIntent)));
  }

  @Test
  void evidenceStatusRejectsAParseableEnvelopeWithTheWrongKindAndPinsEachDurabilityBarrier()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord record = retainedRecord("evidence-status");
    Path claimPath =
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst();
    Path intentPath =
        record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.INTENT).getFirst();

    assertTrue(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    Files.writeString(claimPath, Files.readString(intentPath));
    assertFalse(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    IOException wrongKind =
        assertThrows(
            IOException.class,
            () ->
                SqlitePairPublicationEvidenceStatus.requireExact(
                    record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM, claimPath));
    assertTrue(
        Objects.requireNonNull(wrongKind.getMessage(), "wrong-kind evidence message")
            .contains("evidence changed"));

    SqliteProtectedBookPairPublicationRecord malformed =
        retainedRecord("evidence-status-malformed");
    Path malformedClaim =
        malformed.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst();
    Files.writeString(malformedClaim, "not protected-book recovery evidence");
    assertTrue(
        SqlitePairPublicationEvidenceStatus.hasObserved(
            malformed, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertFalse(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            malformed, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertThrows(
        IOException.class,
        () ->
            SqlitePairPublicationEvidenceStatus.requireComplete(
                malformed, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));

    SqliteProtectedBookPairPublicationRecord mismatched =
        retainedRecord("evidence-status-mismatch");
    SqliteProtectedBookPairPublicationRecord differentRecord =
        retainedRecord("evidence-status-different-record");
    Path mismatchedClaim =
        mismatched.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst();
    Path differentClaim =
        differentRecord
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM)
            .getFirst();
    Files.writeString(mismatchedClaim, Files.readString(differentClaim));
    assertFalse(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            mismatched, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertThrows(
        IOException.class,
        () ->
            SqlitePairPublicationEvidenceStatus.requireExact(
                mismatched, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM, mismatchedClaim));

    SqliteProtectedBookPairPublicationRecord samePathDifferentRecord =
        retainedRecord("evidence-status-same-path-different-record");
    Path samePathDifferentClaim =
        samePathDifferentRecord
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM)
            .getFirst();
    SqliteProtectedBookPairPublicationRecord alteredImmutableRecord =
        withChangedBookDigest(samePathDifferentRecord);
    Files.writeString(
        samePathDifferentClaim,
        SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
            alteredImmutableRecord, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertFalse(
        SqlitePairPublicationEvidenceStatus.hasComplete(
            samePathDifferentRecord, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertThrows(
        IOException.class,
        () ->
            SqlitePairPublicationEvidenceStatus.requireExact(
                samePathDifferentRecord,
                SqliteProtectedBookPairPublicationEvidenceKind.CLAIM,
                samePathDifferentClaim));

    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.PAIR_STAGE_CLAIM,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));
    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_INTENT,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.INTENT));
    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep.RECOVERY_RECORD,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY));
    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
            .PREPUBLICATION_RETENTION,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.RETAINED));
    assertEquals(
        SqliteProtectedBookPublicationSupport.PairPublicationDurabilityStep
            .RECOVERY_TERMINAL_RETENTION,
        SqlitePairPublicationEvidenceStatus.durabilityStep(
            SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED));
  }

  @Test
  void evidenceStateDistinguishesClaimOnlyAndIncompleteRetainedOrCompletedEvidence()
      throws Exception {
    SqliteProtectedBookPairPublicationRecord claimOnly = retainedRecord("evidence-state-claim");
    assertFalse(SqlitePairPublicationEvidenceState.isCompleteClaimOnly(claimOnly));
    assertFalse(SqlitePairPublicationEvidenceState.hasNoAuthorizationEvidence(claimOnly));

    deleteEvidence(claimOnly, SqliteProtectedBookPairPublicationEvidenceKind.INTENT);
    assertFalse(SqlitePairPublicationEvidenceState.isCompleteClaimOnly(claimOnly));
    assertFalse(SqlitePairPublicationEvidenceState.hasNoAuthorizationEvidence(claimOnly));

    deleteEvidence(claimOnly, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY);
    assertTrue(SqlitePairPublicationEvidenceState.isCompleteClaimOnly(claimOnly));
    assertTrue(SqlitePairPublicationEvidenceState.hasNoAuthorizationEvidence(claimOnly));

    SqliteProtectedBookPairPublicationRecord retainedClaim =
        retainedRecord("evidence-state-retained-claim");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        retainedClaim, (ignoredStep, ignoredParent) -> {});
    deleteEvidence(retainedClaim, SqliteProtectedBookPairPublicationEvidenceKind.INTENT);
    deleteEvidence(retainedClaim, SqliteProtectedBookPairPublicationEvidenceKind.RECOVERY);
    assertFalse(SqlitePairPublicationEvidenceState.isCompleteClaimOnly(retainedClaim));

    SqliteProtectedBookPairPublicationRecord incompleteRetained =
        retainedRecord("evidence-state-retained");
    SqliteProtectedBookPairPublicationEvidenceLifecycle.retainPrepublication(
        incompleteRetained, (ignoredStep, ignoredParent) -> {});
    Files.writeString(
        incompleteRetained
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.RETAINED)
            .getFirst(),
        "changed retained evidence");
    assertTrue(SqlitePairPublicationEvidenceState.retainedEvidenceIsIncomplete(incompleteRetained));

    SqliteProtectedBookPairPublicationRecord incompleteCompleted =
        retainedRecord("evidence-state-completed");
    Files.createLink(incompleteCompleted.bookTargetPath, incompleteCompleted.bookStagePath);
    Files.createLink(incompleteCompleted.secretTargetPath, incompleteCompleted.secretStagePath);
    SqliteProtectedBookPairPublicationEvidenceLifecycle.confirmCompletedPublication(
        incompleteCompleted, (ignoredStep, ignoredParent) -> {});
    Files.writeString(
        incompleteCompleted
            .evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.COMPLETED)
            .getFirst(),
        "changed completed evidence");
    assertTrue(
        SqlitePairPublicationEvidenceState.completionEvidenceIsIncomplete(incompleteCompleted));
  }
}
