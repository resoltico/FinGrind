package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
  private static final Path BOOK_PATH =
      Path.of("/tmp/Rīga büro/2026 Q2 close/Ops & Sales [April] #1.sqlite");

  @Test
  void renderTrialBalanceWrapsFontLoadingIoFailures() {
    PdfReportService service =
        new PdfReportService(
            CLOCK,
            new PdfDocumentFactory(
                "FinGrind",
                "0.25.0",
                resourcePath ->
                    new ByteArrayInputStream(
                        ("not-a-font:" + resourcePath).getBytes(StandardCharsets.UTF_8))));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> service.renderTrialBalance(BOOK_PATH, sampleTrialBalanceReport()));

    assertEquals("Failed to render Trial Balance PDF.", exception.getMessage());
    assertInstanceOf(IOException.class, exception.getCause());
  }

  @Test
  void createRejectsMissingBundledFontResources() {
    PdfDocumentFactory factory = new PdfDocumentFactory("FinGrind", "0.25.0", resourcePath -> null);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                factory.create(
                    "Trial Balance", BOOK_PATH, GENERATED_AT, PageOrientation.LANDSCAPE));

    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("Missing bundled font resource"));
  }

  @Test
  void documentSessionAggregatesCloseFailuresAndRemainsIdempotent() throws IOException {
    try (PdfDocumentFactory.DocumentSession backingSession =
            standardFactory()
                .create("Trial Balance", BOOK_PATH, GENERATED_AT, PageOrientation.PORTRAIT);
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
            standardFactory()
                .create("Trial Balance", BOOK_PATH, GENERATED_AT, PageOrientation.PORTRAIT);
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
        standardFactory()
            .create("Coverage Cases", BOOK_PATH, GENERATED_AT, PageOrientation.PORTRAIT)) {
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
    return new PdfDocumentFactory("FinGrind", "0.25.0");
  }

  private static TrialBalanceReport sampleTrialBalanceReport() {
    DeclaredAccount cashAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-01T08:00:00Z"));
    return new TrialBalanceReport(
        Optional.of(LocalDate.parse("2026-04-30")),
        List.of(
            new TrialBalanceRow(
                cashAccount,
                new CurrencyBalance(
                    money("EUR", "1250.00"),
                    money("EUR", "10.00"),
                    money("EUR", "1240.00"),
                    BalanceSide.DEBIT))));
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
    return new Money(new CurrencyCode(currencyCode), new BigDecimal(amount));
  }
}
