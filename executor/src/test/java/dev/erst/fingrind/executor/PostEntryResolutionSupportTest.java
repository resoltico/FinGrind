package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.account;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.inventoryAssetAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ensures pre-tax inventory admission failures remain deterministic resolution outcomes. */
class PostEntryResolutionSupportTest {
  @Test
  void resolve_returnsQuantityAdmissionRejectionBeforeTaxLookup() {
    var outcome =
        PostEntryResolutionSupport.resolve(
            new BookkeepingEntry.PurchaseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("1000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("0.5"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard")),
                null),
            new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
                Map.of(
                    new AccountCode("1400"),
                    inventoryAssetAccount("1400"),
                    new AccountCode("1000"),
                    account("1000", AccountType.ASSET))));

    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            outcome.rejection().orElseThrow());

    assertEquals(
        "inventory-quantity-incompatible-with-unit-of-measure",
        rejection.violations().getFirst().code());
  }
}
