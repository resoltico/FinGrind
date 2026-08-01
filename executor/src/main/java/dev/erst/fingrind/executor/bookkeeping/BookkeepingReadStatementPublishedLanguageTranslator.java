package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import java.util.Objects;

/** Projects bookkeeping read statements into the public bookkeeping contract. */
public final class BookkeepingReadStatementPublishedLanguageTranslator {
  private BookkeepingReadStatementPublishedLanguageTranslator() {}

  /** Projects one local statement of financial position back into the public contract. */
  public static FinancialPositionReport toPublished(FinancialPositionView view) {
    Objects.requireNonNull(view, "view");
    return new FinancialPositionReport(
        view.bookIdentity(),
        view.effectiveDateAsOf(),
        view.resolvedEffectiveDateAsOf(),
        view.comparativeEffectiveDateRange(),
        view.postingCoverage(),
        view.accountingEquationBalanced(),
        view.sections().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        view.comparativeSections().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList());
  }

  /** Projects one local income statement back into the public contract. */
  public static IncomeStatementReport toPublished(IncomeStatementView view) {
    Objects.requireNonNull(view, "view");
    return new IncomeStatementReport(
        view.bookIdentity(),
        view.effectiveDateFrom(),
        view.effectiveDateTo(),
        view.comparativeEffectiveDateRange(),
        view.postingCoverage(),
        view.sections().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        view.netIncomeTotals(),
        view.comparativeSections().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        view.comparativeNetIncomeTotals());
  }

  /** Projects one local cash-flow statement back into the public contract. */
  public static CashFlowStatementReport toPublished(CashFlowStatementView view) {
    Objects.requireNonNull(view, "view");
    return new CashFlowStatementReport(
        view.bookIdentity(),
        view.effectiveDateFrom(),
        view.effectiveDateTo(),
        view.comparativeEffectiveDateRange(),
        view.postingCoverage(),
        view.openingCashTotals(),
        view.sections().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        view.movementTotals(),
        view.closingCashTotals(),
        view.comparativeOpeningCashTotals(),
        view.comparativeSections().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        view.comparativeMovementTotals(),
        view.comparativeClosingCashTotals());
  }

  /** Projects one local statement of changes in equity back into the public contract. */
  public static ChangesInEquityReport toPublished(ChangesInEquityView view) {
    Objects.requireNonNull(view, "view");
    return new ChangesInEquityReport(
        view.bookIdentity(),
        view.effectiveDateFrom(),
        view.effectiveDateTo(),
        view.comparativeEffectiveDateRange(),
        view.postingCoverage(),
        view.rows().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        view.openingTotals(),
        view.movementTotals(),
        view.closingTotals(),
        view.comparativeRows().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        view.comparativeOpeningTotals(),
        view.comparativeMovementTotals(),
        view.comparativeClosingTotals());
  }

  private static FinancialPositionSection toPublished(FinancialPositionSectionView section) {
    Objects.requireNonNull(section, "section");
    return new FinancialPositionSection(
        section.accountType(),
        section.rows().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        section.totals());
  }

  private static FinancialPositionRow toPublished(FinancialPositionRowView row) {
    Objects.requireNonNull(row, "row");
    return new FinancialPositionRow(
        row.lineCode(),
        StatementContraPresentation.lineName(row.lineName(), row.contraOfLineCode()),
        row.lineType(),
        row.lineClassification(),
        row.lineKind(),
        row.balance());
  }

  private static IncomeStatementSection toPublished(IncomeStatementSectionView section) {
    Objects.requireNonNull(section, "section");
    return new IncomeStatementSection(
        section.accountType(),
        section.rows().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        section.totals());
  }

  private static IncomeStatementRow toPublished(IncomeStatementRowView row) {
    Objects.requireNonNull(row, "row");
    return new IncomeStatementRow(
        row.lineCode(),
        StatementContraPresentation.lineName(row.lineName(), row.contraOfLineCode()),
        row.lineType(),
        row.lineClassification(),
        row.lineKind(),
        row.movement());
  }

  private static CashFlowSection toPublished(CashFlowSectionView section) {
    Objects.requireNonNull(section, "section");
    return new CashFlowSection(
        section.sectionKind(),
        section.rows().stream()
            .map(BookkeepingReadStatementPublishedLanguageTranslator::toPublished)
            .toList(),
        section.totals());
  }

  private static CashFlowRow toPublished(CashFlowRowView row) {
    Objects.requireNonNull(row, "row");
    return new CashFlowRow(
        row.lineCode(),
        row.lineName(),
        row.lineType(),
        row.financialPositionLineClassification(),
        row.profitAndLossLineClassification(),
        row.lineKind(),
        row.movement());
  }

  private static ChangesInEquityRow toPublished(ChangesInEquityRowView row) {
    Objects.requireNonNull(row, "row");
    return new ChangesInEquityRow(
        row.lineCode(),
        row.lineName(),
        row.lineType(),
        row.lineClassification(),
        row.lineKind(),
        row.openingBalance(),
        row.movement(),
        row.closingBalance());
  }
}
