package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.policy.ResultTransferPolicy;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Domain planner for contiguous period-result-transfer behavior. */
public final class PeriodResultTransferPlanner {
  private final ResultTransferPolicy resultTransferPolicy;
  private final PeriodResultTransferHoldingAccountSelector holdingAccountSelector;
  private final PeriodResultTransferCloseDraftFactory closeDraftFactory;

  /** Creates one period-result-transfer planner from the selected result-transfer policy. */
  public PeriodResultTransferPlanner(ResultTransferPolicy resultTransferPolicy) {
    this.resultTransferPolicy =
        Objects.requireNonNull(resultTransferPolicy, "resultTransferPolicy");
    this.holdingAccountSelector =
        new PeriodResultTransferHoldingAccountSelector(resultTransferPolicy);
    this.closeDraftFactory = new PeriodResultTransferCloseDraftFactory();
  }

  /** Selects the single active result-holding account required by the result-transfer policy. */
  public ResultHoldingSelection resultHoldingAccount(
      BookIdentity bookIdentity, List<RegisteredAccount> accounts) {
    return holdingAccountSelector.resultHoldingAccount(bookIdentity, accounts);
  }

  /** Returns the first deterministic close-horizon rejection for the selected period, if any. */
  public Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    return PeriodResultTransferCloseHorizonValidator.closeHorizonRejection(
        reportingPeriod, bookIdentity, currentUtcDate, transferredThroughEffectiveDate);
  }

  /** Plans durable period-result-transfer postings and the published close totals they produce. */
  public PeriodResultTransferPlan closingPostings(
      ReportingPeriod reportingPeriod,
      RegisteredAccount resultHoldingAccount,
      List<RegisteredAccount> accounts,
      List<CommittedPosting> postings,
      Instant transferredAt) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(resultHoldingAccount, "resultHoldingAccount");
    Objects.requireNonNull(accounts, "accounts");
    Objects.requireNonNull(postings, "postings");
    Objects.requireNonNull(transferredAt, "transferredAt");
    Map<AccountCode, RegisteredAccount> accountsByCode =
        accounts.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    RegisteredAccount::accountCode, account -> account));
    PeriodResultTransferClosingTotals.ByCurrency totalsByCurrency =
        PeriodResultTransferClosingTotals.byCurrency();
    for (CommittedPosting posting : postings) {
      if (posting.postingKind() != PostingKind.STANDARD) {
        continue;
      }
      for (JournalLine line : posting.journalEntry().lines()) {
        RegisteredAccount account = accountsByCode.get(line.accountCode());
        if (account == null || !resultTransferPolicy.closesAccountType(account.accountType())) {
          continue;
        }
        totalsByCurrency.record(line);
      }
    }

    List<PostingDraft> drafts = new ArrayList<>();
    List<CurrencyBalance> transferredTotals = new ArrayList<>();
    for (Map.Entry<CurrencyUnit, Map<AccountCode, PeriodResultTransferClosingTotals.Totals>>
        currencyEntry : totalsByCurrency.orderedEntries()) {
      Optional<PeriodResultTransferCloseDraftFactory.CurrencyCloseDraft> currencyCloseDraft =
          closeDraftFactory.closingDraftForCurrency(
              reportingPeriod,
              currencyEntry.getKey(),
              currencyEntry.getValue(),
              accountsByCode,
              resultHoldingAccount,
              transferredAt);
      if (currencyCloseDraft.isPresent()) {
        PeriodResultTransferCloseDraftFactory.CurrencyCloseDraft closeDraft =
            currencyCloseDraft.orElseThrow();
        drafts.add(closeDraft.postingDraft());
        transferredTotals.add(closeDraft.closedTotal());
      }
    }
    return new PeriodResultTransferPlan(List.copyOf(drafts), List.copyOf(transferredTotals));
  }
}
