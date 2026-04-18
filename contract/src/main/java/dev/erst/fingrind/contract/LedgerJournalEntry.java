package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One per-step journal entry emitted by ledger-plan execution. */
public sealed interface LedgerJournalEntry
    permits LedgerJournalEntry.Succeeded, LedgerJournalEntry.Failed {
  /** Returns the caller-visible step identifier for this journal entry. */
  String stepId();

  /** Returns the canonical step kind executed for this journal entry. */
  LedgerStepKind kind();

  /** Returns the optional nested assertion kind for assertion steps only. */
  Optional<LedgerAssertionKind> detailKind();

  /** Returns the step start instant. */
  Instant startedAt();

  /** Returns the step finish instant. */
  Instant finishedAt();

  /** Returns the compact machine-readable facts observed for this step. */
  List<LedgerFact> facts();

  /** Returns the stable per-step execution status. */
  LedgerStepStatus status();

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
                    "Ledger journal entry '%s' does not carry a failure.".formatted(stepId())));
  }

  /** Successful journal entry with facts and no failure payload. */
  record Succeeded(
      String stepId,
      LedgerStepKind kind,
      Optional<LedgerAssertionKind> detailKind,
      Instant startedAt,
      Instant finishedAt,
      List<LedgerFact> facts)
      implements LedgerJournalEntry {
    /** Validates one succeeded step journal entry. */
    public Succeeded {
      requireCommon(stepId, kind, detailKind, startedAt, finishedAt, facts);
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
      String stepId,
      LedgerStepKind kind,
      Optional<LedgerAssertionKind> detailKind,
      Instant startedAt,
      Instant finishedAt,
      List<LedgerFact> facts,
      LedgerStepFailure failure)
      implements Failed {
    /** Validates one rejected step journal entry. */
    public Rejected {
      requireCommon(stepId, kind, detailKind, startedAt, finishedAt, facts);
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
      String stepId,
      LedgerStepKind kind,
      Optional<LedgerAssertionKind> detailKind,
      Instant startedAt,
      Instant finishedAt,
      List<LedgerFact> facts,
      LedgerStepFailure failure)
      implements Failed {
    /** Validates one assertion-failed step journal entry. */
    public AssertionFailed {
      requireCommon(stepId, kind, detailKind, startedAt, finishedAt, facts);
      facts = List.copyOf(facts);
      Objects.requireNonNull(failure, "failure");
    }

    @Override
    public LedgerStepStatus status() {
      return LedgerStepStatus.ASSERTION_FAILED;
    }
  }

  private static void requireCommon(
      String stepId,
      LedgerStepKind kind,
      Optional<LedgerAssertionKind> detailKind,
      Instant startedAt,
      Instant finishedAt,
      List<LedgerFact> facts) {
    Objects.requireNonNull(stepId, "stepId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(detailKind, "detailKind");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    List.copyOf(Objects.requireNonNull(facts, "facts"));
    if (stepId.isBlank()) {
      throw new IllegalArgumentException("Ledger journal stepId must not be blank.");
    }
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException(
          "Ledger journal step finishedAt must not precede startedAt.");
    }
  }
}
