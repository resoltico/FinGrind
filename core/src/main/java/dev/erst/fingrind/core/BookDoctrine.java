package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical doctrine owner for one protected book's accounting posture. */
public record BookDoctrine(
    AccountingKernelProfileId accountingKernelProfileId,
    AccountingBasis accountingBasis,
    AccountingFrameworkPosition accountingFrameworkPosition,
    EntityForm entityForm,
    BookTemplateId bookTemplateId) {
  /** Validates one book doctrine. */
  public BookDoctrine {
    Objects.requireNonNull(accountingKernelProfileId, "accountingKernelProfileId");
    Objects.requireNonNull(accountingBasis, "accountingBasis");
    Objects.requireNonNull(accountingFrameworkPosition, "accountingFrameworkPosition");
    Objects.requireNonNull(entityForm, "entityForm");
    Objects.requireNonNull(bookTemplateId, "bookTemplateId");
  }
}
