package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Coverage tests for package-private posting-template field-support helpers. */
class ContractPostingTemplateFieldSupportCoverageTest {
  @Test
  void requireTextFields_arrayOverloadRejectsMissingFieldsAndAcceptsSatisfiedShapes() {
    IllegalArgumentException missingCash =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ContractPostingRequestTemplateFieldSupport.requireTextFields(
                    fields(null, "service-revenue"),
                    new ContractPostingRequestTemplateFieldSupport.TemplateTextField[] {
                      ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
                      ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE
                    }));

    assertEquals("cashAccountCode must not be null.", missingCash.getMessage());
    assertDoesNotThrow(
        () ->
            ContractPostingRequestTemplateFieldSupport.requireTextFields(
                fields("cash", "service-revenue"),
                new ContractPostingRequestTemplateFieldSupport.TemplateTextField[] {
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.CASH,
                  ContractPostingRequestTemplateFieldSupport.TemplateTextField.REVENUE
                }));
  }

  @Test
  void validateInventoryRelief_honorsOptionalAndForbiddenPolicies() {
    assertDoesNotThrow(
        () ->
            ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
                inventoryRelief(),
                "saleSettled",
                ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.OPTIONAL));

    IllegalArgumentException forbiddenViolation =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ContractPostingRequestTemplateFieldSupport.validateInventoryRelief(
                    inventoryRelief(),
                    "purchaseSettled",
                    ContractPostingRequestTemplateFieldSupport.InventoryReliefPolicy.FORBIDDEN));

    assertEquals(
        "inventoryRelief must be absent for purchaseSettled.", forbiddenViolation.getMessage());
  }

  private static ContractPostingRequestTemplateValidators.PostingTemplateFields fields(
      @Nullable String cashAccountCode, @Nullable String revenueAccountCode) {
    return new ContractPostingRequestTemplateValidators.PostingTemplateFields(
        cashAccountCode,
        null,
        null,
        revenueAccountCode,
        null,
        null,
        null,
        null,
        null,
        null,
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static InventoryReliefTemplateDescriptor inventoryRelief() {
    return new InventoryReliefTemplateDescriptor("inventory-on-hand", "cost-of-sales", "1");
  }
}
