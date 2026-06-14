package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BalanceSide;
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
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Builds one period-result-transfer draft per currency close bucket. */
final class PeriodResultTransferCloseDraftFactory {
  private static final ActorId PERIOD_RESULT_TRANSFER_ACTOR_ID =
      new ActorId("system:periodResultTransfer");
  private static final ActorType PERIOD_RESULT_TRANSFER_ACTOR_TYPE = ActorType.SYSTEM;
  private static final SourceChannel PERIOD_RESULT_TRANSFER_SOURCE_CHANNEL = SourceChannel.SYSTEM;
  private static final String PERIOD_RESULT_TRANSFER_REQUEST_TOKEN = "periodResultTransfer";

  Optional<CurrencyCloseDraft> closingDraftForCurrency(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      Map<AccountCode, PeriodResultTransferClosingTotals.Totals> accountTotals,
      Map<AccountCode, RegisteredAccount> accountsByCode,
      RegisteredAccount resultHoldingAccount,
      Instant transferredAt) {
    List<JournalLine> lines = new ArrayList<>();
    long netIncomeMinor = 0L;
    List<Map.Entry<AccountCode, PeriodResultTransferClosingTotals.Totals>> orderedAccounts =
        accountTotals.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().value()))
            .toList();
    for (Map.Entry<AccountCode, PeriodResultTransferClosingTotals.Totals> accountEntry :
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
        periodResultTransferEvidence(reportingPeriod, currencyUnit, lines, transferredAt),
        new CommittedProvenance(
            requestProvenance, transferredAt, PERIOD_RESULT_TRANSFER_SOURCE_CHANNEL));
  }

  private static AccountingEvidence periodResultTransferEvidence(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      List<JournalLine> lines,
      Instant transferredAt) {
    String closeToken =
        reportingPeriod.effectiveDateFrom()
            + ":"
            + reportingPeriod.effectiveDateTo()
            + ":"
            + currencyUnit.code()
            + ":"
            + transferredAt.toEpochMilli();
    String artifactContent =
        periodResultTransferArtifactContent(reportingPeriod, currencyUnit, lines, transferredAt);
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId(PERIOD_RESULT_TRANSFER_REQUEST_TOKEN + ":" + closeToken),
                new SourceDocumentType("period-result-transfer-plan"),
                reportingPeriod.effectiveDateTo(),
                transferredAt,
                new StorageLocator("system://period-result-transfer/" + closeToken),
                new ContentSha256(sha256Hex(artifactContent)))),
        List.of());
  }

  private static String periodResultTransferArtifactContent(
      ReportingPeriod reportingPeriod,
      CurrencyUnit currencyUnit,
      List<JournalLine> lines,
      Instant transferredAt) {
    String serializedLines =
        lines.stream()
            .map(
                line ->
                    line.accountCode().value()
                        + "|"
                        + line.side().wireValue()
                        + "|"
                        + line.amount().currencyUnit().code()
                        + "|"
                        + line.amount().minorUnits())
            .collect(java.util.stream.Collectors.joining("\n"));
    return """
        kind=period-result-transfer-plan
        effectiveDateFrom=%s
        effectiveDateTo=%s
        currency=%s
        transferredAt=%s
        lines=
        %s
        """
        .formatted(
            reportingPeriod.effectiveDateFrom(),
            reportingPeriod.effectiveDateTo(),
            currencyUnit.code(),
            transferredAt,
            serializedLines);
  }

  static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable in this Java runtime.", exception);
    }
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
