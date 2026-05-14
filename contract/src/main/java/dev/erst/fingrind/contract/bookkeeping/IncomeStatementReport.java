package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Canonical income statement for one bounded reporting period. */
public record IncomeStatementReport(
    BookIdentity bookIdentity,
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    EffectiveDateRange comparativeEffectiveDateRange,
    PostingCoverage postingCoverage,
    List<IncomeStatementSection> sections,
    List<CurrencyBalance> netIncomeTotals,
    List<IncomeStatementSection> comparativeSections,
    List<CurrencyBalance> comparativeNetIncomeTotals) {
  /** Validates one income-statement report. */
  public IncomeStatementReport {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    sections = ContractDescriptorValidation.copyList(sections, "sections");
    netIncomeTotals = ContractDescriptorValidation.copyList(netIncomeTotals, "netIncomeTotals");
    comparativeSections =
        ContractDescriptorValidation.copyList(comparativeSections, "comparativeSections");
    comparativeNetIncomeTotals =
        ContractDescriptorValidation.copyList(
            comparativeNetIncomeTotals, "comparativeNetIncomeTotals");
  }
}
