package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.CommitEntryResult;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.PostEntryResult.Committed;
import dev.erst.fingrind.contract.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PreflightEntryResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.jazzer.tool.PostingLifecycleStatus;
import dev.erst.fingrind.sqlite.SqliteBookSession;
import java.util.Objects;
import java.util.Optional;

/** Lifecycle and domain assertions for SQLite round-trip workflow coverage. */
final class SqliteRoundTripWorkflowLifecycleAssertions {
  private SqliteRoundTripWorkflowLifecycleAssertions() {}

  static void assertAccountReactivationPersisted(
      SqliteBookSession postingFactStore, AccountCode accountCode) {
    if (!postingFactStore.findAccount(accountCode).orElseThrow().active()) {
      throw new IllegalStateException("Account reactivation did not persist to SQLite.");
    }
  }

  static void assertRejectedStateDidNotPersistPosting(Optional<PostingFact> persistedPosting) {
    if (persistedPosting.isPresent()) {
      throw new IllegalStateException("Rejected SQLite command must not persist a posting fact.");
    }
  }

  static void assertDuplicateWorkflowPreflightRejected(PreflightEntryResult duplicatePreflight) {
    if (!(duplicatePreflight instanceof PreflightRejected rejected)
        || !(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
      throw new IllegalStateException(
          "Duplicate workflow preflight must reject with duplicate-idempotency-key.");
    }
  }

  static void assertDuplicateWorkflowCommitRejected(CommitEntryResult duplicateCommit) {
    if (!(duplicateCommit instanceof CommitRejected duplicateRejected)
        || !(duplicateRejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
      throw new IllegalStateException(
          "Duplicate workflow commit must reject with duplicate-idempotency-key.");
    }
  }

  static void assertNearMissReversalRejected(CommitRejected nearMissRejected) {
    if (!(nearMissRejected.rejection() instanceof PostingRejection.ReversalDoesNotNegateTarget)) {
      throw new IllegalStateException(
          "Derived near-miss reversal must reject with reversal-does-not-negate-target.");
    }
  }

  static void assertDuplicateReversalRejected(CommitRejected duplicateRejected) {
    if (!(duplicateRejected.rejection() instanceof PostingRejection.ReversalAlreadyExists)) {
      throw new IllegalStateException(
          "Derived duplicate reversal must reject with reversal-already-exists.");
    }
  }

  static PreflightAccepted requirePreflightAccepted(
      ContractDecision<PreflightEntryResult> decision) {
    PreflightEntryResult result = decision.requireAccepted();
    if (result instanceof PreflightAccepted accepted) {
      return accepted;
    }
    throw new IllegalStateException("Expected workflow preflight to accept a fresh valid command.");
  }

  static Committed requireCommitted(ContractDecision<CommitEntryResult> decision) {
    CommitEntryResult result = decision.requireAccepted();
    if (result instanceof Committed committed) {
      return committed;
    }
    throw new IllegalStateException("Expected workflow commit to succeed on a fresh valid book.");
  }

  static CommitRejected requireCommitRejected(ContractDecision<CommitEntryResult> decision) {
    CommitEntryResult result = decision.requireAccepted();
    return requiredCommitRejected(result);
  }

  static CommitRejected requiredCommitRejected(CommitEntryResult result) {
    if (result instanceof CommitRejected rejected) {
      return rejected;
    }
    throw new IllegalStateException(
        "Expected deterministic commit rejection during SQLite lifecycle setup.");
  }

  static DeclareAccountResult.Declared requireDeclared(
      ContractDecision<DeclareAccountResult> decision) {
    DeclareAccountResult result = decision.requireAccepted();
    if (result instanceof DeclareAccountResult.Declared declared) {
      return declared;
    }
    throw new IllegalStateException("Expected workflow account declaration to succeed.");
  }

  static PostingFact requireStoredPosting(Optional<PostingFact> storedPosting) {
    if (storedPosting.isEmpty()) {
      throw new IllegalStateException("Committed posting fact was not persisted to SQLite.");
    }
    return storedPosting.orElseThrow();
  }

  static void verifyDeclaredAccountListing(int listedAccountCount, int declaredAccountCount) {
    if (listedAccountCount != declaredAccountCount) {
      throw new IllegalStateException("Declared-account listing drifted from setup declarations.");
    }
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
    CommitRejected rejected = requiredCommitRejected(duplicateResult);
    if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
      throw new IllegalStateException("Duplicate SQLite commit returned the wrong rejection code.");
    }
    return rejectionStatus(rejected.rejection());
  }

  static PostingLifecycleStatus verifyRejectedCommitConsistency(
      CommitRejected rejected, CommitEntryResult repeatedResult) {
    CommitRejected repeatedRejected = requiredCommitRejected(repeatedResult);
    if (!repeatedRejected.rejection().equals(rejected.rejection())) {
      throw new IllegalStateException("Repeated SQLite rejection changed unexpectedly.");
    }
    return rejectionStatus(repeatedRejected.rejection());
  }

  static PostingLifecycleStatus rejectionStatus(PostingRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case PostingRejection.BookNotInitialized _ -> PostingLifecycleStatus.BOOK_NOT_INITIALIZED;
      case PostingRejection.AccountStateViolations violations ->
          accountStateViolationStatus(violations);
      case PostingRejection.DuplicateIdempotencyKey _ ->
          PostingLifecycleStatus.DUPLICATE_IDEMPOTENCY_KEY;
      case PostingRejection.ReversalTargetNotFound _ ->
          PostingLifecycleStatus.REVERSAL_TARGET_NOT_FOUND;
      case PostingRejection.ReversalAlreadyExists _ ->
          PostingLifecycleStatus.REVERSAL_ALREADY_EXISTS;
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          PostingLifecycleStatus.REVERSAL_DOES_NOT_NEGATE_TARGET;
    };
  }

  static PostingLifecycleStatus accountStateViolationStatus(
      PostingRejection.AccountStateViolations accountStateViolations) {
    boolean allUnknown =
        accountStateViolations.violations().stream()
            .allMatch(PostingRejection.UnknownAccount.class::isInstance);
    if (allUnknown) {
      return PostingLifecycleStatus.UNKNOWN_ACCOUNT;
    }
    boolean allInactive =
        accountStateViolations.violations().stream()
            .allMatch(PostingRejection.InactiveAccount.class::isInstance);
    if (allInactive) {
      return PostingLifecycleStatus.INACTIVE_ACCOUNT;
    }
    return PostingLifecycleStatus.ACCOUNT_STATE_VIOLATIONS;
  }
}
