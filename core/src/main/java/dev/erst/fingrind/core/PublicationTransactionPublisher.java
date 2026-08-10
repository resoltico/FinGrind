package dev.erst.fingrind.core;

import java.io.IOException;
import java.time.InstantSource;
import java.util.Objects;

/** Owns durable, private publication of one or more secret-bearing final artifacts. */
public final class PublicationTransactionPublisher implements PublicationTransactionService {
  private final PublicationTransactionRuntime runtime;

  private PublicationTransactionPublisher(PublicationTransactionRuntime runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  /** Opens the canonical private owner journal store for production publication and recovery. */
  public static PublicationTransactionPublisher openCanonical()
      throws PrivateOutputDirectory.Violation, IOException {
    return open(
        PublicationTransactionJournalRepository.openCanonical(),
        PublicationTransactionDirectoryDurability.production(),
        InstantSource.system(),
        PublicationTransactionFaultInjector.NONE);
  }

  static PublicationTransactionPublisher open(
      PublicationTransactionJournalRepository repository,
      PublicationTransactionDirectoryDurability directoryDurability,
      InstantSource clock,
      PublicationTransactionFaultInjector faultInjector) {
    return new PublicationTransactionPublisher(
        new PublicationTransactionRuntime(repository, directoryDurability, clock, faultInjector));
  }

  /** Publishes the complete requested member set under one newly created authenticated journal. */
  @Override
  public PublicationTransactionResult publish(PublicationTransactionRequest request)
      throws IOException {
    PublicationTransactionJournal journal =
        PublicationTransactionPlan.prepare(Objects.requireNonNull(request, "request"), runtime);
    return execute(journal, () -> createAndPublish(journal, request));
  }

  /** Recovers one transaction strictly by its authenticated canonical-store identifier. */
  @Override
  public PublicationTransactionResult recover(PublicationTransactionId transactionId)
      throws IOException {
    PublicationTransactionJournal journal =
        runtime.repository().read(Objects.requireNonNull(transactionId, "transactionId"));
    return execute(journal, () -> new PublicationTransactionRunner(runtime).recover(journal));
  }

  private PublicationTransactionResult createAndPublish(
      PublicationTransactionJournal journal, PublicationTransactionRequest request)
      throws IOException {
    runtime.repository().create(journal);
    runtime.faultInjector().after(PublicationTransactionFaultPoint.JOURNAL_PREPARED);
    return new PublicationTransactionRunner(runtime).publishFresh(journal, request);
  }

  private PublicationTransactionResult execute(
      PublicationTransactionJournal journal, PublicationTransactionWork work) throws IOException {
    try (PublicationTransactionDirectoryLeases ignored =
        PublicationTransactionDirectoryLeases.acquire(
            PublicationTransactionPlan.leaseDirectories(journal, runtime))) {
      PublicationTransactionPlan.requireCurrentPrivateDirectories(journal);
      return Objects.requireNonNull(work, "work").run();
    } catch (PublicationTransactionInjectedFault interruption) {
      throw interruption;
    } catch (IOException | RuntimeException failure) {
      throw recordFailure(journal, failure);
    }
  }

  PublicationTransactionExecutionException recordFailure(
      PublicationTransactionJournal journal, Throwable failure) {
    try {
      PublicationTransactionJournal current = runtime.repository().read(journal.transactionId());
      PublicationTransactionJournal recorded =
          switch (current.state()) {
            case PREPARED, STAGED ->
                runtime.transition(
                    current,
                    PublicationTransactionState.BLOCKED,
                    new PublicationTransactionOutcome(
                        PublicationCommitOutcome.NONE_COMMITTED,
                        PublicationCleanupOutcome.INCOMPLETE),
                    PublicationTransactionFaultPoint.JOURNAL_PREPARED);
            case COMMITTING ->
                runtime.transition(
                    current,
                    PublicationTransactionState.COMMIT_UNCERTAIN,
                    new PublicationTransactionOutcome(
                        PublicationCommitOutcome.COMMIT_UNCERTAIN,
                        PublicationCleanupOutcome.INCOMPLETE),
                    PublicationTransactionFaultPoint.JOURNAL_COMMITTING);
            case COMMITTED ->
                runtime.transition(
                    current,
                    PublicationTransactionState.CLEANUP_INCOMPLETE,
                    new PublicationTransactionOutcome(
                        PublicationCommitOutcome.ALL_COMMITTED,
                        PublicationCleanupOutcome.INCOMPLETE),
                    PublicationTransactionFaultPoint.JOURNAL_COMMITTED);
            case CLEANING ->
                runtime.transition(
                    current,
                    PublicationTransactionState.CLEANUP_UNCERTAIN,
                    new PublicationTransactionOutcome(
                        PublicationCommitOutcome.ALL_COMMITTED,
                        PublicationCleanupOutcome.UNCERTAIN),
                    PublicationTransactionFaultPoint.JOURNAL_CLEANING);
            case COMPLETE, BLOCKED, COMMIT_UNCERTAIN, CLEANUP_INCOMPLETE, CLEANUP_UNCERTAIN ->
                current;
          };
      return new PublicationTransactionExecutionException(runtime.result(recorded), failure);
    } catch (IOException | RuntimeException recordingFailure) {
      failure.addSuppressed(recordingFailure);
      return new PublicationTransactionExecutionException(runtime.result(journal), failure);
    }
  }

  /** Runs one publication operation after all transaction directory leases are held. */
  @FunctionalInterface
  private interface PublicationTransactionWork {
    /** Runs the operation using the current owner-held private transaction context. */
    PublicationTransactionResult run() throws IOException;
  }
}
