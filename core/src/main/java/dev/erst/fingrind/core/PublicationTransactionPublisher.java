package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

  /**
   * Creates the authenticated journal before a caller writes an externally-produced secret stage.
   *
   * <p>Every member must originate from {@link
   * PublicationTransactionMemberRequest#reserveStage(String, PublicationTransactionMemberRole,
   * java.nio.file.Path, PublicationMode)}. A caller completes the production into the returned
   * private paths and then invokes {@link
   * #publishReservedStages(PublicationTransactionStageReservation)}. If production is interrupted
   * before admission, {@link #recover(PublicationTransactionId)} fails closed without publishing or
   * deleting unauthenticated residue.
   */
  @Override
  public PublicationTransactionStageReservation reserveStages(PublicationTransactionRequest request)
      throws IOException {
    PublicationTransactionRequest checkedRequest = Objects.requireNonNull(request, "request");
    if (checkedRequest.members().stream().anyMatch(member -> !member.reservesStage())) {
      throw new IllegalArgumentException(
          "Reserved publication stages require producer-owned members without inline secret input.");
    }
    PublicationTransactionJournal journal =
        PublicationTransactionPlan.prepare(checkedRequest, runtime);
    try (PublicationTransactionDirectoryLeases ignored =
        PublicationTransactionDirectoryLeases.acquire(
            PublicationTransactionPlan.leaseDirectories(journal))) {
      PublicationTransactionPlan.requireCurrentPrivateDirectories(journal);
      runtime.repository().create(journal);
      runtime.faultInjector().after(PublicationTransactionFaultPoint.JOURNAL_PREPARED);
      return new PublicationTransactionStageReservation(journal);
    } catch (PublicationTransactionInjectedFault interruption) {
      throw interruption;
    } catch (IOException | RuntimeException failure) {
      throw recordFailure(journal, failure);
    }
  }

  /**
   * Authenticates every completed producer-written stage and publishes the complete member set.
   *
   * <p>The reservation's paths are never consulted as recovery authority; this method first reads
   * the authenticated canonical journal by ID.
   */
  @Override
  public PublicationTransactionResult publishReservedStages(
      PublicationTransactionStageReservation reservation) throws IOException {
    PublicationTransactionId transactionId =
        Objects.requireNonNull(reservation, "reservation").transactionId();
    PublicationTransactionJournal journal = runtime.repository().read(transactionId);
    return execute(
        journal, () -> new PublicationTransactionRunner(runtime).publishReserved(journal));
  }

  /** Recovers one transaction strictly by its authenticated canonical-store identifier. */
  @Override
  public PublicationTransactionResult recover(PublicationTransactionId transactionId)
      throws IOException {
    PublicationTransactionJournal journal =
        runtime.repository().read(Objects.requireNonNull(transactionId, "transactionId"));
    return execute(journal, () -> new PublicationTransactionRunner(runtime).recover(journal));
  }

  /**
   * Recovers one transaction and proves its complete final member set without revealing a stage.
   *
   * <p>This method intentionally derives artifacts only from the authenticated journal after the
   * ID-only recovery has completed. An incomplete terminal result therefore carries no inferred
   * final artifact, even when a filesystem happens to contain a similarly named path.
   */
  @Override
  public PublicationTransactionRecoveryReceipt recoverWithReceipt(
      PublicationTransactionId transactionId) throws IOException {
    PublicationTransactionId checkedTransactionId =
        Objects.requireNonNull(transactionId, "transactionId");
    PublicationTransactionResult result = recover(checkedTransactionId);
    if (!result.successful()) {
      return new PublicationTransactionRecoveryReceipt(result, List.of());
    }
    PublicationTransactionJournal completed = runtime.repository().read(checkedTransactionId);
    List<PublicationTransactionMemberArtifact> artifacts =
        completed.members().stream()
            .map(
                member ->
                    new PublicationTransactionMemberArtifact(
                        member.memberId(),
                        member.role(),
                        new PublicationTransactionArtifact(member.finalPath(), result)))
            .toList();
    return new PublicationTransactionRecoveryReceipt(result, artifacts);
  }

  /**
   * Recovers the one transaction already bound to an adapter-owned canonical operation context.
   *
   * <p>The lookup itself conveys no artifact or cleanup authority. It only locates an authenticated
   * journal before this implementation immediately applies the ordinary ID-only recovery path.
   */
  @Override
  public Optional<PublicationTransactionRecoveryReceipt> recoverMatchingOwnerContext(
      PublicationTransactionOwnerContext ownerContext) throws IOException {
    List<PublicationTransactionJournal> matches =
        runtime
            .repository()
            .findByOwnerContext(Objects.requireNonNull(ownerContext, "ownerContext"));
    if (matches.isEmpty()) {
      return Optional.empty();
    }
    if (matches.size() != 1) {
      throw new IOException(
          "More than one authenticated publication transaction claims the operation context.");
    }
    return Optional.of(recoverWithReceipt(matches.getFirst().transactionId()));
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
            PublicationTransactionPlan.leaseDirectories(journal))) {
      PublicationTransactionPlan.requireCurrentPrivateDirectories(journal);
      return Objects.requireNonNull(work, "work").run();
    } catch (PublicationTransactionFinalTargetOccupiedException occupied) {
      throw completedNoReplaceCollision(journal, occupied);
    } catch (PublicationTransactionInjectedFault interruption) {
      throw interruption;
    } catch (IOException | RuntimeException failure) {
      throw recordFailure(journal, failure);
    }
  }

  private FileAlreadyExistsException completedNoReplaceCollision(
      PublicationTransactionJournal journal,
      PublicationTransactionFinalTargetOccupiedException occupied)
      throws IOException {
    try {
      PublicationTransactionJournal current = runtime.repository().read(journal.transactionId());
      new PublicationTransactionRunner(runtime).abortNoReplaceCollision(current);
    } catch (PublicationTransactionInjectedFault interruption) {
      throw interruption;
    } catch (IOException | RuntimeException cleanupFailure) {
      cleanupFailure.addSuppressed(occupied);
      throw recordFailure(journal, cleanupFailure);
    }
    return new FileAlreadyExistsException(occupied.finalPath().toString());
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
            case ABORTING ->
                runtime.transition(
                    current,
                    PublicationTransactionState.CLEANUP_UNCERTAIN,
                    new PublicationTransactionOutcome(
                        PublicationCommitOutcome.NONE_COMMITTED,
                        PublicationCleanupOutcome.UNCERTAIN),
                    PublicationTransactionFaultPoint.JOURNAL_CLEANING);
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
