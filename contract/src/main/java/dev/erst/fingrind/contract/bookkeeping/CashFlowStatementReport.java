package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;

/** Canonical statement of cash receipts and payments for one bounded reporting period. */
public record CashFlowStatementReport(
    BookIdentity bookIdentity,
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    List<CurrencyBalance> openingCashTotals,
    List<CashFlowSection> sections,
    List<CurrencyBalance> movementTotals,
    List<CurrencyBalance> closingCashTotals,
    List<CurrencyBalance> comparativeOpeningCashTotals,
    List<CashFlowSection> comparativeSections,
    List<CurrencyBalance> comparativeMovementTotals,
    List<CurrencyBalance> comparativeClosingCashTotals) {
  /** Validates one cash-flow statement report. */
  public CashFlowStatementReport {
    var statementWindow =
        BookkeepingComparativeReportValidation.requireStatementWindow(
            bookIdentity,
            effectiveDateFrom,
            effectiveDateTo,
            comparativeEffectiveDateRange,
            postingCoverage);
    bookIdentity = statementWindow.bookIdentity();
    effectiveDateFrom = statementWindow.effectiveDateFrom();
    effectiveDateTo = statementWindow.effectiveDateTo();
    comparativeEffectiveDateRange = statementWindow.comparativeEffectiveDateRange();
    postingCoverage = statementWindow.postingCoverage();
    var totals =
        BookkeepingComparativeReportValidation.copyComparativeCurrencyBalances(
            openingCashTotals,
            movementTotals,
            closingCashTotals,
            comparativeOpeningCashTotals,
            comparativeMovementTotals,
            comparativeClosingCashTotals);
    openingCashTotals = totals.openingTotals();
    sections = ContractDescriptorValidation.copyList(sections, "sections");
    movementTotals = totals.movementTotals();
    closingCashTotals = totals.closingTotals();
    comparativeOpeningCashTotals = totals.comparativeOpeningTotals();
    comparativeSections =
        ContractDescriptorValidation.copyList(comparativeSections, "comparativeSections");
    comparativeMovementTotals = totals.comparativeMovementTotals();
    comparativeClosingCashTotals = totals.comparativeClosingTotals();
  }
}
