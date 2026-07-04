package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Exhaustive coverage tests for published posting-template variants and validators. */
class MachineContractPostEntryVariantTemplateCoverageTest {
  @Test
  void variantTemplatesAndOperationMappings_coverEveryPublishedPostingKind() {
    for (BookkeepingEntryKind entryKind : BookkeepingEntryKind.values()) {
      ContractTemplates.PostingRequestTemplateDescriptor template =
          MachineContractPostEntryVariantSchemas.template(entryKind);

      MachineContractPostEntryVariantTemplateTestSupport.assertCanonicalTemplate(
          entryKind, template);
    }

    for (Map.Entry<OperationId, BookkeepingEntryKind> scaffold :
        MachineContractPostEntryVariantTemplateTestSupport.scaffoldOperationEntryKinds()
            .entrySet()) {
      assertEquals(
          scaffold.getValue(),
          Objects.requireNonNull(
                  MachineContractTemplatesCatalog.postingRequestTemplateFor(
                      ProtocolCatalog.operation(scaffold.getKey()), null))
              .entryKind());
    }
  }

  @Test
  void tradingSaleTemplates_publishInventoryReliefAndTradingRevenueLabels() {
    ContractTemplates.PostingRequestTemplateDescriptor settledTradingTemplate =
        MachineContractPostEntryVariantSchemas.template(
            BookkeepingEntryKind.SALE_SETTLED, BookTemplateId.OWNER_MANAGED_TRADING);
    ContractTemplates.PostingRequestTemplateDescriptor creditTradingTemplate =
        MachineContractPostEntryVariantSchemas.template(
            BookkeepingEntryKind.SALE_ON_CREDIT, BookTemplateId.OWNER_MANAGED_TRADING);

    assertEquals("sales-revenue", settledTradingTemplate.revenueAccountCode());
    assertEquals("sales-revenue", creditTradingTemplate.revenueAccountCode());
    assertNotNull(settledTradingTemplate.inventoryRelief());
    assertNotNull(creditTradingTemplate.inventoryRelief());
    assertEquals("inventory", settledTradingTemplate.inventoryRelief().inventoryAccountCode());
    assertEquals(
        "cost-of-sales", settledTradingTemplate.inventoryRelief().costOfSalesAccountCode());
  }

  @Test
  void postingTemplateValidators_acceptCanonicalShapesForEveryEntryKind() {
    for (BookkeepingEntryKind entryKind : BookkeepingEntryKind.values()) {
      assertDoesNotThrow(
          () ->
              ContractPostingRequestTemplateValidators.validate(
                  entryKind,
                  MachineContractPostEntryVariantTemplateTestSupport.canonicalFields(
                      entryKind,
                      MachineContractPostEntryVariantTemplateTestSupport.settlementAdjunctIfOwned(
                          entryKind)),
                  MachineContractPostEntryVariantTemplateTestSupport.reversalIfOwned(entryKind)));
    }
  }

  @Test
  void postingTemplateValidators_rejectSettlementAdjunctOnEntryKindsThatDoNotOwnIt() {
    ContractTemplates.SettlementAdjunctTemplateDescriptor settlementAdjunct =
        MachineContractPostEntryVariantTemplateTestSupport.settlementAdjunct();

    for (Map.Entry<BookkeepingEntryKind, String> rejection :
        MachineContractPostEntryVariantTemplateTestSupport.settlementAdjunctForbiddenContexts()
            .entrySet()) {
      IllegalArgumentException violation =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  ContractPostingRequestTemplateValidators.validate(
                      rejection.getKey(),
                      MachineContractPostEntryVariantTemplateTestSupport.canonicalFields(
                          rejection.getKey(), settlementAdjunct),
                      MachineContractPostEntryVariantTemplateTestSupport.reversalIfOwned(
                          rejection.getKey())));

      assertEquals(
          "settlementAdjunct must be absent for " + rejection.getValue() + ".",
          violation.getMessage());
    }
  }

  @Test
  void settlementAdjunctTemplateDescriptor_rejectsNonPositiveAmounts() {
    IllegalArgumentException zeroAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractTemplates.SettlementAdjunctTemplateDescriptor(
                    "settlement-clearing", new MonetaryAmount("EUR", "0")));

    assertEquals("amount must carry one positive minor-unit value.", zeroAmount.getMessage());
  }
}
