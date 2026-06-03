package dev.erst.fingrind.core;

/** Canonical built-in book doctrines. */
public final class BookDoctrines {
  public static final BookDoctrine INTERNAL_MANAGEMENT_OWNER_MANAGED_CASH_SERVICE =
      new BookDoctrine(
          AccountingKernelProfiles.INTERNAL_MANAGEMENT_CASH_BOOKKEEPING_KERNEL,
          AccountingBasis.CASH_BASIS,
          AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
          EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
          BookTemplateId.OWNER_MANAGED_SERVICE_CASH);

  private BookDoctrines() {}
}
