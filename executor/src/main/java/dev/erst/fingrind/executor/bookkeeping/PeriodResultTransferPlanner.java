package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
import dev.erst.fingrind.executor.bookkeeping.policy.ResultTransferPolicy;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Domain planner for contiguous period-result-transfer behavior. */
public final class PeriodResultTransferPlanner {
  private static final ActorId PERIOD_RESULT_TRANSFER_ACTOR_ID =
      new ActorId("system:periodResultTransfer");
  private static final ActorType PERIOD_RESULT_TRANSFER_ACTOR_TYPE = ActorType.SYSTEM;
  private static final SourceChannel PERIOD_RESULT_TRANSFER_SOURCE_CHANNEL = SourceChannel.SYSTEM;
  private static final String PERIOD_RESULT_TRANSFER_REQUEST_TOKEN = "periodResultTransfer";

  private final ResultTransferPolicy resultTransferPolicy;

  /** Creates one period-result-transfer planner from the selected result-transfer policy. */
  public PeriodResultTransferPlanner(ResultTransferPolicy resultTransferPolicy) {
    this.resultTransferPolicy =
        Objects.requireNonNull(resultTransferPolicy, "resultTransferPolicy");
  }

  /** Selects the single active result-holding account required by the result-transfer policy. */
  public ResultHoldingSelection resultHoldingAccount(
      BookIdentity bookIdentity, List<RegisteredAccount> accounts) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(accounts, "accounts");
    var requiredClassification = resultTransferPolicy.resultHoldingLineClassification(bookIdentity);
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
      return new RejectedResultHoldingSelection(
          new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateMissing(
              requiredClassification,
              matchingCandidates.stream().map(RegisteredAccount::accountCode).toList()));
    }
    if (activeCandidates.size() > 1) {
      return new RejectedResultHoldingSelection(
          new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateAmbiguous(
              requiredClassification,
              activeCandidates.stream().map(RegisteredAccount::accountCode).toList()));
    }
    return new AcceptedResultHoldingSelection(activeCandidates.getFirst());
  }

  /** Returns the first deterministic close-horizon rejection for the selected period, if any. */
  public Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    if (reportingPeriod.effectiveDateTo().isAfter(currentUtcDate)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.PeriodResultTransferFutureDate(
              reportingPeriod.effectiveDateTo()));
    }
    if (!bookIdentity
        .fiscalYearStart()
        .containsSingleFiscalYear(
            reportingPeriod.effectiveDateFrom(), reportingPeriod.effectiveDateTo())) {
      return Optional.of(
          new BookkeepingAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary(
              reportingPeriod.effectiveDateFrom(),
              reportingPeriod.effectiveDateTo(),
              bookIdentity.fiscalYearStart()));
    }
    return transferredThroughEffectiveDate
        .map(closedThrough -> closedThrough.plusDays(1))
        .filter(requiredStart -> !requiredStart.equals(reportingPeriod.effectiveDateFrom()))
        .<BookkeepingAdministrationRejection>map(
            BookkeepingAdministrationRejection.PeriodResultTransferMustStartAt::new);
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
    ClosingTotalsByCurrency totalsByCurrency = new ClosingTotalsByCurrency();
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
    for (Map.Entry<CurrencyUnit, Map<AccountCode, Totals>> currencyEntry :
        totalsByCurrency.orderedEntries()) {
      Optional<CurrencyCloseDraft> currencyCloseDraft =
          closingDraftForCurrency(
              reportingPeriod,
              currencyEntry.getKey(),
              currencyEntry.getValue(),
              accountsByCode,
              resultHoldingAccount,
              transferredAt);
      if (currencyCloseDraft.isPresent()) {
        CurrencyCloseDraft closeDraft = currencyCloseDraft.orElseThrow();
        drafts.add(closeDraft.postingDraft());
        transferredTotals.add(closeDraft.closedTotal());
      }
    }
    return new PeriodResultTransferPlan(List.copyOf(drafts), List.copyOf(transferredTotals));
  }

  private Optional<CurrencyCloseDraft> closingDraftForCurrency(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      Map<AccountCode, Totals> accountTotals,
      Map<AccountCode, RegisteredAccount> accountsByCode,
      RegisteredAccount resultHoldingAccount,
      Instant transferredAt) {
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
              resultHoldingAccount.accountCode(),
              netIncomeMinor > 0L ? JournalLine.EntrySide.CREDIT : JournalLine.EntrySide.DEBIT,
              Money.ofMinorUnits(currencyUnit, Math.absExact(netIncomeMinor))));
    }
    if (lines.size() < 2) {
      return Optional.empty();
    }
    return Optional.of(
        new CurrencyCloseDraft(
            periodResultTransferDraft(
                reportingPeriod, currencyUnit, List.copyOf(lines), transferredAt),
            resultHoldingMovement(currencyUnit, netIncomeMinor)));
  }

  private PostingDraft periodResultTransferDraft(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      List<JournalLine> lines,
      Instant transferredAt) {
    String closeToken =
        reportingPeriod.effectiveDateFrom()
            + ":"
            + reportingPeriod.effectiveDateTo()
            + ":"
            + transferredAt.toEpochMilli();
    String currencyToken = currencyUnit.code();
    RequestProvenance requestProvenance =
        new RequestProvenance(
            PERIOD_RESULT_TRANSFER_ACTOR_ID,
            PERIOD_RESULT_TRANSFER_ACTOR_TYPE,
            new CommandId(
                PERIOD_RESULT_TRANSFER_REQUEST_TOKEN + ":" + closeToken + ":" + currencyToken),
            new IdempotencyKey(
                PERIOD_RESULT_TRANSFER_REQUEST_TOKEN + ":" + closeToken + ":" + currencyToken),
            new CausationId(PERIOD_RESULT_TRANSFER_REQUEST_TOKEN + ":" + closeToken),
            Optional.of(
                new CorrelationId(PERIOD_RESULT_TRANSFER_REQUEST_TOKEN + ":" + closeToken)));
    return new PostingDraft(
        new JournalEntry(reportingPeriod.effectiveDateTo(), lines),
        PostingLineageModel.direct(),
        PostingKind.PERIOD_RESULT_TRANSFER,
        PostingOriginKind.PERIOD_RESULT_TRANSFER,
        periodResultTransferEvidence(reportingPeriod, currencyUnit, transferredAt),
        new CommittedProvenance(
            requestProvenance, transferredAt, PERIOD_RESULT_TRANSFER_SOURCE_CHANNEL));
  }

  private static AccountingEvidence periodResultTransferEvidence(
      ReportingPeriod reportingPeriod, CurrencyUnit currencyUnit, Instant transferredAt) {
    String closeToken =
        reportingPeriod.effectiveDateFrom()
            + ":"
            + reportingPeriod.effectiveDateTo()
            + ":"
            + currencyUnit.code()
            + ":"
            + transferredAt.toEpochMilli();
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId(PERIOD_RESULT_TRANSFER_REQUEST_TOKEN + ":" + closeToken),
                new SourceDocumentType("period-result-transfer-plan"),
                reportingPeriod.effectiveDateTo(),
                transferredAt,
                new StorageLocator("system://period-result-transfer/" + closeToken),
                new ContentSha256(sha256Hex(closeToken)))),
        List.of());
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable in this Java runtime.", exception);
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

  /** One generated posting draft plus the result-holding movement it closes. */
  private record CurrencyCloseDraft(PostingDraft postingDraft, CurrencyBalance closedTotal) {
    private CurrencyCloseDraft {
      Objects.requireNonNull(postingDraft, "postingDraft");
      Objects.requireNonNull(closedTotal, "closedTotal");
    }
  }

  private static CurrencyBalance resultHoldingMovement(
      CurrencyUnit currencyUnit, long netIncomeMinor) {
    long resultHoldingDebit = netIncomeMinor < 0L ? Math.absExact(netIncomeMinor) : 0L;
    long resultHoldingCredit = netIncomeMinor > 0L ? netIncomeMinor : 0L;
    return BalanceMath.currencyBalance(currencyUnit, resultHoldingDebit, resultHoldingCredit);
  }
}
