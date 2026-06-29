package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Shared validation for bounded comparative report contracts. */
final class BookkeepingComparativeReportValidation {
  private BookkeepingComparativeReportValidation() {}

  static StatementWindow requireStatementWindow(
      BookIdentity bookIdentity,
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    return new StatementWindow(
        bookIdentity,
        effectiveDateFrom,
        effectiveDateTo,
        comparativeEffectiveDateRange,
        postingCoverage);
  }

  static ComparativeCurrencyBalances copyComparativeCurrencyBalances(
      List<CurrencyBalance> openingTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingTotals,
      List<CurrencyBalance> comparativeOpeningTotals,
      List<CurrencyBalance> comparativeMovementTotals,
      List<CurrencyBalance> comparativeClosingTotals) {
    return new ComparativeCurrencyBalances(
        ContractDescriptorValidation.copyList(openingTotals, "openingTotals"),
        ContractDescriptorValidation.copyList(movementTotals, "movementTotals"),
        ContractDescriptorValidation.copyList(closingTotals, "closingTotals"),
        ContractDescriptorValidation.copyList(comparativeOpeningTotals, "comparativeOpeningTotals"),
        ContractDescriptorValidation.copyList(
            comparativeMovementTotals, "comparativeMovementTotals"),
        ContractDescriptorValidation.copyList(
            comparativeClosingTotals, "comparativeClosingTotals"));
  }

  record StatementWindow(
      BookIdentity bookIdentity,
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage) {}

  record ComparativeCurrencyBalances(
      List<CurrencyBalance> openingTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingTotals,
      List<CurrencyBalance> comparativeOpeningTotals,
      List<CurrencyBalance> comparativeMovementTotals,
      List<CurrencyBalance> comparativeClosingTotals) {}
}
