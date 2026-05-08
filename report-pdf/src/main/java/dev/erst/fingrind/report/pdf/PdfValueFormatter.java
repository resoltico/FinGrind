package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Shared formatting helpers for FinGrind PDF reports. */
final class PdfValueFormatter {
  private PdfValueFormatter() {}

  static String displayMoney(Money money) {
    return displayAmount(money.amount());
  }

  static String displayAmount(BigDecimal amount) {
    return amount.stripTrailingZeros().toPlainString();
  }

  static String optionalDate(@Nullable LocalDate date) {
    return date == null ? "(current)" : date.toString();
  }

  static String optionalDateRange(@Nullable LocalDate from, @Nullable LocalDate to) {
    String lower = from == null ? "(start)" : from.toString();
    String upper = to == null ? "(current)" : to.toString();
    return lower + " to " + upper;
  }

  static String effectiveDateRange(EffectiveDateRange range) {
    return optionalDateRange(
        range.effectiveDateFrom().orElse(null), range.effectiveDateTo().orElse(null));
  }

  static String reversalTarget(PostingFact postingFact) {
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse("(direct)");
  }
}
