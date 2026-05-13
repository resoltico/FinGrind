package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Canonical income statement for one bounded reporting period. */
public record IncomeStatementReport(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    List<IncomeStatementSection> sections,
    List<CurrencyBalance> netIncomeTotals) {
  /** Validates one income-statement report. */
  public IncomeStatementReport {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    sections = ContractDescriptorValidation.copyList(sections, "sections");
    netIncomeTotals = ContractDescriptorValidation.copyList(netIncomeTotals, "netIncomeTotals");
  }
}
