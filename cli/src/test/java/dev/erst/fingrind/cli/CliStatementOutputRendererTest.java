package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
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
    ChangesInEquityReport changesInEquityReport = sampleChangesInEquityReport();

    String financialPositionText =
        CliReportOutputRenderer.renderFinancialPositionText(emptyFinancialPosition);
    String incomeStatementText =
        CliReportOutputRenderer.renderIncomeStatementText(emptyIncomeStatement);
    String changesInEquityText =
        CliReportOutputRenderer.renderChangesInEquityText(changesInEquityReport);

    assertTrue(financialPositionText.contains("Financial Position"));
    assertTrue(
        financialPositionText.contains("No financial position lines matched the selected scope."));
    assertTrue(incomeStatementText.contains("Income Statement"));
    assertTrue(
        incomeStatementText.contains("No income statement lines matched the selected scope."));
    assertTrue(changesInEquityText.contains("Changes In Equity"));
    assertTrue(changesInEquityText.contains("Closing totals"));
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
                            Optional.of(AccountRole.ORDINARY),
                            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                            StatementLineKind.DECLARED_ACCOUNT,
                            eurDebitBalance())),
                    List.of(eurDebitBalance()))),
            List.of());

    String rendered = CliReportOutputRenderer.renderFinancialPositionText(report);

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
        CliReportOutputRenderer.renderFinancialPositionText(financialPositionReport);
    String changesInEquityText =
        CliReportOutputRenderer.renderChangesInEquityText(changesInEquityReport);

    assertTrue(financialPositionText.contains("Calculated line"));
    assertFalse(financialPositionText.contains("current-period-result"));
    assertTrue(changesInEquityText.contains("Calculated line"));
    assertFalse(changesInEquityText.contains("current-period-result"));
  }

  @Test
  void renderChangesInEquityCsv_fillsMissingCurrencyTotalsWithZeroBalances() {
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
                    Optional.of(AccountRole.ORDINARY),
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

    String rendered = CliReportOutputRenderer.renderChangesInEquityCsv(report);

    assertTrue(
        rendered.contains(
            "current,report-total,2026-04-01,2026-04-30,report-total,Report total,,,REPORT_TOTAL,EUR"));
    assertTrue(
        rendered.contains(",EUR,0.00,0.00,0.00,ZERO,0.00,10.00,10.00,CREDIT,0.00,0.00,0.00,ZERO"));
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
                            Optional.of(AccountRole.ORDINARY),
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
                            Optional.of(AccountRole.ORDINARY),
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
        CliReportOutputRenderer.renderFinancialPositionText(financialPositionReport);
    String incomeStatementText =
        CliReportOutputRenderer.renderIncomeStatementText(incomeStatementReport);

    assertTrue(financialPositionText.contains("Cash without Totals"));
    assertTrue(financialPositionText.contains("Equity"));
    assertTrue(financialPositionText.contains("Section totals"));
    assertTrue(financialPositionText.contains("Empty sections"));
    assertTrue(financialPositionText.contains("Liabilities"));
    assertFalse(financialPositionText.contains("Comparative Financial Position"));
    assertTrue(incomeStatementText.contains("Revenue without Totals"));
    assertTrue(incomeStatementText.contains("Expenses"));
    assertTrue(incomeStatementText.contains("Section totals"));
    assertTrue(incomeStatementText.contains("Empty sections"));
    assertFalse(incomeStatementText.contains("Comparative Income Statement"));
  }
}
