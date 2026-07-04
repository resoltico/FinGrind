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
  INVENTORY_BALANCE_BELOW_ZERO(
      "inventory-balance-below-zero",
      "inventory-balance",
      "One request attribute would create or deepen a negative inventory carrying balance.",
      "Reduce the requested inventory decrease, record the missing inventory acquisition first, or post a corrective inventory increase before retrying.");

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

  private static final List<ContractResponse.FieldDescriptor> DETAIL_FIELDS =
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

  String field() {
    return "lines[].accountCode";
  }

  private ContractResponse.RejectionDescriptor descriptor() {
    return new ContractResponse.RejectionDescriptor(code, description, DETAIL_FIELDS, List.of());
  }

  static AccountStateViolationOwner require(PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount _ -> UNKNOWN_ACCOUNT;
      case PostingRejection.InactiveAccount _ -> INACTIVE_ACCOUNT;
      case PostingRejection.NonPostableAccount _ -> NON_POSTABLE_ACCOUNT;
      case InventoryBalanceBelowZero _ -> INVENTORY_BALANCE_BELOW_ZERO;
    };
  }

  static AccountCode accountCode(PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount unknownAccount -> unknownAccount.accountCode();
      case PostingRejection.InactiveAccount inactiveAccount -> inactiveAccount.accountCode();
      case PostingRejection.NonPostableAccount nonPostableAccount ->
          nonPostableAccount.accountCode();
      case InventoryBalanceBelowZero inventoryBalanceBelowZero ->
          inventoryBalanceBelowZero.accountCode();
    };
  }

  static @Nullable String accountNodeKind(PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount _ -> null;
      case PostingRejection.InactiveAccount _ -> null;
      case PostingRejection.NonPostableAccount nonPostableAccount ->
          nonPostableAccount.accountNodeKind().wireValue();
      case InventoryBalanceBelowZero _ -> null;
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
      case InventoryBalanceBelowZero inventoryBalanceBelowZero -> inventoryBalanceBelowZero.field();
    };
  }

  static String category(PostingRejection.AccountStateViolation violation) {
    return require(violation).category();
  }

  static String repair(PostingRejection.AccountStateViolation violation) {
    return require(violation).repair();
  }

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
      case InventoryBalanceBelowZero inventoryBalanceBelowZero ->
          inventoryBalanceMessage(inventoryBalanceBelowZero);
    };
  }

  private static String inventoryBalanceMessage(
      InventoryBalanceBelowZero inventoryBalanceBelowZero) {
    String requestedDecrease = monetaryText(inventoryBalanceBelowZero.requestedDecreaseAmount());
    String currentBalance = monetaryText(inventoryBalanceBelowZero.currentNetAmount());
    String resultingBalance = monetaryText(inventoryBalanceBelowZero.resultingCreditBalance());
    if (inventoryBalanceBelowZero.currentBalanceSide()
        == dev.erst.fingrind.core.BalanceSide.CREDIT) {
      return "Request field '%s' reduces inventory account '%s' on '%s' by %s while the account already carries a credit balance of %s, deepening it to %s credit."
          .formatted(
              inventoryBalanceBelowZero.field(),
              inventoryBalanceBelowZero.accountCode().value(),
              inventoryBalanceBelowZero.effectiveDate(),
              requestedDecrease,
              currentBalance,
              resultingBalance);
    }
    return "Request field '%s' reduces inventory account '%s' on '%s' by %s, but only %s is on hand; resulting balance would be %s credit."
        .formatted(
            inventoryBalanceBelowZero.field(),
            inventoryBalanceBelowZero.accountCode().value(),
            inventoryBalanceBelowZero.effectiveDate(),
            requestedDecrease,
            currentBalance,
            resultingBalance);
  }

  private static String monetaryText(dev.erst.fingrind.core.Money amount) {
    return "%s %s".formatted(amount.currencyUnit().code(), amount.canonicalDecimal());
  }

  static List<PostingRejection.AccountStateViolation> inCanonicalOrder(
      List<PostingRejection.AccountStateViolation> violations) {
    return ContractDescriptorValidation.copyList(violations, "violations").stream()
        .sorted(CANONICAL_ORDER)
        .toList();
  }

  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Arrays.stream(values()).map(AccountStateViolationOwner::descriptor).toList();
  }

  static String envelopeMessage(List<PostingRejection.AccountStateViolation> violations) {
    int issueCount = inCanonicalOrder(violations).size();
    return issueCount == 1
        ? "Posting rejected with 1 account-state issue."
        : "Posting rejected with %d account-state issues.".formatted(issueCount);
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(
        ContractDescriptorValidation.requireText(name, "name"),
        ContractDescriptorValidation.requireText(description, "description"));
  }
}
