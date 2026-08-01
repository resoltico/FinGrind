package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import java.util.Arrays;
import java.util.List;

/** Canonical narrative and descriptor publisher for account-state violations. */
final class AccountStateViolationNarrativeSupport {
  private static final List<FieldDescriptor> DETAIL_FIELDS =
      List.of(
          detailField("code", "Stable account-state violation code."),
          detailField(
              "field",
              "Request-field path for the posting attribute whose account-state issue failed."),
          detailField("message", "Canonical plain-language explanation for this one violation."),
          detailField("category", "Stable repair category owned by this violation code."),
          detailField("repair", "Canonical action-first repair guidance for this one violation."),
          detailField("accountCode", "Account code that caused this one account-state violation."),
          detailField(
              "accountNodeKind",
              "Declared accountNodeKind when the account exists but cannot accept direct postings."));

  private AccountStateViolationNarrativeSupport() {}

  static String message(PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount unknownAccount ->
          "Journal line references undeclared account '%s'."
              .formatted(unknownAccount.accountCode().value());
      case PostingRejection.InactiveAccount inactiveAccount ->
          "Journal line references inactive account '%s'."
              .formatted(inactiveAccount.accountCode().value());
      case PostingRejection.NonPostableAccount nonPostableAccount ->
          "Journal line references header account '%s', declared as '%s', which cannot accept direct postings."
              .formatted(
                  nonPostableAccount.accountCode().value(),
                  nonPostableAccount.accountNodeKind().wireValue());
      case InventoryMovementPrecedesAccountHorizon horizonViolation ->
          inventoryMovementPrecedesAccountHorizonMessage(horizonViolation);
      case InventoryQuantityBelowZero quantityViolation ->
          inventoryQuantityBelowZeroMessage(quantityViolation);
      case InventoryWriteDownExceedsCarryingCost carryingCostViolation ->
          inventoryWriteDownExceedsCarryingCostMessage(carryingCostViolation);
    };
  }

  static List<RejectionDescriptor> descriptors() {
    return Arrays.stream(AccountStateViolationOwner.values())
        .map(AccountStateViolationNarrativeSupport::descriptor)
        .toList();
  }

  static String envelopeMessage(List<PostingRejection.AccountStateViolation> violations) {
    int issueCount = AccountStateViolationOwner.inCanonicalOrder(violations).size();
    return issueCount == 1
        ? "Posting rejected with 1 account-state issue."
        : "Posting rejected with %d account-state issues.".formatted(issueCount);
  }

  private static RejectionDescriptor descriptor(AccountStateViolationOwner owner) {
    return new RejectionDescriptor(
        owner.code(),
        FailureCategory.DOMAIN_SEMANTIC,
        2,
        owner.description(),
        DETAIL_FIELDS,
        List.of());
  }

  private static String inventoryMovementPrecedesAccountHorizonMessage(
      InventoryMovementPrecedesAccountHorizon violation) {
    return "Request field '%s' would record an inventory movement on '%s' for account '%s', but this account already has durable inventory history through '%s'."
        .formatted(
            violation.field(),
            violation.attemptedEffectiveDate(),
            violation.accountCode().value(),
            violation.accountHorizonEffectiveDate());
  }

  private static String inventoryQuantityBelowZeroMessage(InventoryQuantityBelowZero violation) {
    return "Request field '%s' reduces inventory account '%s' on '%s' by %s while only %s is on hand; shortfall would be %s."
        .formatted(
            violation.field(),
            violation.accountCode().value(),
            violation.effectiveDate(),
            quantityText(violation.requestedDecreaseQuantity()),
            quantityText(violation.quantityOnHand()),
            quantityText(violation.resultingShortfallQuantity()));
  }

  private static String inventoryWriteDownExceedsCarryingCostMessage(
      InventoryWriteDownExceedsCarryingCost violation) {
    return "Request field '%s' reduces inventory account '%s' on '%s' by %s while only %s of carrying cost is on hand; shortfall would be %s."
        .formatted(
            violation.field(),
            violation.accountCode().value(),
            violation.effectiveDate(),
            monetaryText(violation.requestedCostDecrease()),
            monetaryText(violation.carryingCostOnHand()),
            monetaryText(violation.resultingCostShortfall()));
  }

  private static String monetaryText(Money amount) {
    return "%s %s".formatted(amount.currencyUnit().code(), amount.canonicalDecimal());
  }

  private static String quantityText(Quantity quantity) {
    return quantity.canonicalDecimal();
  }

  private static FieldDescriptor detailField(String name, String description) {
    return new FieldDescriptor(name, description);
  }
}
