package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
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
            new EntityProfile(
                new BookEntityName("Acme Studio"),
                List.of(
                    new BusinessActivityTag("translation-services"),
                    new BusinessActivityTag("platform-sales"))),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1);

    assertEquals(
        List.of(
            List.of(
                "Book",
                "Acme Studio | Currency EUR | FY 01-01 | Policy Internal Management Single Entity V1")),
        CliBookIdentityDisplay.summaryRows(bookIdentity));
    assertEquals(
        List.of("Business activity", "translation-services, platform-sales"),
        CliBookIdentityDisplay.detailRows(bookIdentity).get(1));
  }
}
