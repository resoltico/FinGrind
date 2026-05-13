package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import java.util.Optional;

/** Shared invariant checks for SQLite round-trip replay. */
final class SqliteRoundTripReplayVerifier {
  private SqliteRoundTripReplayVerifier() {}

  static void verifyDeclaredAccountListing(int listedAccountCount, int declaredAccountCount) {
    if (listedAccountCount != declaredAccountCount) {
      throw new IllegalStateException("Declared-account listing drifted from setup declarations.");
    }
  }

  static PostingFact requireStoredPosting(Optional<PostingFact> storedPosting) {
    if (storedPosting.isEmpty()) {
      throw new IllegalStateException("Committed posting fact was not persisted to SQLite.");
    }
    return storedPosting.orElseThrow();
  }

  static void verifyReloadedPosting(
      PostingFact postingFact, Committed committed, PostEntryCommand command) {
    if (!postingFact.postingId().equals(committed.postingId())) {
      throw new IllegalStateException("Reloaded posting id differs from the commit result.");
    }
    if (!postingFact.journalEntry().equals(command.journalEntry())) {
      throw new IllegalStateException("Reloaded journal entry differs from the parsed command.");
    }
    if (!postingFact.reversalReference().equals(command.reversalReference())) {
      throw new IllegalStateException("Reloaded reversal differs from the parsed command.");
    }
    if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
      throw new IllegalStateException(
          "Reloaded request provenance differs from the parsed command.");
    }
    if (!postingFact.provenance().recordedAt().equals(CliFuzzFixtures.fixedClock().instant())) {
      throw new IllegalStateException("Reloaded recorded-at differs from the deterministic clock.");
    }
  }

  static PostingLifecycleStatus requireDuplicateRejection(CommitEntryResult duplicateResult) {
    if (!(duplicateResult instanceof CommitRejected rejected)) {
      throw new IllegalStateException("Duplicate SQLite commit should be rejected.");
    }
    if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
      throw new IllegalStateException("Duplicate SQLite commit returned the wrong rejection code.");
    }
    return JazzerReplayDetailsMapper.rejectionStatus(rejected.rejection());
  }

  static PostingLifecycleStatus verifyRejectedCommitConsistency(
      CommitRejected rejected, CommitEntryResult repeatedResult) {
    if (!(repeatedResult instanceof CommitRejected repeatedRejected)) {
      throw new IllegalStateException("Rejected SQLite command should remain rejected.");
    }
    if (!repeatedRejected.rejection().equals(rejected.rejection())) {
      throw new IllegalStateException("Repeated SQLite rejection changed unexpectedly.");
    }
    return JazzerReplayDetailsMapper.rejectionStatus(repeatedRejected.rejection());
  }
}
