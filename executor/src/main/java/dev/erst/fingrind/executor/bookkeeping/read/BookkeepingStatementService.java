package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.CurrencyBalance;
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
import dev.erst.fingrind.executor.bookkeeping.policy.BookkeepingPolicyPack;
import dev.erst.fingrind.executor.bookkeeping.policy.CoreBookkeepingPolicyPack;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.BookkeepingReportStore;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Coordinates statement computation over the selected bookkeeping read/report seams. */
final class BookkeepingStatementService {
  static final Comparator<CurrencyBalance> BALANCE_ORDER =
      BookkeepingStatementViewSupport.BALANCE_ORDER;
  static final Comparator<FinancialPositionRowView> FINANCIAL_POSITION_ROW_ORDER =
      BookkeepingStatementViewSupport.FINANCIAL_POSITION_ROW_ORDER;
  static final Comparator<IncomeStatementRowView> INCOME_STATEMENT_ROW_ORDER =
      BookkeepingStatementViewSupport.INCOME_STATEMENT_ROW_ORDER;
  static final Comparator<ChangesInEquityRowView> CHANGES_IN_EQUITY_ROW_ORDER =
      BookkeepingStatementViewSupport.CHANGES_IN_EQUITY_ROW_ORDER;

  private final FinancialPositionStatementCalculator financialPositionCalculator;
  private final IncomeStatementCalculator incomeStatementCalculator;
  private final ChangesInEquityStatementCalculator changesInEquityCalculator;

  BookkeepingStatementService(
      BookLifecycleReader lifecycleReader, BookkeepingReportStore reportStore) {
    this(lifecycleReader, reportStore, CoreBookkeepingPolicyPack.current());
  }

  BookkeepingStatementService(
      BookLifecycleReader lifecycleReader,
      BookkeepingReportStore reportStore,
      BookkeepingPolicyPack policyPack) {
    BookkeepingStatementContext context =
        new BookkeepingStatementContext(lifecycleReader, reportStore, policyPack);
    this.financialPositionCalculator = new FinancialPositionStatementCalculator(context);
    this.incomeStatementCalculator = new IncomeStatementCalculator(context);
    this.changesInEquityCalculator = new ChangesInEquityStatementCalculator(context);
  }

  FinancialPositionView financialPosition(FinancialPositionCriteria criteria) {
    return financialPositionCalculator.view(Objects.requireNonNull(criteria, "criteria"));
  }

  IncomeStatementView incomeStatement(IncomeStatementCriteria criteria) {
    return incomeStatementCalculator.view(Objects.requireNonNull(criteria, "criteria"));
  }

  ChangesInEquityView changesInEquity(ChangesInEquityCriteria criteria) {
    return changesInEquityCalculator.view(Objects.requireNonNull(criteria, "criteria"));
  }

  static void assertAccountingEquation(List<FinancialPositionSectionView> sections) {
    BookkeepingStatementViewSupport.assertAccountingEquation(sections);
  }

  static long signedMinorUnits(CurrencyBalance balance) {
    return BookkeepingStatementViewSupport.signedMinorUnits(balance);
  }
}
