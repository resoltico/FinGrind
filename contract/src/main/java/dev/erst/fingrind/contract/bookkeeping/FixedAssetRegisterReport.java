package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reconciliation register for fixed-asset cost, depreciation, carrying value, and disposal state.
 */
public record FixedAssetRegisterReport(
    BookIdentity bookIdentity,
    Optional<LocalDate> effectiveDateAsOf,
    List<FixedAssetRegisterRow> rows) {
  /** Validates one published fixed-asset register. */
  public FixedAssetRegisterReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
  }
}
