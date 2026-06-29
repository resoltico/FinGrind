package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;
import java.util.Objects;

/** One cash-flow statement section grouped by owned cash-flow classification doctrine. */
public record CashFlowSection(
    CashFlowSectionKind sectionKind, List<CashFlowRow> rows, List<CurrencyBalance> totals) {
  /** Validates one cash-flow statement section. */
  public CashFlowSection {
    Objects.requireNonNull(sectionKind, "sectionKind");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
    totals = ContractDescriptorValidation.copyList(totals, "totals");
  }
}
