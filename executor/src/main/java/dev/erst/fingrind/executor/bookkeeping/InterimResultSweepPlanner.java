package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.policy.ClosePostingPolicy;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Domain planner for contiguous interim-result-sweep behavior. */
public final class InterimResultSweepPlanner {
  private final ClosePostingPolicy closePostingPolicy;
  private final InterimResultSweepHoldingAccountSelector holdingAccountSelector;
  private final InterimResultSweepDraftFactory closeDraftFactory;

  /** Creates one interim-result-sweep planner from the selected close-posting policy. */
  public InterimResultSweepPlanner(ClosePostingPolicy closePostingPolicy) {
    this.closePostingPolicy = Objects.requireNonNull(closePostingPolicy, "closePostingPolicy");
    this.holdingAccountSelector = new InterimResultSweepHoldingAccountSelector(closePostingPolicy);
    this.closeDraftFactory = new InterimResultSweepDraftFactory();
  }

  /** Selects the single active result-holding account required by the close-posting policy. */
  public InterimResultTargetSelection resultHoldingAccount(
      BookIdentity bookIdentity, List<RegisteredAccount> accounts) {
    return holdingAccountSelector.resultHoldingAccount(bookIdentity, accounts);
  }

  /** Returns the first deterministic close-horizon rejection for the selected period, if any. */
  public Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    return InterimResultSweepHorizonValidator.closeHorizonRejection(
        reportingPeriod, bookIdentity, currentUtcDate, transferredThroughEffectiveDate);
  }

  /** Plans durable interim-result-sweep postings and the published close totals they produce. */
  public InterimResultSweepPlan closingPostings(
      ReportingPeriod reportingPeriod,
      RegisteredAccount resultHoldingAccount,
      List<RegisteredAccount> accounts,
      List<CommittedPosting> postings,
      Instant sweptAt) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(resultHoldingAccount, "resultHoldingAccount");
    Objects.requireNonNull(accounts, "accounts");
    Objects.requireNonNull(postings, "postings");
    Objects.requireNonNull(sweptAt, "sweptAt");
    Map<AccountCode, RegisteredAccount> accountsByCode =
        accounts.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    RegisteredAccount::accountCode, account -> account));
    InterimResultSweepClosingTotals.ByCurrency totalsByCurrency =
        InterimResultSweepClosingTotals.byCurrency();
    for (CommittedPosting posting : postings) {
      if (posting.postingKind() != PostingKind.STANDARD) {
        continue;
      }
      for (JournalLine line : posting.journalEntry().lines()) {
        RegisteredAccount account = accountsByCode.get(line.accountCode());
        if (account == null || !closePostingPolicy.closesAccountType(account.accountType())) {
          continue;
        }
        totalsByCurrency.record(line);
      }
    }

    List<PostingDraft> drafts = new ArrayList<>();
    List<CurrencyBalance> sweptTotals = new ArrayList<>();
    for (Map.Entry<CurrencyUnit, Map<AccountCode, InterimResultSweepClosingTotals.Totals>>
        currencyEntry : totalsByCurrency.orderedEntries()) {
      Optional<InterimResultSweepDraftFactory.CurrencyCloseDraft> currencyCloseDraft =
          closeDraftFactory.closingDraftForCurrency(
              reportingPeriod,
              currencyEntry.getKey(),
              currencyEntry.getValue(),
              accountsByCode,
              resultHoldingAccount,
              sweptAt);
      if (currencyCloseDraft.isPresent()) {
        InterimResultSweepDraftFactory.CurrencyCloseDraft closeDraft =
            currencyCloseDraft.orElseThrow();
        drafts.add(closeDraft.postingDraft());
        sweptTotals.add(closeDraft.closedTotal());
      }
    }
    return new InterimResultSweepPlan(List.copyOf(drafts), List.copyOf(sweptTotals));
  }
}
