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
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.render;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.trialBalanceReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CashFlowSectionKind;
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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

/** Tests for {@link PdfReportService}. */
class PdfReportServiceTest {
  @Test
  void renderCreatesTaggedEnglishPdfWithOneStructureTree() throws IOException {
    byte[] pdf = PDF_REPORT_SERVICE.render(PdfReportLayoutFixtureModels.sampleTrialBalanceModel());

    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertTrue(document.getDocumentCatalog().getMarkInfo().isMarked());
      assertEquals("en", document.getDocumentCatalog().getLanguage());
      assertFalse(document.getDocumentCatalog().getStructureTreeRoot().getKids().isEmpty());
      assertTrue(document.getPage(0).getStructParents() >= 0);
    }
  }

  @Test
  void renderAccountBalanceAndTrialBalanceIncludeMetadataAndExpectedText() throws IOException {
    byte[] accountBalancePdf =
        render(
            PDF_REPORT_SERVICE,
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
        render(
            PDF_REPORT_SERVICE,
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
    assertTrue(extractedText(accountBalancePdf).contains("Book start effective date"));
    assertTrue(extractedText(accountBalancePdf).contains("Per-Currency Balances"));
    assertTrue(extractedText(trialBalancePdf).contains("Trial Balance"));
    String trialBalanceText = extractedText(trialBalancePdf);
    assertTrue(trialBalanceText.contains("Acme Studio"));
    assertTrue(trialBalanceText.contains("Owner-managed service"));
    assertTrue(trialBalanceText.contains("EUR"));
    assertTrue(trialBalanceText.contains("All posting kinds"));
    assertTrue(trialBalanceText.contains("Comparative Trial Balance"));
    assertTrue(trialBalanceText.contains("Debit"));
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
        extractedText(render(PDF_REPORT_SERVICE, financialPositionReport));
    String changesInEquityText = extractedText(render(PDF_REPORT_SERVICE, changesInEquityReport));

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
                    FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
                    balance("EUR", "0.00", "800.00", "800.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT))),
            List.of(balance("EUR", "0.00", "800.00", "800.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)));

    byte[] financialPositionPdf = render(PDF_REPORT_SERVICE, financialPositionReport);
    byte[] incomeStatementPdf = render(PDF_REPORT_SERVICE, incomeStatementReport);
    byte[] changesInEquityPdf = render(PDF_REPORT_SERVICE, changesInEquityReport);

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
    assertTrue(financialPositionText.contains("Current asset"));
    assertTrue(financialPositionText.contains("Account"));
    assertTrue(financialPositionText.contains("Comparative Assets"));
    assertTrue(financialPositionText.contains("Prior Cash and Cash"));
    assertTrue(financialPositionText.contains("Equivalents"));
    assertTrue(incomeStatementText.contains("Subscription Revenue"));
    assertTrue(incomeStatementText.contains("Income Statement"));
    assertTrue(incomeStatementText.contains("Non-close postings"));
    assertTrue(incomeStatementText.contains("Operating revenue"));
    assertTrue(incomeStatementText.contains("Account"));
    assertTrue(incomeStatementText.contains("Comparative Revenue"));
    assertTrue(incomeStatementText.contains("Comparative Net Income Totals"));
    assertTrue(incomeStatementText.contains("Prior Subscription Revenue"));
    assertTrue(changesInEquityText.contains("Contributed Capital"));
    assertTrue(changesInEquityText.contains("Changes In Equity"));
    assertTrue(changesInEquityText.contains("Acme Studio"));
    assertTrue(changesInEquityText.contains("Contributed capital"));
    assertTrue(changesInEquityText.contains("Account"));
    assertTrue(changesInEquityText.contains("Comparative Changes In Equity"));
    assertTrue(changesInEquityText.contains("Comparative Equity Totals"));
    assertTrue(changesInEquityText.contains("Prior Contributed Capital"));
  }

  @Test
  void renderCashFlowStatementIncludesSectionTablesAndMetadata() throws IOException {
    CashFlowStatementReport report =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
            List.of(
                new CashFlowSection(
                    CashFlowSectionKind.OPERATING,
                    List.of(
                        new CashFlowRow(
                            "2000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            Optional.empty(),
                            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                            StatementLineKind.DECLARED_ACCOUNT,
                            balance("EUR", "25.00", "0.00", "25.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "25.00", "0.00", "25.00", BalanceSide.DEBIT))),
                new CashFlowSection(CashFlowSectionKind.INVESTING, List.of(), List.of()),
                new CashFlowSection(
                    CashFlowSectionKind.FINANCING,
                    List.of(
                        new CashFlowRow(
                            "3000",
                            "Contributed Capital",
                            AccountType.EQUITY,
                            Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                            Optional.empty(),
                            StatementLineKind.DECLARED_ACCOUNT,
                            balance("EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT)))),
            List.of(balance("EUR", "30.00", "0.00", "30.00", BalanceSide.DEBIT)),
            List.of(balance("EUR", "40.00", "0.00", "40.00", BalanceSide.DEBIT)),
            List.of(balance("EUR", "8.00", "0.00", "8.00", BalanceSide.DEBIT)),
            List.of(
                new CashFlowSection(
                    CashFlowSectionKind.OPERATING,
                    List.of(
                        new CashFlowRow(
                            "2000",
                            "Prior Subscription Revenue",
                            AccountType.REVENUE,
                            Optional.empty(),
                            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                            StatementLineKind.DECLARED_ACCOUNT,
                            balance("EUR", "20.00", "0.00", "20.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "20.00", "0.00", "20.00", BalanceSide.DEBIT))),
                new CashFlowSection(CashFlowSectionKind.INVESTING, List.of(), List.of()),
                new CashFlowSection(CashFlowSectionKind.FINANCING, List.of(), List.of())),
            List.of(balance("EUR", "20.00", "0.00", "20.00", BalanceSide.DEBIT)),
            List.of(balance("EUR", "28.00", "0.00", "28.00", BalanceSide.DEBIT)));

    byte[] pdf = render(PDF_REPORT_SERVICE, report);

    assertPdfMetadata(pdf, "Cash Receipts And Payments", false);
    String text = extractedText(pdf);
    assertTrue(text.contains("Cash Receipts And Payments"));
    assertTrue(text.contains("Comparative Cash Receipts And Payments"));
    assertTrue(text.contains("Operating"));
    assertTrue(text.contains("Financing"));
    assertTrue(text.contains("Comparative Operating"));
    assertTrue(text.contains("Opening Cash Totals"));
    assertTrue(text.contains("Closing Cash Totals"));
    assertTrue(text.contains("Non-close postings"));
  }

  @Test
  void renderCashFlowStatementHandlesTotalsOnlyRowsOnlyAndMissingComparatives() throws IOException {
    CashFlowStatementReport report =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
            List.of(
                new CashFlowSection(
                    CashFlowSectionKind.OPERATING,
                    List.of(),
                    List.of(balance("EUR", "7.00", "0.00", "7.00", BalanceSide.DEBIT))),
                new CashFlowSection(
                    CashFlowSectionKind.FINANCING,
                    List.of(
                        new CashFlowRow(
                            "3000",
                            "Contributed Capital",
                            AccountType.EQUITY,
                            Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                            Optional.empty(),
                            StatementLineKind.DECLARED_ACCOUNT,
                            balance("EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT))),
                    List.of())),
            List.of(balance("EUR", "12.00", "0.00", "12.00", BalanceSide.DEBIT)),
            List.of(balance("EUR", "22.00", "0.00", "22.00", BalanceSide.DEBIT)),
            List.of(),
            List.of(
                new CashFlowSection(CashFlowSectionKind.OPERATING, List.of(), List.of()),
                new CashFlowSection(
                    CashFlowSectionKind.FINANCING,
                    List.of(
                        new CashFlowRow(
                            "3000",
                            "Prior Contributed Capital",
                            AccountType.EQUITY,
                            Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                            Optional.empty(),
                            StatementLineKind.DECLARED_ACCOUNT,
                            balance("EUR", "4.00", "0.00", "4.00", BalanceSide.DEBIT))),
                    List.of())),
            List.of(),
            List.of());

    byte[] pdf = render(PDF_REPORT_SERVICE, report);

    assertPdfMetadata(pdf, "Cash Receipts And Payments", false);
    String text = extractedText(pdf);
    assertTrue(text.contains("Comparative Cash Receipts And Payments"));
    assertTrue(text.contains("Operating Totals"));
    assertTrue(text.contains("Financing"));
    assertTrue(text.contains("Prior Contributed Capital"));
    assertFalse(text.contains("Comparative Opening Cash Totals"));
    assertFalse(text.contains("Comparative Movement Totals"));
    assertFalse(text.contains("Comparative Closing Cash Totals"));
  }

  @Test
  void renderCashFlowStatementSurfacesRequestedEmptyComparativeScope() throws IOException {
    CashFlowStatementReport report =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
            List.of(
                new CashFlowSection(
                    CashFlowSectionKind.OPERATING,
                    List.of(
                        new CashFlowRow(
                            "2000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            Optional.empty(),
                            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                            StatementLineKind.DECLARED_ACCOUNT,
                            balance("EUR", "8.00", "0.00", "8.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "8.00", "0.00", "8.00", BalanceSide.DEBIT))),
                new CashFlowSection(CashFlowSectionKind.INVESTING, List.of(), List.of()),
                new CashFlowSection(CashFlowSectionKind.FINANCING, List.of(), List.of())),
            List.of(balance("EUR", "8.00", "0.00", "8.00", BalanceSide.DEBIT)),
            List.of(balance("EUR", "18.00", "0.00", "18.00", BalanceSide.DEBIT)),
            List.of(),
            List.of(
                new CashFlowSection(CashFlowSectionKind.OPERATING, List.of(), List.of()),
                new CashFlowSection(CashFlowSectionKind.INVESTING, List.of(), List.of()),
                new CashFlowSection(CashFlowSectionKind.FINANCING, List.of(), List.of())),
            List.of(),
            List.of());

    String text = extractedText(render(PDF_REPORT_SERVICE, report));

    assertTrue(text.contains("Comparative Cash Receipts And Payments"));
    assertTrue(text.contains("Comparative reference"));
    assertTrue(text.contains("2025-04-01 to 2025-04-30"));
    assertTrue(text.contains("No cash-flow lines matched the selected scope."));
    assertTrue(text.contains("Empty sections"));
    assertTrue(text.contains("Operating, Investing, Financing"));
  }

  @Test
  void renderCashFlowStatementCoversComparativePresenceBranches() throws IOException {
    CashFlowStatementReport noComparativeRequested =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
            List.of(
                new CashFlowSection(
                    CashFlowSectionKind.OPERATING,
                    List.of(
                        new CashFlowRow(
                            "2000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            Optional.empty(),
                            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                            StatementLineKind.DECLARED_ACCOUNT,
                            balance("EUR", "8.00", "0.00", "8.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "8.00", "0.00", "8.00", BalanceSide.DEBIT)))),
            List.of(balance("EUR", "8.00", "0.00", "8.00", BalanceSide.DEBIT)),
            List.of(balance("EUR", "18.00", "0.00", "18.00", BalanceSide.DEBIT)),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    CashFlowStatementReport lowerBoundOnlyComparative =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.from(LocalDate.parse("2025-04-01")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            noComparativeRequested.openingCashTotals(),
            noComparativeRequested.sections(),
            noComparativeRequested.movementTotals(),
            noComparativeRequested.closingCashTotals(),
            List.of(),
            List.of(
                new CashFlowSection(CashFlowSectionKind.OPERATING, List.of(), List.of()),
                new CashFlowSection(CashFlowSectionKind.INVESTING, List.of(), List.of())),
            List.of(),
            List.of());
    CashFlowStatementReport upperBoundOnlyComparative =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.to(LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            noComparativeRequested.openingCashTotals(),
            noComparativeRequested.sections(),
            noComparativeRequested.movementTotals(),
            noComparativeRequested.closingCashTotals(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    CashFlowStatementReport openingOnlyComparative =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            noComparativeRequested.openingCashTotals(),
            noComparativeRequested.sections(),
            noComparativeRequested.movementTotals(),
            noComparativeRequested.closingCashTotals(),
            List.of(balance("EUR", "4.00", "0.00", "4.00", BalanceSide.DEBIT)),
            List.of(),
            List.of(),
            List.of());
    CashFlowStatementReport movementOnlyComparative =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            noComparativeRequested.openingCashTotals(),
            noComparativeRequested.sections(),
            noComparativeRequested.movementTotals(),
            noComparativeRequested.closingCashTotals(),
            List.of(),
            List.of(),
            List.of(balance("EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT)),
            List.of());
    CashFlowStatementReport closingOnlyComparative =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            noComparativeRequested.openingCashTotals(),
            noComparativeRequested.sections(),
            noComparativeRequested.movementTotals(),
            noComparativeRequested.closingCashTotals(),
            List.of(),
            List.of(),
            List.of(),
            List.of(balance("EUR", "6.00", "0.00", "6.00", BalanceSide.DEBIT)));
    CashFlowStatementReport totalsOnlySectionComparative =
        new CashFlowStatementReport(
            BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            noComparativeRequested.openingCashTotals(),
            noComparativeRequested.sections(),
            noComparativeRequested.movementTotals(),
            noComparativeRequested.closingCashTotals(),
            List.of(),
            List.of(
                new CashFlowSection(
                    CashFlowSectionKind.FINANCING,
                    List.of(),
                    List.of(balance("EUR", "7.00", "0.00", "7.00", BalanceSide.DEBIT)))),
            List.of(),
            List.of());

    String noComparativeText = extractedText(render(PDF_REPORT_SERVICE, noComparativeRequested));
    String lowerBoundComparativeText =
        extractedText(render(PDF_REPORT_SERVICE, lowerBoundOnlyComparative));
    String upperBoundComparativeText =
        extractedText(render(PDF_REPORT_SERVICE, upperBoundOnlyComparative));
    String openingOnlyComparativeText =
        extractedText(render(PDF_REPORT_SERVICE, openingOnlyComparative));
    String movementOnlyComparativeText =
        extractedText(render(PDF_REPORT_SERVICE, movementOnlyComparative));
    String closingOnlyComparativeText =
        extractedText(render(PDF_REPORT_SERVICE, closingOnlyComparative));
    String totalsOnlySectionComparativeText =
        extractedText(render(PDF_REPORT_SERVICE, totalsOnlySectionComparative));

    assertFalse(noComparativeText.contains("Comparative Cash Receipts And Payments"));

    assertTrue(lowerBoundComparativeText.contains("Comparative Cash Receipts And Payments"));
    assertTrue(lowerBoundComparativeText.contains("2025-04-01 to current book horizon"));
    assertTrue(lowerBoundComparativeText.contains("Empty sections"));

    assertTrue(upperBoundComparativeText.contains("Comparative Cash Receipts And Payments"));
    assertTrue(upperBoundComparativeText.contains("book start to 2025-04-30"));
    assertFalse(upperBoundComparativeText.contains("Empty sections"));

    assertTrue(openingOnlyComparativeText.contains("Comparative Opening Cash Totals"));
    assertTrue(movementOnlyComparativeText.contains("Comparative Movement Totals"));
    assertTrue(closingOnlyComparativeText.contains("Comparative Closing Cash Totals"));
    assertTrue(totalsOnlySectionComparativeText.contains("Comparative Financing Totals"));
  }

  @Test
  void renderFinancialPositionSurfacesImbalancedEquationVerdict() throws IOException {
    FinancialPositionReport imbalancedReport =
        new FinancialPositionReport(
            BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
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
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT)))),
            List.of());

    String financialPositionText = extractedText(render(PDF_REPORT_SERVICE, imbalancedReport));

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
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT))),
                    List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)))),
            List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)),
            List.of(),
            List.of());

    String trialBalanceText =
        extractedText(render(PDF_REPORT_SERVICE, trialBalanceWithoutComparatives));
    String incomeStatementText =
        extractedText(render(PDF_REPORT_SERVICE, incomeStatementWithoutComparativeTotals));

    assertFalse(trialBalanceText.contains("Comparative Trial Balance"));
    assertTrue(incomeStatementText.contains("Comparative Income Statement"));
    assertTrue(incomeStatementText.contains("Comparative Net Income Totals"));

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

    String noComparativeEquityText = extractedText(render(PDF_REPORT_SERVICE, noComparativeEquity));
    String movementOnlyComparativeEquityText =
        extractedText(render(PDF_REPORT_SERVICE, movementOnlyComparativeEquity));
    String closingOnlyComparativeEquityText =
        extractedText(render(PDF_REPORT_SERVICE, closingOnlyComparativeEquity));

    assertTrue(noComparativeEquityText.contains("Comparative Changes In Equity"));
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
        extractedText(render(PDF_REPORT_SERVICE, financialPositionReport));
    String incomeStatementText = extractedText(render(PDF_REPORT_SERVICE, incomeStatementReport));

    assertTrue(financialPositionText.contains("Cash without Totals"));
    assertTrue(financialPositionText.contains("Empty sections"));
    assertTrue(financialPositionText.contains("Liabilities"));
    assertFalse(financialPositionText.contains("Assets Totals"));
    assertTrue(financialPositionText.contains("Equity Totals"));
    assertTrue(financialPositionText.contains("Comparative Financial Position"));

    assertTrue(incomeStatementText.contains("Revenue without Totals"));
    assertTrue(incomeStatementText.contains("Expenses Totals"));
    assertFalse(incomeStatementText.contains("Revenue Totals"));
    assertTrue(incomeStatementText.contains("Comparative Income Statement"));
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void constructorAndRenderMethodsRejectNullInputs() {
    assertThrows(NullPointerException.class, () -> new PdfReportService(null, "0.57.0", CLOCK));
    assertThrows(NullPointerException.class, () -> new PdfReportService("FinGrind", null, CLOCK));
    assertThrows(
        NullPointerException.class, () -> new PdfReportService("FinGrind", "0.57.0", null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.render(null));
  }
}
