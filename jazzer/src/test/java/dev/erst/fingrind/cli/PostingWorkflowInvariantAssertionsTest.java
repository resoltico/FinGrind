package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.jazzer.support.PostingWorkflowInvariantAssertions;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Directly proves the shared posting-workflow invariant owner used by fuzz and replay. */
class PostingWorkflowInvariantAssertionsTest {
  @Test
  void accepted_invariants_accept_matching_shapes() {
    PostEntryCommand command = basicCommand();
    PreflightAccepted accepted =
        new PreflightAccepted(
            command.requestProvenance().idempotencyKey(), command.journalEntry().effectiveDate());
    Committed committed = committed(command, "posting-1");
    PostingFact postingFact = postingFact(command, "posting-1");
    CommitRejected duplicateRejected =
        new CommitRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.DuplicateIdempotencyKey());

    assertDoesNotThrow(() -> PostingWorkflowInvariantAssertions.verifyDeclaredAccountListing(2, 2));
    assertDoesNotThrow(
        () ->
            PostingWorkflowInvariantAssertions.assertAccountReactivationPersisted(
                List.of(
                    SqliteRoundTripWorkflowTestSupport.declaredAccount(
                        new AccountCode("1000"), true)),
                SqliteRoundTripWorkflowTestSupport.declaredAccount(
                    new AccountCode("1000"), false)));
    assertDoesNotThrow(
        () -> PostingWorkflowInvariantAssertions.verifyAcceptedPreflight(accepted, command));
    assertEquals(
        committed,
        PostingWorkflowInvariantAssertions.requireCommittedAfterAcceptedPreflight(committed));
    assertEquals(
        postingFact,
        PostingWorkflowInvariantAssertions.requireStoredPosting(Optional.of(postingFact)));
    assertDoesNotThrow(
        () ->
            PostingWorkflowInvariantAssertions.verifyStoredPosting(
                postingFact, committed, command));
    assertEquals(
        duplicateRejected,
        PostingWorkflowInvariantAssertions.requireDuplicateRejection(duplicateRejected));
  }

  @Test
  void accepted_invariants_reject_mismatches() {
    PostEntryCommand command = basicCommand();
    PostEntryCommand reversalCommand = reversalCommand();
    Committed committed = committed(command, "posting-1");

    assertThrows(
        IllegalStateException.class,
        () -> PostingWorkflowInvariantAssertions.verifyDeclaredAccountListing(1, 2));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertAccountReactivationPersisted(
                List.of(
                    SqliteRoundTripWorkflowTestSupport.declaredAccount(
                        new AccountCode("2000"), true)),
                SqliteRoundTripWorkflowTestSupport.declaredAccount(
                    new AccountCode("1000"), false)));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertAccountReactivationPersisted(
                List.of(
                    SqliteRoundTripWorkflowTestSupport.declaredAccount(
                        new AccountCode("1000"), false)),
                SqliteRoundTripWorkflowTestSupport.declaredAccount(
                    new AccountCode("1000"), false)));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyAcceptedPreflight(
                new PreflightAccepted(
                    reversalCommand.requestProvenance().idempotencyKey(),
                    command.journalEntry().effectiveDate()),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyAcceptedPreflight(
                new PreflightAccepted(
                    command.requestProvenance().idempotencyKey(),
                    command.journalEntry().effectiveDate().plusDays(1)),
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.requireCommittedAfterAcceptedPreflight(
                new CommitRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.BookNotInitialized())));
    assertThrows(
        IllegalStateException.class,
        () -> PostingWorkflowInvariantAssertions.requireStoredPosting(Optional.empty()));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyStoredPosting(
                postingFact(command, "posting-2"), committed, command));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyStoredPosting(
                postingFact(
                    "posting-1",
                    reversalCommand.journalEntry(),
                    command.postingLineage(),
                    command.evidence(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant(),
                    command.sourceChannel()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyStoredPosting(
                postingFact(
                    "posting-1",
                    command.journalEntry(),
                    reversalCommand.postingLineage(),
                    command.evidence(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant(),
                    command.sourceChannel()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyStoredPosting(
                postingFact(
                    "posting-1",
                    command.journalEntry(),
                    command.postingLineage(),
                    command.evidence(),
                    reversalCommand.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant(),
                    command.sourceChannel()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyStoredPosting(
                postingFact(
                    "posting-1",
                    command.journalEntry(),
                    command.postingLineage(),
                    command.evidence(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant().plusSeconds(1),
                    command.sourceChannel()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyStoredPosting(
                postingFact(
                    "posting-1",
                    command.journalEntry(),
                    command.postingLineage(),
                    command.evidence(),
                    command.requestProvenance(),
                    CliFuzzFixtures.fixedClock().instant(),
                    alternateSourceChannel()),
                committed,
                command));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.requireDuplicateRejection(
                committed(command, "posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.requireDuplicateRejection(
                new CommitRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.BookNotInitialized())));
  }

  @Test
  void rejected_invariants_accept_matching_shapes() {
    PostEntryCommand command = basicCommand();
    AccountCode accountCode = new AccountCode("1000");
    PreflightRejected deterministicRejected =
        new PreflightRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.BookNotInitialized());
    CommitRejected deterministicCommitRejected =
        new CommitRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.BookNotInitialized());
    PreflightRejected duplicateRejected =
        new PreflightRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.DuplicateIdempotencyKey());
    CommitRejected duplicateCommitRejected =
        new CommitRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.DuplicateIdempotencyKey());
    PreflightRejected preflightAccountRejected =
        accountStateRejected(command, new PostingRejection.UnknownAccount(accountCode));
    CommitRejected commitAccountRejected =
        accountStateCommitRejected(command, new PostingRejection.UnknownAccount(accountCode));

    assertEquals(
        deterministicRejected,
        PostingWorkflowInvariantAssertions.assertRejected(
            deterministicRejected, PostingRejection.BookNotInitialized.class));
    assertEquals(
        deterministicCommitRejected,
        PostingWorkflowInvariantAssertions.assertRejected(
            deterministicCommitRejected, PostingRejection.BookNotInitialized.class));
    assertEquals(
        preflightAccountRejected,
        PostingWorkflowInvariantAssertions.assertAccountStateRejected(
            preflightAccountRejected, PostingRejection.UnknownAccount.class));
    assertEquals(
        commitAccountRejected,
        PostingWorkflowInvariantAssertions.assertAccountStateRejected(
            commitAccountRejected, PostingRejection.UnknownAccount.class));
    assertEquals(
        duplicateCommitRejected,
        PostingWorkflowInvariantAssertions.verifyRejectedPreflightAndCommit(
            duplicateRejected, duplicateCommitRejected));
    assertDoesNotThrow(
        () ->
            PostingWorkflowInvariantAssertions.assertRejectedStateDidNotPersistPosting(
                Optional.empty()));
  }

  @Test
  void rejected_invariants_reject_mismatches() {
    PostEntryCommand command = basicCommand();
    AccountCode accountCode = new AccountCode("1000");
    PreflightRejected duplicateRejected =
        new PreflightRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.DuplicateIdempotencyKey());
    PreflightRejected mixedAccountRejected =
        new PreflightRejected(
            command.requestProvenance().idempotencyKey(),
            new PostingRejection.AccountStateViolations(
                List.of(
                    new PostingRejection.UnknownAccount(accountCode),
                    new PostingRejection.InactiveAccount(accountCode))));

    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyRejectedPreflightAndCommit(
                duplicateRejected, committed(command, "posting-1")));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.verifyRejectedPreflightAndCommit(
                duplicateRejected,
                new CommitRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.BookNotInitialized())));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertRejectedStateDidNotPersistPosting(
                Optional.of(postingFact(command, "posting-1"))));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertRejected(
                new PreflightAccepted(
                    command.requestProvenance().idempotencyKey(),
                    command.journalEntry().effectiveDate()),
                PostingRejection.BookNotInitialized.class));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertRejected(
                new PreflightRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.DuplicateIdempotencyKey()),
                PostingRejection.BookNotInitialized.class));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertRejected(
                committed(command, "posting-1"), PostingRejection.BookNotInitialized.class));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertRejected(
                new CommitRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.BookNotInitialized()),
                PostingRejection.DuplicateIdempotencyKey.class));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertAccountStateRejected(
                new PreflightAccepted(
                    command.requestProvenance().idempotencyKey(),
                    command.journalEntry().effectiveDate()),
                PostingRejection.UnknownAccount.class));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertAccountStateRejected(
                committed(command, "posting-1"), PostingRejection.UnknownAccount.class));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertAccountStateRejected(
                new PreflightRejected(
                    command.requestProvenance().idempotencyKey(),
                    new PostingRejection.BookNotInitialized()),
                PostingRejection.UnknownAccount.class));
    assertThrows(
        IllegalStateException.class,
        () ->
            PostingWorkflowInvariantAssertions.assertAccountStateRejected(
                mixedAccountRejected, PostingRejection.UnknownAccount.class));
  }

  private static PostEntryCommand basicCommand() {
    return SqliteRoundTripWorkflowTestSupport.basicValidCommand();
  }

  private static PostEntryCommand reversalCommand() {
    return CliFuzzFixtures.readPostEntryCommand(
        CliFuzzHarnessTestSupport.reversalTargetMissingRequest().getBytes(UTF_8));
  }

  private static Committed committed(PostEntryCommand command, String postingId) {
    return new Committed(
        new PostingId(postingId),
        command.requestProvenance().idempotencyKey(),
        command.journalEntry().effectiveDate(),
        CliFuzzFixtures.fixedClock().instant());
  }

  private static PostingFact postingFact(PostEntryCommand command, String postingId) {
    return postingFact(
        postingId,
        command.journalEntry(),
        command.postingLineage(),
        command.evidence(),
        command.requestProvenance(),
        CliFuzzFixtures.fixedClock().instant(),
        command.sourceChannel());
  }

  private static PostingFact postingFact(
      String postingId,
      dev.erst.fingrind.core.JournalEntry journalEntry,
      PostingLineage postingLineage,
      dev.erst.fingrind.core.AccountingEvidence evidence,
      RequestProvenance requestProvenance,
      Instant recordedAt,
      SourceChannel sourceChannel) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry,
        postingLineage,
        PostingKind.STANDARD,
        evidence,
        new CommittedProvenance(requestProvenance, recordedAt, sourceChannel));
  }

  private static PreflightRejected accountStateRejected(
      PostEntryCommand command, PostingRejection.AccountStateViolation violation) {
    return new PreflightRejected(
        command.requestProvenance().idempotencyKey(),
        new PostingRejection.AccountStateViolations(List.of(violation)));
  }

  private static CommitRejected accountStateCommitRejected(
      PostEntryCommand command, PostingRejection.AccountStateViolation violation) {
    return new CommitRejected(
        command.requestProvenance().idempotencyKey(),
        new PostingRejection.AccountStateViolations(List.of(violation)));
  }

  private static SourceChannel alternateSourceChannel() {
    return SourceChannel.SYSTEM;
  }
}
