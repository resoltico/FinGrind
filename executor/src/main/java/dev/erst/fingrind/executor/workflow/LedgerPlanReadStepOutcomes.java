package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.AttestationPostingCommitmentProjection;
import dev.erst.fingrind.executor.PostingPreflightService;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.AttestationPostingCommitmentStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.LedgerPlanReadStore;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Shared read and preflight outcomes used by attested and credential-free ledger plans. */
final class LedgerPlanReadStepOutcomes {
  private final BookkeepingReadService bookkeepingReadService;
  private final AttestationPostingCommitmentStore attestationCommitmentStore;
  private final PostingPreflightService postingPreflightService;

  LedgerPlanReadStepOutcomes(LedgerPlanReadStore executionStore, Clock clock) {
    LedgerPlanReadStore checkedExecutionStore =
        Objects.requireNonNull(executionStore, "executionStore");
    bookkeepingReadService = new BookkeepingReadService(checkedExecutionStore);
    attestationCommitmentStore = checkedExecutionStore;
    postingPreflightService =
        new PostingPreflightService(checkedExecutionStore, Objects.requireNonNull(clock, "clock"));
  }

  BookLifecycleInspection inspectBook() {
    return bookkeepingReadService.inspectBook();
  }

  boolean allowsInitializedWorkflow() {
    return bookkeepingReadService.allowsInitializedWorkflow();
  }

  LedgerPlanStepOutcome preflightOutcome(BookWorkflowStep.PreflightEntry step) {
    return switch (postingPreflightService.preflight(step.command())) {
      case dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted accepted ->
          LedgerPlanStepOutcomes.stepSucceeded(
              BookWorkflowFact.text("idempotencyKey", accepted.idempotencyKey().value()),
              BookWorkflowFact.text("effectiveDate", accepted.effectiveDate().toString()));
      case dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected rejected ->
          LedgerPlanRejectedOutcomes.postingRejection(rejected.rejection());
    };
  }

  LedgerPlanStepOutcome inspectBookOutcome() {
    BookLifecycleInspection inspection = inspectBook();
    BookLifecycleInspection.Status status = inspection.status();
    return LedgerPlanStepOutcomes.stepSucceeded(
        BookWorkflowFact.text("state", status.wireValue()),
        BookWorkflowFact.flag("initialized", status.initialized()),
        BookWorkflowFact.flag("compatibleWithCurrentBinary", status.compatibleWithCurrentBinary()));
  }

  LedgerPlanStepOutcome listAccountsOutcome(BookWorkflowStep.ListAccounts step) {
    return switch (bookkeepingReadService.listAccounts(step.query())) {
      case BookkeepingReadOutcome.Reported<AccountRegistryPage> reported ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountPageFacts(reported.value()));
      case BookkeepingReadOutcome.Rejected<AccountRegistryPage> rejected ->
          LedgerPlanRejectedOutcomes.queryRejection(rejected.rejection());
    };
  }

  LedgerPlanStepOutcome getPostingOutcome(BookWorkflowStep.GetPosting step) {
    return switch (bookkeepingReadService.getPosting(step.postingId())) {
      case BookkeepingReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              reported ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.postingFacts(
                      reported.value(), attestationCommitmentFor(reported.value().postingId()))
                  .toArray(BookWorkflowFact[]::new));
      case BookkeepingReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              rejected ->
          LedgerPlanRejectedOutcomes.queryRejection(rejected.rejection());
    };
  }

  LedgerPlanStepOutcome listPostingsOutcome(BookWorkflowStep.ListPostings step) {
    return switch (bookkeepingReadService.listPostings(step.query())) {
      case BookkeepingReadOutcome.Reported<PostingHistoryPage> reported ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.postingPageFacts(
                  reported.value(), attestationCommitmentsFor(reported.value())));
      case BookkeepingReadOutcome.Rejected<PostingHistoryPage> rejected ->
          LedgerPlanRejectedOutcomes.queryRejection(rejected.rejection());
    };
  }

  LedgerPlanStepOutcome accountBalanceOutcome(BookWorkflowStep.AccountBalance step) {
    return switch (bookkeepingReadService.accountBalance(step.query())) {
      case BookkeepingReadOutcome.Reported<AccountBalanceView> reported ->
          LedgerPlanStepOutcomes.balanceFacts(reported.value());
      case BookkeepingReadOutcome.Rejected<AccountBalanceView> rejected ->
          LedgerPlanRejectedOutcomes.queryRejection(rejected.rejection());
    };
  }

  LedgerPlanStepOutcome assertionOutcome(BookWorkflowAssertion assertion) {
    return LedgerPlanAssertionEvaluator.evaluate(bookkeepingReadService, assertion);
  }

  private @Nullable AttestationCommit attestationCommitmentFor(PostingId postingId) {
    return AttestationPostingCommitmentProjection.resolve(
            attestationCommitmentStore, Set.of(postingId))
        .get(postingId);
  }

  private Map<PostingId, AttestationCommit> attestationCommitmentsFor(PostingHistoryPage page) {
    Set<PostingId> postingIds = new LinkedHashSet<>();
    page.postings().forEach(posting -> postingIds.add(posting.postingId()));
    return AttestationPostingCommitmentProjection.resolve(attestationCommitmentStore, postingIds);
  }
}
