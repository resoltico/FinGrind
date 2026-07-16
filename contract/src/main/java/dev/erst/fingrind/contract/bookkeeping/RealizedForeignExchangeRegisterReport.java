package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import java.util.List;
import java.util.Objects;

/**
 * Reconciliation register for foreign-currency receivable carrying and realized settlement results.
 */
public record RealizedForeignExchangeRegisterReport(
    BookIdentity bookIdentity, List<RealizedForeignExchangeRegisterRow> rows) {
  /** Validates one published realized foreign-exchange register. */
  public RealizedForeignExchangeRegisterReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
  }
}
