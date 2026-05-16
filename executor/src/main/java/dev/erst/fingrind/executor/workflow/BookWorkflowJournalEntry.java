package dev.erst.fingrind.executor.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Internal journal entry for one executed workflow step before public projection. */
public sealed interface BookWorkflowJournalEntry
    permits BookWorkflowJournalEntry.Succeeded, BookWorkflowJournalEntry.Failed {
  /** Returns the local step identifier or synthetic boundary identifier. */
  BookWorkflowStepId stepId();

  /** Returns the internal journal descriptor. */
  BookWorkflowJournalDescriptor descriptor();

  /** Returns the step or boundary start instant. */
  Instant startedAt();

  /** Returns the step or boundary finish instant. */
  Instant finishedAt();

  /** Returns the compact machine facts gathered while executing the unit. */
  List<BookWorkflowFact> facts();

  /** Returns the optional failure payload. */
  default Optional<BookWorkflowFailure> optionalFailure() {
    return switch (this) {
      case Succeeded _ -> Optional.empty();
      case Rejected rejected -> Optional.of(rejected.failure());
      case AssertionFailed assertionFailed -> Optional.of(assertionFailed.failure());
    };
  }

  /** Returns the required failure payload or throws when the entry succeeded. */
  default BookWorkflowFailure requiredFailure() {
    return optionalFailure()
        .orElseThrow(
            () -> new IllegalStateException("Workflow journal entry does not carry a failure."));
  }

  /** Successful entry with facts and no failure payload. */
  record Succeeded(
      BookWorkflowStepId stepId,
      BookWorkflowJournalDescriptor descriptor,
      Instant startedAt,
      Instant finishedAt,
      List<BookWorkflowFact> facts)
      implements BookWorkflowJournalEntry {
    public Succeeded {
      requireCommon(stepId, descriptor, startedAt, finishedAt, facts);
      facts = List.copyOf(facts);
    }
  }

  /** Shared failed-entry branch for rejected and assertion-failed workflow steps. */
  sealed interface Failed extends BookWorkflowJournalEntry
      permits BookWorkflowJournalEntry.Rejected, BookWorkflowJournalEntry.AssertionFailed {}

  /** Rejected entry with a required failure payload. */
  record Rejected(
      BookWorkflowStepId stepId,
      BookWorkflowJournalDescriptor descriptor,
      Instant startedAt,
      Instant finishedAt,
      List<BookWorkflowFact> facts,
      BookWorkflowFailure failure)
      implements Failed {
    public Rejected {
      requireCommon(stepId, descriptor, startedAt, finishedAt, facts);
      facts = List.copyOf(facts);
      Objects.requireNonNull(failure, "failure");
    }
  }

  /** Assertion-failed entry with a required failure payload. */
  record AssertionFailed(
      BookWorkflowStepId stepId,
      BookWorkflowJournalDescriptor descriptor,
      Instant startedAt,
      Instant finishedAt,
      List<BookWorkflowFact> facts,
      BookWorkflowFailure failure)
      implements Failed {
    public AssertionFailed {
      requireCommon(stepId, descriptor, startedAt, finishedAt, facts);
      if (!(descriptor instanceof BookWorkflowJournalDescriptor.Step descriptorStep)
          || !(descriptorStep.step() instanceof BookWorkflowStep.Assert)) {
        throw new IllegalArgumentException(
            "Assertion-failed workflow journal entries must describe an assertion step.");
      }
      facts = List.copyOf(facts);
      Objects.requireNonNull(failure, "failure");
    }
  }

  private static void requireCommon(
      BookWorkflowStepId stepId,
      BookWorkflowJournalDescriptor descriptor,
      Instant startedAt,
      Instant finishedAt,
      List<BookWorkflowFact> facts) {
    Objects.requireNonNull(stepId, "stepId");
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    Objects.requireNonNull(facts, "facts");
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException(
          "Workflow journal entry finishedAt must not precede startedAt.");
    }
  }
}
