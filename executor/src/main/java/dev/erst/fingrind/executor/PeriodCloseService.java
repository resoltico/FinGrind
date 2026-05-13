package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Application service that closes one contiguous reporting period into retained earnings. */
public final class PeriodCloseService {
  private static final ActorId PERIOD_CLOSE_ACTOR_ID = new ActorId("system:periodClose");
  private static final ActorType PERIOD_CLOSE_ACTOR_TYPE = ActorType.SYSTEM;
  private static final SourceChannel PERIOD_CLOSE_SOURCE_CHANNEL = SourceChannel.CLI;
  private static final String PERIOD_CLOSE_REQUEST_TOKEN = "periodClose";

  private final BookStore bookStore;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  /** Creates the close-period service with its application-owned seams. */
  public PeriodCloseService(
      BookStore bookStore, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Closes one contiguous reporting period using generated retained-earnings closing postings. */
  public PeriodCloseOutcome closePeriod(ReportingPeriod reportingPeriod) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    if (!bookStore.inspectBook().allowsInitializedWorkflow()) {
      return new PeriodCloseOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }

    List<RegisteredAccount> accounts = bookStore.allAccounts();
    Optional<RegisteredAccount> retainedEarningsAccount = retainedEarningsAccount(accounts);
    Optional<BookkeepingAdministrationRejection> retainedEarningsRejection =
        retainedEarningsRejection(retainedEarningsAccount);
    if (retainedEarningsRejection.isPresent()) {
      return new PeriodCloseOutcome.Rejected(retainedEarningsRejection.orElseThrow());
    }

    Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
        closeHorizonRejection(reportingPeriod);
    if (closeHorizonRejection.isPresent()) {
      return new PeriodCloseOutcome.Rejected(closeHorizonRejection.orElseThrow());
    }

    Instant closedAt = clock.instant();
    RegisteredAccount requiredRetainedEarningsAccount = retainedEarningsAccount.orElseThrow();
    PeriodClosePlan closePlan =
        closingPostings(reportingPeriod, requiredRetainedEarningsAccount, accounts, closedAt);
    return bookStore.closePeriod(
        new PeriodCloseDraft(
            reportingPeriod,
            requiredRetainedEarningsAccount.accountCode(),
            closePlan.closedTotals(),
            closedAt,
            closePlan.closingPostings()),
        postingIdGenerator);
  }

  private Optional<RegisteredAccount> retainedEarningsAccount(List<RegisteredAccount> accounts) {
    Objects.requireNonNull(accounts, "accounts");
    List<RegisteredAccount> retainedEarningsAccounts =
        accounts.stream()
            .filter(account -> account.accountRole() == AccountRole.RETAINED_EARNINGS)
            .sorted(Comparator.comparing(account -> account.accountCode().value()))
            .toList();
    if (retainedEarningsAccounts.isEmpty()) {
      return Optional.empty();
    }
    if (retainedEarningsAccounts.size() > 1) {
      throw new IllegalStateException(
          "Retained-earnings account lookup returned more than one account.");
    }
    return Optional.of(retainedEarningsAccounts.getFirst());
  }

  private Optional<BookkeepingAdministrationRejection> retainedEarningsRejection(
      Optional<RegisteredAccount> retainedEarningsAccount) {
    if (retainedEarningsAccount.isEmpty()) {
      return Optional.of(new BookkeepingAdministrationRejection.RetainedEarningsAccountMissing());
    }
    if (!retainedEarningsAccount.orElseThrow().active()) {
      return Optional.of(
          new BookkeepingAdministrationRejection.RetainedEarningsAccountInactive(
              retainedEarningsAccount.orElseThrow().accountCode()));
    }
    return Optional.empty();
  }

  private Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod) {
    return bookStore
        .closedThroughEffectiveDate()
        .map(closedThrough -> closedThrough.plusDays(1))
        .filter(requiredStart -> !requiredStart.equals(reportingPeriod.effectiveDateFrom()))
        .<BookkeepingAdministrationRejection>map(
            BookkeepingAdministrationRejection.PeriodCloseMustStartAt::new);
  }

  private PeriodClosePlan closingPostings(
      ReportingPeriod reportingPeriod,
      RegisteredAccount retainedEarningsAccount,
      List<RegisteredAccount> accounts,
      Instant closedAt) {
    Map<AccountCode, RegisteredAccount> accountsByCode =
        accounts.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    RegisteredAccount::accountCode, account -> account));
    ClosingTotalsByCurrency totalsByCurrency = new ClosingTotalsByCurrency();
    for (CommittedPosting posting : bookStore.postings(reportingPeriod.effectiveDateRange())) {
      if (posting.postingKind() != PostingKind.STANDARD) {
        continue;
      }
      for (JournalLine line : posting.journalEntry().lines()) {
        RegisteredAccount account = accountsByCode.get(line.accountCode());
        if (account == null
            || !AccountSemantics.closesIntoRetainedEarnings(account.accountType())) {
          continue;
        }
        totalsByCurrency.record(line);
      }
    }

    List<PostingDraft> drafts = new ArrayList<>();
    List<dev.erst.fingrind.core.CurrencyBalance> closedTotals = new ArrayList<>();
    List<Map.Entry<CurrencyUnit, Map<AccountCode, Totals>>> orderedCurrencies =
        totalsByCurrency.orderedEntries();
    for (Map.Entry<CurrencyUnit, Map<AccountCode, Totals>> currencyEntry : orderedCurrencies) {
      Optional<CurrencyCloseDraft> currencyCloseDraft =
          closingDraftForCurrency(
              reportingPeriod,
              currencyEntry.getKey(),
              currencyEntry.getValue(),
              accountsByCode,
              retainedEarningsAccount,
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
      RegisteredAccount retainedEarningsAccount,
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
              retainedEarningsAccount.accountCode(),
              netIncomeMinor > 0L ? JournalLine.EntrySide.CREDIT : JournalLine.EntrySide.DEBIT,
              Money.ofMinorUnits(currencyUnit, Math.absExact(netIncomeMinor))));
    }
    if (lines.size() < 2) {
      return Optional.empty();
    }
    return Optional.of(
        new CurrencyCloseDraft(
            periodCloseDraft(reportingPeriod, currencyUnit, List.copyOf(lines), closedAt),
            retainedEarningsMovement(currencyUnit, netIncomeMinor)));
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

  /** One generated posting draft plus the retained-earnings movement it closes. */
  private record CurrencyCloseDraft(
      PostingDraft postingDraft, dev.erst.fingrind.core.CurrencyBalance closedTotal) {
    private CurrencyCloseDraft {
      Objects.requireNonNull(postingDraft, "postingDraft");
      Objects.requireNonNull(closedTotal, "closedTotal");
    }
  }

  /** One complete close plan containing durable drafts and their published close totals. */
  private record PeriodClosePlan(
      List<PostingDraft> closingPostings,
      List<dev.erst.fingrind.core.CurrencyBalance> closedTotals) {
    private PeriodClosePlan {
      Objects.requireNonNull(closingPostings, "closingPostings");
      Objects.requireNonNull(closedTotals, "closedTotals");
      closingPostings = List.copyOf(closingPostings);
      closedTotals = List.copyOf(closedTotals);
    }
  }

  private static dev.erst.fingrind.core.CurrencyBalance retainedEarningsMovement(
      CurrencyUnit currencyUnit, long netIncomeMinor) {
    long retainedEarningsDebit = netIncomeMinor < 0L ? Math.absExact(netIncomeMinor) : 0L;
    long retainedEarningsCredit = netIncomeMinor > 0L ? netIncomeMinor : 0L;
    return BalanceMath.currencyBalance(currencyUnit, retainedEarningsDebit, retainedEarningsCredit);
  }
}
