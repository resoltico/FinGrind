package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.jspecify.annotations.Nullable;

/** Shared temporal formatting helpers for FinGrind PDF reports. */
final class PdfTemporalValueFormatter {
  private static final DateTimeFormatter TEXT_INSTANT_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

  private PdfTemporalValueFormatter() {}

  static String instant(Instant instant) {
    return TEXT_INSTANT_FORMATTER.format(instant);
  }

  static String optionalDate(@Nullable LocalDate date) {
    return date == null ? "current book horizon" : date.toString();
  }

  static String optionalDateRange(@Nullable LocalDate from, @Nullable LocalDate to) {
    String lower = from == null ? "book start" : from.toString();
    String upper = to == null ? "current book horizon" : to.toString();
    return lower + " to " + upper;
  }

  static String effectiveDateRange(EffectiveDateRange range) {
    return optionalDateRange(
        range.effectiveDateFrom().orElse(null), range.effectiveDateTo().orElse(null));
  }

  static String comparativeRange(EffectiveDateRange range) {
    return range.effectiveDateFrom().isEmpty() && range.effectiveDateTo().isEmpty()
        ? "(none)"
        : effectiveDateRange(range);
  }
}
