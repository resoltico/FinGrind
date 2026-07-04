package dev.erst.fingrind.core;

/** Canonical built-in book doctrines. */
public final class BookDoctrines {
  public static final BookDoctrine INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE =
      new BookDoctrine(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
          AccountingBasis.CASH,
          AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
          EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
          BookTemplateId.OWNER_MANAGED_SERVICE);

  public static final BookDoctrine INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL =
      new BookDoctrine(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
          AccountingBasis.ACCRUAL,
          AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
          EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
          BookTemplateId.OWNER_MANAGED_SERVICE);

  public static final BookDoctrine INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING =
      new BookDoctrine(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
          AccountingBasis.CASH,
          AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
          EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
          BookTemplateId.OWNER_MANAGED_TRADING);

  public static final BookDoctrine INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL =
      new BookDoctrine(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
          AccountingBasis.ACCRUAL,
          AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
          EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
          BookTemplateId.OWNER_MANAGED_TRADING);

  /** Resolves the canonical built-in doctrine for a published seed template and basis. */
  public static BookDoctrine forTemplateAndBasis(
      BookTemplateId bookTemplateId, AccountingBasis accountingBasis) {
    return switch (java.util.Objects.requireNonNull(bookTemplateId, "bookTemplateId")) {
      case OWNER_MANAGED_SERVICE ->
          switch (java.util.Objects.requireNonNull(accountingBasis, "accountingBasis")) {
            case CASH -> INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE;
            case ACCRUAL -> INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL;
          };
      case OWNER_MANAGED_TRADING ->
          switch (java.util.Objects.requireNonNull(accountingBasis, "accountingBasis")) {
            case CASH -> INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING;
            case ACCRUAL -> INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL;
          };
    };
  }

  private BookDoctrines() {}
}
