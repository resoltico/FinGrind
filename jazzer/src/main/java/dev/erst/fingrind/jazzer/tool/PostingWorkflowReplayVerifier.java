package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import java.util.Optional;

/** Shared invariant checks for in-memory posting-workflow replay. */
final class PostingWorkflowReplayVerifier {
  private PostingWorkflowReplayVerifier() {}

  static void verifyDeclaredAccountListing(int listedAccountCount, int declaredAccountCount) {
    if (listedAccountCount != declaredAccountCount) {
      throw new IllegalStateException("Declared-account listing drifted from setup declarations.");
    }
  }

  static void verifyAcceptedPreflight(PreflightAccepted accepted, PostEntryCommand command) {
    if (!accepted.idempotencyKey().equals(command.requestProvenance().idempotencyKey())) {
      throw new IllegalStateException("Preflight changed the idempotency key.");
    }
    if (!accepted.effectiveDate().equals(command.journalEntry().effectiveDate())) {
      throw new IllegalStateException("Preflight changed the effective date.");
    }
  }

  static PostingFact requireStoredPosting(Optional<PostingFact> storedPosting) {
    if (storedPosting.isEmpty()) {
      throw new IllegalStateException("Committed posting fact was not persisted.");
    }
    return storedPosting.orElseThrow();
  }

  static void verifyStoredPosting(
      PostingFact postingFact, Committed committed, PostEntryCommand command) {
    if (!postingFact.postingId().equals(committed.postingId())) {
      throw new IllegalStateException("Stored posting id differs from the commit result.");
    }
    if (!postingFact.journalEntry().equals(command.journalEntry())) {
      throw new IllegalStateException("Stored journal entry differs from the parsed command.");
    }
    if (!postingFact.reversalReference().equals(command.reversalReference())) {
      throw new IllegalStateException("Stored reversal differs from the parsed command.");
    }
    if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
      throw new IllegalStateException("Stored request provenance differs from the parsed command.");
    }
    if (!postingFact.provenance().recordedAt().equals(CliFuzzFixtures.fixedClock().instant())) {
      throw new IllegalStateException("Stored recorded-at differs from the deterministic clock.");
    }
  }

  static PostingLifecycleStatus requireDuplicateRejection(CommitEntryResult duplicateResult) {
    if (!(duplicateResult instanceof CommitRejected rejected)) {
      throw new IllegalStateException("Duplicate commit should be rejected.");
    }
    if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
      throw new IllegalStateException("Duplicate commit returned the wrong rejection code.");
    }
    return JazzerReplayDetailsMapper.rejectionStatus(rejected.rejection());
  }

  static PostingLifecycleStatus verifyRejectedPreflightAndCommit(
      PreflightRejected preflightRejected, CommitEntryResult committedResult) {
    if (!(committedResult instanceof CommitRejected commitRejected)) {
      throw new IllegalStateException("Rejected preflight should remain rejected on commit.");
    }
    if (!commitRejected.rejection().equals(preflightRejected.rejection())) {
      throw new IllegalStateException("Commit changed the deterministic rejection.");
    }
    return JazzerReplayDetailsMapper.rejectionStatus(commitRejected.rejection());
  }
}
