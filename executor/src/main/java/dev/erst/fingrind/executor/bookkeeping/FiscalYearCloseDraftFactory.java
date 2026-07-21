package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountTaxonomyDoctrine;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Builds fiscal-year-close posting drafts for withdrawal settlement and retained accumulation. */
final class FiscalYearCloseDraftFactory {
  private static final SourceChannel FISCAL_YEAR_CLOSE_SOURCE_CHANNEL = SourceChannel.CLI;

  List<PostingDraft> withdrawalCloseDrafts(
      ReportingPeriod reportingPeriod,
      Map<AccountCode, RegisteredAccount> accountsByCode,
      RegisteredAccount capitalAccount,
      List<CommittedPosting> postings,
      Instant closedAt) {
    return closeDraftsForClassification(
        reportingPeriod,
        accountsByCode,
        capitalAccount,
        postings,
        FiscalYearCloseDraftFactory::isEquityWithdrawal,
        FiscalYearCloseDraftFactory::withdrawalTargetSide,
        "withdrawal-settlement",
        closedAt);
  }

  List<PostingDraft> retainedAccumulationDrafts(
      ReportingPeriod reportingPeriod,
      RegisteredAccount resultHoldingAccount,
      RegisteredAccount retainedAccumulatedAccount,
      List<CommittedPosting> postings,
      List<PostingDraft> plannedInterimResultSweepPostingDrafts,
      Instant closedAt) {
    InterimResultSweepClosingTotals.ByCurrency totalsByCurrency =
        InterimResultSweepClosingTotals.byCurrency();
    for (CommittedPosting posting : postings) {
      recordMatchingAccountLines(
          totalsByCurrency, posting.journalEntry().lines(), resultHoldingAccount);
    }
    for (PostingDraft postingDraft : plannedInterimResultSweepPostingDrafts) {
      recordMatchingAccountLines(
          totalsByCurrency, postingDraft.journalEntry().lines(), resultHoldingAccount);
    }
    return closeDraftsForTarget(
        reportingPeriod,
        retainedAccumulatedAccount,
        resultHoldingAccount.accountCode(),
        resultHoldingAccount,
        totalsByCurrency,
        FiscalYearCloseDraftFactory::retainedAccumulatedTargetSide,
        "retained-accumulation",
        closedAt);
  }

  private List<PostingDraft> closeDraftsForClassification(
      ReportingPeriod reportingPeriod,
      Map<AccountCode, RegisteredAccount> accountsByCode,
      RegisteredAccount targetAccount,
      List<CommittedPosting> postings,
      Predicate<RegisteredAccount> classifier,
      TargetEntrySideResolver targetEntrySideResolver,
      String closeStep,
      Instant closedAt) {
    InterimResultSweepClosingTotals.ByCurrency totalsByCurrency =
        InterimResultSweepClosingTotals.byCurrency();
    for (CommittedPosting posting : postings) {
      if (posting.postingKind() == PostingKind.FISCAL_YEAR_CLOSE) {
        continue;
      }
      for (JournalLine line : posting.journalEntry().lines()) {
        RegisteredAccount account = accountsByCode.get(line.accountCode());
        if (account == null || !classifier.test(account)) {
          continue;
        }
        totalsByCurrency.record(line);
      }
    }
    List<PostingDraft> drafts = new ArrayList<>();
    for (Map.Entry<CurrencyUnit, Map<AccountCode, InterimResultSweepClosingTotals.Totals>>
        currencyEntry : totalsByCurrency.orderedEntries()) {
      PostingDraft draft =
          closeDraftForCurrency(
              reportingPeriod,
              accountsByCode,
              targetAccount,
              currencyEntry.getKey(),
              currencyEntry.getValue(),
              targetEntrySideResolver,
              closeStep,
              closedAt);
      if (draft != null) {
        drafts.add(draft);
      }
    }
    return List.copyOf(drafts);
  }

  private List<PostingDraft> closeDraftsForTarget(
      ReportingPeriod reportingPeriod,
      RegisteredAccount targetAccount,
      AccountCode sourceAccountCode,
      RegisteredAccount sourceAccount,
      InterimResultSweepClosingTotals.ByCurrency totalsByCurrency,
      TargetEntrySideResolver targetEntrySideResolver,
      String closeStep,
      Instant closedAt) {
    List<PostingDraft> drafts = new ArrayList<>();
    for (Map.Entry<CurrencyUnit, Map<AccountCode, InterimResultSweepClosingTotals.Totals>>
        currencyEntry : totalsByCurrency.orderedEntries()) {
      PostingDraft draft =
          closeDraftForCurrency(
              reportingPeriod,
              Map.of(sourceAccountCode, sourceAccount),
              targetAccount,
              currencyEntry.getKey(),
              currencyEntry.getValue(),
              targetEntrySideResolver,
              closeStep,
              closedAt);
      if (draft != null) {
        drafts.add(draft);
      }
    }
    return List.copyOf(drafts);
  }

  private @Nullable PostingDraft closeDraftForCurrency(
      ReportingPeriod reportingPeriod,
      Map<AccountCode, RegisteredAccount> accountsByCode,
      RegisteredAccount targetAccount,
      CurrencyUnit currencyUnit,
      Map<AccountCode, InterimResultSweepClosingTotals.Totals> accountTotals,
      TargetEntrySideResolver targetEntrySideResolver,
      String closeStep,
      Instant closedAt) {
    List<JournalLine> lines = new ArrayList<>();
    long netTargetMinor = 0L;
    List<Map.Entry<AccountCode, InterimResultSweepClosingTotals.Totals>> orderedAccounts =
        accountTotals.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().value()))
            .toList();
    for (Map.Entry<AccountCode, InterimResultSweepClosingTotals.Totals> accountEntry :
        orderedAccounts) {
      RegisteredAccount account =
          Objects.requireNonNull(accountsByCode.get(accountEntry.getKey()), "account");
      long debit = accountEntry.getValue().debit();
      long credit = accountEntry.getValue().credit();
      if (debit == credit) {
        continue;
      }
      JournalLine.EntrySide balanceSide =
          debit > credit ? JournalLine.EntrySide.DEBIT : JournalLine.EntrySide.CREDIT;
      long amountMinor = Math.absExact(debit - credit);
      lines.add(
          new JournalLine(
              account.accountCode(),
              balanceSide == JournalLine.EntrySide.DEBIT
                  ? JournalLine.EntrySide.CREDIT
                  : JournalLine.EntrySide.DEBIT,
              Money.ofMinorUnits(currencyUnit, amountMinor)));
      netTargetMinor =
          Math.addExact(netTargetMinor, naturalSignedMinorUnits(account, balanceSide, amountMinor));
    }
    if (netTargetMinor != 0L) {
      lines.add(
          new JournalLine(
              targetAccount.accountCode(),
              targetEntrySideResolver.resolve(netTargetMinor),
              Money.ofMinorUnits(currencyUnit, Math.absExact(netTargetMinor))));
    }
    if (lines.size() < 2) {
      return null;
    }
    return fiscalYearCloseDraft(
        reportingPeriod, currencyUnit, closeStep, List.copyOf(lines), closedAt);
  }

  private PostingDraft fiscalYearCloseDraft(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      String closeStep,
      List<JournalLine> lines,
      Instant closedAt) {
    var requestProvenance =
        FiscalYearCloseDraftMetadataFactory.requestProvenance(
            reportingPeriod, currencyUnit, closeStep, closedAt);
    PostingCommand requestModel =
        new PostingCommand(
            PostingKind.FISCAL_YEAR_CLOSE,
            PostingOriginKind.FISCAL_YEAR_CLOSE,
            new JournalEntry(reportingPeriod.effectiveDateTo(), lines),
            PostingLineageModel.direct(),
            FiscalYearCloseDraftMetadataFactory.evidence(
                reportingPeriod, currencyUnit, closeStep, closedAt),
            requestProvenance,
            FISCAL_YEAR_CLOSE_SOURCE_CHANNEL);
    return new PostingDraft(
        requestModel.journalEntry(),
        requestModel.postingLineage(),
        requestModel.postingKind(),
        requestModel.postingOriginKind(),
        requestModel.evidence(),
        RequestFingerprintOwner.fingerprint(requestModel),
        new CommittedProvenance(requestProvenance, closedAt, FISCAL_YEAR_CLOSE_SOURCE_CHANNEL));
  }

  private static void recordMatchingAccountLines(
      InterimResultSweepClosingTotals.ByCurrency totalsByCurrency,
      List<JournalLine> lines,
      RegisteredAccount account) {
    for (JournalLine line : lines) {
      if (line.accountCode().equals(account.accountCode())) {
        totalsByCurrency.record(line);
      }
    }
  }

  private static long naturalSignedMinorUnits(
      RegisteredAccount account, JournalLine.EntrySide balanceSide, long amountMinor) {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(balanceSide, "balanceSide");
    if (amountMinor < 0L) {
      throw new IllegalArgumentException("amountMinor must not be negative.");
    }
    NormalBalance normalBalance =
        AccountTaxonomyDoctrine.normalBalance(account.accountType(), account.accountTaxonomy());
    boolean matchesNormalBalance =
        (balanceSide == JournalLine.EntrySide.DEBIT) == (normalBalance == NormalBalance.DEBIT);
    return matchesNormalBalance ? amountMinor : -amountMinor;
  }

  private static boolean isEquityWithdrawal(RegisteredAccount account) {
    return account
        .accountTaxonomy()
        .financialPositionLineClassification()
        .filter(
            dev.erst.fingrind.core.FinancialPositionLineClassification.EQUITY_WITHDRAWAL::equals)
        .isPresent();
  }

  private static JournalLine.EntrySide withdrawalTargetSide(long netTargetMinor) {
    return netTargetMinor > 0L ? JournalLine.EntrySide.DEBIT : JournalLine.EntrySide.CREDIT;
  }

  private static JournalLine.EntrySide retainedAccumulatedTargetSide(long netTargetMinor) {
    return netTargetMinor > 0L ? JournalLine.EntrySide.CREDIT : JournalLine.EntrySide.DEBIT;
  }

  /** Resolves the balancing target side for one aggregated close total. */
  @FunctionalInterface
  private interface TargetEntrySideResolver {
    /** Returns the target journal side for the supplied signed net source total. */
    JournalLine.EntrySide resolve(long netTargetMinor);
  }
}
