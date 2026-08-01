package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.policy.ClosePostingPolicy;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Domain planner for one fiscal-year close. */
public final class FiscalYearClosePlanner {
  private static final String BOOK_IDENTITY_PARAMETER = "bookIdentity";
  private final ClosePostingPolicy closePostingPolicy;
  private final InterimResultSweepPlanner interimResultSweepPlanner;
  private final FiscalYearCloseDraftFactory draftFactory;

  /** Creates a planner bound to the accounting kernel selected by one initialized book. */
  public static FiscalYearClosePlanner forBookIdentity(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, BOOK_IDENTITY_PARAMETER);
    return new FiscalYearClosePlanner(
        bookIdentity,
        KernelAccountingRulesResolver.forBookIdentity(bookIdentity).closePostingPolicy());
  }

  private FiscalYearClosePlanner(BookIdentity bookIdentity, ClosePostingPolicy closePostingPolicy) {
    Objects.requireNonNull(bookIdentity, BOOK_IDENTITY_PARAMETER);
    this.closePostingPolicy = Objects.requireNonNull(closePostingPolicy, "closePostingPolicy");
    this.interimResultSweepPlanner = InterimResultSweepPlanner.forBookIdentity(bookIdentity);
    this.draftFactory = new FiscalYearCloseDraftFactory();
  }

  /** Selects the only active capital account required for fiscal-year close. */
  public CloseTargetSelection capitalAccount(List<RegisteredAccount> accounts) {
    return CloseTargetAccountSelector.select(
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION, accounts);
  }

  /** Selects the only active result-holding account required for fiscal-year close. */
  public CloseTargetSelection resultHoldingAccount(
      BookIdentity bookIdentity, List<RegisteredAccount> accounts) {
    Objects.requireNonNull(bookIdentity, BOOK_IDENTITY_PARAMETER);
    return CloseTargetAccountSelector.select(
        closePostingPolicy.resultHoldingLineClassification(bookIdentity), accounts);
  }

  /** Selects the only active retained-accumulated account required for fiscal-year close. */
  public CloseTargetSelection retainedAccumulatedAccount(List<RegisteredAccount> accounts) {
    return CloseTargetAccountSelector.select(
        FinancialPositionLineClassification.RETAINED_ACCUMULATED, accounts);
  }

  /** Returns the first deterministic fiscal-year-close rejection, if any. */
  public Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod, BookIdentity bookIdentity, LocalDate currentUtcDate) {
    return closeHorizonRejection(
        reportingPeriod, bookIdentity, currentUtcDate, java.util.Optional.empty());
  }

  /** Returns the first deterministic close-horizon rejection for one fiscal-year close, if any. */
  public Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    return FiscalYearCloseValidator.rejectionFor(
        reportingPeriod, bookIdentity, currentUtcDate, transferredThroughEffectiveDate);
  }

  /** Derives the admissible fiscal-year segment identified by the selected label. */
  public ReportingPeriod reportingPeriod(BookIdentity bookIdentity, int fiscalYearLabel) {
    Objects.requireNonNull(bookIdentity, BOOK_IDENTITY_PARAMETER);
    LocalDate fiscalYearStart =
        bookIdentity.fiscalYearStart().labeledFiscalYearStart(fiscalYearLabel);
    LocalDate fiscalYearEnd = bookIdentity.fiscalYearStart().labeledFiscalYearEnd(fiscalYearLabel);
    LocalDate effectiveDateFrom =
        bookIdentity.bookStartEffectiveDate().isAfter(fiscalYearStart)
                && !bookIdentity.bookStartEffectiveDate().isAfter(fiscalYearEnd)
            ? bookIdentity.bookStartEffectiveDate()
            : fiscalYearStart;
    return new ReportingPeriod(effectiveDateFrom, fiscalYearEnd);
  }

  /** Plans one fiscal-year close, including any unswept remainder inside the selected year. */
  public FiscalYearCloseDraft closeDraft(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      RegisteredAccount capitalAccount,
      RegisteredAccount resultHoldingAccount,
      RegisteredAccount retainedAccumulatedAccount,
      List<RegisteredAccount> accounts,
      List<CommittedPosting> postings,
      Optional<LocalDate> latestInterimResultSweepThroughWithinPeriod,
      Instant closedAt) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, BOOK_IDENTITY_PARAMETER);
    Objects.requireNonNull(capitalAccount, "capitalAccount");
    Objects.requireNonNull(resultHoldingAccount, "resultHoldingAccount");
    Objects.requireNonNull(retainedAccumulatedAccount, "retainedAccumulatedAccount");
    Objects.requireNonNull(accounts, "accounts");
    Objects.requireNonNull(postings, "postings");
    Objects.requireNonNull(
        latestInterimResultSweepThroughWithinPeriod, "latestInterimResultSweepThroughWithinPeriod");
    Objects.requireNonNull(closedAt, "closedAt");
    Optional<InterimResultSweepDraft> unsweptInterimResultSweepDraft = Optional.empty();
    List<PostingDraft> plannedInterimResultSweepPostingDrafts = List.of();
    Optional<ReportingPeriod> unsweptPeriod =
        unsweptInterimResultSweepPeriod(
            reportingPeriod, latestInterimResultSweepThroughWithinPeriod);
    if (unsweptPeriod.isPresent()) {
      ReportingPeriod pendingSweepPeriod = unsweptPeriod.orElseThrow();
      InterimResultSweepPlan interimResultSweepPlan =
          interimResultSweepPlanner.closingPostings(
              pendingSweepPeriod,
              resultHoldingAccount,
              accounts,
              postingsInRange(postings, pendingSweepPeriod.effectiveDateRange()),
              closedAt);
      unsweptInterimResultSweepDraft =
          Optional.of(
              new InterimResultSweepDraft(
                  pendingSweepPeriod,
                  resultHoldingAccount.accountCode(),
                  interimResultSweepPlan.sweptTotals(),
                  closedAt,
                  interimResultSweepPlan.closingPostings()));
      plannedInterimResultSweepPostingDrafts =
          unsweptInterimResultSweepDraft.orElseThrow().closingPostings();
    }
    Map<dev.erst.fingrind.core.AccountCode, RegisteredAccount> accountsByCode =
        accounts.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    RegisteredAccount::accountCode, account -> account));
    List<PostingDraft> closePostingDrafts = new ArrayList<>();
    closePostingDrafts.addAll(
        draftFactory.withdrawalCloseDrafts(
            reportingPeriod, accountsByCode, capitalAccount, postings, closedAt));
    closePostingDrafts.addAll(
        draftFactory.retainedAccumulationDrafts(
            reportingPeriod,
            resultHoldingAccount,
            retainedAccumulatedAccount,
            postings,
            plannedInterimResultSweepPostingDrafts,
            closedAt));
    return new FiscalYearCloseDraft(
        reportingPeriod,
        capitalAccount.accountCode(),
        resultHoldingAccount.accountCode(),
        retainedAccumulatedAccount.accountCode(),
        closedAt,
        unsweptInterimResultSweepDraft.orElse(null),
        List.copyOf(closePostingDrafts));
  }

  private static Optional<ReportingPeriod> unsweptInterimResultSweepPeriod(
      ReportingPeriod reportingPeriod,
      Optional<LocalDate> latestInterimResultSweepThroughWithinPeriod) {
    LocalDate effectiveDateFrom =
        latestInterimResultSweepThroughWithinPeriod
            .map(date -> date.plusDays(1))
            .orElse(reportingPeriod.effectiveDateFrom());
    if (effectiveDateFrom.isAfter(reportingPeriod.effectiveDateTo())) {
      return Optional.empty();
    }
    return Optional.of(new ReportingPeriod(effectiveDateFrom, reportingPeriod.effectiveDateTo()));
  }

  private static List<CommittedPosting> postingsInRange(
      List<CommittedPosting> postings, EffectiveDateRange effectiveDateRange) {
    return postings.stream()
        .filter(
            posting ->
                !posting
                        .journalEntry()
                        .effectiveDate()
                        .isBefore(effectiveDateRange.effectiveDateFrom().orElseThrow())
                    && !posting
                        .journalEntry()
                        .effectiveDate()
                        .isAfter(effectiveDateRange.effectiveDateTo().orElseThrow()))
        .toList();
  }
}
