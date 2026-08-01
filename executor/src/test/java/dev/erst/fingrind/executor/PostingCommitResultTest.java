package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.existingPosting;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.spi.PlanPostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the attestation result invariant on ordinary durable posting commits. */
class PostingCommitResultTest {
  @Test
  void keepsThePlanChildOutcomeFamilyDistinctFromAnImmediateAppend() {
    AttestationVerification verification =
        new AttestationVerification(
            UUID.randomUUID(), BigInteger.ONE, new byte[32], new byte[32], List.of());
    PostingCommitResult replay =
        new PostingCommitResult.Replayed(existingPosting("posting-1", "idem-1"));
    PlanPostingCommitResult deferred =
        new PlanPostingCommitResult.Deferred(existingPosting("posting-2", "idem-2"));
    PostingCommitResult appended =
        new PostingCommitResult.Appended(
            existingPosting("posting-3", "idem-3"),
            new AttestationAppendOutcome.Appended(verification));

    assertInstanceOf(PostingCommitResult.Replayed.class, replay);
    assertInstanceOf(PlanPostingCommitResult.Deferred.class, deferred);
    assertInstanceOf(PostingCommitResult.Appended.class, appended);
  }

  @Test
  void preservesReplayAndRejectionPlanChildOutcomes() {
    PlanPostingCommitResult replayed =
        new PlanPostingCommitResult.Replayed(existingPosting("posting-replay", "idem-replay"));
    PlanPostingCommitResult rejected =
        new PlanPostingCommitResult.Rejected(new BookkeepingPostingRejection.BookNotInitialized());

    assertInstanceOf(PlanPostingCommitResult.Replayed.class, replayed);
    assertInstanceOf(PlanPostingCommitResult.Rejected.class, rejected);
  }
}
