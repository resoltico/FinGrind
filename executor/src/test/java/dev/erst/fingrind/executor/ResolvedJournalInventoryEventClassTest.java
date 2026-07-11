package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.generatedEvidence;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.account;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.inventoryAssetAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.EconomicEventClass;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Ensures inventory capitalization carries its asserted economic event through classification. */
class ResolvedJournalInventoryEventClassTest {
  @Test
  void resolve_classifiesSettledCapitalizationAsInventoryCapitalization() {
    var resolved =
        ResolvedJournalSupport.resolve(
            new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null),
            generatedEvidence("capitalization", "cash-disbursement"),
            Map.of(
                new AccountCode("1400"),
                inventoryAssetAccount("1400"),
                new AccountCode("1000"),
                account("1000", AccountType.ASSET)));

    assertEquals(
        EconomicEventClass.INVENTORY_CAPITALIZATION, resolved.classification().eventClass());
  }

  @Test
  void resolve_classifiesCreditCapitalizationAsInventoryCapitalization() {
    var resolved =
        ResolvedJournalSupport.resolve(
            new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null),
            generatedEvidence("capitalization-credit", "supplier-invoice"),
            Map.of(
                new AccountCode("1400"),
                inventoryAssetAccount("1400"),
                new AccountCode("2100"),
                PostEntrySemanticsPolicyTestSupport.payableAccount("2100")));

    assertEquals(
        EconomicEventClass.INVENTORY_CAPITALIZATION, resolved.classification().eventClass());
  }
}
