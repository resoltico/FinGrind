package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;

/** Holds the private dependencies that make one publication transaction durable and testable. */
record PublicationTransactionRuntime(
    PublicationTransactionJournalRepository repository,
    PublicationTransactionDirectoryDurability directoryDurability,
    InstantSource clock,
    PublicationTransactionFaultInjector faultInjector) {
  PublicationTransactionRuntime {
    Objects.requireNonNull(repository, "repository");
    Objects.requireNonNull(directoryDurability, "directoryDurability");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(faultInjector, "faultInjector");
  }

  PublicationTransactionJournal transition(
      PublicationTransactionJournal journal,
      PublicationTransactionState nextState,
      PublicationTransactionOutcome outcome,
      PublicationTransactionFaultPoint faultPoint)
      throws IOException {
    PublicationTransactionJournal updated =
        repository.transition(
            journal.transactionId(),
            new PublicationTransactionTransition(
                Objects.requireNonNull(nextState, "nextState"),
                clock.instant(),
                Objects.requireNonNull(outcome, "outcome")));
    faultInjector.after(Objects.requireNonNull(faultPoint, "faultPoint"));
    return updated;
  }

  PublicationTransactionJournal updateMembers(
      PublicationTransactionJournal journal,
      List<PublicationTransactionMember> members,
      PublicationTransactionFaultPoint faultPoint)
      throws IOException {
    PublicationTransactionJournal updated =
        repository.updateMembers(
            journal.transactionId(),
            new PublicationTransactionJournalMembers(
                List.copyOf(Objects.requireNonNull(members, "members"))));
    faultInjector.after(Objects.requireNonNull(faultPoint, "faultPoint"));
    return updated;
  }

  void forceDirectory(Path directory, PublicationTransactionFaultPoint faultPoint)
      throws IOException {
    directoryDurability.force(Objects.requireNonNull(directory, "directory"));
    faultInjector.after(Objects.requireNonNull(faultPoint, "faultPoint"));
  }

  PublicationTransactionResult result(PublicationTransactionJournal journal) {
    PublicationTransactionJournal checkedJournal = Objects.requireNonNull(journal, "journal");
    return new PublicationTransactionResult(
        checkedJournal.transactionId(),
        checkedJournal.state(),
        checkedJournal.transitions().getLast().outcome());
  }

  Instant now() {
    return clock.instant();
  }
}
