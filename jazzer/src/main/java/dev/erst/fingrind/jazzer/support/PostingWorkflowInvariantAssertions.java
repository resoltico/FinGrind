package dev.erst.fingrind.jazzer.support;

import dev.erst.fingrind.cli.CliFuzzFixtures;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared posting-workflow invariant checks used by Jazzer fuzz and replay surfaces. */
public final class PostingWorkflowInvariantAssertions {
  private PostingWorkflowInvariantAssertions() {}

  /** Verifies that every setup declaration is present in the initialized account registry. */
  public static void verifyDeclaredAccountListing(
      List<DeclaredAccount> listedAccounts, List<DeclaredAccount> declaredAccounts) {
    Objects.requireNonNull(listedAccounts, "listedAccounts must not be null");
    Objects.requireNonNull(declaredAccounts, "declaredAccounts must not be null");
    if (!listedAccounts.containsAll(declaredAccounts)) {
      throw new IllegalStateException(
          "Declared-account listing drifted from the initialized registry.");
    }
  }

  /**
   * Verifies that account reactivation survives persistence and is visible in the listed accounts.
   */
  public static void assertAccountReactivationPersisted(
      List<DeclaredAccount> listedAccounts, DeclaredAccount primaryAccount) {
    Objects.requireNonNull(listedAccounts, "listedAccounts must not be null");
    Objects.requireNonNull(primaryAccount, "primaryAccount must not be null");
    if (listedAccounts.stream()
        .noneMatch(
            account ->
                account.accountCode().equals(primaryAccount.accountCode()) && account.active())) {
      throw new IllegalStateException("Account reactivation did not persist in the registry.");
    }
  }

  /** Verifies that accepted preflight preserves the original idempotency key and effective date. */
  public static void verifyAcceptedPreflight(PreflightAccepted accepted, PostEntryCommand command) {
    Objects.requireNonNull(accepted, "accepted must not be null");
    Objects.requireNonNull(command, "command must not be null");
    if (!accepted.idempotencyKey().equals(command.requestProvenance().idempotencyKey())) {
      throw new IllegalStateException("Preflight changed the idempotency key.");
    }
    if (!accepted.effectiveDate().equals(CliFuzzFixtures.journalEntry(command).effectiveDate())) {
      throw new IllegalStateException("Preflight changed the effective date.");
    }
  }

  /** Requires a committed result after an accepted preflight on a fresh valid book. */
  public static Committed requireCommittedAfterAcceptedPreflight(
      CommitEntryResult committedResult) {
    Objects.requireNonNull(committedResult, "committedResult must not be null");
    return switch (committedResult) {
      case Committed committed -> committed;
      case CommitRejected _ ->
          throw new IllegalStateException(
              "Accepted preflight should commit on a fresh valid book.");
    };
  }

  /** Requires the committed posting to be present in persistent storage. */
  public static PostingFact requireStoredPosting(Optional<PostingFact> storedPosting) {
    Objects.requireNonNull(storedPosting, "storedPosting must not be null");
    if (storedPosting.isEmpty()) {
      throw new IllegalStateException("Committed posting fact was not persisted.");
    }
    return storedPosting.orElseThrow();
  }

  /**
   * Verifies that the stored posting matches the committed result, parsed command, and fixed clock.
   */
  public static void verifyStoredPosting(
      PostingFact postingFact, Committed committed, PostEntryCommand command) {
    Objects.requireNonNull(postingFact, "postingFact must not be null");
    Objects.requireNonNull(committed, "committed must not be null");
    Objects.requireNonNull(command, "command must not be null");
    if (!postingFact.postingId().equals(committed.postingId())) {
      throw new IllegalStateException("Stored posting id differs from the commit result.");
    }
    if (!postingFact.journalEntry().equals(CliFuzzFixtures.journalEntry(command))) {
      throw new IllegalStateException("Stored journal entry differs from the parsed command.");
    }
    if (!postingFact.reversalReference().equals(CliFuzzFixtures.reversalReference(command))) {
      throw new IllegalStateException("Stored reversal differs from the parsed command.");
    }
    if (!postingFact.provenance().requestProvenance().equals(command.requestProvenance())) {
      throw new IllegalStateException("Stored request provenance differs from the parsed command.");
    }
    if (!postingFact.provenance().recordedAt().equals(CliFuzzFixtures.fixedClock().instant())) {
      throw new IllegalStateException("Stored recorded-at differs from the deterministic clock.");
    }
    if (postingFact.provenance().sourceChannel() != command.sourceChannel()) {
      throw new IllegalStateException("Stored source channel differs from the parsed command.");
    }
  }

  /** Requires duplicate submission to produce a duplicate-idempotency-key rejection. */
  public static CommitRejected requireDuplicateRejection(CommitEntryResult duplicateResult) {
    Objects.requireNonNull(duplicateResult, "duplicateResult must not be null");
    CommitRejected rejected =
        switch (duplicateResult) {
          case CommitRejected commitRejected -> commitRejected;
          case Committed _ ->
              throw new IllegalStateException("Duplicate commit should be rejected.");
        };
    if (!(rejected.rejection() instanceof PostingRejection.DuplicateIdempotencyKey)) {
      throw new IllegalStateException("Duplicate commit returned the wrong rejection code.");
    }
    return rejected;
  }

  /** Verifies that a rejected preflight remains rejected with the same deterministic rejection. */
  public static CommitRejected verifyRejectedPreflightAndCommit(
      PreflightRejected preflightRejected, CommitEntryResult committedResult) {
    Objects.requireNonNull(preflightRejected, "preflightRejected must not be null");
    Objects.requireNonNull(committedResult, "committedResult must not be null");
    CommitRejected rejected =
        switch (committedResult) {
          case CommitRejected commitRejected -> commitRejected;
          case Committed _ ->
              throw new IllegalStateException(
                  "Rejected preflight should remain rejected on commit.");
        };
    if (!rejected.rejection().equals(preflightRejected.rejection())) {
      throw new IllegalStateException("Commit changed the deterministic rejection.");
    }
    return rejected;
  }

  /** Verifies that a rejected command leaves no persisted posting behind. */
  public static void assertRejectedStateDidNotPersistPosting(Optional<PostingFact> storedPosting) {
    Objects.requireNonNull(storedPosting, "storedPosting must not be null");
    if (storedPosting.isPresent()) {
      throw new IllegalStateException("Rejected command must not persist a posting fact.");
    }
  }

  /** Requires a preflight result to be rejected with the expected rejection type. */
  public static PreflightRejected assertRejected(
      PreflightEntryResult result, Class<? extends PostingRejection> rejectionType) {
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(rejectionType, "rejectionType must not be null");
    PreflightRejected rejected =
        switch (result) {
          case PreflightRejected preflightRejected -> preflightRejected;
          case PreflightAccepted _ ->
              throw new IllegalStateException(
                  "Expected deterministic rejection during lifecycle setup.");
        };
    if (!rejectionType.isInstance(rejected.rejection())) {
      throw new IllegalStateException(
          "Lifecycle setup returned the wrong rejection type: " + rejected.rejection());
    }
    return rejected;
  }

  /** Requires a commit result to be rejected with the expected rejection type. */
  public static CommitRejected assertRejected(
      CommitEntryResult result, Class<? extends PostingRejection> rejectionType) {
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(rejectionType, "rejectionType must not be null");
    CommitRejected rejected =
        switch (result) {
          case CommitRejected commitRejected -> commitRejected;
          case Committed _ ->
              throw new IllegalStateException(
                  "Expected deterministic rejection during lifecycle setup.");
        };
    if (!rejectionType.isInstance(rejected.rejection())) {
      throw new IllegalStateException(
          "Lifecycle setup returned the wrong rejection type: " + rejected.rejection());
    }
    return rejected;
  }

  /** Requires a preflight result to be rejected with the expected account-state violation type. */
  public static PreflightRejected assertAccountStateRejected(
      PreflightEntryResult result,
      Class<? extends PostingRejection.AccountStateViolation> violationType) {
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(violationType, "violationType must not be null");
    PreflightRejected rejected =
        switch (result) {
          case PreflightRejected preflightRejected -> preflightRejected;
          case PreflightAccepted _ ->
              throw new IllegalStateException(
                  "Expected deterministic rejection during lifecycle setup.");
        };
    requireAccountStateViolations(rejected.rejection(), violationType);
    return rejected;
  }

  /** Requires a commit result to be rejected with the expected account-state violation type. */
  public static CommitRejected assertAccountStateRejected(
      CommitEntryResult result,
      Class<? extends PostingRejection.AccountStateViolation> violationType) {
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(violationType, "violationType must not be null");
    CommitRejected rejected =
        switch (result) {
          case CommitRejected commitRejected -> commitRejected;
          case Committed _ ->
              throw new IllegalStateException(
                  "Expected deterministic rejection during lifecycle setup.");
        };
    requireAccountStateViolations(rejected.rejection(), violationType);
    return rejected;
  }

  private static void requireAccountStateViolations(
      PostingRejection rejection,
      Class<? extends PostingRejection.AccountStateViolation> violationType) {
    if (!(rejection instanceof PostingRejection.AccountStateViolations violations)) {
      throw new IllegalStateException(
          "Expected account-state violations during lifecycle setup but got: " + rejection);
    }
    if (violations.violations().stream()
        .anyMatch(violation -> !violationType.isInstance(violation))) {
      throw new IllegalStateException(
          "Lifecycle setup returned the wrong account-state violations: "
              + violations.violations());
    }
  }
}
