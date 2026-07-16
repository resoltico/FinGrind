package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused coverage for trading-sale inventory-relief discovery templates. */
class InventoryReliefTemplateCoverageTest {
  @Test
  void inventoryReliefTemplateDescriptor_validatesShapeAndPositiveQuantity() {
    InventoryReliefTemplateDescriptor validDescriptor =
        new InventoryReliefTemplateDescriptor("inventory-on-hand", "cost-of-sales", "2.5");
    IllegalArgumentException zeroQuantity =
        assertThrows(
            IllegalArgumentException.class,
            () -> new InventoryReliefTemplateDescriptor("inventory-on-hand", "cost-of-sales", "0"));

    assertEquals("inventory-on-hand", validDescriptor.inventoryAccountCode());
    assertEquals("cost-of-sales", validDescriptor.costOfSalesAccountCode());
    assertEquals("2.5", validDescriptor.quantity());
    assertEquals("quantity must carry one positive quantity.", zeroQuantity.getMessage());
  }

  @Test
  void postingTemplateValidators_rejectDuplicateInventoryReliefAccounts() {
    IllegalArgumentException violation =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ContractPostingRequestTemplateValidators.validate(
                    BookkeepingEntryKind.SALE_SETTLED,
                    new ContractPostingRequestTemplateValidators.PostingTemplateFields(
                        "cash",
                        null,
                        null,
                        "service-revenue",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new MonetaryAmount("EUR", "1000"),
                        null,
                        null,
                        new InventoryReliefTemplateDescriptor(
                            "inventory-on-hand", "inventory-on-hand", "1"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                    null));

    assertEquals(
        "saleSettled inventoryRelief requires distinct inventoryAccountCode and costOfSalesAccountCode.",
        violation.getMessage());
  }

  @Test
  void postingTemplateValidators_acceptDistinctInventoryReliefOnSaleTemplates() {
    assertDoesNotThrow(
        () ->
            ContractPostingRequestTemplateValidators.validate(
                BookkeepingEntryKind.SALE_SETTLED,
                new ContractPostingRequestTemplateValidators.PostingTemplateFields(
                    "cash",
                    null,
                    null,
                    "service-revenue",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new MonetaryAmount("EUR", "1000"),
                    null,
                    null,
                    inventoryRelief(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null),
                null));
  }

  @Test
  void postingTemplateValidators_rejectInventoryReliefOutsideTradingSaleVariants() {
    IllegalArgumentException violation =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ContractPostingRequestTemplateValidators.validate(
                    BookkeepingEntryKind.REVERSAL,
                    new ContractPostingRequestTemplateValidators.PostingTemplateFields(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        inventoryRelief(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                    new ContractReversalTemplates.ReversalTemplateDescriptor(
                        "posting-1", "correction")));

    assertEquals("inventoryRelief must be absent for reversal.", violation.getMessage());
  }

  @Test
  void saleRequestShapes_publishInventoryReliefAsConditionalAndDescribeTradingRequirement() {
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor settledSaleDescriptor =
        MachineContractPostEntrySchemas.descriptor(BookkeepingEntryKind.SALE_SETTLED);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor creditSaleDescriptor =
        MachineContractPostEntrySchemas.descriptor(BookkeepingEntryKind.SALE_ON_CREDIT);
    ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor purchaseDescriptor =
        MachineContractPostEntrySchemas.descriptor(BookkeepingEntryKind.PURCHASE_SETTLED);

    assertEquals(
        RequestFieldPresence.CONDITIONAL,
        fieldNamed(
                settledSaleDescriptor.topLevelFields(),
                ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF)
            .presence());
    assertEquals(
        RequestFieldPresence.CONDITIONAL,
        fieldNamed(
                creditSaleDescriptor.topLevelFields(),
                ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF)
            .presence());
    assertEquals(
        RequestFieldPresence.FORBIDDEN,
        fieldNamed(
                purchaseDescriptor.topLevelFields(),
                ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF)
            .presence());
    assertTrue(
        fieldNamed(
                settledSaleDescriptor.topLevelFields(),
                ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF)
            .description()
            .contains("Trading-template sale requests require this object"));
  }

  @Test
  void conditionalFieldGate_rejectsUnownedConditionalNamesAndNonSaleEntryKinds() throws Throwable {
    MethodHandle conditionalFieldGate =
        MethodHandles.privateLookupIn(MachineContractPostEntrySchemas.class, MethodHandles.lookup())
            .findStatic(
                MachineContractPostEntrySchemas.class,
                "conditionallyAcceptedTopLevelField",
                MethodType.methodType(
                    boolean.class, BookkeepingEntryKind.class, MachineContractFieldSpec.class));
    MachineContractFieldSpec inventoryReliefField =
        MachineContractFieldSpec.conditional(
            ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
            "inventory-relief coverage",
            java.util.Map.of("type", "object"));
    MachineContractFieldSpec unrelatedConditionalField =
        MachineContractFieldSpec.conditional(
            "foreignConditional", "foreign coverage", java.util.Map.of("type", "string"));

    assertTrue(
        (boolean)
            conditionalFieldGate.invokeExact(
                BookkeepingEntryKind.SALE_SETTLED, inventoryReliefField));
    assertFalse(
        (boolean)
            conditionalFieldGate.invokeExact(
                BookkeepingEntryKind.PURCHASE_SETTLED, inventoryReliefField));
    assertFalse(
        (boolean)
            conditionalFieldGate.invokeExact(
                BookkeepingEntryKind.SALE_SETTLED, unrelatedConditionalField));
  }

  private static ContractRequestShapes.RequestFieldDescriptor fieldNamed(
      List<ContractRequestShapes.RequestFieldDescriptor> fields, String name) {
    return fields.stream().filter(field -> name.equals(field.name())).findFirst().orElseThrow();
  }

  private static InventoryReliefTemplateDescriptor inventoryRelief() {
    return new InventoryReliefTemplateDescriptor("inventory-on-hand", "cost-of-sales", "1");
  }
}
