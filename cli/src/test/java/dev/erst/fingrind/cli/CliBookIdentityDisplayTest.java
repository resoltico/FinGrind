package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliBookIdentityDisplay}. */
class CliBookIdentityDisplayTest {
  @Test
  void summaryRows_useCompactIdentityShape() {
    BookIdentity bookIdentity =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Acme Studio")),
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_CASH_SERVICE,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"));

    assertEquals(
        List.of(
            List.of("Entity", "Acme Studio"),
            List.of("Starter chart", "Owner-managed service starter chart"),
            List.of("Functional currency", "EUR"),
            List.of("Fiscal year start", "01-01")),
        CliBookIdentityDisplay.summaryRows(bookIdentity));
    assertEquals(
        List.of(
            List.of("Entity", "Acme Studio"),
            List.of("Starter chart", "Owner-managed service starter chart"),
            List.of("Functional currency", "EUR"),
            List.of("Fiscal year start", "01-01")),
        CliBookIdentityDisplay.contextRows(bookIdentity));
    assertEquals(
        List.of("Accounting kernel", "Internal management cash bookkeeping"),
        CliBookIdentityDisplay.detailRows(bookIdentity).get(1));
    assertEquals(
        List.of("Accounting basis", "Cash basis"),
        CliBookIdentityDisplay.detailRows(bookIdentity).get(2));
  }
}
