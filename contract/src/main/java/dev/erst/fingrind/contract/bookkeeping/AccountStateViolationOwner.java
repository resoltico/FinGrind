package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountCode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Canonical owner for account-state violation metadata, ordering, and publication. */
enum AccountStateViolationOwner {
  UNKNOWN_ACCOUNT(
      "unknown-account",
      "account-registry",
      "One journal line references an undeclared account.",
      "Declare the missing account before retrying the posting."),
  INACTIVE_ACCOUNT(
      "inactive-account",
      "account-activation",
      "One journal line references an inactive account.",
      "Reactivate the account or replace it with an active posting account before retrying."),
  NON_POSTABLE_ACCOUNT(
      "non-postable-account",
      "account-node-kind",
      "One journal line references a header account that cannot accept direct postings.",
      "Replace the header account with a postable account before retrying."),
  INVENTORY_MOVEMENT_PRECEDES_ACCOUNT_HORIZON(
      "inventory-movement-precedes-account-horizon",
      "inventory-horizon",
      "One request attribute would append an inventory movement before the account's durable movement horizon.",
      "Retry with an effective date on or after the account horizon, or reverse later movements before restating earlier inventory history."),
  INVENTORY_QUANTITY_BELOW_ZERO(
      "inventory-quantity-below-zero",
      "inventory-quantity",
      "One request attribute would drive exact inventory quantity on hand below zero.",
      "Reduce the requested inventory decrease, record the missing inventory acquisition first, or post a corrective inventory increase before retrying."),
  INVENTORY_WRITE_DOWN_EXCEEDS_CARRYING_COST(
      "inventory-write-down-exceeds-carrying-cost",
      "inventory-carrying-cost",
      "One request attribute would reduce inventory carrying cost below zero.",
      "Reduce the requested inventory cost decrease, capitalize the missing cost first, or post a corrective inventory increase before retrying.");

  private static final Map<String, Integer> ORDER_BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  AccountStateViolationOwner::code,
                  owner -> Arrays.asList(values()).indexOf(owner)));

  private static final Comparator<PostingRejection.AccountStateViolation> CANONICAL_ORDER =
      Comparator.<PostingRejection.AccountStateViolation>comparingInt(
              violation -> ORDER_BY_CODE.get(require(violation).code()))
          .thenComparing(violation -> accountCode(violation).value());

  private final String code;
  private final String category;
  private final String description;
  private final String repair;

  AccountStateViolationOwner(String code, String category, String description, String repair) {
    this.code = ContractDescriptorValidation.requireText(code, "code");
    this.category = ContractDescriptorValidation.requireText(category, "category");
    this.description = ContractDescriptorValidation.requireText(description, "description");
    this.repair = ContractDescriptorValidation.requireText(repair, "repair");
  }

  String code() {
    return code;
  }

  String category() {
    return category;
  }

  String repair() {
    return repair;
  }

  String description() {
    return description;
  }

  String field() {
    return "lines[].accountCode";
  }

  static AccountStateViolationOwner require(PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount _ -> UNKNOWN_ACCOUNT;
      case PostingRejection.InactiveAccount _ -> INACTIVE_ACCOUNT;
      case PostingRejection.NonPostableAccount _ -> NON_POSTABLE_ACCOUNT;
      case InventoryMovementPrecedesAccountHorizon _ -> INVENTORY_MOVEMENT_PRECEDES_ACCOUNT_HORIZON;
      case InventoryQuantityBelowZero _ -> INVENTORY_QUANTITY_BELOW_ZERO;
      case InventoryWriteDownExceedsCarryingCost _ -> INVENTORY_WRITE_DOWN_EXCEEDS_CARRYING_COST;
    };
  }

  static AccountCode accountCode(PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount unknownAccount -> unknownAccount.accountCode();
      case PostingRejection.InactiveAccount inactiveAccount -> inactiveAccount.accountCode();
      case PostingRejection.NonPostableAccount nonPostableAccount ->
          nonPostableAccount.accountCode();
      case InventoryMovementPrecedesAccountHorizon horizonViolation ->
          horizonViolation.accountCode();
      case InventoryQuantityBelowZero quantityViolation -> quantityViolation.accountCode();
      case InventoryWriteDownExceedsCarryingCost carryingCostViolation ->
          carryingCostViolation.accountCode();
    };
  }

  static @Nullable String accountNodeKind(PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount _ -> null;
      case PostingRejection.InactiveAccount _ -> null;
      case PostingRejection.NonPostableAccount nonPostableAccount ->
          nonPostableAccount.accountNodeKind().wireValue();
      case InventoryMovementPrecedesAccountHorizon _ -> null;
      case InventoryQuantityBelowZero _ -> null;
      case InventoryWriteDownExceedsCarryingCost _ -> null;
    };
  }

  static String code(PostingRejection.AccountStateViolation violation) {
    return require(violation).code();
  }

  static String field(PostingRejection.AccountStateViolation violation) {
    return switch (Objects.requireNonNull(violation, "violation")) {
      case PostingRejection.UnknownAccount _ -> UNKNOWN_ACCOUNT.field();
      case PostingRejection.InactiveAccount _ -> INACTIVE_ACCOUNT.field();
      case PostingRejection.NonPostableAccount _ -> NON_POSTABLE_ACCOUNT.field();
      case InventoryMovementPrecedesAccountHorizon horizonViolation -> horizonViolation.field();
      case InventoryQuantityBelowZero quantityViolation -> quantityViolation.field();
      case InventoryWriteDownExceedsCarryingCost carryingCostViolation ->
          carryingCostViolation.field();
    };
  }

  static String category(PostingRejection.AccountStateViolation violation) {
    return require(violation).category();
  }

  static String repair(PostingRejection.AccountStateViolation violation) {
    return require(violation).repair();
  }

  static String message(PostingRejection.AccountStateViolation violation) {
    return AccountStateViolationNarrativeSupport.message(violation);
  }

  static List<PostingRejection.AccountStateViolation> inCanonicalOrder(
      List<PostingRejection.AccountStateViolation> violations) {
    return ContractDescriptorValidation.copyList(violations, "violations").stream()
        .sorted(CANONICAL_ORDER)
        .toList();
  }

  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return AccountStateViolationNarrativeSupport.descriptors();
  }

  static String envelopeMessage(List<PostingRejection.AccountStateViolation> violations) {
    return AccountStateViolationNarrativeSupport.envelopeMessage(violations);
  }
}
