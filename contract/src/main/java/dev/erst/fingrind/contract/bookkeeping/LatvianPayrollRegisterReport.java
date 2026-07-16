package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import java.util.List;
import java.util.Objects;

/** Complete durable Latvian payroll register for one initialized protected book. */
public record LatvianPayrollRegisterReport(
    BookIdentity bookIdentity, List<LatvianPayrollRegisterRow> rows) {
  /** Validates one published payroll register. */
  public LatvianPayrollRegisterReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
  }
}
