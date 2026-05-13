package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Tests for {@link PdfReportService}. */
class PdfReportServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-04-19T10:15:30Z"), ZoneOffset.UTC);
  private static final PdfReportService PDF_REPORT_SERVICE =
      new PdfReportService("FinGrind", "0.35.0", CLOCK);
  private static final DeclaredAccount CASH_ACCOUNT =
      declaredAccount("1000", "Cash on Hand and Bank Balances", NormalBalance.DEBIT, true);
  private static final DeclaredAccount REVENUE_ACCOUNT =
      declaredAccount(
          "2000", "Subscription Revenue from Enterprise Customers", NormalBalance.CREDIT, true);

  @Test
  void renderAccountBalanceAndTrialBalanceIncludeMetadataAndExpectedText() throws IOException {
    byte[] accountBalancePdf =
        PDF_REPORT_SERVICE.renderAccountBalance(
            new AccountBalanceSnapshot(
                CASH_ACCOUNT,
                Optional.of(LocalDate.parse("2026-04-01")),
                Optional.of(LocalDate.parse("2026-04-30")),
                List.of(
                    balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT),
                    balance("USD", "500.00", "100.00", "400.00", BalanceSide.DEBIT))));
    byte[] trialBalancePdf =
        PDF_REPORT_SERVICE.renderTrialBalance(
            new TrialBalanceReport(
                Optional.of(LocalDate.parse("2026-04-30")),
                List.of(
                    new TrialBalanceRow(
                        CASH_ACCOUNT,
                        balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT)),
                    new TrialBalanceRow(
                        REVENUE_ACCOUNT,
                        balance("EUR", "10.00", "1250.00", "1240.00", BalanceSide.CREDIT)))));
    assertPdfMetadata(accountBalancePdf, "Account Balance", true);
    assertPdfMetadata(trialBalancePdf, "Trial Balance", false);
    assertTrue(extractedText(accountBalancePdf).contains("Cash on Hand and Bank Balances"));
    assertTrue(extractedText(accountBalancePdf).contains("Per-Currency Balances"));
    assertTrue(extractedText(trialBalancePdf).contains("Trial Balance"));
    String trialBalanceText = extractedText(trialBalancePdf);
    assertTrue(trialBalanceText.contains("Subscription Revenue from"));
    assertTrue(trialBalanceText.contains("Enterprise Customers"));
  }

  @Test
  void renderAccountLedgerAndPeriodSummaryPaginateLongTables() throws IOException {
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            List.of(balance("EUR", "0.00", "0.00", "0.00", BalanceSide.ZERO)),
            ledgerEntries(72),
            List.of(balance("EUR", "3600.00", "0.00", "3600.00", BalanceSide.DEBIT)));
    PeriodSummaryReport periodSummaryReport =
        new PeriodSummaryReport(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            72,
            144,
            72,
            List.of(
                new PeriodCurrencySummary(
                    balance("EUR", "3600.00", "3600.00", "0.00", BalanceSide.ZERO))),
            accountActivityRows(72));
    byte[] accountLedgerPdf = PDF_REPORT_SERVICE.renderAccountLedger(accountLedgerReport);
    byte[] periodSummaryPdf = PDF_REPORT_SERVICE.renderPeriodSummary(periodSummaryReport);
    assertPdfPageCountAtLeast(accountLedgerPdf, 2);
    assertPdfPageCountAtLeast(periodSummaryPdf, 2);
    assertTrue(extractedText(accountLedgerPdf).contains("Ledger Entries"));
    assertTrue(extractedText(accountLedgerPdf).contains("Closing Balances"));
    assertTrue(extractedText(periodSummaryPdf).contains("Account Activity"));
    assertTrue(extractedText(periodSummaryPdf).contains("Accounts touched"));
  }

  @Test
  void renderStatementsIncludeStatementSpecificTablesAndMetadata() throws IOException {
    FinancialPositionReport financialPositionReport =
        new FinancialPositionReport(
            Optional.of(LocalDate.parse("2026-04-30")),
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        new FinancialPositionRow(
                            "1000",
                            "Cash and Cash Equivalents",
                            AccountType.ASSET,
                            false,
                            balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
                    List.of(balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT)))));
    IncomeStatementReport incomeStatementReport =
        new IncomeStatementReport(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        new IncomeStatementRow(
                            "4000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            false,
                            balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT))),
                    List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)))),
            List.of(balance("EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)));
    ChangesInEquityReport changesInEquityReport =
        new ChangesInEquityReport(
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            List.of(
                new ChangesInEquityRow(
                    "3000",
                    "Owner Capital",
                    false,
                    balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT),
                    balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT))),
            List.of(balance("EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT)),
            List.of(balance("EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT)));

    byte[] financialPositionPdf =
        PDF_REPORT_SERVICE.renderFinancialPosition(financialPositionReport);
    byte[] incomeStatementPdf = PDF_REPORT_SERVICE.renderIncomeStatement(incomeStatementReport);
    byte[] changesInEquityPdf = PDF_REPORT_SERVICE.renderChangesInEquity(changesInEquityReport);

    assertPdfMetadata(financialPositionPdf, "Financial Position", false);
    assertPdfMetadata(incomeStatementPdf, "Income Statement", false);
    assertPdfMetadata(changesInEquityPdf, "Changes In Equity", false);
    assertTrue(extractedText(financialPositionPdf).contains("Cash and Cash Equivalents"));
    assertTrue(extractedText(financialPositionPdf).contains("Financial Position"));
    assertTrue(extractedText(incomeStatementPdf).contains("Subscription Revenue"));
    assertTrue(extractedText(incomeStatementPdf).contains("Income Statement"));
    assertTrue(extractedText(changesInEquityPdf).contains("Owner Capital"));
    assertTrue(extractedText(changesInEquityPdf).contains("Changes In Equity"));
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void constructorAndRenderMethodsRejectNullInputs() {
    assertThrows(NullPointerException.class, () -> new PdfReportService(null, "0.35.0", CLOCK));
    assertThrows(NullPointerException.class, () -> new PdfReportService("FinGrind", null, CLOCK));
    assertThrows(
        NullPointerException.class, () -> new PdfReportService("FinGrind", "0.35.0", null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderAccountBalance(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderTrialBalance(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderAccountLedger(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderPeriodSummary(null));
    assertThrows(
        NullPointerException.class, () -> PDF_REPORT_SERVICE.renderFinancialPosition(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderIncomeStatement(null));
    assertThrows(NullPointerException.class, () -> PDF_REPORT_SERVICE.renderChangesInEquity(null));
  }

  private static void assertPdfMetadata(byte[] pdfBytes, String title, boolean portrait)
      throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      PDDocumentInformation information = document.getDocumentInformation();
      PDRectangle mediaBox = document.getPage(0).getMediaBox();
      assertEquals(title, information.getTitle());
      assertEquals("FinGrind 0.35.0", information.getCreator());
      assertEquals(title, information.getSubject());
      assertEquals(portrait, mediaBox.getHeight() > mediaBox.getWidth());
    }
  }

  private static void assertPdfPageCountAtLeast(byte[] pdfBytes, int minimumPages)
      throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      assertTrue(document.getNumberOfPages() >= minimumPages);
    }
  }

  private static String extractedText(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      return new PDFTextStripper().getText(document);
    }
  }

  private static List<AccountLedgerEntry> ledgerEntries(int count) {
    List<AccountLedgerEntry> entries = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      String amount = Integer.toString((index + 1) * 50);
      entries.add(
          new AccountLedgerEntry(
              postingFact(index, amount),
              balance("EUR", amount, "0.00", amount, BalanceSide.DEBIT),
              money("EUR", Integer.toString((index + 1) * 50)),
              BalanceSide.DEBIT));
    }
    return entries;
  }

  private static List<PeriodAccountActivityRow> accountActivityRows(int count) {
    List<PeriodAccountActivityRow> rows = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      rows.add(
          new PeriodAccountActivityRow(
              declaredAccount(
                  "%04d".formatted(3000 + index),
                  "Operating Expense Category " + index + " with a deliberately long display name",
                  NormalBalance.DEBIT,
                  true),
              balance(
                  "EUR",
                  Integer.toString(index + 1),
                  "0.00",
                  Integer.toString(index + 1),
                  BalanceSide.DEBIT)));
    }
    return rows;
  }

  private static PostingFact postingFact(int index, String amount) {
    LocalDate effectiveDate = LocalDate.parse("2026-04-01").plusDays(index % 28);
    PostingId postingId = new PostingId("posting-%03d".formatted(index));
    return new PostingFact(
        postingId,
        new JournalEntry(
            effectiveDate,
            List.of(
                new JournalLine(
                    CASH_ACCOUNT.accountCode(), JournalLine.EntrySide.DEBIT, money("EUR", amount)),
                new JournalLine(
                    REVENUE_ACCOUNT.accountCode(),
                    JournalLine.EntrySide.CREDIT,
                    money("EUR", amount)))),
        index % 5 == 0
            ? PostingLineage.reversal(
                new ReversalReference(new PostingId("prior-%03d".formatted(index))),
                new ReversalReason("Automated reversal %03d".formatted(index)))
            : PostingLineage.direct(),
        PostingKind.STANDARD,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("office-worker"),
                ActorType.HUMAN,
                new CommandId("command-%03d".formatted(index)),
                new IdempotencyKey("idem-%03d".formatted(index)),
                new CausationId("cause-%03d".formatted(index)),
                Optional.of(new CorrelationId("corr-%03d".formatted(index)))),
            Instant.parse("2026-04-19T10:15:30Z").plusSeconds(index),
            SourceChannel.CLI));
  }

  private static DeclaredAccount declaredAccount(
      String code, String name, NormalBalance normalBalance, boolean active) {
    return new DeclaredAccount(
        new AccountCode(code),
        new AccountName(name),
        normalBalance == NormalBalance.DEBIT ? AccountType.ASSET : AccountType.REVENUE,
        AccountRole.ORDINARY,
        active,
        Instant.parse("2026-04-01T08:00:00Z"));
  }

  private static CurrencyBalance balance(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(money(currencyCode, debitTotal), money(currencyCode, creditTotal));
    if (!balance.netAmount().equals(money(currencyCode, netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }

  private static Money money(String currencyCode, String amount) {
    return Money.parse(currencyCode, amount);
  }
}
