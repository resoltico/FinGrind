package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.PlanPostingCommitResult;
import dev.erst.fingrind.executor.spi.PlanPostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Application service for the sole authored-posting mutation family admitted as a plan child. */
public final class PlanPostingApplicationService {
  private final PostingValidationStore validationStore;
  private final PlanPostingCommitStore planStore;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;
  private final PostingAcceptancePolicy acceptancePolicy;
  private final PostingCommandAdmission commandAdmission;
  private final PostingPreflightService preflightService;

  /** Creates the capability-confined posting service for aggregate ledger plans. */
  public PlanPostingApplicationService(
      PostingValidationStore validationStore,
      PlanPostingCommitStore planStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.validationStore = Objects.requireNonNull(validationStore, "validationStore");
    this.planStore = Objects.requireNonNull(planStore, "planStore");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
    acceptancePolicy = PostingAcceptancePolicy.currentKernel();
    commandAdmission = new PostingCommandAdmission(this.validationStore, this.clock);
    preflightService = new PostingPreflightService(this.validationStore, this.clock);
  }

  /** Validates one plan posting command without creating a child mutation. */
  public PreflightEntryResult preflight(PostEntryCommand command) {
    return preflightService.preflight(command);
  }

  /** Commits one posting as a child of the currently active aggregate ledger plan. */
  public PlanPostEntryOutcome commit(
      PostEntryCommand command, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    PostEntryCommand checkedCommand = Objects.requireNonNull(command, "command");
    AttestationPlanOperationAuthorizer checkedAuthorizer =
        Objects.requireNonNull(attestationAuthorizer, "attestationAuthorizer");
    Optional<PostingRejection> rejection = commandAdmission.rejectionFor(checkedCommand);
    if (rejection.isPresent()) {
      return new PlanPostEntryOutcome.Rejected(rejection.orElseThrow());
    }
    PostingCommand postingCommand = commandAdmission.localPostingCommand(checkedCommand);
    return switch (acceptancePolicy.decisionFor(postingCommand, validationStore)) {
      case PostingAcceptancePolicy.Decision.Replay replay ->
          new PlanPostEntryOutcome.Committed(replay.postingFact(), true);
      case PostingAcceptancePolicy.Decision.Rejected rejected ->
          new PlanPostEntryOutcome.Rejected(
              BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
      case PostingAcceptancePolicy.Decision.Accepted accepted ->
          planCommit(accepted, checkedAuthorizer);
    };
  }

  private PlanPostEntryOutcome planCommit(
      PostingAcceptancePolicy.Decision.Accepted accepted,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    PostingDraft draft =
        new PostingDraft(
            accepted.acceptedPosting().journalEntry(),
            accepted.acceptedPosting().postingLineage(),
            accepted.acceptedPosting().postingKind(),
            accepted.acceptedPosting().postingOriginKind(),
            accepted.acceptedPosting().evidence(),
            accepted.requestFingerprint(),
            new CommittedProvenance(
                accepted.acceptedPosting().requestProvenance(),
                clock.instant(),
                accepted.acceptedPosting().sourceChannel()),
            accepted.acceptedPosting().callerAuthoredEntry().orElse(null),
            accepted.acceptedPosting().resolvedOriginatingEntry().orElse(null));
    return switch (planStore.commitForPlan(draft, postingIdGenerator, attestationAuthorizer)) {
      case PlanPostingCommitResult.Deferred deferred ->
          new PlanPostEntryOutcome.Committed(deferred.postingFact(), false);
      case PlanPostingCommitResult.Replayed replayed ->
          new PlanPostEntryOutcome.Committed(replayed.postingFact(), true);
      case PlanPostingCommitResult.Rejected rejected ->
          new PlanPostEntryOutcome.Rejected(
              BookkeepingPublishedLanguageTranslator.toPublished(rejected.rejection()));
    };
  }
}
