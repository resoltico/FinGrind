package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.core.Money;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

/** Shared formatting helpers for FinGrind PDF reports. */
final class PdfValueFormatter {
  private PdfValueFormatter() {}

  static String absolutePath(Path path) {
    return path.toAbsolutePath().normalize().toString();
  }

  static String displayMoney(Money money) {
    return displayAmount(money.amount());
  }

  static String displayAmount(BigDecimal amount) {
    return amount.stripTrailingZeros().toPlainString();
  }

  static String optionalDate(Optional<LocalDate> date) {
    return date.map(LocalDate::toString).orElse("(current)");
  }

  static String optionalDateRange(Optional<LocalDate> from, Optional<LocalDate> to) {
    String lower = from.map(LocalDate::toString).orElse("(start)");
    String upper = to.map(LocalDate::toString).orElse("(current)");
    return lower + " to " + upper;
  }

  static String effectiveDateRange(EffectiveDateRange range) {
    return optionalDateRange(range.effectiveDateFrom(), range.effectiveDateTo());
  }

  static String reversalTarget(PostingFact postingFact) {
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse("(direct)");
  }
}
