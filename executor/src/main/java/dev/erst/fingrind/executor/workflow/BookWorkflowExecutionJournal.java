package dev.erst.fingrind.executor.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Internal execution journal for one complete workflow run. */
public record BookWorkflowExecutionJournal(
    Instant startedAt, Instant finishedAt, List<BookWorkflowJournalEntry> entries) {
  public BookWorkflowExecutionJournal {
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    if (entries.isEmpty()) {
      throw new IllegalArgumentException(
          "Workflow execution journal must contain at least one entry.");
    }
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException(
          "Workflow execution journal finishedAt must not precede startedAt.");
    }
    BookWorkflowJournalEntry terminalEntry = entries.getLast();
    List<BookWorkflowJournalEntry> priorEntries = entries.subList(0, entries.size() - 1);
    switch (terminalEntry) {
      case BookWorkflowJournalEntry.Succeeded _ -> {
        if (!entries.stream().allMatch(BookWorkflowExecutionJournal::succeededEntry)) {
          throw new IllegalArgumentException(
              "Succeeded workflow journals may contain only succeeded entries.");
        }
      }
      case BookWorkflowJournalEntry.Failed _ -> {
        if (!priorEntries.stream().allMatch(BookWorkflowExecutionJournal::succeededEntry)) {
          throw new IllegalArgumentException(
              "Failed workflow journals may contain succeeded entries only before the terminal failure.");
        }
      }
    }
  }

  /** Returns the terminal entry that determines the final status. */
  public BookWorkflowJournalEntry terminalEntry() {
    return entries.getLast();
  }

  /** Returns the status derived from the terminal entry. */
  public BookWorkflowExecutionStatus status() {
    return switch (terminalEntry()) {
      case BookWorkflowJournalEntry.Succeeded _ -> BookWorkflowExecutionStatus.SUCCEEDED;
      case BookWorkflowJournalEntry.Rejected _ -> BookWorkflowExecutionStatus.REJECTED;
      case BookWorkflowJournalEntry.AssertionFailed _ ->
          BookWorkflowExecutionStatus.ASSERTION_FAILED;
    };
  }

  /** Returns the required terminal failed entry. */
  public BookWorkflowJournalEntry.Failed requiredFailedEntry() {
    return switch (terminalEntry()) {
      case BookWorkflowJournalEntry.Succeeded _ ->
          throw new IllegalStateException(
              "Succeeded workflow journals do not have a failed entry.");
      case BookWorkflowJournalEntry.Failed failed -> failed;
    };
  }

  private static boolean succeededEntry(BookWorkflowJournalEntry entry) {
    return entry instanceof BookWorkflowJournalEntry.Succeeded;
  }
}
