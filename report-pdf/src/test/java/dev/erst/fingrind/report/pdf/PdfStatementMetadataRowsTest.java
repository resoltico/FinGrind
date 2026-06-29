package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.PostingCoverage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage tests for shared PDF statement metadata rows. */
class PdfStatementMetadataRowsTest {
  @Test
  void reportParameters_includeLeanOperatorContext() {
    BookIdentity bookIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Acme Studio")),
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"));

    List<List<String>> rows =
        PdfStatementMetadataRows.reportParameters(
            bookIdentity,
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(List.of("As of", "2026-04-30")));

    assertEquals("Entity", rows.get(0).getFirst());
    assertEquals("Starter chart", rows.get(1).getFirst());
    assertEquals("Accounting basis", rows.get(2).getFirst());
    assertEquals("Posting coverage", rows.get(5).getFirst());
    assertTrue(rows.contains(List.of("As of", "2026-04-30")));
  }
}
