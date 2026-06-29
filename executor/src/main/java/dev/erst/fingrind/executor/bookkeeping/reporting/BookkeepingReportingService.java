package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.executor.bookkeeping.CashFlowRowView;
import dev.erst.fingrind.executor.bookkeeping.CashFlowStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.CashFlowStatementView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementView;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Coordinates statement computation over the selected bookkeeping reporting seams. */
public final class BookkeepingReportingService {
  static final Comparator<CurrencyBalance> BALANCE_ORDER = ReportingRowOrdering.BALANCE_ORDER;
  static final Comparator<FinancialPositionRowView> FINANCIAL_POSITION_ROW_ORDER =
      ReportingRowOrdering.FINANCIAL_POSITION_ROW_ORDER;
  static final Comparator<IncomeStatementRowView> INCOME_STATEMENT_ROW_ORDER =
      ReportingRowOrdering.INCOME_STATEMENT_ROW_ORDER;
  static final Comparator<ChangesInEquityRowView> CHANGES_IN_EQUITY_ROW_ORDER =
      ReportingRowOrdering.CHANGES_IN_EQUITY_ROW_ORDER;
  static final Comparator<CashFlowRowView> CASH_FLOW_ROW_ORDER =
      ReportingRowOrdering.CASH_FLOW_ROW_ORDER;

  private final FinancialPositionStatementCalculator financialPositionCalculator;
  private final IncomeStatementCalculator incomeStatementCalculator;
  private final CashFlowStatementCalculator cashFlowStatementCalculator;
  private final ChangesInEquityStatementCalculator changesInEquityCalculator;

  /** Creates one reporting service over the selected lifecycle and report-store seams. */
  public BookkeepingReportingService(BookkeepingReadStore bookStore) {
    ReportingContext context = new ReportingContext(bookStore);
    this.financialPositionCalculator = new FinancialPositionStatementCalculator(context);
    this.incomeStatementCalculator = new IncomeStatementCalculator(context);
    this.cashFlowStatementCalculator = new CashFlowStatementCalculator(context);
    this.changesInEquityCalculator = new ChangesInEquityStatementCalculator(context);
  }

  /** Computes one statement of financial position for the selected bookkeeping book. */
  public FinancialPositionView financialPosition(FinancialPositionCriteria criteria) {
    return financialPositionCalculator.view(Objects.requireNonNull(criteria, "criteria"));
  }

  /** Computes one income statement for the selected bookkeeping book. */
  public IncomeStatementView incomeStatement(IncomeStatementCriteria criteria) {
    return incomeStatementCalculator.view(Objects.requireNonNull(criteria, "criteria"));
  }

  /** Computes one cash-flow statement for the selected bookkeeping book. */
  public CashFlowStatementView cashFlowStatement(CashFlowStatementCriteria criteria) {
    return cashFlowStatementCalculator.view(Objects.requireNonNull(criteria, "criteria"));
  }

  /** Computes one statement of changes in equity for the selected bookkeeping book. */
  public ChangesInEquityView changesInEquity(ChangesInEquityCriteria criteria) {
    return changesInEquityCalculator.view(Objects.requireNonNull(criteria, "criteria"));
  }

  static void assertAccountingEquation(List<FinancialPositionSectionView> sections) {
    FinancialPositionEquationSupport.assertAccountingEquation(sections);
  }

  static long signedMinorUnits(CurrencyBalance balance) {
    return ReportingBalanceSupport.signedMinorUnits(balance);
  }
}
