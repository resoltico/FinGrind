package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CryptographicPrimitives;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossAccountDoctrine;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Builds one interim-result-sweep draft per currency close bucket. */
final class InterimResultSweepDraftFactory {
  private static final ActorId INTERIM_RESULT_SWEEP_ACTOR_ID =
      new ActorId("system:interimResultSweep");
  private static final ActorType INTERIM_RESULT_SWEEP_ACTOR_TYPE = ActorType.SYSTEM;
  private static final SourceChannel INTERIM_RESULT_SWEEP_SOURCE_CHANNEL = SourceChannel.SYSTEM;
  private static final String INTERIM_RESULT_SWEEP_REQUEST_TOKEN = "interimResultSweep";
  private static final String INTERIM_RESULT_SWEEP_OPERATION =
      OperationId.INTERIM_RESULT_SWEEP.wireName();
  private static final String GENERATED_PLAN_SUFFIX = "-plan";

  Optional<CurrencyCloseDraft> closingDraftForCurrency(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      Map<AccountCode, InterimResultSweepClosingTotals.Totals> accountTotals,
      Map<AccountCode, RegisteredAccount> accountsByCode,
      RegisteredAccount resultHoldingAccount,
      Instant sweptAt) {
    List<JournalLine> lines = new ArrayList<>();
    long netIncomeMinor = 0L;
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
              ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
                  account.accountType(), account.accountTaxonomy(), balanceSide, amountMinor));
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
            interimResultSweepDraft(reportingPeriod, currencyUnit, List.copyOf(lines), sweptAt),
            resultHoldingMovement(currencyUnit, netIncomeMinor)));
  }

  private PostingDraft interimResultSweepDraft(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      List<JournalLine> lines,
      Instant sweptAt) {
    String closeToken =
        reportingPeriod.effectiveDateFrom()
            + ":"
            + reportingPeriod.effectiveDateTo()
            + ":"
            + sweptAt.toEpochMilli();
    String currencyToken = currencyUnit.code();
    RequestProvenance requestProvenance =
        new RequestProvenance(
            INTERIM_RESULT_SWEEP_ACTOR_ID,
            INTERIM_RESULT_SWEEP_ACTOR_TYPE,
            new CommandId(
                INTERIM_RESULT_SWEEP_REQUEST_TOKEN + ":" + closeToken + ":" + currencyToken),
            new IdempotencyKey(
                INTERIM_RESULT_SWEEP_REQUEST_TOKEN + ":" + closeToken + ":" + currencyToken),
            new CausationId(INTERIM_RESULT_SWEEP_REQUEST_TOKEN + ":" + closeToken),
            Optional.of(new CorrelationId(INTERIM_RESULT_SWEEP_REQUEST_TOKEN + ":" + closeToken)));
    PostingCommand requestModel =
        new PostingCommand(
            PostingKind.INTERIM_RESULT_SWEEP,
            PostingOriginKind.INTERIM_RESULT_SWEEP,
            new JournalEntry(reportingPeriod.effectiveDateTo(), lines),
            PostingLineageModel.direct(),
            interimResultSweepEvidence(reportingPeriod, currencyUnit, sweptAt),
            requestProvenance,
            INTERIM_RESULT_SWEEP_SOURCE_CHANNEL);
    return new PostingDraft(
        requestModel.journalEntry(),
        requestModel.postingLineage(),
        requestModel.postingKind(),
        requestModel.postingOriginKind(),
        requestModel.evidence(),
        RequestFingerprintOwner.fingerprint(requestModel),
        new CommittedProvenance(requestProvenance, sweptAt, INTERIM_RESULT_SWEEP_SOURCE_CHANNEL));
  }

  private static AccountingEvidence interimResultSweepEvidence(
      ReportingPeriod reportingPeriod, CurrencyUnit currencyUnit, Instant sweptAt) {
    String closeToken =
        reportingPeriod.effectiveDateFrom()
            + ":"
            + reportingPeriod.effectiveDateTo()
            + ":"
            + currencyUnit.code()
            + ":"
            + sweptAt.toEpochMilli();
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId(INTERIM_RESULT_SWEEP_REQUEST_TOKEN + ":" + closeToken),
                new SourceDocumentType(INTERIM_RESULT_SWEEP_OPERATION + GENERATED_PLAN_SUFFIX),
                reportingPeriod.effectiveDateTo())),
        List.of());
  }

  static String sha256Hex(String value) {
    return CryptographicPrimitives.sha256HexUtf8(value);
  }

  /** One generated posting draft plus the result-holding movement it closes. */
  record CurrencyCloseDraft(PostingDraft postingDraft, CurrencyBalance closedTotal) {
    CurrencyCloseDraft {
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
