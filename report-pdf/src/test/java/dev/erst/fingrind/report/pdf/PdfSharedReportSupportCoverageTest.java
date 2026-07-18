package dev.erst.fingrind.report.pdf;

import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.BOOK_IDENTITY;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.accountActivityRows;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.balance;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.changesInEquityRow;
import static dev.erst.fingrind.report.pdf.PdfReportFixtureSupport.extractedText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.reportmodel.ReportColumn;
import dev.erst.fingrind.contract.reportmodel.ReportContext;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.ReportRow;
import dev.erst.fingrind.contract.reportmodel.ReportSection;
import dev.erst.fingrind.contract.reportmodel.ReportTotals;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;

/** Direct coverage for shared PDF support primitives behind the generic report projector. */
class PdfSharedReportSupportCoverageTest {
  @Test
  void helpersExposeExpectedLayoutsRowsAndMetadata() {
    List<PdfTableColumn> columns =
        PdfReportTableLayouts.reportColumns(
            List.of(
                new ReportColumn("accountName", "Account name", ReportColumn.Alignment.LEFT),
                new ReportColumn("lineName", "Line name", ReportColumn.Alignment.LEFT),
                new ReportColumn("counterparts", "Counterparts", ReportColumn.Alignment.LEFT),
                new ReportColumn("classification", "Classification", ReportColumn.Alignment.LEFT),
                new ReportColumn("lineKind", "Line kind", ReportColumn.Alignment.LEFT),
                new ReportColumn("accountType", "Type", ReportColumn.Alignment.LEFT),
                new ReportColumn("normalBalance", "Normal", ReportColumn.Alignment.LEFT),
                new ReportColumn("active", "Active", ReportColumn.Alignment.LEFT),
                new ReportColumn("netAmount", "Net", ReportColumn.Alignment.RIGHT),
                new ReportColumn("entry", "Entry", ReportColumn.Alignment.LEFT)));
    assertEquals(2.6f, columns.get(0).widthWeight());
    assertEquals(1.9f, columns.get(1).widthWeight());
    assertEquals(1.9f, columns.get(2).widthWeight());
    assertEquals(1.5f, columns.get(3).widthWeight());
    assertEquals(0.85f, columns.get(4).widthWeight());
    assertEquals(0.85f, columns.get(5).widthWeight());
    assertEquals(0.85f, columns.get(6).widthWeight());
    assertEquals(0.85f, columns.get(7).widthWeight());
    assertEquals(PdfTableColumn.CellAlignment.RIGHT, columns.get(8).alignment());
    assertEquals(1.2f, columns.get(9).widthWeight());
    assertEquals(11, PdfReportTableLayouts.accountActivityColumns().size());
    assertEquals(10, PdfReportTableLayouts.statementBalanceColumns().size());
    assertEquals(5, PdfReportTableLayouts.currencyBalanceSummaryColumns().size());
    assertEquals(5, PdfReportTableLayouts.detailedCurrencyBalanceColumns().size());
    assertEquals(10, PdfReportTableLayouts.changesInEquityColumns().size());
    assertEquals(6, PdfReportTableLayouts.equityTotalsColumns().size());

    CurrencyBalance eurBalance = balance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT);
    assertEquals(
        List.of("EUR", "10.00", "4.00", "6.00", "Debit"),
        PdfBalanceTableSupport.summaryRow(eurBalance));
    assertEquals(
        List.of("EUR", "10.00", "4.00", "6.00", "Debit"),
        PdfBalanceTableSupport.detailedRow(eurBalance));

    assertEquals(
        "Calculated line",
        PdfStatementRowRenderers.financialPositionRow(
                new FinancialPositionRow(
                    "current-period-result",
                    "Current period result",
                    AccountType.EQUITY,
                    Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT)))
            .getFirst());
    assertEquals(
        "Calculated line",
        PdfStatementRowRenderers.incomeStatementRow(
                new IncomeStatementRow(
                    "current-period-result",
                    "Current period result",
                    AccountType.REVENUE,
                    ProfitAndLossLineClassification.OPERATING_REVENUE,
                    StatementLineKind.CURRENT_PERIOD_RESULT,
                    balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT)))
            .getFirst());
    assertEquals(
        "Current asset",
        PdfStatementRowRenderers.cashFlowRow(
                new CashFlowRow(
                    "1000",
                    "Cash",
                    AccountType.ASSET,
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.empty(),
                    StatementLineKind.DECLARED_ACCOUNT,
                    eurBalance))
            .get(2));
    assertEquals(
        "Operating revenue",
        PdfStatementRowRenderers.cashFlowRow(
                new CashFlowRow(
                    "4000",
                    "Revenue",
                    AccountType.REVENUE,
                    Optional.empty(),
                    Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                    StatementLineKind.DECLARED_ACCOUNT,
                    eurBalance))
            .get(2));

    List<List<String>> reportParameters =
        PdfStatementMetadataRows.reportParameters(
            BOOK_IDENTITY,
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(List.of("As of", "2026-04-30")));
    List<List<String>> statementParameters =
        PdfStatementMetadataRows.statementParameters(
            BOOK_IDENTITY,
            dev.erst.fingrind.core.EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(List.of("Period start", "2026-04-01")));
    assertEquals("Entity", reportParameters.getFirst().getFirst());
    assertEquals("As of", reportParameters.getLast().getFirst());
    assertEquals("Posting coverage", statementParameters.get(5).getFirst());
    assertEquals("Period start", statementParameters.getLast().getFirst());
  }

  @Test
  void writersRenderBalanceActivityAndEquitySupportTables() throws IOException {
    List<TrialBalanceRow> trialBalanceRows =
        List.of(
            new TrialBalanceRow(
                CASH_ACCOUNT, balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)));
    List<PeriodAccountActivityRow> periodRows = accountActivityRows(1);
    List<ChangesInEquityRow> equityRows =
        List.of(
            changesInEquityRow(
                "3000",
                "Contributed Capital",
                FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
                balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT),
                balance("EUR", "0.00", "5.00", "5.00", BalanceSide.CREDIT),
                balance("EUR", "0.00", "15.00", "15.00", BalanceSide.CREDIT)));

    byte[] pdfBytes;
    try (PdfDocumentFactory.DocumentSession session =
        new PdfDocumentFactory("FinGrind", "0.61.0")
            .create(
                "Shared Support Coverage",
                Instant.parse("2026-07-01T12:00:00Z"),
                PageOrientation.LANDSCAPE)) {
      PdfBalanceTableSupport.writeSummaryTable(
          session.pageWriter(),
          "Currency Summary",
          List.of(balance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT)));
      PdfBalanceTableSupport.writeDetailedTable(
          session.pageWriter(),
          "Currency Details",
          List.of(balance("EUR", "10.00", "4.00", "6.00", BalanceSide.DEBIT)));
      PdfAccountActivityTableSupport.writeTrialBalanceTable(
          session.pageWriter(), "Accounts", trialBalanceRows);
      PdfAccountActivityTableSupport.writePeriodAccountActivityTable(
          session.pageWriter(), "Account Activity", periodRows);
      PdfChangesInEquityTableSupport.writeChangesTable(session.pageWriter(), "Changes", equityRows);
      PdfChangesInEquityTableSupport.writeTotalsTable(
          session.pageWriter(),
          "Equity Totals",
          List.of(balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT)),
          List.of(balance("EUR", "0.00", "5.00", "5.00", BalanceSide.CREDIT)),
          List.of(balance("EUR", "0.00", "15.00", "15.00", BalanceSide.CREDIT)));
      pdfBytes = session.toByteArray();
    }

    String text = extractedText(pdfBytes);
    assertTrue(text.contains("Currency Summary"), text);
    assertTrue(text.contains("Currency Details"), text);
    assertTrue(text.contains("Accounts"), text);
    assertTrue(text.contains("Account Activity"), text);
    assertTrue(text.contains("Changes"), text);
    assertTrue(text.contains("Equity Totals"), text);
    assertTrue(text.contains("Contributed Capital"), text);
    assertTrue(text.contains("Opening"), text);
    assertTrue(text.contains("Movement"), text);
    assertTrue(text.contains("Closing"), text);
  }

  @Test
  void projectorAndStatementSectionSupportHandleSummarylessEmptyAndTotalsOnlyShapes()
      throws IOException {
    ReportModel reportModel =
        new ReportModel(
            "synthetic",
            "Synthetic Report",
            ReportModel.Orientation.PORTRAIT,
            new ReportContext(
                "Acme Studio",
                "Owner-managed service seed template",
                "Cash basis",
                "EUR",
                "01-01",
                "2026-01-01",
                "All posting kinds",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()),
            List.of(),
            List.of(
                new ReportSection(
                    "rows",
                    "Row Section",
                    List.of(),
                    List.of(new ReportColumn("name", "Name", ReportColumn.Alignment.LEFT)),
                    List.of(new ReportRow("row-1", List.of("Value A"))),
                    List.of()),
                new ReportSection(
                    "totals",
                    "Totals Section",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                        new ReportTotals(
                            "netPosition",
                            "Net position",
                            List.of(
                                new ReportColumn("metric", "Metric", ReportColumn.Alignment.LEFT)),
                            List.of(new ReportRow("metric-1", List.of("Payable")))))),
                new ReportSection(
                    "empty", "Empty Section", List.of(), List.of(), List.of(), List.of())));
    String projectorText =
        extractedText(
            new PdfReportService("FinGrind", "0.61.0", PdfReportFixtureSupport.CLOCK)
                .render(reportModel));
    assertFalse(projectorText.contains("Summary"), projectorText);
    assertTrue(projectorText.contains("Row Section"), projectorText);
    assertTrue(projectorText.contains("Net position"), projectorText);
    assertFalse(projectorText.contains("Empty Section"), projectorText);
    assertTrue(projectorText.contains("Context"), projectorText);

    byte[] sectionBytes;
    try (PdfDocumentFactory.DocumentSession session =
        new PdfDocumentFactory("FinGrind", "0.61.0")
            .create(
                "Statement Sections",
                Instant.parse("2026-07-01T12:00:00Z"),
                PageOrientation.LANDSCAPE)) {
      PdfStatementSectionTableRenderer.renderSections(
          session.pageWriter(),
          List.of(
              new SyntheticSection(AccountType.ASSET, List.of(statementRow()), List.of()),
              new SyntheticSection(
                  AccountType.LIABILITY,
                  List.of(),
                  List.of(balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT))),
              new SyntheticSection(AccountType.EQUITY, List.of(), List.of())),
          "Statement ",
          SyntheticSection::accountType,
          SyntheticSection::rows,
          SyntheticSection::totals,
          row -> row);
      sectionBytes = session.toByteArray();
    }

    String sectionText = extractedText(sectionBytes);
    assertTrue(sectionText.contains("Statement Assets"), sectionText);
    assertTrue(sectionText.contains("Statement Liabilities"), sectionText);
    assertTrue(sectionText.contains("Statement Liabilities Totals"), sectionText);
    assertFalse(sectionText.contains("Statement Equity"), sectionText);
  }

  @Test
  void pageWriterUsesFallbackAscentWhenFontDescriptorIsMissing() throws Throwable {
    float ascent =
        (float)
            MethodHandles.privateLookupIn(PdfPageWriter.class, MethodHandles.lookup())
                .findStatic(
                    PdfPageWriter.class,
                    "fontAscent",
                    MethodType.methodType(float.class, PDFont.class, float.class))
                .invokeExact((PDFont) new DescriptorlessFont(), 10f);
    assertEquals(7.5f, ascent);
  }

  private static List<String> statementRow() {
    return List.of(
        "1000",
        "Cash",
        "Asset",
        "Current asset",
        "Declared account",
        "EUR",
        "EUR 10.00",
        "EUR 0.00",
        "EUR 10.00",
        "Debit");
  }

  private record SyntheticSection(
      AccountType accountType, List<List<String>> rows, List<CurrencyBalance> totals) {}

  /** Minimal synthetic font that forces the descriptor-missing ascent fallback path. */
  // PDFBox keeps this deprecated abstract member on PDFontLike, so every minimal test double must
  // implement it.
  @SuppressWarnings("deprecation")
  private static final class DescriptorlessFont extends PDFont {
    private DescriptorlessFont() {
      super(new COSDictionary());
    }

    @Override
    protected float getStandard14Width(int code) {
      return 500f;
    }

    @Override
    protected byte[] encode(int unicode) {
      return new byte[] {(byte) unicode};
    }

    @Override
    public int readCode(InputStream input) throws IOException {
      return input.read();
    }

    @Override
    public String getName() {
      return "DescriptorlessFont";
    }

    @Override
    public org.apache.fontbox.util.BoundingBox getBoundingBox() {
      return new org.apache.fontbox.util.BoundingBox(0f, 0f, 1000f, 1000f);
    }

    @Override
    public float getHeight(int code) {
      return 1000f;
    }

    @Override
    public boolean hasExplicitWidth(int code) {
      return true;
    }

    @Override
    public float getWidthFromFont(int code) {
      return 500f;
    }

    @Override
    public boolean isEmbedded() {
      return false;
    }

    @Override
    public boolean isVertical() {
      return false;
    }

    @Override
    public boolean isDamaged() {
      return false;
    }

    @Override
    public void addToSubset(int code) {}

    @Override
    public void subset() {}

    @Override
    public boolean willBeSubset() {
      return false;
    }
  }
}
