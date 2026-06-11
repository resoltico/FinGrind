package dev.erst.fingrind.report.pdf;

import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.BOOK_IDENTITY;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.CLOCK;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.PDF_REPORT_SERVICE;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.REVENUE_ACCOUNT;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.assertPdfMetadata;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.balance;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.changesInEquityRow;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.extractedText;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.financialPositionRow;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.incomeStatementRow;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.trialBalanceReport;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests for {@link PdfReportService}. */
class PdfReportServiceTest {
  @Test
  void renderAccountBalanceAndTrialBalanceIncludeMetadataAndExpectedText() throws IOException {
    byte[] accountBalancePdf =
        PDF_REPORT_SERVICE.renderAccountBalance(
            new AccountBalanceSnapshot(
                BOOK_IDENTITY,
                CASH_ACCOUNT,
                Optional.of(LocalDate.parse("2026-04-01")),
                Optional.of(LocalDate.parse("2026-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(
                    balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT),
                    balance("USD", "500.00", "100.00", "400.00", BalanceSide.DEBIT))));
    byte[] trialBalancePdf =
        PDF_REPORT_SERVICE.renderTrialBalance(
            trialBalanceReport(
                BOOK_IDENTITY,
                Optional.of(LocalDate.parse("2026-04-30")),
                EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(
                    new TrialBalanceRow(
                        CASH_ACCOUNT,
                        balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT)),
                    new TrialBalanceRow(
                        REVENUE_ACCOUNT,
                        balance("EUR", "10.00", "1250.00", "1240.00", BalanceSide.CREDIT))),
                List.of(
                    new TrialBalanceRow(
                        CASH_ACCOUNT,
                        balance("EUR", "1000.00", "10.00", "990.00", BalanceSide.DEBIT)))));
    assertPdfMetadata(accountBalancePdf, "Account Balance", true);
    assertPdfMetadata(trialBalancePdf, "Trial Balance", false);
    assertTrue(extractedText(accountBalancePdf).contains("Cash on Hand and Bank Balances"));
    assertTrue(extractedText(accountBalancePdf).contains("Per-Currency Balances"));
    assertTrue(extractedText(trialBalancePdf).contains("Trial Balance"));
    String trialBalanceText = extractedText(trialBalancePdf);
    assertTrue(trialBalanceText.contains("Acme Studio"));
    assertTrue(trialBalanceText.contains("Starter chart"));
    assertTrue(trialBalanceText.contains("EUR"));
    assertTrue(trialBalanceText.contains("All posting kinds"));
    assertTrue(trialBalanceText.contains("Comparative Trial Balance"));
    assertTrue(trialBalanceText.contains("Asset"));
    assertTrue(trialBalanceText.contains("Ordinary"));
    assertTrue(trialBalanceText.contains("Yes"));
    assertTrue(trialBalanceText.contains("Subscription Revenue from"));
    assertTrue(trialBalanceText.contains("Enterprise Customers"));
  }

  @Test
  void renderStatementsHideSyntheticDerivedLineCodesInPdfOutput() throws IOException {
    CurrencyBalance creditBalance = balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT);
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS,
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
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new ChangesInEquityRow(
                    "current-period-result",
                    "Current period result",
                    Optional.of(AccountType.EQUITY),
                    Optional.empty(),
                    Optional.empty(),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    balance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO),
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
        extractedText(PDF_REPORT_SERVICE.renderFinancialPosition(financialPositionReport));
    String changesInEquityText =
        extractedText(PDF_REPORT_SERVICE.renderChangesInEquity(changesInEquityReport));

    assertTrue(financialPositionText.contains("Calculated line"));
    assertFalse(financialPositionText.contains("current-period-result"));
    assertTrue(changesInEquityText.contains("Calculated line"));
    assertFalse(changesInEquityText.contains("current-period-result"));
  }

  @Test
  void renderStatementsIncludeStatementSpecificTablesAndMetadata() throws IOException {
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            true,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        financialPositionRow(
                            "1000",
                            "Cash and Cash Equivalents",
                            AccountType.ASSET,
                            AccountRole.ORDINARY,
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT)))),
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        financialPositionRow(
                            "1000",
                            "Prior Cash and Cash Equivalents",
                            AccountType.ASSET,
                            AccountRole.ORDINARY,
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            balance("EUR", "1000.00", "10.00", "990.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "1000.00", "10.00", "990.00", BalanceSide.DEBIT)))));
    IncomeStatementReport incomeStatementReport =
        new IncomeStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        incomeStatementRow(
                            "4000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            AccountRole.ORDINARY,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT))),
                    List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)))),
            List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)),
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        incomeStatementRow(
                            "4000",
                            "Prior Subscription Revenue",
                            AccountType.REVENUE,
                            AccountRole.ORDINARY,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            balance("EUR", "0.00", "1750.00", "1750.00", BalanceSide.CREDIT))),
                    List.of(balance("EUR", "0.00", "1750.00", "1750.00", BalanceSide.CREDIT)))),
            List.of(balance("EUR", "0.00", "1750.00", "1750.00", BalanceSide.CREDIT)));
    ChangesInEquityReport changesInEquityReport =
        new ChangesInEquityReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                changesInEquityRow(
                    "3000",
                    "Contributed Capital",
                    AccountRole.ORDINARY,
                    FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
                    balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT))),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT)),
            List.of(
                changesInEquityRow(
                    "3000",
                    "Prior Contributed Capital",
                    AccountRole.ORDINARY,
                    FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
                    balance("EUR", "0.00", "800.00", "800.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT))),
            List.of(balance("EUR", "0.00", "800.00", "800.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)));

    byte[] financialPositionPdf =
        PDF_REPORT_SERVICE.renderFinancialPosition(financialPositionReport);
    byte[] incomeStatementPdf = PDF_REPORT_SERVICE.renderIncomeStatement(incomeStatementReport);
    byte[] changesInEquityPdf = PDF_REPORT_SERVICE.renderChangesInEquity(changesInEquityReport);

    assertPdfMetadata(financialPositionPdf, "Financial Position", false);
    assertPdfMetadata(incomeStatementPdf, "Income Statement", false);
    assertPdfMetadata(changesInEquityPdf, "Changes In Equity", false);
    String financialPositionText = extractedText(financialPositionPdf);
    String incomeStatementText = extractedText(incomeStatementPdf);
    String changesInEquityText = extractedText(changesInEquityPdf);
    assertTrue(financialPositionText.contains("Cash and Cash Equivalents"));
    assertTrue(financialPositionText.contains("Financial Position"));
    assertTrue(financialPositionText.contains("Acme Studio"));
    assertTrue(financialPositionText.contains("All posting kinds"));
    assertTrue(financialPositionText.contains("Balanced"));
    assertTrue(financialPositionText.contains("Ordinary"));
    assertTrue(financialPositionText.contains("Comparative Assets"));
    assertTrue(financialPositionText.contains("Prior Cash and Cash"));
    assertTrue(financialPositionText.contains("Equivalents"));
    assertTrue(incomeStatementText.contains("Subscription Revenue"));
    assertTrue(incomeStatementText.contains("Income Statement"));
    assertTrue(incomeStatementText.contains("Non-transfer postings"));
    assertTrue(incomeStatementText.contains("Ordinary"));
    assertTrue(incomeStatementText.contains("Comparative Revenue"));
    assertTrue(incomeStatementText.contains("Comparative Net Income Totals"));
    assertTrue(incomeStatementText.contains("Prior Subscription Revenue"));
    assertTrue(changesInEquityText.contains("Contributed Capital"));
    assertTrue(changesInEquityText.contains("Changes In Equity"));
    assertTrue(changesInEquityText.contains("Acme Studio"));
    assertTrue(changesInEquityText.contains("Ordinary"));
    assertTrue(changesInEquityText.contains("Comparative Changes In Equity"));
    assertTrue(changesInEquityText.contains("Comparative Equity Totals"));
    assertTrue(changesInEquityText.contains("Prior Contributed Capital"));
  }

  @Test
  void renderFinancialPositionSurfacesImbalancedEquationVerdict() throws IOException {
    FinancialPositionReport imbalancedReport =
        new FinancialPositionReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS,
            false,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        financialPositionRow(
                            "1000",
                            "Cash and Cash Equivalents",
                            AccountType.ASSET,
                            AccountRole.ORDINARY,
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT)))),
            List.of());

    String financialPositionText =
        extractedText(PDF_REPORT_SERVICE.renderFinancialPosition(imbalancedReport));

    assertTrue(financialPositionText.contains("Accounting equation"));
    assertTrue(financialPositionText.contains("Imbalanced"));
  }

  @Test
  void renderComparativeBranchesWhenComparativesAreOmittedOrPartiallyPresent() throws IOException {
    TrialBalanceReport trialBalanceWithoutComparatives =
        trialBalanceReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new TrialBalanceRow(
                    CASH_ACCOUNT,
                    balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
            List.of());
    IncomeStatementReport incomeStatementWithoutComparativeTotals =
        new IncomeStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        incomeStatementRow(
                            "4000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            AccountRole.ORDINARY,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT))),
                    List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)))),
            List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)),
            List.of(),
            List.of());

    String trialBalanceText =
        extractedText(PDF_REPORT_SERVICE.renderTrialBalance(trialBalanceWithoutComparatives));
    String incomeStatementText =
        extractedText(
            PDF_REPORT_SERVICE.renderIncomeStatement(incomeStatementWithoutComparativeTotals));

    assertFalse(trialBalanceText.contains("Comparative Trial Balance"));
    assertFalse(incomeStatementText.contains("Comparative Net Income Totals"));

    ChangesInEquityReport noComparativeEquity =
        new ChangesInEquityReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                changesInEquityRow(
                    "3000",
                    "Contributed Capital",
                    AccountRole.ORDINARY,
                    FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
                    balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT))),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT)),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ChangesInEquityReport movementOnlyComparativeEquity =
        new ChangesInEquityReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            noComparativeEquity.rows(),
            noComparativeEquity.openingTotals(),
            noComparativeEquity.movementTotals(),
            noComparativeEquity.closingTotals(),
            List.of(),
            List.of(),
            List.of(balance("EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT)),
            List.of());
    ChangesInEquityReport closingOnlyComparativeEquity =
        new ChangesInEquityReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            noComparativeEquity.rows(),
            noComparativeEquity.openingTotals(),
            noComparativeEquity.movementTotals(),
            noComparativeEquity.closingTotals(),
            List.of(),
            List.of(),
            List.of(),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)));

    String noComparativeEquityText =
        extractedText(PDF_REPORT_SERVICE.renderChangesInEquity(noComparativeEquity));
    String movementOnlyComparativeEquityText =
        extractedText(PDF_REPORT_SERVICE.renderChangesInEquity(movementOnlyComparativeEquity));
    String closingOnlyComparativeEquityText =
        extractedText(PDF_REPORT_SERVICE.renderChangesInEquity(closingOnlyComparativeEquity));

    assertFalse(noComparativeEquityText.contains("Comparative Changes In Equity"));
    assertFalse(noComparativeEquityText.contains("Comparative Equity Totals"));
    assertTrue(movementOnlyComparativeEquityText.contains("Comparative Equity Totals"));
    assertTrue(closingOnlyComparativeEquityText.contains("Comparative Equity Totals"));
  }

  @Test
  void renderStatementsSkipEmptySectionsAndAllowRowsWithoutTotals() throws IOException {
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            true,
            List.of(
                new FinancialPositionSection(AccountType.LIABILITY, List.of(), List.of()),
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        financialPositionRow(
                            "1000",
                            "Cash without Totals",
                            AccountType.ASSET,
                            AccountRole.ORDINARY,
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
                    List.of()),
                new FinancialPositionSection(
                    AccountType.EQUITY,
                    List.of(),
                    List.of(balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT)))),
            List.of(new FinancialPositionSection(AccountType.EQUITY, List.of(), List.of())));
    IncomeStatementReport incomeStatementReport =
        new IncomeStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of()),
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        incomeStatementRow(
                            "4000",
                            "Revenue without Totals",
                            AccountType.REVENUE,
                            AccountRole.ORDINARY,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT))),
                    List.of()),
                new IncomeStatementSection(
                    AccountType.EXPENSE,
                    List.of(),
                    List.of(balance("EUR", "900.00", "0.00", "900.00", BalanceSide.DEBIT)))),
            List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)),
            List.of(new IncomeStatementSection(AccountType.EXPENSE, List.of(), List.of())),
            List.of());

    String financialPositionText =
        extractedText(PDF_REPORT_SERVICE.renderFinancialPosition(financialPositionReport));
    String incomeStatementText =
        extractedText(PDF_REPORT_SERVICE.renderIncomeStatement(incomeStatementReport));

    assertTrue(financialPositionText.contains("Cash without Totals"));
    assertFalse(financialPositionText.contains("Liabilities"));
    assertFalse(financialPositionText.contains("Assets Totals"));
    assertTrue(financialPositionText.contains("Equity Totals"));
    assertFalse(financialPositionText.contains("Comparative Financial Position"));

    assertTrue(incomeStatementText.contains("Revenue without Totals"));
    assertTrue(incomeStatementText.contains("Expenses Totals"));
    assertFalse(incomeStatementText.contains("Revenue Totals"));
    assertFalse(incomeStatementText.contains("Comparative Income Statement"));
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void constructorAndRenderMethodsRejectNullInputs() {
    assertThrows(NullPointerException.class, () -> new PdfReportService(null, "0.50.0", CLOCK));
    assertThrows(NullPointerException.class, () -> new PdfReportService("FinGrind", null, CLOCK));
    assertThrows(
        NullPointerException.class, () -> new PdfReportService("FinGrind", "0.50.0", null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderAccountBalance(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderTrialBalance(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderAccountLedger(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderPeriodSummary(null));
    assertThrows(
        NullPointerException.class, () -> PDF_REPORT_SERVICE.renderFinancialPosition(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderIncomeStatement(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderChangesInEquity(null));
  }
}
