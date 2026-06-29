package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.ClosedFiscalYear;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.SweptInterimResult;
import java.util.Objects;

/** Translates between the public published language and the local bookkeeping model. */
public final class BookkeepingPublishedLanguageTranslator {
  private BookkeepingPublishedLanguageTranslator() {}

  /** Translates one published committed posting into the local bookkeeping model. */
  public static CommittedPosting fromPublished(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return new CommittedPosting(
        postingFact.postingId(),
        postingFact.journalEntry(),
        fromPublished(postingFact.postingLineage()),
        postingFact.postingKind(),
        postingFact.postingOriginKind(),
        postingFact.evidence(),
        postingFact.provenance(),
        postingFact.originatingEntry());
  }

  /** Translates one published posting lineage into the bookkeeping model. */
  public static PostingLineageModel fromPublished(PostingLineage postingLineage) {
    Objects.requireNonNull(postingLineage, "postingLineage");
    return switch (postingLineage) {
      case PostingLineage.Direct _ -> PostingLineageModel.direct();
      case PostingLineage.Reversal reversal ->
          PostingLineageModel.reversal(reversal.reference(), reversal.reason());
    };
  }

  /** Translates one bookkeeping registered account into the public response model. */
  public static DeclaredAccount toPublished(RegisteredAccount account) {
    Objects.requireNonNull(account, "account");
    return new DeclaredAccount(
        account.accountCode(),
        account.accountName(),
        account.accountType(),
        account.accountTaxonomy(),
        account.active(),
        account.declaredAt());
  }

  /** Translates one bookkeeping committed posting into the public response model. */
  public static PostingFact toPublished(CommittedPosting posting) {
    Objects.requireNonNull(posting, "posting");
    return new PostingFact(
        posting.postingId(),
        posting.journalEntry(),
        toPublished(posting.postingLineage()),
        posting.postingKind(),
        posting.postingOriginKind(),
        posting.evidence(),
        posting.provenance(),
        posting.originatingEntry());
  }

  /** Translates one bookkeeping opening outcome into the public response model. */
  public static OpenBookResult toPublished(BookOpeningOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case BookOpeningOutcome.Opened opened ->
          new OpenBookResult.Opened(opened.initializedAt(), opened.bookIdentity());
      case BookOpeningOutcome.Rejected rejected ->
          new OpenBookResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Translates one bookkeeping account-declaration outcome into the public response model. */
  public static DeclareAccountResult toPublished(AccountDeclarationOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case AccountDeclarationOutcome.Declared declared ->
          new DeclareAccountResult.Declared(toPublished(declared.account()));
      case AccountDeclarationOutcome.Reactivated reactivated ->
          new DeclareAccountResult.Reactivated(toPublished(reactivated.account()));
      case AccountDeclarationOutcome.Renamed renamed ->
          new DeclareAccountResult.Renamed(toPublished(renamed.account()));
      case AccountDeclarationOutcome.Unchanged unchanged ->
          new DeclareAccountResult.Unchanged(toPublished(unchanged.account()));
      case AccountDeclarationOutcome.Rejected rejected ->
          new DeclareAccountResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Translates one bookkeeping interim-result-sweep outcome into the public response model. */
  public static InterimResultSweepResult toPublished(InterimResultSweepOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case InterimResultSweepOutcome.Transferred closed ->
          new InterimResultSweepResult.Swept(toPublished(closed.sweptInterimResult()));
      case InterimResultSweepOutcome.Rejected rejected ->
          new InterimResultSweepResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Translates one bookkeeping fiscal-year-close outcome into the public response model. */
  public static FiscalYearCloseResult toPublished(FiscalYearCloseOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case FiscalYearCloseOutcome.Closed closed ->
          new FiscalYearCloseResult.Closed(toPublished(closed.closedFiscalYear()));
      case FiscalYearCloseOutcome.Rejected rejected ->
          new FiscalYearCloseResult.Rejected(toPublished(rejected.rejection()));
    };
  }

  /** Translates one bookkeeping administration rejection into the public rejection contract. */
  public static BookAdministrationRejection toPublished(
      BookkeepingAdministrationRejection rejection) {
    return BookkeepingAdministrationRejectionPublishedMapper.toPublished(rejection);
  }

  /** Translates one bookkeeping posting rejection into the public rejection contract. */
  public static PostingRejection toPublished(BookkeepingPostingRejection rejection) {
    Objects.requireNonNull(rejection, "rejection");
    return switch (rejection) {
      case BookkeepingPostingRejection.BookNotInitialized _ ->
          new PostingRejection.BookNotInitialized();
      case BookkeepingPostingRejection.AccountStateViolations violations ->
          new PostingRejection.AccountStateViolations(
              violations.violations().stream()
                  .map(BookkeepingPublishedLanguageTranslator::toPublished)
                  .toList());
      case BookkeepingPostingRejection.EntrySemanticsViolations violations ->
          new PostingRejection.EntrySemanticsViolations(
              violations.violations().stream()
                  .map(BookkeepingPublishedLanguageTranslator::toPublished)
                  .toList());
      case BookkeepingPostingRejection.IdempotencyKeyConflict _ ->
          new PostingRejection.IdempotencyKeyConflict();
      case BookkeepingPostingRejection.BookFunctionalCurrencyMismatch currencyMismatch ->
          new PostingRejection.BookFunctionalCurrencyMismatch(
              currencyMismatch.functionalCurrency(), currencyMismatch.attemptedCurrency());
      case BookkeepingPostingRejection.SweptInterimResultViolation rejectionClosedPeriod ->
          new PostingRejection.SweptInterimResultViolation(
              rejectionClosedPeriod.transferredThroughEffectiveDate(),
              rejectionClosedPeriod.attemptedEffectiveDate());
      case BookkeepingPostingRejection.OpeningPositionWindowClosed rejectionWindowClosed ->
          new PostingRejection.OpeningPositionWindowClosed(
              rejectionWindowClosed.firstBlockingPostingKind(),
              rejectionWindowClosed.firstBlockingEffectiveDate());
      case BookkeepingPostingRejection.OpeningPositionTouchesNominalAccount rejectionNominal ->
          new PostingRejection.OpeningPositionTouchesNominalAccount(
              rejectionNominal.accountCode(), rejectionNominal.accountType());
      case BookkeepingPostingRejection.ReservedResultClassification rejectionReserved ->
          new PostingRejection.ReservedResultClassification(
              rejectionReserved.accountCode(),
              rejectionReserved.financialPositionLineClassification());
      case BookkeepingPostingRejection.ReversalTargetNotFound rejectionTarget ->
          new PostingRejection.ReversalTargetNotFound(rejectionTarget.priorPostingId());
      case BookkeepingPostingRejection.ReversalAlreadyExists rejectionExists ->
          new PostingRejection.ReversalAlreadyExists(rejectionExists.priorPostingId());
      case BookkeepingPostingRejection.ReversalDoesNotNegateTarget rejectionMismatch ->
          new PostingRejection.ReversalDoesNotNegateTarget(rejectionMismatch.priorPostingId());
    };
  }

  private static PostingRejection.AccountStateViolation toPublished(
      BookkeepingPostingRejection.AccountStateViolation violation) {
    Objects.requireNonNull(violation, "violation");
    return switch (violation) {
      case BookkeepingPostingRejection.UnknownAccount unknownAccount ->
          new PostingRejection.UnknownAccount(unknownAccount.accountCode());
      case BookkeepingPostingRejection.InactiveAccount inactiveAccount ->
          new PostingRejection.InactiveAccount(inactiveAccount.accountCode());
      case BookkeepingPostingRejection.NonPostableAccount nonPostableAccount ->
          new PostingRejection.NonPostableAccount(
              nonPostableAccount.accountCode(), nonPostableAccount.accountNodeKind());
    };
  }

  private static PostingRejection.EntrySemanticsViolation toPublished(
      BookkeepingPostingRejection.EntrySemanticsViolation violation) {
    Objects.requireNonNull(violation, "violation");
    return new PostingRejection.EntrySemanticsViolation(
        violation.code(), violation.field(), violation.message());
  }

  /** Translates one bookkeeping lineage back into the public published language. */
  public static PostingLineage toPublished(PostingLineageModel postingLineage) {
    Objects.requireNonNull(postingLineage, "postingLineage");
    return switch (postingLineage) {
      case PostingLineageModel.Direct _ -> PostingLineage.direct();
      case PostingLineageModel.Reversal reversal ->
          PostingLineage.reversal(reversal.reference(), reversal.reason());
    };
  }

  /** Translates one durably recorded interim-result sweep into the public contract. */
  public static SweptInterimResult toPublished(
      dev.erst.fingrind.executor.bookkeeping.SweptInterimResult sweptInterimResult) {
    Objects.requireNonNull(sweptInterimResult, "sweptInterimResult");
    return new SweptInterimResult(
        sweptInterimResult.sweepOrder(),
        sweptInterimResult.reportingPeriod(),
        sweptInterimResult.resultHoldingAccountCode(),
        sweptInterimResult.sweptTotals(),
        sweptInterimResult.sweptAt(),
        sweptInterimResult.sweepPostingIds());
  }

  /** Translates one durably recorded fiscal-year close into the public contract. */
  public static ClosedFiscalYear toPublished(ClosedFiscalYearRecord closedFiscalYear) {
    Objects.requireNonNull(closedFiscalYear, "closedFiscalYear");
    return new ClosedFiscalYear(
        closedFiscalYear.closeOrder(),
        closedFiscalYear.reportingPeriod(),
        closedFiscalYear.capitalAccountCode(),
        closedFiscalYear.resultHoldingAccountCode(),
        closedFiscalYear.retainedAccumulatedAccountCode(),
        closedFiscalYear.closedAt(),
        closedFiscalYear.closePostingIds());
  }
}
