package dev.erst.fingrind.core;

import java.util.Objects;

/** Human-facing labels for one protected book's doctrine and setup posture. */
public final class BookDoctrineDisplay {
  private BookDoctrineDisplay() {}

  /** Returns one human-facing label for the selected accounting kernel profile. */
  public static String accountingKernel(AccountingKernelProfileId accountingKernelProfileId) {
    String wireValue =
        Objects.requireNonNull(accountingKernelProfileId, "accountingKernelProfileId").value();
    if ("internal-management-bookkeeping-kernel".equals(wireValue)) {
      return "Internal management bookkeeping";
    }
    return wireValue;
  }

  /** Returns one human-facing label for the selected accounting basis. */
  public static String accountingBasis(AccountingBasis accountingBasis) {
    return switch (Objects.requireNonNull(accountingBasis, "accountingBasis")) {
      case CASH_BASIS -> "Cash basis";
    };
  }

  /** Returns one human-facing label for the selected accounting framework posture. */
  public static String accountingFrameworkPosition(
      AccountingFrameworkPosition accountingFrameworkPosition) {
    return switch (Objects.requireNonNull(
        accountingFrameworkPosition, "accountingFrameworkPosition")) {
      case NON_STATUTORY_INTERNAL_MANAGEMENT -> "Non-statutory internal management";
    };
  }

  /** Returns one human-facing label for the selected entity form. */
  public static String entityForm(EntityForm entityForm) {
    return switch (Objects.requireNonNull(entityForm, "entityForm")) {
      case OWNER_MANAGED_SINGLE_ENTITY -> "Owner-managed single entity";
    };
  }

  /** Returns one human-facing label for the selected book template. */
  public static String bookTemplate(BookTemplateId bookTemplateId) {
    return switch (Objects.requireNonNull(bookTemplateId, "bookTemplateId")) {
      case OWNER_MANAGED_SERVICE -> "Owner-managed service starter chart";
    };
  }
}
