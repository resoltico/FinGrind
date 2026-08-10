package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies transaction internals reject every state and member shape that recovery cannot trust.
 */
class PublicationTransactionEngineInvariantTest {
  private static final Instant NOW = Instant.parse("2026-08-10T12:34:56Z");

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void rejectsRequestsThatDoNotExactlyMatchThePreparedPlan(@TempDir Path temporaryDirectory)
      throws Exception {
    Fixture fixture = fixture(temporaryDirectory);
    PublicationTransactionRequest request = request(fixture.outputDirectory(), "pdf-report");
    PublicationTransactionJournal planned =
        PublicationTransactionPlan.prepare(request, fixture.runtime());
    fixture.repository().create(planned);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            PublicationTransactionStager.stageAll(
                planned,
                new PublicationTransactionRequest(
                    List.of(
                        request.members().getFirst(),
                        member(
                            "book-report",
                            fixture.outputDirectory().resolve("book.fgb"),
                            PublicationMode.REPLACE))),
                fixture.runtime()));
    for (PublicationTransactionRequest mismatch : mismatches(fixture.outputDirectory())) {
      assertThrows(
          IllegalArgumentException.class,
          () -> PublicationTransactionStager.stageAll(planned, mismatch, fixture.runtime()));
    }
    assertThrows(
        IOException.class,
        () -> PublicationTransactionCommitter.commitAll(planned, fixture.runtime()));
    assertThrows(
        IOException.class,
        () -> PublicationTransactionCleaner.cleanAll(planned, fixture.runtime()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PublicationTransactionRunner(fixture.runtime()).continueFrom(planned));

    PublicationTransactionJournal staged =
        PublicationTransactionStager.stageAll(planned, request, fixture.runtime());
    assertThrows(
        IllegalArgumentException.class,
        () -> PublicationTransactionStager.stageAll(staged, request, fixture.runtime()));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void blocksACleanupRecoveryWhoseMembersAreNotAllCommitted(@TempDir Path temporaryDirectory)
      throws Exception {
    Fixture fixture = fixture(temporaryDirectory);
    PublicationTransactionRequest request = request(fixture.outputDirectory(), "pdf-report");
    PublicationTransactionJournal planned =
        PublicationTransactionPlan.prepare(request, fixture.runtime());
    fixture.repository().create(planned);
    PublicationTransactionJournal stagedMembers =
        PublicationTransactionStager.stageAll(planned, request, fixture.runtime());
    PublicationTransactionJournal staged =
        transition(
            fixture.runtime(),
            stagedMembers,
            PublicationTransactionState.STAGED,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.INCOMPLETE));
    PublicationTransactionJournal incomplete =
        transition(
            fixture.runtime(),
            staged,
            PublicationTransactionState.CLEANUP_INCOMPLETE,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.PARTIALLY_COMMITTED,
                PublicationCleanupOutcome.INCOMPLETE));

    PublicationTransactionResult result =
        new PublicationTransactionRunner(fixture.runtime()).recover(incomplete);

    assertEquals(PublicationTransactionState.BLOCKED, result.state());
    assertEquals(
        PublicationTransactionState.BLOCKED,
        new PublicationTransactionRunner(fixture.runtime())
            .recover(fixture.repository().read(result.transactionId()))
            .state());
    assertEquals(
        PublicationTransactionState.BLOCKED,
        new PublicationTransactionRunner(fixture.runtime())
            .continueFrom(fixture.repository().read(result.transactionId()))
            .state());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void rejectsReplacementCleanupWhenASecretStageStillExists(@TempDir Path temporaryDirectory)
      throws Exception {
    Fixture fixture = fixture(temporaryDirectory);
    PublicationTransactionRequest request =
        new PublicationTransactionRequest(
            List.of(
                member(
                    "pdf-report",
                    fixture.outputDirectory().resolve("report.pdf"),
                    PublicationMode.REPLACE)));
    PublicationTransactionJournal planned =
        PublicationTransactionPlan.prepare(request, fixture.runtime());
    fixture.repository().create(planned);
    PublicationTransactionJournal stagedMembers =
        PublicationTransactionStager.stageAll(planned, request, fixture.runtime());
    PublicationTransactionJournal staged =
        transition(
            fixture.runtime(),
            stagedMembers,
            PublicationTransactionState.STAGED,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.INCOMPLETE));
    PublicationTransactionJournal committing =
        transition(
            fixture.runtime(),
            staged,
            PublicationTransactionState.COMMITTING,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.INCOMPLETE));
    PublicationTransactionMember stagedMember = committing.members().getFirst();
    PublicationTransactionArtifactFiles.createNoReplaceHardLink(
        stagedMember.finalPath(), stagedMember.stagePath());
    PublicationTransactionJournal committedMembers =
        fixture
            .runtime()
            .updateMembers(
                committing,
                PublicationTransactionMemberUpdates.committed(
                    committing,
                    0,
                    PublicationTransactionArtifactFiles.finalEvidence(stagedMember.finalPath())),
                PublicationTransactionFaultPoint.MEMBER_COMMITTED);
    PublicationTransactionJournal committed =
        transition(
            fixture.runtime(),
            committedMembers,
            PublicationTransactionState.COMMITTED,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.INCOMPLETE));
    PublicationTransactionJournal cleaning =
        transition(
            fixture.runtime(),
            committed,
            PublicationTransactionState.CLEANING,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.INCOMPLETE));

    assertThrows(
        IOException.class,
        () -> PublicationTransactionCleaner.cleanAll(cleaning, fixture.runtime()));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void completesRecoveryWhenCleanupProgressWasPersistedBeforeAnOrdinaryFailure(
      @TempDir Path temporaryDirectory) throws Exception {
    Fixture interrupted =
        fixture(
            temporaryDirectory,
            point -> {
              if (point == PublicationTransactionFaultPoint.MEMBER_CLEANED) {
                throw new IOException("cleanup progress interruption");
              }
            });
    PublicationTransactionExecutionException exception =
        assertThrows(
            PublicationTransactionExecutionException.class,
            () ->
                interrupted
                    .publisher()
                    .publish(request(interrupted.outputDirectory(), "pdf-report")));
    Fixture recovered = fixture(temporaryDirectory);

    PublicationTransactionResult result =
        new PublicationTransactionRunner(recovered.runtime())
            .recover(recovered.repository().read(exception.result().transactionId()));

    assertEquals(PublicationTransactionState.COMPLETE, result.state());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void preservesCompletedJournalsAndRejectsDivergentCommitEvidence(@TempDir Path temporaryDirectory)
      throws Exception {
    Fixture fixture = fixture(temporaryDirectory);
    PublicationTransactionResult result =
        fixture.publisher().publish(request(fixture.outputDirectory(), "pdf-report"));
    PublicationTransactionJournal complete = fixture.repository().read(result.transactionId());

    assertSame(complete, PublicationTransactionCommitter.commitAll(complete, fixture.runtime()));
    assertEquals(
        PublicationTransactionState.COMPLETE,
        new PublicationTransactionRunner(fixture.runtime()).continueFrom(complete).state());
    assertEquals(
        PublicationTransactionState.COMPLETE,
        fixture
            .publisher()
            .recordFailure(complete, new IOException("late observation"))
            .result()
            .state());
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionCommitter.requireSameArtifact(
                new PublicationTransactionStagedArtifact(
                    Path.of(".stage"), "first", "a".repeat(64)),
                new PublicationTransactionFinalizedArtifact("second", "a".repeat(64))));
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionCommitter.requireSameArtifact(
                new PublicationTransactionStagedArtifact(Path.of(".stage"), "same", "a".repeat(64)),
                new PublicationTransactionFinalizedArtifact("same", "b".repeat(64))));
  }

  private static List<PublicationTransactionRequest> mismatches(Path outputDirectory) {
    return List.of(
        request(outputDirectory, "book-report"),
        new PublicationTransactionRequest(
            List.of(
                new PublicationTransactionMemberRequest(
                    "pdf-report",
                    PublicationTransactionMemberRole.PROTECTED_BOOK,
                    outputDirectory.resolve("report.pdf"),
                    PublicationMode.NO_REPLACE_LINK,
                    new byte[] {1}))),
        new PublicationTransactionRequest(
            List.of(
                member(
                    "pdf-report",
                    outputDirectory.resolve("other.pdf"),
                    PublicationMode.NO_REPLACE_LINK))),
        new PublicationTransactionRequest(
            List.of(
                member(
                    "pdf-report",
                    outputDirectory.resolve("report.pdf"),
                    PublicationMode.REPLACE))));
  }

  private static PublicationTransactionRequest request(Path outputDirectory, String memberId) {
    return new PublicationTransactionRequest(
        List.of(
            member(
                memberId, outputDirectory.resolve("report.pdf"), PublicationMode.NO_REPLACE_LINK)));
  }

  private static PublicationTransactionMemberRequest member(
      String memberId, Path finalPath, PublicationMode mode) {
    return new PublicationTransactionMemberRequest(
        memberId, PublicationTransactionMemberRole.PDF_REPORT, finalPath, mode, new byte[] {1});
  }

  private static PublicationTransactionJournal transition(
      PublicationTransactionRuntime runtime,
      PublicationTransactionJournal journal,
      PublicationTransactionState state,
      PublicationTransactionOutcome outcome)
      throws IOException {
    return runtime.transition(
        journal, state, outcome, PublicationTransactionFaultPoint.JOURNAL_STAGED);
  }

  private static Fixture fixture(Path temporaryDirectory) throws IOException {
    return fixture(temporaryDirectory, PublicationTransactionFaultInjector.NONE);
  }

  private static Fixture fixture(
      Path temporaryDirectory, PublicationTransactionFaultInjector faultInjector)
      throws IOException {
    Path root = privateDirectory(temporaryDirectory);
    Path outputDirectory = privateDirectory(root.resolve("output"));
    PublicationTransactionJournalRepository repository =
        PublicationTransactionJournalRepository.open(root.resolve("journal-store"), ignored -> {});
    PublicationTransactionRuntime runtime =
        new PublicationTransactionRuntime(
            repository, ignored -> {}, InstantSource.fixed(NOW), faultInjector);
    return new Fixture(
        PublicationTransactionPublisher.open(
            repository, ignored -> {}, InstantSource.fixed(NOW), faultInjector),
        repository,
        runtime,
        outputDirectory);
  }

  private static Path privateDirectory(Path directory) throws IOException {
    Files.createDirectories(directory);
    Files.setPosixFilePermissions(
        directory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    return directory;
  }

  private record Fixture(
      PublicationTransactionPublisher publisher,
      PublicationTransactionJournalRepository repository,
      PublicationTransactionRuntime runtime,
      Path outputDirectory) {}
}
