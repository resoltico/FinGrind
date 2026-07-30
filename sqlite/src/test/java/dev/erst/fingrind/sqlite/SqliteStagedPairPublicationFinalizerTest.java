package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Proves terminal pair-publication outcomes retain authority at every evidence boundary. */
class SqliteStagedPairPublicationFinalizerTest extends SqliteArtifactPublicationTestSupport {

  @Test
  void finishPostRecoveryFailure_preservesTheUnattemptedEvidenceBlockedOutcome() throws Exception {
    Fixture fixture = fixture("unattempted");

    StagedPairPublicationCommitOutcome outcome =
        fixture
            .finalizer()
            .finishPostRecoveryFailure(
                new SqlitePairPublicationMemberAttempt(),
                new SqlitePairPublicationMemberAttempt(),
                false);

    assertInstanceOf(ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class, outcome);
    assertEquals(1, fixture.reservationCloses().get());
    assertEquals(1, fixture.passphraseCloses().get());
    assertTrue(fixture.finalizer().isFinished());
    assertTrue(Files.exists(fixture.bookStage().stagedPath()));
    assertTrue(Files.exists(fixture.secretStage().stagedPath()));
  }

  @Test
  void finishDurablyRetainedPrepublication_fallsBackToEvidenceBlockedWhenEvidenceCannotBeRetained()
      throws Exception {
    Fixture fixture = fixture("retention-failure");
    fixture.finalizer().recordRecoveryBoundary(fixture.record());

    StagedPairPublicationCommitOutcome outcome =
        fixture.finalizer().finishDurablyRetainedPrepublication();

    assertInstanceOf(ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked.class, outcome);
    assertEquals(1, fixture.reservationCloses().get());
    assertEquals(1, fixture.passphraseCloses().get());
    assertTrue(fixture.finalizer().isFinished());
    assertTrue(Files.exists(fixture.bookStage().stagedPath()));
    assertTrue(Files.exists(fixture.secretStage().stagedPath()));
  }

  @Test
  void finishAfterSuccessfulPublication_retainsBothStagesWhenCompletionEvidenceCannotBeConfirmed()
      throws Exception {
    Fixture fixture = fixture("completion-evidence-failure");
    fixture.finalizer().recordRecoveryBoundary(fixture.record());

    ProtectedBookPairPublicationFailureOutcome.CompletionUncertain outcome =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
            fixture.finalizer().finishAfterSuccessfulPublication());

    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE, outcome.bookArtifactState());
    assertEquals(
        ProtectedBookPairPublicationMemberState.PUBLISHED_DURABLE, outcome.secretArtifactState());
    assertEquals(1, fixture.reservationCloses().get());
    assertEquals(1, fixture.passphraseCloses().get());
    assertTrue(fixture.finalizer().isFinished());
    assertTrue(Files.exists(fixture.bookStage().stagedPath()));
    assertTrue(Files.exists(fixture.secretStage().stagedPath()));
  }

  @Test
  void finishAfterPreBoundaryFailure_releasesOnlyLocalAuthorityWhenNoRecoveryBoundaryExists()
      throws Exception {
    Fixture fixture = fixture("pre-boundary");

    fixture.finalizer().finishAfterPreBoundaryFailure();

    assertEquals(1, fixture.reservationCloses().get());
    assertEquals(1, fixture.passphraseCloses().get());
    assertTrue(fixture.finalizer().isFinished());
    assertTrue(Files.exists(fixture.bookStage().stagedPath()));
    assertTrue(Files.exists(fixture.secretStage().stagedPath()));
    assertNotEquals(fixture.bookStage().stagedPath(), fixture.secretStage().stagedPath());
  }

  @Test
  void finishAfterPreBoundaryFailure_retainsStagesWhenTheRecoveryBoundaryMayExist()
      throws Exception {
    Fixture fixture = fixture("recovery-boundary");
    fixture.finalizer().recordRecoveryBoundary(fixture.record());

    fixture.finalizer().finishAfterPreBoundaryFailure();

    assertEquals(1, fixture.reservationCloses().get());
    assertEquals(1, fixture.passphraseCloses().get());
    assertTrue(fixture.finalizer().isFinished());
    assertTrue(Files.exists(fixture.bookStage().stagedPath()));
    assertTrue(Files.exists(fixture.secretStage().stagedPath()));
  }

  @Test
  void finishCompletionUncertain_reportsReservationReleaseFailureWithoutDiscardingEvidence()
      throws Exception {
    Fixture fixture =
        fixture(
            "uncertain-reservation-release",
            () -> {
              throw new IllegalStateException("reservation close failed");
            });

    ProtectedBookPairPublicationFailureOutcome.CompletionUncertain outcome =
        assertInstanceOf(
            ProtectedBookPairPublicationFailureOutcome.CompletionUncertain.class,
            fixture
                .finalizer()
                .finishCompletionUncertain(
                    ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
                    ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN));

    assertEquals(
        ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN, outcome.secretArtifactState());
    assertEquals(1, fixture.reservationCloses().get());
    assertEquals(1, fixture.passphraseCloses().get());
    assertTrue(fixture.finalizer().isFinished());
    assertTrue(Files.exists(fixture.bookStage().stagedPath()));
    assertTrue(Files.exists(fixture.secretStage().stagedPath()));
  }

  @Test
  void finishAfterSuccessfulPublication_keepsPublishedResultWhenReservationReleaseFails()
      throws Exception {
    Fixture fixture =
        fixture(
            "published-reservation-release",
            () -> {
              throw new IllegalStateException("reservation close failed");
            });
    Files.createLink(fixture.record().bookTargetPath, fixture.bookStage().stagedPath());
    Files.createLink(fixture.record().secretTargetPath, fixture.secretStage().stagedPath());
    SqliteProtectedBookPairPublicationRecord completeRecord =
        SqliteProtectedBookPairPublicationRecord.create(
            fixture.record().bookTargetPath,
            fixture.record().secretTargetPath,
            fixture.bookStage().stagedPath(),
            fixture.secretStage().stagedPath(),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            backupBinding(fixture.record().bookTargetPath.resolveSibling("source.sqlite")),
            (ignoredStep, ignoredParent) -> {});
    fixture.finalizer().recordRecoveryBoundary(completeRecord);

    StagedPairPublicationCommitOutcome outcome =
        fixture.finalizer().finishAfterSuccessfulPublication();

    assertInstanceOf(StagedPairPublicationCommitOutcome.Published.class, outcome);
    assertEquals(1, fixture.reservationCloses().get());
    assertEquals(1, fixture.passphraseCloses().get());
    assertTrue(fixture.finalizer().isFinished());
    assertTrue(Files.isSameFile(fixture.record().bookTargetPath, fixture.bookStage().stagedPath()));
    assertTrue(
        Files.isSameFile(fixture.record().secretTargetPath, fixture.secretStage().stagedPath()));
  }

  private Fixture fixture(String name) throws IOException {
    return fixture(name, () -> {});
  }

  private Fixture fixture(String name, Runnable reservationClose) throws IOException {
    Path parent = Files.createDirectory(tempDirectory.resolve(name));
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    Path bookTarget = parent.resolve("published.sqlite");
    Path secretTarget = parent.resolve("published.key");
    SqliteOwnedStagedArtifact bookStage =
        SqliteOwnedStagedArtifact.create(bookTarget, ".book-stage-", ".sqlite");
    SqliteOwnedStagedArtifact secretStage =
        SqliteOwnedStagedArtifact.create(secretTarget, ".secret-stage-", ".key");
    Files.writeString(bookStage.stagedPath(), "staged book", StandardCharsets.UTF_8);
    Files.writeString(secretStage.stagedPath(), "staged secret", StandardCharsets.UTF_8);
    AtomicInteger passphraseCloses = new AtomicInteger();
    AtomicInteger reservationCloses = new AtomicInteger();
    SqliteStagedPairPublicationFinalizer finalizer =
        new SqliteStagedPairPublicationFinalizer(
            bookTarget,
            secretTarget,
            bookStage,
            secretStage,
            passphraseCloses::incrementAndGet,
            () -> {
              reservationCloses.incrementAndGet();
              reservationClose.run();
            },
            (ignoredStep, ignoredParent) -> {},
            "fixture pair publication");
    SqliteProtectedBookPairPublicationRecord record =
        new SqliteProtectedBookPairPublicationRecord(
            new SqliteProtectedBookPairPublicationRecord.Components(
                UUID.randomUUID(),
                new SqliteProtectedBookPairPublicationRecord.PairPaths(
                    bookTarget, secretTarget, bookStage.stagedPath(), secretStage.stagedPath()),
                new SqliteProtectedBookPairPublicationRecord.PairDigests(
                    digest(bookStage.stagedPath()), digest(secretStage.stagedPath()), null),
                RestoredBookTargetPolicy.REQUIRE_ABSENT,
                backupBinding(parent.resolve("source.sqlite"))));
    return new Fixture(
        finalizer, record, bookStage, secretStage, passphraseCloses, reservationCloses);
  }

  private static byte[] digest(Path path) throws IOException {
    try (var input = Files.newInputStream(path)) {
      return SqliteProtectedBookPairPublicationRecord.digest(input, "fixture stage");
    }
  }

  private record Fixture(
      SqliteStagedPairPublicationFinalizer finalizer,
      SqliteProtectedBookPairPublicationRecord record,
      SqliteOwnedStagedArtifact bookStage,
      SqliteOwnedStagedArtifact secretStage,
      AtomicInteger passphraseCloses,
      AtomicInteger reservationCloses) {}
}
