package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EFFECTIVE_DATE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.currencyBalance;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadStatementPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementSectionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers statement publication from local bookkeeping views into the public contract. */
class BookkeepingReadStatementPublishedLanguageTranslatorTest {
  @Test
  void statementTranslator_projectsFinancialPositionIncomeStatementAndEquityReports() {
    FinancialPositionView financialPositionView =
        new FinancialPositionView(
            bookIdentity(),
            Optional.of(EFFECTIVE_DATE),
            Optional.of(EFFECTIVE_DATE),
            EffectiveDateRange.of(null, EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            true,
            List.of(
                new FinancialPositionSectionView(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRowView(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))),
            List.of(
                new FinancialPositionSectionView(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRowView(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))));
    IncomeStatementView incomeStatementView =
        new IncomeStatementView(
            bookIdentity(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSectionView(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRowView(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(
                new IncomeStatementSectionView(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRowView(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)));
    ChangesInEquityView changesInEquityView =
        new ChangesInEquityView(
            bookIdentity(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new ChangesInEquityRowView(
                    "current-period-result",
                    "Current Period Result",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(
                new ChangesInEquityRowView(
                    "current-period-result",
                    "Current Period Result",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)));

    assertEquals(
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(EFFECTIVE_DATE),
            Optional.of(EFFECTIVE_DATE),
            EffectiveDateRange.of(null, EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            true,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT)))),
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash",
                            AccountType.ASSET,
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(currencyBalance("10.00", "0.00", "10.00", BalanceSide.DEBIT))))),
        BookkeepingReadStatementPublishedLanguageTranslator.toPublished(financialPositionView));
    assertEquals(
        new IncomeStatementReport(
            bookIdentity(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRow(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRow(
                            "4000",
                            "Revenue",
                            AccountType.REVENUE,
                            Optional.of(AccountRole.ORDINARY),
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
                    List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)))),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
        BookkeepingReadStatementPublishedLanguageTranslator.toPublished(incomeStatementView));
    assertEquals(
        new ChangesInEquityReport(
            bookIdentity(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            EffectiveDateRange.of(EFFECTIVE_DATE.minusYears(1), EFFECTIVE_DATE.minusYears(1)),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new ChangesInEquityRow(
                    "current-period-result",
                    "Current Period Result",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(
                new ChangesInEquityRow(
                    "current-period-result",
                    "Current Period Result",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT),
                    currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
            List.of(currencyBalance("0.00", "0.00", "0.00", BalanceSide.ZERO)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT)),
            List.of(currencyBalance("0.00", "10.00", "10.00", BalanceSide.CREDIT))),
        BookkeepingReadStatementPublishedLanguageTranslator.toPublished(changesInEquityView));
  }
}
