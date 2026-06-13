package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;

/** Deterministic result-shape assertions for SQLite round-trip workflow stages. */
final class SqliteRoundTripWorkflowDecisionAssertions {
  private SqliteRoundTripWorkflowDecisionAssertions() {}

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
    return requiredCommitRejected(decision.requireAccepted());
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
}
