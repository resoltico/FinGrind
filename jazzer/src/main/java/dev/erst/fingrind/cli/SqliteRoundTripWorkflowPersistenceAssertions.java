package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.jazzer.support.PostingLifecycleStatusMapper;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import dev.erst.fingrind.sqlite.SqliteReadSession;
import java.util.List;
import java.util.Optional;

/** Persistence and replay invariant assertions for SQLite round-trip workflows. */
public final class SqliteRoundTripWorkflowPersistenceAssertions {
  private SqliteRoundTripWorkflowPersistenceAssertions() {}

  /** Verifies that account reactivation persisted in the backing SQLite store. */
  public static void assertAccountReactivationPersisted(
      SqliteReadSession postingFactStore, AccountCode accountCode) {
    if (!postingFactStore.findAccount(accountCode).orElseThrow().active()) {
      throw new IllegalStateException("Account reactivation did not persist to SQLite.");
    }
  }

  /** Verifies that a rejected workflow stage did not leave behind a persisted posting fact. */
  public static void assertRejectedStateDidNotPersistPosting(
      Optional<PostingFact> persistedPosting) {
    if (persistedPosting.isPresent()) {
      throw new IllegalStateException("Rejected SQLite command must not persist a posting fact.");
    }
  }

  /** Requires a persisted posting fact to exist after a committed workflow path. */
  public static PostingFact requireStoredPosting(Optional<PostingFact> storedPosting) {
    if (storedPosting.isEmpty()) {
      throw new IllegalStateException("Committed posting fact was not persisted to SQLite.");
    }
    return storedPosting.orElseThrow();
  }

  /** Verifies that the declared-account registry remains query-visible after initialization. */
  public static void verifyDeclaredAccountListing(
      List<DeclaredAccount> listedAccounts, List<DeclaredAccount> declaredAccounts) {
    if (!listedAccounts.containsAll(declaredAccounts)) {
      throw new IllegalStateException(
          "Declared-account listing drifted from the initialized registry.");
    }
  }

  /** Verifies that a reloaded posting matches the committed command shape exactly. */
  public static void verifyReloadedPosting(
      PostingFact postingFact, Committed committed, PostEntryCommand command) {
    if (!postingFact.postingId().equals(committed.postingId())) {
      throw new IllegalStateException("Reloaded posting id differs from the commit result.");
    }
    if (!postingFact.journalEntry().equals(CliFuzzFixtures.journalEntry(command))) {
      throw new IllegalStateException("Reloaded journal entry differs from the parsed command.");
    }
    if (!postingFact.reversalReference().equals(CliFuzzFixtures.reversalReference(command))) {
      throw new IllegalStateException("Reloaded reversal differs from the parsed command.");
    }
    if (!postingFact.evidence().equals(command.evidence())) {
      throw new IllegalStateException("Reloaded evidence differs from the parsed command.");
    }
    if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
      throw new IllegalStateException(
          "Reloaded request provenance differs from the parsed command.");
    }
    if (!postingFact.provenance().recordedAt().equals(CliFuzzFixtures.fixedClock().instant())) {
      throw new IllegalStateException("Reloaded recorded-at differs from the deterministic clock.");
    }
  }

  /** Requires duplicate-commit handling to replay the original success deterministically. */
  public static PostingLifecycleStatus requireIdempotentReplay(
      CommitEntryResult duplicateResult, Committed committed) {
    SqliteRoundTripWorkflowDecisionAssertions.requireCommittedReplay(duplicateResult, committed);
    return PostingLifecycleStatus.IDEMPOTENT_REPLAY;
  }

  /** Verifies that repeating a rejected commit preserves the same rejection outcome. */
  public static PostingLifecycleStatus verifyRejectedCommitConsistency(
      CommitRejected rejected, CommitEntryResult repeatedResult) {
    CommitRejected repeatedRejected =
        SqliteRoundTripWorkflowDecisionAssertions.requiredCommitRejected(repeatedResult);
    if (!repeatedRejected.rejection().equals(rejected.rejection())) {
      throw new IllegalStateException("Repeated SQLite rejection changed unexpectedly.");
    }
    return PostingLifecycleStatusMapper.forRejection(repeatedRejected.rejection());
  }
}
