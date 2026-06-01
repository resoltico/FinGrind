package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountingKernelProfiles;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.PostingCoverage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage tests for shared PDF statement metadata rows. */
class PdfStatementMetadataRowsTest {
  @Test
  void reportParameters_includeBusinessActivityRowWhenTagsExist() {
    BookIdentity bookIdentity =
        new BookIdentity(
            new EntityProfile(
                new BookEntityName("Acme Studio"),
                List.of(new BusinessActivityTag("translation-services"))),
            AccountingKernelProfiles.COUNTRY_AGNOSTIC_BOOKKEEPING_KERNEL,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"));

    List<List<String>> rows =
        PdfStatementMetadataRows.reportParameters(
            bookIdentity,
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(List.of("As of", "2026-04-30")));

    assertEquals("Entity", rows.get(0).getFirst());
    assertEquals("Book context", rows.get(1).getFirst());
    assertEquals(List.of("Business activity", "translation-services"), rows.get(2));
    assertTrue(rows.contains(List.of("As of", "2026-04-30")));
  }
}
