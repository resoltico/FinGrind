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
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.Money;
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
                "0.35.0",
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
    PdfDocumentFactory factory = new PdfDocumentFactory("FinGrind", "0.35.0", resourcePath -> null);

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
    assertEquals(1f, PdfReportTheme.normalizedGray(255));
    assertThrows(IllegalArgumentException.class, () -> PdfReportTheme.normalizedGray(-1));
    assertThrows(IllegalArgumentException.class, () -> PdfReportTheme.normalizedGray(256));
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
    return new PdfDocumentFactory("FinGrind", "0.35.0");
  }

  private static TrialBalanceReport sampleTrialBalanceReport() {
    DeclaredAccount cashAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            AccountRole.ORDINARY,
            true,
            Instant.parse("2026-04-01T08:00:00Z"));
    return new TrialBalanceReport(
        Optional.of(LocalDate.parse("2026-04-30")),
        List.of(
            new TrialBalanceRow(
                cashAccount, balance("EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))));
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
