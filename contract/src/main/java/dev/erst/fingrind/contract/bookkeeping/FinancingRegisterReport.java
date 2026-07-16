package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import java.util.List;
import java.util.Objects;

/** Reconciliation register for durable financing principal and interest state. */
public record FinancingRegisterReport(BookIdentity bookIdentity, List<FinancingRegisterRow> rows) {
  /** Validates one published financing register. */
  public FinancingRegisterReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
  }
}
