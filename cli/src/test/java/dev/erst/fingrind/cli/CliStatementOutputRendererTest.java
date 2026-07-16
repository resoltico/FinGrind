package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliStatementReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for CLI statement and report renderers. */
class CliStatementOutputRendererTest extends FinGrindCliTestSupport {
  @Test
  void renderStatementAndFormatterHelpers_coverAllAccountTypeAndEmptySectionBranches() {
    assertEquals(
        "Assets", CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.ASSET));
    assertEquals(
        "Liabilities",
        CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.LIABILITY));
    assertEquals(
        "Equity", CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.EQUITY));
    assertEquals(
        "Revenue", CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.REVENUE));
    assertEquals(
        "Expenses", CliQueryLabelFormatAccess.displayAccountTypeSectionLabel(AccountType.EXPENSE));
    assertEquals("Asset", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.ASSET));
    assertEquals(
        "Liability", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.LIABILITY));
    assertEquals("Equity", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.EQUITY));
    assertEquals("Revenue", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.REVENUE));
    assertEquals("Expense", CliQueryLabelFormatAccess.displayLineTypeLabel(AccountType.EXPENSE));

    FinancialPositionReport emptyFinancialPosition =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.empty(),
            Optional.empty(),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            true,
            List.of(),
            List.of());
    IncomeStatementReport emptyIncomeStatement =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    CashFlowStatementReport emptyCashFlowStatement =
        new CashFlowStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport changesInEquityReport = sampleChangesInEquityReport();

    String financialPositionText =
        CliQueryOutputRenderer.renderFinancialPositionText(emptyFinancialPosition);
    String incomeStatementText =
        CliQueryOutputRenderer.renderIncomeStatementText(emptyIncomeStatement);
    String cashFlowStatementText =
        CliQueryOutputRenderer.renderCashFlowStatementText(emptyCashFlowStatement);
    String changesInEquityText =
        CliQueryOutputRenderer.renderChangesInEquityText(changesInEquityReport);

    assertTrue(financialPositionText.contains("Financial Position"));
    assertTrue(
        financialPositionText.contains("No financial position lines matched the selected scope."));
    assertTrue(incomeStatementText.contains("Income Statement"));
    assertTrue(
        incomeStatementText.contains("No income statement lines matched the selected scope."));
    assertTrue(cashFlowStatementText.contains("Cash Receipts And Payments"));
    assertTrue(cashFlowStatementText.contains("No cash-flow lines matched the selected scope."));
    assertTrue(changesInEquityText.contains("Changes In Equity"));
    assertTrue(changesInEquityText.contains("Closing totals"));
  }

  @Test
  void renderCashFlowStatementTextAndCsv_includeComparativeAndSectionRows() {
    CashFlowStatementReport report = sampleCashFlowStatementReport();

    String renderedText = CliQueryOutputRenderer.renderCashFlowStatementText(report);
    String renderedCsv = CliQueryOutputRenderer.renderCashFlowStatementCsv(report);

    assertTrue(renderedText.contains("Cash Receipts And Payments"));
    assertTrue(renderedText.contains("Comparative Cash Receipts And Payments"));
    assertTrue(renderedText.contains("Operating"));
    assertTrue(renderedText.contains("Financing"));
    assertTrue(renderedText.contains("Revenue (Operating revenue)"), renderedText);
    assertFalse(renderedText.contains("Revenue | Operating revenue"), renderedText);
    assertTrue(
        renderedCsv.startsWith("family,reportPeriod,sectionKind,lineCode,lineName,lineType"));
    assertTrue(renderedCsv.contains("cash-flow-statement,current,OPERATING,2000,Revenue"));
    assertTrue(renderedCsv.contains("cash-flow-statement,current,FINANCING,3000,Owner Capital"));
    assertTrue(
        renderedCsv.contains("cash-flow-statement,comparative,OPERATING,2000,Prior Revenue"));
    assertFalse(renderedCsv.contains("report-total"));
  }

  @Test
  void renderCashFlowStatementCsv_emitsOnlyTheHeaderWhenNothingMatches() {
    CashFlowStatementReport emptyCashFlowStatement =
        new CashFlowStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String renderedCsv = CliQueryOutputRenderer.renderCashFlowStatementCsv(emptyCashFlowStatement);

    assertTrue(
        renderedCsv.startsWith("family,reportPeriod,sectionKind,lineCode,lineName,lineType"));
    assertEquals(1, renderedCsv.lines().count());
  }

  @Test
  void financialPositionText_surfacesImbalancedEquationVerdict() {
    FinancialPositionReport report =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            false,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash and Cash Equivalents",
                            AccountType.ASSET,
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            eurDebitBalance())),
                    List.of(eurDebitBalance()))),
            List.of());

    String rendered = CliQueryOutputRenderer.renderFinancialPositionText(report);

    assertTrue(rendered.contains("Accounting equation"));
    assertTrue(rendered.contains("Imbalanced"));
  }

  @Test
  void displayRowKind_labelsDeclaredAndDerivedRows() {
    assertEquals(
        "Current period result",
        CliQueryLabelFormatAccess.displayRowKind(StatementLineKind.CURRENT_PERIOD_RESULT));
    assertEquals(
        "Account", CliQueryLabelFormatAccess.displayRowKind(StatementLineKind.DECLARED_ACCOUNT));
  }

  @Test
  void statementTextRenderers_hideSyntheticLineCodesForDerivedRows() {
    CurrencyBalance creditBalance =
        CliResponseWriterTestSupport.currencyBalance(
            "EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT);
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            true,
            List.of(
                new FinancialPositionSection(
                    AccountType.EQUITY,
                    List.of(
                        new FinancialPositionRow(
                            "current-period-result",
                            "Current period result",
                            AccountType.EQUITY,
                            Optional.empty(),
                            StatementLineKind.CURRENT_PERIOD_RESULT,
                            creditBalance)),
                    List.of(creditBalance))),
            List.of());
    ChangesInEquityReport changesInEquityReport =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(
                new ChangesInEquityRow(
                    "current-period-result",
                    "Current period result",
                    Optional.of(AccountType.EQUITY),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    CliResponseWriterTestSupport.currencyBalance(
                        "EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO),
                    creditBalance,
                    creditBalance)),
            List.of(),
            List.of(creditBalance),
            List.of(creditBalance),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String financialPositionText =
        CliQueryOutputRenderer.renderFinancialPositionText(financialPositionReport);
    String changesInEquityText =
        CliQueryOutputRenderer.renderChangesInEquityText(changesInEquityReport);

    assertTrue(financialPositionText.contains("Calculated line"));
    assertFalse(financialPositionText.contains("current-period-result"));
    assertTrue(changesInEquityText.contains("Calculated line"));
    assertFalse(changesInEquityText.contains("current-period-result"));
  }

  @Test
  void displayCashFlowLabels_coverSectionAndAssetClassifications() {
    assertEquals(
        "Cash and cash equivalents",
        CliAccountStatementLabels.displayCashFlowAssetClassification(
            CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
    assertEquals(
        "Non-cash asset",
        CliAccountStatementLabels.displayCashFlowAssetClassification(
            CashFlowAssetClassification.NON_CASH));
    assertEquals(
        "Operating",
        CliAccountStatementLabels.displayCashFlowSectionLabel(CashFlowSectionKind.OPERATING));
    assertEquals(
        "Investing",
        CliAccountStatementLabels.displayCashFlowSectionLabel(CashFlowSectionKind.INVESTING));
    assertEquals(
        "Financing",
        CliAccountStatementLabels.displayCashFlowSectionLabel(CashFlowSectionKind.FINANCING));
  }

  @Test
  void renderChangesInEquityCsv_exportsOnlySemanticEquityRows() {
    CurrencyBalance openingBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "0.00"));
    CurrencyBalance movementBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"));
    CurrencyBalance closingBalance =
        CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"));
    ChangesInEquityReport report =
        new ChangesInEquityReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            allPostingKinds(),
            List.of(
                new ChangesInEquityRow(
                    "3200",
                    "Retained Earnings",
                    Optional.of(AccountType.EQUITY),
                    Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                    StatementLineKind.DECLARED_ACCOUNT,
                    openingBalance,
                    movementBalance,
                    closingBalance)),
            List.of(),
            List.of(movementBalance),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    String rendered = CliQueryOutputRenderer.renderChangesInEquityCsv(report);

    assertTrue(rendered.startsWith("family,reportPeriod,lineCode,lineName,lineType"));
    assertTrue(rendered.contains("changes-in-equity,current,3200,Retained Earnings,EQUITY"));
    assertFalse(rendered.contains("total:"));
  }

  @Test
  void renderStatementTexts_skipEmptySectionsAndKeepTotalsOnlySections() {
    CurrencyBalance debitBalance = eurDebitBalance();
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            bookIdentity(),
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
            allPostingKinds(),
            true,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash without Totals",
                            AccountType.ASSET,
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            debitBalance)),
                    List.of()),
                new FinancialPositionSection(AccountType.LIABILITY, List.of(), List.of()),
                new FinancialPositionSection(AccountType.EQUITY, List.of(), List.of(debitBalance))),
            List.of());
    IncomeStatementReport incomeStatementReport =
        new IncomeStatementReport(
            bookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            standardOnly(),
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRow(
                            "4000",
                            "Revenue without Totals",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            debitBalance)),
                    List.of()),
                new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of()),
                new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of(debitBalance))),
            List.of(),
            List.of(),
            List.of());

    String financialPositionText =
        CliQueryOutputRenderer.renderFinancialPositionText(financialPositionReport);
    String incomeStatementText =
        CliQueryOutputRenderer.renderIncomeStatementText(incomeStatementReport);

    assertTrue(financialPositionText.contains("Cash without Totals"));
    assertTrue(financialPositionText.contains("Equity"));
    assertTrue(financialPositionText.contains("Equity Totals"));
    assertTrue(financialPositionText.contains("Empty sections"));
    assertTrue(financialPositionText.contains("Liabilities"));
    assertTrue(financialPositionText.contains("Comparative Financial Position"));
    assertTrue(incomeStatementText.contains("Revenue without Totals"));
    assertTrue(incomeStatementText.contains("Expenses"));
    assertTrue(incomeStatementText.contains("Expenses Totals"));
    assertTrue(incomeStatementText.contains("Empty sections"));
    assertTrue(incomeStatementText.contains("Comparative Income Statement"));
    assertTrue(incomeStatementText.contains("Comparative Net Income Totals"));
  }

  @Test
  void renderIncomeStatementTextAndCsv_showGrossProfitForTradingBooks() {
    IncomeStatementReport tradingIncomeStatement =
        new IncomeStatementReport(
            tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            standardOnly(),
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRow(
                            "4100",
                            "Sales Revenue",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            CliResponseWriterTestSupport.currencyBalance(
                                "EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT))),
                    List.of(
                        CliResponseWriterTestSupport.currencyBalance(
                            "EUR", "0.00", "100.00", "100.00", BalanceSide.CREDIT))),
                new IncomeStatementSection(
                    AccountType.EXPENSE,
                    List.of(
                        new IncomeStatementRow(
                            "5100",
                            "Cost of Sales",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.COST_OF_SALES,
                            StatementLineKind.DECLARED_ACCOUNT,
                            CliResponseWriterTestSupport.currencyBalance(
                                "EUR", "40.00", "0.00", "40.00", BalanceSide.DEBIT)),
                        new IncomeStatementRow(
                            "6100",
                            "Operating Expense",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.OPERATING_EXPENSE,
                            StatementLineKind.DECLARED_ACCOUNT,
                            CliResponseWriterTestSupport.currencyBalance(
                                "EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
                    List.of(
                        CliResponseWriterTestSupport.currencyBalance(
                            "EUR", "50.00", "0.00", "50.00", BalanceSide.DEBIT)))),
            List.of(
                CliResponseWriterTestSupport.currencyBalance(
                    "EUR", "50.00", "100.00", "50.00", BalanceSide.CREDIT)),
            List.of(),
            List.of());

    String incomeStatementText =
        CliQueryOutputRenderer.renderIncomeStatementText(tradingIncomeStatement);
    String incomeStatementCsv =
        CliQueryOutputRenderer.renderIncomeStatementCsv(tradingIncomeStatement);
    CliStatementReportJsonModels.IncomeStatementPayload incomeStatementPayload =
        CliReportPayloadMapper.incomeStatement(tradingIncomeStatement, Instant.EPOCH);

    assertTrue(incomeStatementText.contains("Gross Profit"));
    assertTrue(incomeStatementText.contains("Cost of Sales"));
    assertTrue(incomeStatementText.contains("Cost of Sales Totals"));
    assertTrue(incomeStatementText.contains("Expenses Totals"));
    assertTrue(incomeStatementText.contains("EUR 60.00"));
    assertTrue(
        incomeStatementText.indexOf("Cost of Sales Totals")
            < incomeStatementText.indexOf("Gross Profit"));
    assertFalse(incomeStatementCsv.contains("GROSS_PROFIT_TOTAL"));
    assertEquals(
        "6000", incomeStatementPayload.grossProfitTotals().get(0).netAmount().minorUnits());
    assertTrue(
        incomeStatementCsv.startsWith(
            "family,reportPeriod,sectionKind,lineCode,lineName,lineType,financialPositionLineClassification,profitAndLossLineClassification,lineKind,"));
    assertTrue(incomeStatementCsv.contains(",EXPENSE,5100,Cost of Sales,EXPENSE,,COST_OF_SALES,"));
    assertTrue(
        incomeStatementCsv.contains(",EXPENSE,6100,Operating Expense,EXPENSE,,OPERATING_EXPENSE,"));
    assertFalse(
        incomeStatementCsv.contains(",EXPENSE,5100,Cost of Sales,EXPENSE,,OPERATING_EXPENSE,"));
  }
}
