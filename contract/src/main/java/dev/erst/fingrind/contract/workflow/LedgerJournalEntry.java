package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** One per-step journal entry emitted by ledger-plan execution. */
public sealed interface LedgerJournalEntry
    permits LedgerJournalEntry.Succeeded, LedgerJournalEntry.Failed {
  /** Returns the caller-visible step identifier for this journal entry. */
  LedgerStepId stepId();

  /** Returns the canonical journal-visible step identity for this journal entry. */
  LedgerJournalStep journalStep();

  /** Returns the step start instant. */
  Instant startedAt();

  /** Returns the step finish instant. */
  Instant finishedAt();

  /** Returns the compact machine-readable facts observed for this step. */
  List<LedgerFact> facts();

  /** Returns the stable per-step execution status. */
  LedgerStepStatus status();

  /** Returns the canonical step kind executed for this journal entry. */
  default LedgerJournalKind kind() {
    return journalStep().kind();
  }

  /**
   * Returns the nested assertion kind for assertion journal entries, or {@code null} for standard
   * and boundary journal entries.
   */
  default @Nullable LedgerAssertionKind detailKind() {
    return journalStep().detailKind();
  }

  /**
   * Returns the nested boundary phase for plan-boundary journal entries, or {@code null} for
   * standard and assertion journal entries.
   */
  default @Nullable LedgerBoundaryPhase boundaryPhase() {
    return journalStep().boundaryPhase();
  }

  /** Returns the optional failure payload for this step journal entry. */
  default Optional<LedgerStepFailure> optionalFailure() {
    return switch (this) {
      case LedgerJournalEntry.Succeeded _ -> Optional.empty();
      case LedgerJournalEntry.Rejected rejected -> Optional.of(rejected.failure());
      case LedgerJournalEntry.AssertionFailed assertionFailed ->
          Optional.of(assertionFailed.failure());
    };
  }

  /** Returns the required failure payload or throws when the step succeeded. */
  default LedgerStepFailure requiredFailure() {
    return optionalFailure()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Ledger journal entry '%s' does not carry a failure."
                        .formatted(stepId().value())));
  }

  /** Successful journal entry with facts and no failure payload. */
  record Succeeded(
      LedgerStepId stepId,
      LedgerJournalStep journalStep,
      Instant startedAt,
      Instant finishedAt,
      List<LedgerFact> facts)
      implements LedgerJournalEntry {
    /** Validates one succeeded step journal entry. */
    public Succeeded {
      requireCommon(stepId, journalStep, startedAt, finishedAt, facts);
      facts = List.copyOf(facts);
    }

    @Override
    public LedgerStepStatus status() {
      return LedgerStepStatus.SUCCEEDED;
    }
  }

  /** Shared base contract for rejected and assertion-failed journal entries. */
  sealed interface Failed extends LedgerJournalEntry
      permits LedgerJournalEntry.Rejected, LedgerJournalEntry.AssertionFailed {}

  /** Deterministically rejected journal entry with a required failure payload. */
  record Rejected(
      LedgerStepId stepId,
      LedgerJournalStep journalStep,
      Instant startedAt,
      Instant finishedAt,
      List<LedgerFact> facts,
      LedgerStepFailure failure)
      implements Failed {
    /** Validates one rejected step journal entry. */
    public Rejected {
      requireCommon(stepId, journalStep, startedAt, finishedAt, facts);
      facts = List.copyOf(facts);
      Objects.requireNonNull(failure, "failure");
    }

    @Override
    public LedgerStepStatus status() {
      return LedgerStepStatus.REJECTED;
    }
  }

  /** Assertion-failed journal entry with a required failure payload. */
  record AssertionFailed(
      LedgerStepId stepId,
      LedgerJournalStep journalStep,
      Instant startedAt,
      Instant finishedAt,
      List<LedgerFact> facts,
      LedgerStepFailure failure)
      implements Failed {
    /** Validates one assertion-failed step journal entry. */
    public AssertionFailed {
      requireCommon(stepId, journalStep, startedAt, finishedAt, facts);
      if (!(journalStep instanceof LedgerJournalStep.Assertion)) {
        throw new IllegalArgumentException(
            "Assertion-failed journal entries must carry an assertion journal step.");
      }
      facts = List.copyOf(facts);
      Objects.requireNonNull(failure, "failure");
    }

    @Override
    public LedgerStepStatus status() {
      return LedgerStepStatus.ASSERTION_FAILED;
    }
  }

  private static void requireCommon(
      LedgerStepId stepId,
      LedgerJournalStep journalStep,
      Instant startedAt,
      Instant finishedAt,
      List<LedgerFact> facts) {
    Objects.requireNonNull(stepId, "stepId");
    Objects.requireNonNull(journalStep, "journalStep");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    Objects.requireNonNull(facts, "facts");
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException(
          "Ledger journal step finishedAt must not precede startedAt.");
    }
  }
}
