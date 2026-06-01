package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

/** Focused coverage tests for PDF rendering edge cases and failure paths. */
class PdfReportCoverageTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-04-19T10:15:30Z"), ZoneOffset.UTC);
  private static final Instant GENERATED_AT = Instant.now(CLOCK);

  @Test
  void renderTrialBalanceWrapsFontLoadingIoFailures() {
    PdfReportService service =
        new PdfReportService(
            CLOCK,
            new PdfDocumentFactory(
                "FinGrind",
                "0.50.0",
                resourcePath ->
                    new ByteArrayInputStream(
                        ("not-a-font:" + resourcePath).getBytes(StandardCharsets.UTF_8))));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> service.renderTrialBalance(sampleTrialBalanceReport()));

    assertEquals("Failed to render Trial Balance PDF.", exception.getMessage());
    assertInstanceOf(IOException.class, exception.getCause());
  }

  @Test
  void createRejectsMissingBundledFontResources() {
    PdfDocumentFactory factory = new PdfDocumentFactory("FinGrind", "0.50.0", resourcePath -> null);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> factory.create("Trial Balance", GENERATED_AT, PageOrientation.LANDSCAPE));

    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("Missing bundled font resource"));
  }

  @Test
  void documentSessionAggregatesCloseFailuresAndRemainsIdempotent() throws IOException {
    try (PdfDocumentFactory.DocumentSession backingSession =
            standardFactory().create("Trial Balance", GENERATED_AT, PageOrientation.PORTRAIT);
        PdfDocumentFactory.DocumentSession session =
            new PdfDocumentFactory.DocumentSession(
                backingSession.pageWriter(),
                () -> {
                  throw new IOException("page-writer-close");
                },
                () -> {
                  throw new IOException("document-close");
                },
                outputStream -> outputStream.write(new byte[] {1, 2, 3}))) {

      IOException failure = assertThrows(IOException.class, session::close);

      assertEquals("page-writer-close", failure.getMessage());
      assertEquals(1, failure.getSuppressed().length);
      assertEquals("document-close", failure.getSuppressed()[0].getMessage());
      assertDoesNotThrow(session::close);
    }
  }

  @Test
  void documentSessionPropagatesDocumentCloseFailureWhenWriterCloseSucceeds() throws IOException {
    try (PdfDocumentFactory.DocumentSession backingSession =
            standardFactory().create("Trial Balance", GENERATED_AT, PageOrientation.PORTRAIT);
        PdfDocumentFactory.DocumentSession session =
            new PdfDocumentFactory.DocumentSession(
                backingSession.pageWriter(),
                () -> {},
                () -> {
                  throw new IOException("document-close");
                },
                outputStream -> outputStream.write(new byte[] {1, 2, 3}))) {

      IOException failure = assertThrows(IOException.class, session::close);

      assertEquals("document-close", failure.getMessage());
      assertEquals(0, failure.getSuppressed().length);
    }
  }

  @Test
  void pageWriterHandlesBlankCellsBrokenWordsAndKeyValuePagination() throws IOException {
    try (PdfDocumentFactory.DocumentSession session =
        standardFactory().create("Coverage Cases", GENERATED_AT, PageOrientation.PORTRAIT)) {
      session
          .pageWriter()
          .writeTable(
              "Wrapping",
              List.of(
                  new PdfTableColumn("Description", 0.8f, PdfTableColumn.CellAlignment.LEFT),
                  new PdfTableColumn("Amount", 0.2f, PdfTableColumn.CellAlignment.RIGHT)),
              List.of(
                  List.of("", "0"),
                  List.of("SupercalifragilisticexpialidociousLedgerToken", "1"),
                  List.of("alpha SupercalifragilisticexpialidociousLedgerToken", "2")));
      session.pageWriter().writeKeyValueTable("Details", paginatedKeyValueRows());

      byte[] pdfBytes = session.toByteArray();

      try (PDDocument document = Loader.loadPDF(pdfBytes)) {
        assertTrue(document.getNumberOfPages() >= 2);
      }
    }
  }

  @Test
  void reportThemeRejectsOutOfRangeGrayValues() {
    assertEquals(1f, PdfReportTheme.grayscale().normalized(255));
    assertThrows(IllegalArgumentException.class, () -> PdfReportTheme.grayscale().normalized(-1));
    assertThrows(IllegalArgumentException.class, () -> PdfReportTheme.grayscale().normalized(256));
  }

  @Test
  void tableColumnRejectsNonPositiveWidthWeights() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new PdfTableColumn("Column", 0f, PdfTableColumn.CellAlignment.LEFT));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PdfTableColumn("Column", -1f, PdfTableColumn.CellAlignment.RIGHT));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PdfTableColumn("  ", 1f, PdfTableColumn.CellAlignment.LEFT));
  }

  private static PdfDocumentFactory standardFactory() {
    return new PdfDocumentFactory("FinGrind", "0.50.0");
  }

  private static TrialBalanceReport sampleTrialBalanceReport() {
    DeclaredAccount cashAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.empty(),
                Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                Optional.empty()),
            true,
            Instant.parse("2026-04-01T08:00:00Z"));
    return trialBalanceReport(
        bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
        PostingCoverage.ALL_POSTING_KINDS,
        List.of(
            new TrialBalanceRow(
                cashAccount, balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
        List.of());
  }

  private static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio"), List.of()),
        dev.erst.fingrind.core.AccountingKernelProfiles.COUNTRY_AGNOSTIC_BOOKKEEPING_KERNEL,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"));
  }

  private static List<List<String>> paginatedKeyValueRows() {
    List<List<String>> rows = new ArrayList<>();
    for (int index = 0; index < 90; index++) {
      rows.add(
          List.of(
              "Key %02d".formatted(index),
              "verylongledgerdetailtoken%02d verylongledgerdetailtoken%02d verylongledgerdetailtoken%02d"
                  .formatted(index, index, index)));
    }
    return rows;
  }

  private static TrialBalanceReport trialBalanceReport(
      BookIdentity bookIdentity,
      Optional<LocalDate> effectiveDateTo,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage,
      List<TrialBalanceRow> rows,
      List<TrialBalanceRow> comparativeRows) {
    List<CurrencyBalance> totals = trialBalanceTotals(rows);
    List<CurrencyBalance> comparativeTotals = trialBalanceTotals(comparativeRows);
    return new TrialBalanceReport(
        bookIdentity,
        effectiveDateTo,
        comparativeEffectiveDateRange,
        postingCoverage,
        rows,
        totals,
        isBalanced(totals),
        comparativeRows,
        comparativeTotals,
        isBalanced(comparativeTotals));
  }

  private static List<CurrencyBalance> trialBalanceTotals(List<TrialBalanceRow> rows) {
    List<CurrencyBalance> totalsByCurrency = new ArrayList<>();
    for (TrialBalanceRow row : rows) {
      mergeCurrencyBalance(totalsByCurrency, row.balance());
    }
    return List.copyOf(totalsByCurrency);
  }

  private static CurrencyBalance sumCurrencyBalances(CurrencyBalance left, CurrencyBalance right) {
    return CurrencyBalance.ofTotals(
        left.debitTotal().plus(right.debitTotal()), left.creditTotal().plus(right.creditTotal()));
  }

  private static boolean isBalanced(List<CurrencyBalance> totals) {
    return totals.stream().allMatch(balance -> balance.balanceSide() == BalanceSide.ZERO);
  }

  private static void mergeCurrencyBalance(
      List<CurrencyBalance> totalsByCurrency, CurrencyBalance candidate) {
    CurrencyUnit currencyUnit = candidate.debitTotal().currencyUnit();
    for (int index = 0; index < totalsByCurrency.size(); index++) {
      CurrencyBalance existing = totalsByCurrency.get(index);
      if (existing.debitTotal().currencyUnit().equals(currencyUnit)) {
        totalsByCurrency.set(index, sumCurrencyBalances(existing, candidate));
        return;
      }
    }
    totalsByCurrency.add(candidate);
  }

  private static Money money(String currencyCode, String amount) {
    return Money.parse(currencyCode, amount);
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
}
