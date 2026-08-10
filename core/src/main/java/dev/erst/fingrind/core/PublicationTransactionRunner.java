package dev.erst.fingrind.core;

import java.io.IOException;
import java.util.Objects;

/**
 * Advances an authenticated journal only from facts already recorded under that journal's owner.
 */
final class PublicationTransactionRunner {
  private final PublicationTransactionRuntime runtime;

  PublicationTransactionRunner(PublicationTransactionRuntime runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  PublicationTransactionResult publishFresh(
      PublicationTransactionJournal journal, PublicationTransactionRequest request)
      throws IOException {
    PublicationTransactionJournal staged =
        PublicationTransactionStager.stageAll(
            Objects.requireNonNull(journal, "journal"),
            Objects.requireNonNull(request, "request"),
            runtime);
    return continueFrom(
        runtime.transition(
            staged,
            PublicationTransactionState.STAGED,
            noneCommittedCleanupIncomplete(),
            PublicationTransactionFaultPoint.JOURNAL_STAGED));
  }

  /** Commits stages that a reservation producer wrote at the journal's exact private paths. */
  PublicationTransactionResult publishReserved(PublicationTransactionJournal journal) throws IOException {
    PublicationTransactionJournal staged =
        PublicationTransactionStager.admitReservedStages(
            Objects.requireNonNull(journal, "journal"), runtime);
    return continueFrom(
        runtime.transition(
            staged,
            PublicationTransactionState.STAGED,
            noneCommittedCleanupIncomplete(),
            PublicationTransactionFaultPoint.JOURNAL_STAGED));
  }

  PublicationTransactionResult recover(PublicationTransactionJournal journal) throws IOException {
    PublicationTransactionJournal current = Objects.requireNonNull(journal, "journal");
    return switch (current.state()) {
      case COMPLETE, BLOCKED -> runtime.result(current);
      case PREPARED -> recoverPrepared(current);
      case ABORTING -> abortNoReplaceCollision(current);
      case COMMIT_UNCERTAIN ->
          continueFrom(
              runtime.transition(
                  current,
                  PublicationTransactionState.COMMITTING,
                  new PublicationTransactionOutcome(
                      PublicationCommitOutcome.COMMIT_UNCERTAIN,
                      PublicationCleanupOutcome.INCOMPLETE),
                  PublicationTransactionFaultPoint.JOURNAL_COMMITTING));
      case CLEANUP_INCOMPLETE, CLEANUP_UNCERTAIN -> recoverCleanup(current);
      case STAGED, COMMITTING, COMMITTED, CLEANING -> continueFrom(current);
    };
  }

  private PublicationTransactionResult recoverPrepared(PublicationTransactionJournal journal)
      throws IOException {
    if (journal.members().stream()
        .allMatch(member -> member.progress() == PublicationTransactionMemberProgress.STAGED)) {
      return continueFrom(
          runtime.transition(
              journal,
              PublicationTransactionState.STAGED,
              noneCommittedCleanupIncomplete(),
              PublicationTransactionFaultPoint.JOURNAL_STAGED));
    }
    return runtime.result(
        runtime.transition(
            journal,
            PublicationTransactionState.BLOCKED,
            noneCommittedCleanupIncomplete(),
            PublicationTransactionFaultPoint.JOURNAL_PREPARED));
  }

  private PublicationTransactionResult recoverCleanup(PublicationTransactionJournal journal)
      throws IOException {
    if (journal.members().stream()
            .allMatch(
                member ->
                    member.progress() == PublicationTransactionMemberProgress.STAGED
                        || member.progress() == PublicationTransactionMemberProgress.ABORTED)
        && PublicationTransactionCleaner.hasVerifiedNoReplaceCollision(journal)) {
      return abortNoReplaceCollision(
          runtime.transition(
              journal,
              PublicationTransactionState.ABORTING,
              noneCommittedCleanupIncomplete(),
              PublicationTransactionFaultPoint.JOURNAL_CLEANING));
    }
    if (journal.members().stream()
        .allMatch(
            member ->
                member.progress() == PublicationTransactionMemberProgress.COMMITTED
                    || member.progress() == PublicationTransactionMemberProgress.CLEANED)) {
      return continueFrom(
          runtime.transition(
              journal,
              PublicationTransactionState.CLEANING,
              new PublicationTransactionOutcome(
                  PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.INCOMPLETE),
              PublicationTransactionFaultPoint.JOURNAL_CLEANING));
    }
    return runtime.result(
        runtime.transition(
            journal,
            PublicationTransactionState.BLOCKED,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.PARTIALLY_COMMITTED, PublicationCleanupOutcome.INCOMPLETE),
            PublicationTransactionFaultPoint.JOURNAL_CLEANING));
  }

  PublicationTransactionResult abortNoReplaceCollision(PublicationTransactionJournal journal)
      throws IOException {
    PublicationTransactionJournal current = Objects.requireNonNull(journal, "journal");
    if (current.state() == PublicationTransactionState.COMMITTING) {
      if (!PublicationTransactionCleaner.hasVerifiedNoReplaceCollision(current)) {
        throw new IOException(
            "Publication transaction cannot abort without a verified unrelated final collision.");
      }
      current =
          runtime.transition(
              current,
              PublicationTransactionState.ABORTING,
              noneCommittedCleanupIncomplete(),
              PublicationTransactionFaultPoint.JOURNAL_CLEANING);
    }
    if (current.state() != PublicationTransactionState.ABORTING) {
      throw new IOException("Publication transaction is not in the aborting state.");
    }
    PublicationTransactionJournal aborted =
        PublicationTransactionCleaner.abortNoReplaceCollision(current, runtime);
    return runtime.result(
        runtime.transition(
            aborted,
            PublicationTransactionState.BLOCKED,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.COMPLETE),
            PublicationTransactionFaultPoint.JOURNAL_BLOCKED));
  }

  PublicationTransactionResult continueFrom(PublicationTransactionJournal journal)
      throws IOException {
    return switch (journal.state()) {
      case STAGED -> continueFrom(startCommitting(journal));
      case COMMITTING -> continueFrom(commitAll(journal));
      case COMMITTED -> continueFrom(startCleaning(journal));
      case CLEANING -> complete(cleanAll(journal));
      case COMPLETE, BLOCKED -> runtime.result(journal);
      case PREPARED, ABORTING, COMMIT_UNCERTAIN, CLEANUP_INCOMPLETE, CLEANUP_UNCERTAIN ->
          throw new IllegalArgumentException(
              "Publication transaction requires explicit recovery handling.");
    };
  }

  private PublicationTransactionJournal startCommitting(PublicationTransactionJournal journal)
      throws IOException {
    return runtime.transition(
        journal,
        PublicationTransactionState.COMMITTING,
        noneCommittedCleanupIncomplete(),
        PublicationTransactionFaultPoint.JOURNAL_COMMITTING);
  }

  private PublicationTransactionJournal commitAll(PublicationTransactionJournal journal)
      throws IOException {
    PublicationTransactionJournal committed =
        PublicationTransactionCommitter.commitAll(journal, runtime);
    return runtime.transition(
        committed,
        PublicationTransactionState.COMMITTED,
        new PublicationTransactionOutcome(
            PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.INCOMPLETE),
        PublicationTransactionFaultPoint.JOURNAL_COMMITTED);
  }

  private PublicationTransactionJournal startCleaning(PublicationTransactionJournal journal)
      throws IOException {
    return runtime.transition(
        journal,
        PublicationTransactionState.CLEANING,
        new PublicationTransactionOutcome(
            PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.INCOMPLETE),
        PublicationTransactionFaultPoint.JOURNAL_CLEANING);
  }

  private PublicationTransactionJournal cleanAll(PublicationTransactionJournal journal)
      throws IOException {
    return PublicationTransactionCleaner.cleanAll(journal, runtime);
  }

  private PublicationTransactionResult complete(PublicationTransactionJournal journal)
      throws IOException {
    return runtime.result(
        runtime.transition(
            journal,
            PublicationTransactionState.COMPLETE,
            new PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE),
            PublicationTransactionFaultPoint.JOURNAL_COMPLETE));
  }

  private static PublicationTransactionOutcome noneCommittedCleanupIncomplete() {
    return new PublicationTransactionOutcome(
        PublicationCommitOutcome.NONE_COMMITTED, PublicationCleanupOutcome.INCOMPLETE);
  }
}
