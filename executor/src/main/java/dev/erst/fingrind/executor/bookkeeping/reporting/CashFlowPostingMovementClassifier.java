package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Classifies one committed posting into cash-flow row movements traced to counterpart accounts. */
final class CashFlowPostingMovementClassifier {
  private CashFlowPostingMovementClassifier() {}

  static List<CashFlowRowMovement> postingMovements(
      Map<AccountCode, RegisteredAccount> accountsByCode, CommittedPosting posting) {
    List<ResolvedLine> resolvedLines =
        posting.journalEntry().lines().stream()
            .map(
                line ->
                    resolveLine(
                        accountsByCode, posting.postingId(), posting.postingOriginKind(), line))
            .toList();
    long cashDebitTotal = sumCash(resolvedLines, JournalLine.EntrySide.DEBIT);
    long cashCreditTotal = sumCash(resolvedLines, JournalLine.EntrySide.CREDIT);
    long internalCashTransferMinor = Math.min(cashDebitTotal, cashCreditTotal);
    long cashReceiptMinor = Math.subtractExact(cashDebitTotal, internalCashTransferMinor);
    long cashPaymentMinor = Math.subtractExact(cashCreditTotal, internalCashTransferMinor);
    if (cashReceiptMinor == 0L && cashPaymentMinor == 0L) {
      return List.of();
    }
    List<CashFlowRowMovement> rowMovements = new ArrayList<>();
    if (cashReceiptMinor > 0L) {
      rowMovements.addAll(
          allocateResidualCash(
              resolvedLines,
              posting.postingOriginKind(),
              JournalLine.EntrySide.CREDIT,
              cashReceiptMinor,
              true));
    }
    if (cashPaymentMinor > 0L) {
      rowMovements.addAll(
          allocateResidualCash(
              resolvedLines,
              posting.postingOriginKind(),
              JournalLine.EntrySide.DEBIT,
              cashPaymentMinor,
              false));
    }
    return List.copyOf(rowMovements);
  }

  /**
   * Allocates residual cash movement onto non-cash counterpart lines only.
   *
   * <p>Internal cash-to-cash transfer legs cancel before classification so each row reflects only
   * external cash movement traced to one non-cash counterpart account.
   */
  private static List<CashFlowRowMovement> allocateResidualCash(
      List<ResolvedLine> resolvedLines,
      PostingOriginKind postingOriginKind,
      JournalLine.EntrySide counterpartSide,
      long residualCashMinor,
      boolean receipt) {
    List<ResolvedLine> candidates =
        resolvedLines.stream()
            .filter(line -> !line.account().cashAndCashEquivalent())
            .filter(line -> line.line().side() == counterpartSide)
            .toList();
    long candidateTotalMinor =
        candidates.stream().mapToLong(candidate -> candidate.line().amount().minorUnits()).sum();
    return allocateMinorUnits(candidates, residualCashMinor, candidateTotalMinor).stream()
        .map(
            allocation ->
                new CashFlowRowMovement(
                    sectionKind(postingOriginKind, allocation.line().account()),
                    allocation.line().account(),
                    receipt
                        ? BalanceMath.currencyBalance(
                            allocation.currencyUnit(), allocation.minorUnits(), 0L)
                        : BalanceMath.currencyBalance(
                            allocation.currencyUnit(), 0L, allocation.minorUnits())))
        .toList();
  }

  private static List<AllocatedMinorUnits> allocateMinorUnits(
      List<ResolvedLine> lines, long residualCashMinor, long candidateTotalMinor) {
    List<AllocationShare> shares = new ArrayList<>(lines.size());
    long allocatedMinor = 0L;
    for (ResolvedLine line : lines) {
      long weightedMinor = Math.multiplyExact(residualCashMinor, line.line().amount().minorUnits());
      long baseMinor = weightedMinor / candidateTotalMinor;
      long remainderMinor = weightedMinor % candidateTotalMinor;
      shares.add(new AllocationShare(line, baseMinor, remainderMinor));
      allocatedMinor = Math.addExact(allocatedMinor, baseMinor);
    }
    long remainingMinor = Math.subtractExact(residualCashMinor, allocatedMinor);
    shares.sort(
        Comparator.comparingLong(AllocationShare::remainderMinor)
            .reversed()
            .thenComparing(share -> share.line().account().accountCode().value()));
    for (int index = 0; index < remainingMinor; index++) {
      AllocationShare share = shares.get(index);
      shares.set(
          index,
          new AllocationShare(
              share.line(), Math.addExact(share.baseMinor(), 1L), share.remainderMinor()));
    }
    return shares.stream()
        .map(
            share ->
                new AllocatedMinorUnits(
                    share.line(), share.line().line().amount().currencyUnit(), share.baseMinor()))
        .filter(share -> share.minorUnits() > 0L)
        .toList();
  }

  private static ResolvedLine resolveLine(
      Map<AccountCode, RegisteredAccount> accountsByCode,
      PostingId postingId,
      PostingOriginKind postingOriginKind,
      JournalLine line) {
    RegisteredAccount account = accountsByCode.get(line.accountCode());
    if (account == null) {
      throw new IllegalStateException(
          "Posting "
              + postingId.value()
              + " references undeclared account "
              + line.accountCode().value()
              + " during cash-flow classification.");
    }
    return new ResolvedLine(postingOriginKind, account, line);
  }

  private static long sumCash(List<ResolvedLine> resolvedLines, JournalLine.EntrySide side) {
    return resolvedLines.stream()
        .filter(line -> line.account().cashAndCashEquivalent())
        .filter(line -> line.line().side() == side)
        .mapToLong(line -> line.line().amount().minorUnits())
        .sum();
  }

  private static CashFlowSectionKind sectionKind(
      PostingOriginKind postingOriginKind, RegisteredAccount account) {
    Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(account, "account");
    if (originDefinesOperatingSection(postingOriginKind)) {
      return CashFlowSectionKind.OPERATING;
    }
    if (originDefinesFinancingSection(postingOriginKind)) {
      return CashFlowSectionKind.FINANCING;
    }
    return sectionKindByAccountType(account);
  }

  private static boolean originDefinesOperatingSection(PostingOriginKind postingOriginKind) {
    return switch (postingOriginKind) {
      case SALE_SETTLED,
          SALE_ON_CREDIT,
          PURCHASE_SETTLED,
          PURCHASE_ON_CREDIT,
          EXPENSE_SETTLED,
          EXPENSE_ON_CREDIT,
          RECEIPT,
          PAYMENT ->
          true;
      default -> false;
    };
  }

  private static boolean originDefinesFinancingSection(PostingOriginKind postingOriginKind) {
    return switch (postingOriginKind) {
      case OWNER_CONTRIBUTION, OWNER_WITHDRAWAL -> true;
      default -> false;
    };
  }

  private static CashFlowSectionKind sectionKindByAccountType(RegisteredAccount account) {
    return switch (account.accountType()) {
      case REVENUE, EXPENSE -> CashFlowSectionKind.OPERATING;
      case dev.erst.fingrind.core.AccountType.ASSET -> assetSectionKind(account);
      case dev.erst.fingrind.core.AccountType.LIABILITY -> liabilitySectionKind(account);
      case dev.erst.fingrind.core.AccountType.EQUITY -> CashFlowSectionKind.FINANCING;
    };
  }

  private static CashFlowSectionKind assetSectionKind(RegisteredAccount account) {
    FinancialPositionLineClassification classification = financialPositionClassification(account);
    return classification == FinancialPositionLineClassification.CURRENT_ASSET
            || classification == FinancialPositionLineClassification.INVENTORY
        ? CashFlowSectionKind.OPERATING
        : CashFlowSectionKind.INVESTING;
  }

  private static CashFlowSectionKind liabilitySectionKind(RegisteredAccount account) {
    FinancialPositionLineClassification classification = financialPositionClassification(account);
    return classification == FinancialPositionLineClassification.CURRENT_LIABILITY
        ? CashFlowSectionKind.OPERATING
        : CashFlowSectionKind.FINANCING;
  }

  private static FinancialPositionLineClassification financialPositionClassification(
      RegisteredAccount account) {
    return account.accountTaxonomy().financialPositionLineClassification().orElseThrow();
  }

  private record ResolvedLine(
      PostingOriginKind postingOriginKind, RegisteredAccount account, JournalLine line) {
    private ResolvedLine {
      Objects.requireNonNull(postingOriginKind, "postingOriginKind");
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(line, "line");
    }
  }

  private record AllocationShare(ResolvedLine line, long baseMinor, long remainderMinor) {
    private AllocationShare {
      Objects.requireNonNull(line, "line");
    }
  }

  private record AllocatedMinorUnits(
      ResolvedLine line, CurrencyUnit currencyUnit, long minorUnits) {
    private AllocatedMinorUnits {
      Objects.requireNonNull(line, "line");
      Objects.requireNonNull(currencyUnit, "currencyUnit");
    }
  }

  record CashFlowRowMovement(
      CashFlowSectionKind sectionKind, RegisteredAccount account, CurrencyBalance movement) {
    CashFlowRowMovement {
      Objects.requireNonNull(sectionKind, "sectionKind");
      Objects.requireNonNull(account, "account");
      Objects.requireNonNull(movement, "movement");
    }
  }
}
