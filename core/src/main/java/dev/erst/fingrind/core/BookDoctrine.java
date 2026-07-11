package dev.erst.fingrind.core;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical doctrine owner for one protected book's accounting posture. */
public record BookDoctrine(
    AccountingKernelProfileId accountingKernelProfileId,
    AccountingBasis accountingBasis,
    AccountingFrameworkPosition accountingFrameworkPosition,
    EntityForm entityForm,
    BookTemplateId bookTemplateId,
    @Nullable InventoryCostingDoctrine inventoryCostingDoctrine) {
  /** Validates one book doctrine. */
  public BookDoctrine {
    Objects.requireNonNull(accountingKernelProfileId, "accountingKernelProfileId");
    Objects.requireNonNull(accountingBasis, "accountingBasis");
    Objects.requireNonNull(accountingFrameworkPosition, "accountingFrameworkPosition");
    Objects.requireNonNull(entityForm, "entityForm");
    Objects.requireNonNull(bookTemplateId, "bookTemplateId");
    if (bookTemplateId == BookTemplateId.OWNER_MANAGED_TRADING
        && inventoryCostingDoctrine == null) {
      throw new IllegalArgumentException(
          "Trading book doctrines require one inventoryCostingDoctrine.");
    }
    if (bookTemplateId == BookTemplateId.OWNER_MANAGED_SERVICE
        && inventoryCostingDoctrine != null) {
      throw new IllegalArgumentException(
          "Service book doctrines must not declare an inventoryCostingDoctrine.");
    }
  }
}
