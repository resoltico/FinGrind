package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/** Verifies recovery rejects every journal state that cannot establish a safe owner action. */
class PublicationTransactionRecoverySafetyTest {
  private static final Instant NOW = Instant.parse("2026-08-10T12:34:56Z");

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void recordsEveryInterruptedPublicationPhaseAndResumesCommitUncertainty(
      @TempDir Path temporaryDirectory) throws Exception {
    assertRecorded(
        temporaryDirectory,
        PublicationTransactionState.PREPARED,
        PublicationTransactionState.BLOCKED);
    assertRecorded(
        temporaryDirectory,
        PublicationTransactionState.STAGED,
        PublicationTransactionState.BLOCKED);
    assertRecorded(
        temporaryDirectory,
        PublicationTransactionState.COMMITTING,
        PublicationTransactionState.COMMIT_UNCERTAIN);
    assertRecorded(
        temporaryDirectory,
        PublicationTransactionState.ABORTING,
        PublicationTransactionState.CLEANUP_UNCERTAIN);
    assertRecorded(
        temporaryDirectory,
        PublicationTransactionState.COMMITTED,
        PublicationTransactionState.CLEANUP_INCOMPLETE);
    assertRecorded(
        temporaryDirectory,
        PublicationTransactionState.CLEANING,
        PublicationTransactionState.CLEANUP_UNCERTAIN);

    Fixture fixture = fixture(temporaryDirectory.resolve("commit-uncertain"));
    PublicationTransactionJournal committing =
        journalAt(fixture, PublicationTransactionState.COMMITTING);
    PublicationTransactionJournal uncertain =
        transition(
            fixture.runtime(),
            committing,
            PublicationTransactionState.COMMIT_UNCERTAIN,
            PublicationCommitOutcome.COMMIT_UNCERTAIN);

    assertEquals(
        PublicationTransactionState.COMPLETE,
        new PublicationTransactionRunner(fixture.runtime()).recover(uncertain).state());

    Fixture cleanupFixture = fixture(temporaryDirectory.resolve("cleanup-incomplete"));
    PublicationTransactionJournal committed =
        journalAt(cleanupFixture, PublicationTransactionState.COMMITTED);
    PublicationTransactionJournal incompleteCleanup =
        transition(
            cleanupFixture.runtime(),
            committed,
            PublicationTransactionState.CLEANUP_INCOMPLETE,
            PublicationCommitOutcome.ALL_COMMITTED);

    assertEquals(
        PublicationTransactionState.COMPLETE,
        new PublicationTransactionRunner(cleanupFixture.runtime())
            .recover(incompleteCleanup)
            .state());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void rejectsUnverifiedCollisionAbortsAndRecoversOnlyAProvenExternalCollision(
      @TempDir Path temporaryDirectory) throws Exception {
    Fixture fixture = fixture(temporaryDirectory.resolve("unverified"));
    PublicationTransactionJournal staged = journalAt(fixture, PublicationTransactionState.STAGED);
    PublicationTransactionJournal committing =
        transition(
            fixture.runtime(),
            staged,
            PublicationTransactionState.COMMITTING,
            PublicationCommitOutcome.NONE_COMMITTED);
    PublicationTransactionRunner runner = new PublicationTransactionRunner(fixture.runtime());

    assertThrows(IOException.class, () -> runner.abortNoReplaceCollision(committing));
    assertThrows(IOException.class, () -> runner.abortNoReplaceCollision(staged));
    assertThrows(
        IOException.class,
        () -> PublicationTransactionCleaner.abortNoReplaceCollision(staged, fixture.runtime()));
    assertThrows(
        IOException.class,
        () ->
            PublicationTransactionCleaner.hasVerifiedNoReplaceCollision(
                replacementStaged(fixture(temporaryDirectory.resolve("replacement")))));
    assertThrows(
        IOException.class,
        () -> PublicationTransactionCleaner.hasVerifiedNoReplaceCollision(planned(fixture)));
    PublicationTransactionArtifactFiles.createNoReplaceHardLink(
        staged.members().getFirst().finalPath(), staged.members().getFirst().stagePath());
    assertThrows(
        IOException.class,
        () -> PublicationTransactionCleaner.hasVerifiedNoReplaceCollision(staged));

    Fixture collision = fixture(temporaryDirectory.resolve("verified"));
    writePrivateFile(collision.outputDirectory().resolve("report.pdf"), "unrelated");
    PublicationTransactionJournal collisionStaged =
        journalAt(collision, PublicationTransactionState.STAGED);
    PublicationTransactionJournal incomplete =
        transition(
            collision.runtime(),
            collisionStaged,
            PublicationTransactionState.CLEANUP_INCOMPLETE,
            PublicationCommitOutcome.NONE_COMMITTED);

    assertEquals(
        PublicationTransactionState.BLOCKED,
        new PublicationTransactionRunner(collision.runtime()).recover(incomplete).state());

    Fixture interruptedAbort = fixture(temporaryDirectory.resolve("already-aborted"));
    writePrivateFile(interruptedAbort.outputDirectory().resolve("report.pdf"), "unrelated");
    PublicationTransactionJournal abortingStaged =
        journalAt(interruptedAbort, PublicationTransactionState.STAGED);
    PublicationTransactionJournal aborting =
        transition(
            interruptedAbort.runtime(),
            abortingStaged,
            PublicationTransactionState.COMMITTING,
            PublicationCommitOutcome.NONE_COMMITTED);
    aborting =
        transition(
            interruptedAbort.runtime(),
            aborting,
            PublicationTransactionState.ABORTING,
            PublicationCommitOutcome.NONE_COMMITTED);
    PublicationTransactionMember abortedMember = aborting.members().getFirst();
    Files.delete(abortedMember.stagePath());
    PublicationTransactionJournal aborted =
        interruptedAbort
            .runtime()
            .updateMembers(
                aborting,
                PublicationTransactionMemberUpdates.aborted(aborting, 0),
                PublicationTransactionFaultPoint.MEMBER_ABORTED);
    PublicationTransactionJournal uncertainAbort =
        interruptedAbort
            .runtime()
            .transition(
                aborted,
                PublicationTransactionState.CLEANUP_UNCERTAIN,
                new PublicationTransactionOutcome(
                    PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.UNCERTAIN),
                PublicationTransactionFaultPoint.JOURNAL_CLEANING);

    assertEquals(
        PublicationTransactionState.BLOCKED,
        new PublicationTransactionRunner(interruptedAbort.runtime())
            .recover(uncertainAbort)
            .state());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void blocksALegacyReplacementJournalInsteadOfGuessingItsMissingTargetPreimage(
      @TempDir Path temporaryDirectory) throws Exception {
    Fixture fixture = fixture(temporaryDirectory);
    Path finalPath = fixture.outputDirectory().resolve("report.pdf");
    writePrivateFile(finalPath, "legacy-target");
    PublicationTransactionJournal prepared =
        new PublicationTransactionJournal(
            PublicationTransactionJournal.LEGACY_SCHEMA_VERSION,
            new PublicationTransactionId("0123456789abcdef0123456789abcdea"),
            "fedcba9876543210fedcba9876543210",
            fixture.repository().ownerKeyFingerprint(),
            NOW,
            List.of(
                new PublicationTransactionMember(
                    "pdf-report",
                    PublicationTransactionMemberRole.PDF_REPORT,
                    finalPath,
                    fixture.outputDirectory().resolve(".legacy-replacement-stage"),
                    PrivateOutputDirectory.physicalObjectIdentity(fixture.outputDirectory()),
                    PublicationMode.REPLACE,
                    PublicationTransactionMemberProgress.PLANNED,
                    java.util.Optional.empty(),
                    java.util.Optional.empty())),
            List.of(PublicationTransactionTransition.prepared(NOW)));
    fixture.repository().create(prepared);
    PublicationTransactionJournal stagedMembers =
        PublicationTransactionStager.stageAll(
            prepared,
            request(fixture.outputDirectory(), PublicationMode.REPLACE),
            fixture.runtime());
    PublicationTransactionJournal staged =
        transition(
            fixture.runtime(),
            stagedMembers,
            PublicationTransactionState.STAGED,
            PublicationCommitOutcome.NONE_COMMITTED);

    IOException refusal =
        assertThrows(
            IOException.class,
            () -> new PublicationTransactionRunner(fixture.runtime()).continueFrom(staged));
    PublicationTransactionExecutionException recorded =
        fixture.publisher().recordFailure(staged, refusal);

    assertEquals(PublicationTransactionState.BLOCKED, recorded.result().state());
    assertEquals("legacy-target", Files.readString(finalPath));
    assertEquals(java.util.Optional.empty(), staged.members().getFirst().replacementTarget());
    assertEquals(
        PublicationTransactionState.BLOCKED,
        fixture.publisher().recover(staged.transactionId()).state());
  }

  private static void assertRecorded(
      Path temporaryDirectory,
      PublicationTransactionState state,
      PublicationTransactionState expectedState)
      throws IOException {
    Fixture fixture = fixture(temporaryDirectory.resolve(state.wireValue()));
    PublicationTransactionExecutionException failure =
        fixture.publisher().recordFailure(journalAt(fixture, state), new IOException("failure"));
    assertEquals(expectedState, failure.result().state(), state.wireValue());
  }

  private static PublicationTransactionJournal journalAt(
      Fixture fixture, PublicationTransactionState state) throws IOException {
    PublicationTransactionJournal prepared = planned(fixture);
    if (state == PublicationTransactionState.PREPARED) {
      return prepared;
    }
    PublicationTransactionJournal staged =
        stage(fixture, prepared, PublicationMode.NO_REPLACE_LINK);
    if (state == PublicationTransactionState.STAGED) {
      return staged;
    }
    PublicationTransactionJournal committing =
        transition(
            fixture.runtime(),
            staged,
            PublicationTransactionState.COMMITTING,
            PublicationCommitOutcome.NONE_COMMITTED);
    if (state == PublicationTransactionState.COMMITTING) {
      return committing;
    }
    if (state == PublicationTransactionState.ABORTING) {
      return transition(
          fixture.runtime(),
          committing,
          PublicationTransactionState.ABORTING,
          PublicationCommitOutcome.NONE_COMMITTED);
    }
    PublicationTransactionJournal committedMembers =
        PublicationTransactionCommitter.commitAll(committing, fixture.runtime());
    PublicationTransactionJournal committed =
        transition(
            fixture.runtime(),
            committedMembers,
            PublicationTransactionState.COMMITTED,
            PublicationCommitOutcome.ALL_COMMITTED);
    if (state == PublicationTransactionState.COMMITTED) {
      return committed;
    }
    if (state == PublicationTransactionState.CLEANING) {
      return transition(
          fixture.runtime(),
          committed,
          PublicationTransactionState.CLEANING,
          PublicationCommitOutcome.ALL_COMMITTED);
    }
    throw new IllegalArgumentException("Unsupported publication phase.");
  }

  private static PublicationTransactionJournal replacementStaged(Fixture fixture)
      throws IOException {
    return stage(fixture, planned(fixture), PublicationMode.REPLACE);
  }

  private static PublicationTransactionJournal planned(Fixture fixture) throws IOException {
    PublicationTransactionJournal journal =
        PublicationTransactionPlan.prepare(
            request(fixture.outputDirectory(), PublicationMode.NO_REPLACE_LINK), fixture.runtime());
    fixture.repository().create(journal);
    return journal;
  }

  private static PublicationTransactionJournal stage(
      Fixture fixture, PublicationTransactionJournal journal, PublicationMode mode)
      throws IOException {
    PublicationTransactionJournal prepared = journal;
    if (prepared.members().getFirst().publicationMode() != mode) {
      Path finalPath = fixture.outputDirectory().resolve("report.pdf");
      if (mode == PublicationMode.REPLACE && Files.notExists(finalPath)) {
        writePrivateFile(finalPath, "replacement-target");
      }
      prepared =
          PublicationTransactionPlan.prepare(
              request(fixture.outputDirectory(), mode), fixture.runtime());
      fixture.repository().create(prepared);
    }
    PublicationTransactionJournal stagedMembers =
        PublicationTransactionStager.stageAll(
            prepared, request(fixture.outputDirectory(), mode), fixture.runtime());
    return transition(
        fixture.runtime(),
        stagedMembers,
        PublicationTransactionState.STAGED,
        PublicationCommitOutcome.NONE_COMMITTED);
  }

  private static PublicationTransactionJournal transition(
      PublicationTransactionRuntime runtime,
      PublicationTransactionJournal journal,
      PublicationTransactionState state,
      PublicationCommitOutcome commit)
      throws IOException {
    return runtime.transition(
        journal,
        state,
        new PublicationTransactionOutcome(commit, PublicationCleanupOutcome.INCOMPLETE),
        PublicationTransactionFaultPoint.JOURNAL_STAGED);
  }

  private static PublicationTransactionRequest request(Path outputDirectory, PublicationMode mode) {
    return new PublicationTransactionRequest(
        List.of(
            new PublicationTransactionMemberRequest(
                "pdf-report",
                PublicationTransactionMemberRole.PDF_REPORT,
                outputDirectory.resolve("report.pdf"),
                mode,
                new byte[] {1})));
  }

  private static Fixture fixture(Path root) throws IOException {
    Path privateRoot = privateDirectory(root);
    Path outputDirectory = privateDirectory(privateRoot.resolve("output"));
    PublicationTransactionJournalRepository repository =
        PublicationTransactionJournalRepository.open(
            privateRoot.resolve("journal-store"), ignored -> {});
    PublicationTransactionRuntime runtime =
        new PublicationTransactionRuntime(
            repository,
            ignored -> {},
            InstantSource.fixed(NOW),
            PublicationTransactionFaultInjector.NONE);
    return new Fixture(
        PublicationTransactionPublisher.open(
            repository,
            ignored -> {},
            InstantSource.fixed(NOW),
            PublicationTransactionFaultInjector.NONE),
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

  private static void writePrivateFile(Path path, String content) throws IOException {
    try (PrivateOutputFile.OpenedFile opened = PrivateOutputFile.createNew(path)) {
      PublicationTransactionArtifactFiles.writeExactly(
          opened, content.getBytes(StandardCharsets.UTF_8));
      opened.force();
    }
  }

  private record Fixture(
      PublicationTransactionPublisher publisher,
      PublicationTransactionJournalRepository repository,
      PublicationTransactionRuntime runtime,
      Path outputDirectory) {}
}
