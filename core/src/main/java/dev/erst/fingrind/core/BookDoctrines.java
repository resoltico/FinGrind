package dev.erst.fingrind.core;

/** Canonical built-in book doctrines. */
public final class BookDoctrines {
  public static final BookDoctrine INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE =
      new BookDoctrine(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
          AccountingBasis.CASH,
          AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
          EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
          BookTemplateId.OWNER_MANAGED_SERVICE,
          null);

  public static final BookDoctrine INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL =
      new BookDoctrine(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
          AccountingBasis.ACCRUAL,
          AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
          EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
          BookTemplateId.OWNER_MANAGED_SERVICE,
          null);

  public static final BookDoctrine INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING =
      new BookDoctrine(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
          AccountingBasis.CASH,
          AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
          EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
          BookTemplateId.OWNER_MANAGED_TRADING,
          InventoryCostingDoctrine.WEIGHTED_AVERAGE);

  public static final BookDoctrine INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL =
      new BookDoctrine(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
          AccountingBasis.ACCRUAL,
          AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
          EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
          BookTemplateId.OWNER_MANAGED_TRADING,
          InventoryCostingDoctrine.WEIGHTED_AVERAGE);

  /** Resolves the canonical built-in doctrine for a published seed template and basis. */
  public static BookDoctrine forTemplateAndBasis(
      BookTemplateId bookTemplateId,
      AccountingBasis accountingBasis,
      @org.jspecify.annotations.Nullable InventoryCostingDoctrine inventoryCostingDoctrine) {
    BookTemplateId requiredBookTemplateId =
        java.util.Objects.requireNonNull(bookTemplateId, "bookTemplateId");
    AccountingBasis requiredAccountingBasis =
        java.util.Objects.requireNonNull(accountingBasis, "accountingBasis");
    return new BookDoctrine(
        AccountingKernelProfiles.INTERNAL_MANAGEMENT_BOOKKEEPING_KERNEL,
        requiredAccountingBasis,
        AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
        EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
        requiredBookTemplateId,
        inventoryCostingDoctrine);
  }

  private BookDoctrines() {}
}
