package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.Objects;

/** Canonical owner for inventory-specific entry-semantics rejection details. */
final class PostingEntryInventoryRejectionSemantics {
  private PostingEntryInventoryRejectionSemantics() {}

  static PostingRejection.EntrySemanticsViolation tradingSaleRequiresInventoryRelief(
      String selectorField, String selectorValue) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    return new PostingRejection.EntrySemanticsViolation(
        "trading-sale-requires-inventory-relief",
        "inventoryRelief",
        "%s '%s' targets a trading-template book, so inventoryRelief is required on sale requests."
            .formatted(requiredSelectorField, requiredSelectorValue));
  }

  static PostingRejection.EntrySemanticsViolation verbRequiresTradingTemplate(
      String selectorField, String selectorValue, BookTemplateId bookTemplateId) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(bookTemplateId, "bookTemplateId");
    return new PostingRejection.EntrySemanticsViolation(
        "verb-requires-trading-template",
        requiredSelectorField,
        "%s '%s' is an inventory-purchase verb admitted only on trading-template books, but selected bookTemplateId '%s' does not admit that doctrine."
            .formatted(requiredSelectorField, requiredSelectorValue, bookTemplateId.wireValue()));
  }

  static PostingRejection.EntrySemanticsViolation inventoryReliefRequiresTradingBook(
      String selectorField, String selectorValue, BookTemplateId bookTemplateId) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(bookTemplateId, "bookTemplateId");
    return new PostingRejection.EntrySemanticsViolation(
        "inventory-relief-requires-trading-book",
        "inventoryRelief",
        "%s '%s' carries inventoryRelief, but selected bookTemplateId '%s' does not admit trading inventory relief."
            .formatted(requiredSelectorField, requiredSelectorValue, bookTemplateId.wireValue()));
  }

  static PostingRejection.EntrySemanticsViolation inventoryQuantityIncompatibleWithUnitOfMeasure(
      String field,
      String quantityText,
      AccountCode inventoryAccountCode,
      UnitOfMeasure unitOfMeasure,
      String reason) {
    String requiredField = PostingRejectionSemanticsSupport.requireSelectorField(field);
    String requiredQuantityText =
        PostingRejectionSemanticsSupport.requireSelectorValue(quantityText);
    Objects.requireNonNull(inventoryAccountCode, "inventoryAccountCode");
    Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
    String requiredReason = PostingRejectionSemanticsSupport.requireSelectorValue(reason);
    return new PostingRejection.EntrySemanticsViolation(
        "inventory-quantity-incompatible-with-unit-of-measure",
        requiredField,
        "%s '%s' is incompatible with inventoryAccountCode '%s' because that account declares unitOfMeasure '%s' with quantityScale %d. %s"
            .formatted(
                requiredField,
                requiredQuantityText,
                inventoryAccountCode.value(),
                unitOfMeasure.token(),
                unitOfMeasure.quantityScale(),
                requiredReason));
  }

  static PostingRejection.EntrySemanticsViolation inventoryAcquisitionCostNotExact(
      String quantityText,
      Money unitCost,
      AccountCode inventoryAccountCode,
      UnitOfMeasure unitOfMeasure) {
    String requiredQuantityText =
        PostingRejectionSemanticsSupport.requireSelectorValue(quantityText);
    Objects.requireNonNull(unitCost, "unitCost");
    Objects.requireNonNull(inventoryAccountCode, "inventoryAccountCode");
    Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
    return new PostingRejection.EntrySemanticsViolation(
        "inventory-acquisition-cost-not-exact",
        "unitCost",
        "unitCost '%s' cannot compose one exact acquisition carrying cost with quantity '%s' for inventoryAccountCode '%s' because declared unitOfMeasure '%s' owns quantityScale %d and quantity multiplied by unitCost must resolve to one exact currency-minor-unit amount."
            .formatted(
                formatMoney(unitCost),
                requiredQuantityText,
                inventoryAccountCode.value(),
                unitOfMeasure.token(),
                unitOfMeasure.quantityScale()));
  }

  static PostingRejection.EntrySemanticsViolation inventoryAcquisitionBreachesMinorUnitFloor(
      String quantityText,
      Money unitCost,
      AccountCode inventoryAccountCode,
      UnitOfMeasure unitOfMeasure,
      long minimumRequiredMinorUnits,
      Money resultingCostPool) {
    String requiredQuantityText =
        PostingRejectionSemanticsSupport.requireSelectorValue(quantityText);
    Objects.requireNonNull(unitCost, "unitCost");
    Objects.requireNonNull(inventoryAccountCode, "inventoryAccountCode");
    Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
    Objects.requireNonNull(resultingCostPool, "resultingCostPool");
    Money minimumRequiredCost =
        Money.ofMinorUnits(unitCost.currencyUnit(), minimumRequiredMinorUnits);
    return new PostingRejection.EntrySemanticsViolation(
        "inventory-acquisition-breaches-minor-unit-floor",
        "unitCost",
        "unitCost '%s' with quantity '%s' would leave inventoryAccountCode '%s' below the minimum carrying-cost floor required by declared unitOfMeasure '%s' quantityScale %d. Resulting pool: %s. Minimum required: %s."
            .formatted(
                formatMoney(unitCost),
                requiredQuantityText,
                inventoryAccountCode.value(),
                unitOfMeasure.token(),
                unitOfMeasure.quantityScale(),
                formatMoney(resultingCostPool),
                formatMoney(minimumRequiredCost)));
  }

  /**
   * Returns one refusal when FX functional amount conflicts with exact resolved acquisition cost.
   */
  static PostingRejection.EntrySemanticsViolation
      inventoryAcquisitionForeignExchangeFunctionalAmountMismatch(
          String selectorValue,
          MonetaryAmount expectedFunctionalAmount,
          MonetaryAmount actualFunctionalAmount) {
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(expectedFunctionalAmount, "expectedFunctionalAmount");
    Objects.requireNonNull(actualFunctionalAmount, "actualFunctionalAmount");
    return new PostingRejection.EntrySemanticsViolation(
        "inventory-acquisition-foreign-exchange-functional-amount-mismatch",
        "foreignExchange.functionalAmount",
        "entryKind '%s' resolves exact pre-tax acquisition cost '%s', but foreignExchange.functionalAmount is '%s'."
            .formatted(
                requiredSelectorValue,
                formatMoney(expectedFunctionalAmount.toMoney()),
                formatMoney(actualFunctionalAmount.toMoney())));
  }

  /** Returns one refusal when a raw direct journal contains an inventory-account line. */
  static PostingRejection.EntrySemanticsViolation rawJournalTouchesInventory(
      String selectorValue, AccountCode accountCode) {
    return rawJournalTouchesInventory("entryKind", selectorValue, accountCode);
  }

  /** Returns one refusal when a raw direct journal contains an inventory-account line. */
  static PostingRejection.EntrySemanticsViolation rawJournalTouchesInventory(
      String selectorField, String selectorValue, AccountCode accountCode) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(accountCode, "accountCode");
    return new PostingRejection.EntrySemanticsViolation(
        "raw-journal-touches-inventory",
        "lines[].accountCode",
        "%s '%s' contains lines[].accountCode '%s', which resolves to the inventory role. Raw direct-journal requests cannot create or change exact inventory quantity."
            .formatted(requiredSelectorField, requiredSelectorValue, accountCode.value()));
  }

  /** Returns one refusal when an inventory opening balance omits exact quantity. */
  static PostingRejection.EntrySemanticsViolation openingInventoryRequiresQuantity(
      String selectorValue, AccountCode accountCode) {
    return openingInventoryRequiresQuantity("entryKind", selectorValue, accountCode);
  }

  /** Returns one refusal when an inventory opening balance omits exact quantity. */
  static PostingRejection.EntrySemanticsViolation openingInventoryRequiresQuantity(
      String selectorField, String selectorValue, AccountCode accountCode) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(accountCode, "accountCode");
    return new PostingRejection.EntrySemanticsViolation(
        "opening-inventory-requires-quantity",
        "openingBalances[].quantity",
        "%s '%s' uses inventory openingBalances[].accountCode '%s', so openingBalances[].quantity is required to establish exact inventory on hand."
            .formatted(requiredSelectorField, requiredSelectorValue, accountCode.value()));
  }

  /** Returns one refusal when a non-inventory opening balance carries quantity. */
  static PostingRejection.EntrySemanticsViolation openingQuantityRequiresInventory(
      String selectorField, String selectorValue, AccountCode accountCode) {
    String requiredSelectorField =
        PostingRejectionSemanticsSupport.requireSelectorField(selectorField);
    String requiredSelectorValue =
        PostingRejectionSemanticsSupport.requireSelectorValue(selectorValue);
    Objects.requireNonNull(accountCode, "accountCode");
    return new PostingRejection.EntrySemanticsViolation(
        "opening-quantity-requires-inventory",
        "openingBalances[].quantity",
        "%s '%s' uses non-inventory openingBalances[].accountCode '%s', so openingBalances[].quantity must be absent."
            .formatted(requiredSelectorField, requiredSelectorValue, accountCode.value()));
  }

  /** Returns one refusal when capitalization would create cost without quantity. */
  static PostingRejection.EntrySemanticsViolation inventoryCapitalizationRequiresQuantityOnHand(
      AccountCode accountCode) {
    Objects.requireNonNull(accountCode, "accountCode");
    return new PostingRejection.EntrySemanticsViolation(
        "inventory-capitalization-requires-quantity-on-hand",
        "inventoryAccountCode",
        "inventoryAccountCode '%s' has no quantity on hand, so a cost-only capitalization would violate the exact inventory pool invariant."
            .formatted(accountCode.value()));
  }

  /** Returns one refusal when an opening inventory movement is not the account's first movement. */
  static PostingRejection.EntrySemanticsViolation inventoryOpeningMustBeFirstMovement(
      AccountCode accountCode) {
    Objects.requireNonNull(accountCode, "accountCode");
    return new PostingRejection.EntrySemanticsViolation(
        "inventory-opening-must-be-first-movement",
        "openingBalances[].accountCode",
        "inventoryAccountCode '%s' already has durable movement history, so it cannot receive an opening balance."
            .formatted(accountCode.value()));
  }

  /** Returns one refusal when opening quantity and carrying cost cannot form an exact pool. */
  static PostingRejection.EntrySemanticsViolation inventoryOpeningCarryingCostInvalid(
      AccountCode accountCode) {
    Objects.requireNonNull(accountCode, "accountCode");
    return new PostingRejection.EntrySemanticsViolation(
        "inventory-opening-carrying-cost-invalid",
        "openingBalances[].amount",
        "inventoryAccountCode '%s' has opening quantity and carrying cost that cannot form a valid exact inventory pool."
            .formatted(accountCode.value()));
  }

  private static String formatMoney(Money amount) {
    return amount.currencyUnit().code() + " " + amount.canonicalDecimal();
  }
}
