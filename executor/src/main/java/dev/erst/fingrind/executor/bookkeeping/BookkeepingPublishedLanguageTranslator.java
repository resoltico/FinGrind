package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
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
import dev.erst.fingrind.core.JournalEntry;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Translates between the public published language and the local bookkeeping model. */
public final class BookkeepingPublishedLanguageTranslator {
  private BookkeepingPublishedLanguageTranslator() {}

  /** Translates one published committed posting into the local bookkeeping model. */
  public static CommittedPosting fromPublished(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    BookkeepingEntry callerAuthoredEntry = postingFact.callerAuthoredEntry().orElse(null);
    PostingLineageModel postingLineage = fromPublished(postingFact.postingLineage());
    return new CommittedPosting(
        postingFact.postingId(),
        postingFact.journalEntry(),
        postingLineage,
        postingFact.postingKind(),
        postingFact.postingOriginKind(),
        postingFact.evidence(),
        postingFact.provenance(),
        callerAuthoredEntry,
        resolvedOriginatingEntry(
            callerAuthoredEntry,
            postingFact.postingKind(),
            postingFact.postingOriginKind(),
            postingFact.journalEntry(),
            postingLineage));
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
        account.unitOfMeasure(),
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
        posting.resolvedOriginatingEntry().orElse(posting.callerAuthoredEntry().orElse(null)));
  }

  private static @Nullable BookkeepingEntry resolvedOriginatingEntry(
      @Nullable BookkeepingEntry callerAuthoredEntry,
      dev.erst.fingrind.core.PostingKind postingKind,
      dev.erst.fingrind.core.PostingOriginKind postingOriginKind,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage) {
    if (callerAuthoredEntry == null) {
      return null;
    }
    try {
      PostingOriginatingEntryValidator.requireResolvedMatches(
          callerAuthoredEntry,
          postingKind,
          postingOriginKind,
          journalEntry,
          postingLineage,
          "published posting");
      return callerAuthoredEntry;
    } catch (IllegalArgumentException | IllegalStateException exception) {
      return null;
    }
  }

  /** Translates one bookkeeping opening outcome into the public response model. */
  public static OpenBookResult toPublished(BookOpeningOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    return switch (outcome) {
      case BookOpeningOutcome.Opened opened ->
          new OpenBookResult.Opened(
              opened.initializedAt(), opened.bookIdentity(), opened.attestationTrustRoot());
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
          new FiscalYearCloseResult.Closed(
              toPublished(closed.closedFiscalYear()), closed.idempotentReplay());
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
    return BookkeepingPostingRejectionPublishedMapper.toPublished(rejection);
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
