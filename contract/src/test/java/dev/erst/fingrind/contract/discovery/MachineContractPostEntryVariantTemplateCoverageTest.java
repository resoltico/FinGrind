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
      ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template =
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
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor settledTradingTemplate =
        MachineContractPostEntryVariantSchemas.template(
            BookkeepingEntryKind.SALE_SETTLED, BookTemplateId.OWNER_MANAGED_TRADING);
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor creditTradingTemplate =
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
  void foreignCurrencyObligationTemplates_useTheSelectedTemplateRevenueAndCanonicalReceivable() {
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor serviceTemplate =
        MachineContractPostEntryVariantSchemas.template(
            BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION, BookTemplateId.OWNER_MANAGED_SERVICE);
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor tradingTemplate =
        MachineContractPostEntryVariantSchemas.template(
            BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION, BookTemplateId.OWNER_MANAGED_TRADING);

    assertEquals("accounts-receivable", serviceTemplate.receivableAccountCode());
    assertEquals("service-revenue", serviceTemplate.revenueAccountCode());
    assertEquals("accounts-receivable", tradingTemplate.receivableAccountCode());
    assertEquals("sales-revenue", tradingTemplate.revenueAccountCode());
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
    ContractSettlementTemplates.SettlementAdjunctTemplateDescriptor settlementAdjunct =
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
  void payrollTemplateValidationRejectsMissingAndMisplacedPayrollFactBlocks() {
    ContractPostingRequestTemplateValidators.PostingTemplateFields emptyFields =
        MachineContractPostEntryVariantTemplateTestSupport.canonicalFields(
            BookkeepingEntryKind.REVERSAL, null);
    ContractPostingRequestTemplateValidators.PostingTemplateFields monthlyPayrollFields =
        MachineContractPostEntryVariantTemplateTestSupport.canonicalFields(
            BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL, null);
    ContractPostingRequestTemplateValidators.PostingTemplateFields settlementFields =
        MachineContractPostEntryVariantTemplateTestSupport.canonicalFields(
            BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT, null);

    assertEquals(
        "latvianMonthlyPayroll must be present for LATVIAN_MONTHLY_PAYROLL.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingRequestTemplateValidators.validate(
                        BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL, emptyFields, null))
            .getMessage());
    assertEquals(
        "latvianPayrollSettlement must be present for Latvian payroll settlements.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingRequestTemplateValidators.validate(
                        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
                        emptyFields,
                        null))
            .getMessage());
    assertEquals(
        "latvianMonthlyPayroll must be absent for SALE_SETTLED.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingRequestTemplateValidators.validate(
                        BookkeepingEntryKind.SALE_SETTLED, monthlyPayrollFields, null))
            .getMessage());
    assertEquals(
        "latvianPayrollSettlement must be absent for SALE_SETTLED.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    ContractPostingRequestTemplateValidators.validate(
                        BookkeepingEntryKind.SALE_SETTLED, settlementFields, null))
            .getMessage());
    assertEquals(
        Map.of("payrollRunId", "payroll-lv-2026-01-employee-001"),
        Objects.requireNonNull(settlementFields.latvianPayrollSettlement()).requestFields());
  }

  @Test
  void settlementAdjunctTemplateDescriptor_rejectsNonPositiveAmounts() {
    IllegalArgumentException zeroAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractSettlementTemplates.SettlementAdjunctTemplateDescriptor(
                    "settlement-clearing", new MonetaryAmount("EUR", "0")));

    assertEquals("amount must carry one positive minor-unit value.", zeroAmount.getMessage());
  }
}
