package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.policy.ClosePolicy;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Domain planner for contiguous reporting-period close behavior. */
public final class PeriodClosePlanner {
  private static final ActorId PERIOD_CLOSE_ACTOR_ID = new ActorId("system:periodClose");
  private static final ActorType PERIOD_CLOSE_ACTOR_TYPE = ActorType.SYSTEM;
  private static final SourceChannel PERIOD_CLOSE_SOURCE_CHANNEL = SourceChannel.SYSTEM;
  private static final String PERIOD_CLOSE_REQUEST_TOKEN = "periodClose";

  private final ClosePolicy closePolicy;

  /** Creates one period-close planner from the selected close policy. */
  public PeriodClosePlanner(ClosePolicy closePolicy) {
    this.closePolicy = Objects.requireNonNull(closePolicy, "closePolicy");
  }

  /** Selects the single active closing-equity account required by the close policy. */
  public ClosingEquitySelection closingEquityAccount(
      BookIdentity bookIdentity, List<RegisteredAccount> accounts) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(accounts, "accounts");
    var requiredClassification = closePolicy.closingEquityLineClassification(bookIdentity);
    List<RegisteredAccount> matchingCandidates =
        accounts.stream()
            .filter(account -> account.accountType() == AccountType.EQUITY)
            .filter(
                account ->
                    account
                        .accountTaxonomy()
                        .financialPositionLineClassification()
                        .filter(requiredClassification::equals)
                        .isPresent())
            .sorted(Comparator.comparing(account -> account.accountCode().value()))
            .toList();
    List<RegisteredAccount> activeCandidates =
        matchingCandidates.stream().filter(RegisteredAccount::active).toList();
    if (activeCandidates.isEmpty()) {
      return new RejectedClosingEquitySelection(
          new BookkeepingAdministrationRejection.ClosingEquityAccountCandidateMissing(
              requiredClassification,
              matchingCandidates.stream().map(RegisteredAccount::accountCode).toList()));
    }
    if (activeCandidates.size() > 1) {
      return new RejectedClosingEquitySelection(
          new BookkeepingAdministrationRejection.ClosingEquityAccountCandidateAmbiguous(
              requiredClassification,
              activeCandidates.stream().map(RegisteredAccount::accountCode).toList()));
    }
    return new AcceptedClosingEquitySelection(activeCandidates.getFirst());
  }

  /** Returns the first deterministic close-horizon rejection for the selected period, if any. */
  public Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> closedThroughEffectiveDate) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(closedThroughEffectiveDate, "closedThroughEffectiveDate");
    if (reportingPeriod.effectiveDateTo().isAfter(currentUtcDate)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.PeriodCloseFutureDate(
              reportingPeriod.effectiveDateTo()));
    }
    if (!bookIdentity
        .fiscalYearStart()
        .containsSingleFiscalYear(
            reportingPeriod.effectiveDateFrom(), reportingPeriod.effectiveDateTo())) {
      return Optional.of(
          new BookkeepingAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary(
              reportingPeriod.effectiveDateFrom(),
              reportingPeriod.effectiveDateTo(),
              bookIdentity.fiscalYearStart()));
    }
    return closedThroughEffectiveDate
        .map(closedThrough -> closedThrough.plusDays(1))
        .filter(requiredStart -> !requiredStart.equals(reportingPeriod.effectiveDateFrom()))
        .<BookkeepingAdministrationRejection>map(
            BookkeepingAdministrationRejection.PeriodCloseMustStartAt::new);
  }

  /** Plans durable period-close postings and the published close totals they produce. */
  public PeriodClosePlan closingPostings(
      ReportingPeriod reportingPeriod,
      RegisteredAccount closingEquityAccount,
      List<RegisteredAccount> accounts,
      List<CommittedPosting> postings,
      Instant closedAt) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(closingEquityAccount, "closingEquityAccount");
    Objects.requireNonNull(accounts, "accounts");
    Objects.requireNonNull(postings, "postings");
    Objects.requireNonNull(closedAt, "closedAt");
    Map<AccountCode, RegisteredAccount> accountsByCode =
        accounts.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    RegisteredAccount::accountCode, account -> account));
    ClosingTotalsByCurrency totalsByCurrency = new ClosingTotalsByCurrency();
    for (CommittedPosting posting : postings) {
      if (posting.postingKind() != PostingKind.STANDARD) {
        continue;
      }
      for (JournalLine line : posting.journalEntry().lines()) {
        RegisteredAccount account = accountsByCode.get(line.accountCode());
        if (account == null || !closePolicy.closesAccountType(account.accountType())) {
          continue;
        }
        totalsByCurrency.record(line);
      }
    }

    List<PostingDraft> drafts = new ArrayList<>();
    List<CurrencyBalance> closedTotals = new ArrayList<>();
    for (Map.Entry<CurrencyUnit, Map<AccountCode, Totals>> currencyEntry :
        totalsByCurrency.orderedEntries()) {
      Optional<CurrencyCloseDraft> currencyCloseDraft =
          closingDraftForCurrency(
              reportingPeriod,
              currencyEntry.getKey(),
              currencyEntry.getValue(),
              accountsByCode,
              closingEquityAccount,
              closedAt);
      if (currencyCloseDraft.isPresent()) {
        CurrencyCloseDraft closeDraft = currencyCloseDraft.orElseThrow();
        drafts.add(closeDraft.postingDraft());
        closedTotals.add(closeDraft.closedTotal());
      }
    }
    return new PeriodClosePlan(List.copyOf(drafts), List.copyOf(closedTotals));
  }

  private Optional<CurrencyCloseDraft> closingDraftForCurrency(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      Map<AccountCode, Totals> accountTotals,
      Map<AccountCode, RegisteredAccount> accountsByCode,
      RegisteredAccount closingEquityAccount,
      Instant closedAt) {
    List<JournalLine> lines = new ArrayList<>();
    long netIncomeMinor = 0L;
    List<Map.Entry<AccountCode, Totals>> orderedAccounts =
        accountTotals.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().value()))
            .toList();
    for (Map.Entry<AccountCode, Totals> accountEntry : orderedAccounts) {
      RegisteredAccount account =
          Objects.requireNonNull(accountsByCode.get(accountEntry.getKey()), "account");
      long debit = accountEntry.getValue().debit;
      long credit = accountEntry.getValue().credit;
      if (debit == credit) {
        continue;
      }
      BalanceSide balanceSide = debit > credit ? BalanceSide.DEBIT : BalanceSide.CREDIT;
      long amountMinor = Math.absExact(debit - credit);
      lines.add(
          new JournalLine(
              account.accountCode(),
              balanceSide == BalanceSide.DEBIT
                  ? JournalLine.EntrySide.CREDIT
                  : JournalLine.EntrySide.DEBIT,
              Money.ofMinorUnits(currencyUnit, amountMinor)));
      netIncomeMinor =
          Math.addExact(
              netIncomeMinor,
              AccountSemantics.profitAndLossContributionMinorUnits(
                  account.accountType(), account.accountRole(), balanceSide, amountMinor));
    }
    if (netIncomeMinor != 0L) {
      lines.add(
          new JournalLine(
              closingEquityAccount.accountCode(),
              netIncomeMinor > 0L ? JournalLine.EntrySide.CREDIT : JournalLine.EntrySide.DEBIT,
              Money.ofMinorUnits(currencyUnit, Math.absExact(netIncomeMinor))));
    }
    if (lines.size() < 2) {
      return Optional.empty();
    }
    return Optional.of(
        new CurrencyCloseDraft(
            periodCloseDraft(reportingPeriod, currencyUnit, List.copyOf(lines), closedAt),
            closingEquityMovement(currencyUnit, netIncomeMinor)));
  }

  private PostingDraft periodCloseDraft(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      List<JournalLine> lines,
      Instant closedAt) {
    String closeToken =
        reportingPeriod.effectiveDateFrom()
            + ":"
            + reportingPeriod.effectiveDateTo()
            + ":"
            + closedAt.toEpochMilli();
    String currencyToken = currencyUnit.code();
    RequestProvenance requestProvenance =
        new RequestProvenance(
            PERIOD_CLOSE_ACTOR_ID,
            PERIOD_CLOSE_ACTOR_TYPE,
            new CommandId(PERIOD_CLOSE_REQUEST_TOKEN + ":" + closeToken + ":" + currencyToken),
            new IdempotencyKey(PERIOD_CLOSE_REQUEST_TOKEN + ":" + closeToken + ":" + currencyToken),
            new CausationId(PERIOD_CLOSE_REQUEST_TOKEN + ":" + closeToken),
            Optional.of(new CorrelationId(PERIOD_CLOSE_REQUEST_TOKEN + ":" + closeToken)));
    return new PostingDraft(
        new JournalEntry(reportingPeriod.effectiveDateTo(), lines),
        PostingLineageModel.direct(),
        PostingKind.PERIOD_CLOSE,
        new CommittedProvenance(requestProvenance, closedAt, PERIOD_CLOSE_SOURCE_CHANNEL));
  }

  /** One complete close plan containing durable drafts and their published close totals. */
  public record PeriodClosePlan(
      List<PostingDraft> closingPostings, List<CurrencyBalance> closedTotals) {
    public PeriodClosePlan {
      Objects.requireNonNull(closingPostings, "closingPostings");
      Objects.requireNonNull(closedTotals, "closedTotals");
      closingPostings = List.copyOf(closingPostings);
      closedTotals = List.copyOf(closedTotals);
    }
  }

  /** Resolution outcome for selecting the single closing-equity account required by a close. */
  public sealed interface ClosingEquitySelection
      permits AcceptedClosingEquitySelection, RejectedClosingEquitySelection {}

  /** Successful selection of the only valid active closing-equity account. */
  public static final class AcceptedClosingEquitySelection implements ClosingEquitySelection {
    private final RegisteredAccount account;

    /** Creates one accepted selection for the resolved closing-equity account. */
    public AcceptedClosingEquitySelection(RegisteredAccount account) {
      this.account = Objects.requireNonNull(account, "account");
    }

    /** Returns the selected active closing-equity account. */
    public RegisteredAccount account() {
      return account;
    }
  }

  /** Deterministic close rejection caused by missing or ambiguous closing-equity candidates. */
  public static final class RejectedClosingEquitySelection implements ClosingEquitySelection {
    private final BookkeepingAdministrationRejection rejection;

    /** Creates one rejected selection carrying the deterministic close refusal. */
    public RejectedClosingEquitySelection(BookkeepingAdministrationRejection rejection) {
      this.rejection = Objects.requireNonNull(rejection, "rejection");
    }

    /** Returns the deterministic refusal that prevented close-account selection. */
    public BookkeepingAdministrationRejection rejection() {
      return rejection;
    }
  }

  /** Ordered close buckets keyed first by currency and then by account code. */
  private static final class ClosingTotalsByCurrency {
    private final Map<CurrencyUnit, AccountClosingTotals> totalsByCurrency =
        new ConcurrentHashMap<>();

    void record(JournalLine line) {
      accountTotals(line.amount().currencyUnit())
          .record(line.accountCode(), line.side(), line.amount().minorUnits());
    }

    List<Map.Entry<CurrencyUnit, Map<AccountCode, Totals>>> orderedEntries() {
      return totalsByCurrency.entrySet().stream()
          .sorted(Comparator.comparing(entry -> entry.getKey().code()))
          .map(entry -> Map.entry(entry.getKey(), entry.getValue().snapshotTotals()))
          .toList();
    }

    private AccountClosingTotals accountTotals(CurrencyUnit currencyUnit) {
      return totalsByCurrency.computeIfAbsent(currencyUnit, ignored -> new AccountClosingTotals());
    }
  }

  /** Ordered debit/credit close totals keyed by account code. */
  private static final class AccountClosingTotals {
    private final Map<AccountCode, Totals> totalsByAccount = new ConcurrentHashMap<>();

    void record(AccountCode accountCode, JournalLine.EntrySide side, long amountMinor) {
      totalsByAccount.compute(
          accountCode,
          (ignored, existing) ->
              (existing == null ? Totals.ZERO : existing).plus(side, amountMinor));
    }

    Map<AccountCode, Totals> snapshotTotals() {
      return Map.copyOf(totalsByAccount);
    }
  }

  /** Exact debit/credit totals for one account/currency close bucket. */
  private record Totals(long debit, long credit) {
    private static final Totals ZERO = new Totals(0L, 0L);

    private Totals plus(JournalLine.EntrySide side, long amountMinor) {
      return switch (Objects.requireNonNull(side, "side")) {
        case DEBIT -> new Totals(Math.addExact(debit, amountMinor), credit);
        case CREDIT -> new Totals(debit, Math.addExact(credit, amountMinor));
      };
    }
  }

  /** One generated posting draft plus the closing-equity movement it closes. */
  private record CurrencyCloseDraft(PostingDraft postingDraft, CurrencyBalance closedTotal) {
    private CurrencyCloseDraft {
      Objects.requireNonNull(postingDraft, "postingDraft");
      Objects.requireNonNull(closedTotal, "closedTotal");
    }
  }

  private static CurrencyBalance closingEquityMovement(
      CurrencyUnit currencyUnit, long netIncomeMinor) {
    long retainedEarningsDebit = netIncomeMinor < 0L ? Math.absExact(netIncomeMinor) : 0L;
    long retainedEarningsCredit = netIncomeMinor > 0L ? netIncomeMinor : 0L;
    return BalanceMath.currencyBalance(currencyUnit, retainedEarningsDebit, retainedEarningsCredit);
  }
}
