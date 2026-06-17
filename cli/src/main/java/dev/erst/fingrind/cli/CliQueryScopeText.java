package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.Money;
import java.nio.file.Path;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Shared query/report text for scope, emptiness, booleans, dates, and paths. */
final class CliQueryScopeText {
  private CliQueryScopeText() {}

  static String noMatchesLabel(String subjectPlural) {
    return "No " + subjectPlural + " matched the selected scope.";
  }

  static String zeroAcrossCurrenciesLabel() {
    return "Zero across all currencies.";
  }

  static String lowerDateBoundaryMeaning(@Nullable LocalDate effectiveDateFrom) {
    return CliTemporalScopeText.lowerDateBoundaryMeaning(effectiveDateFrom);
  }

  static String upperDateBoundaryMeaning(@Nullable LocalDate effectiveDateTo) {
    return CliTemporalScopeText.upperDateBoundaryMeaning(effectiveDateTo);
  }

  static String upperDateBoundaryMeaning(
      @Nullable LocalDate selectedEffectiveDateTo, @Nullable LocalDate resolvedEffectiveDateTo) {
    return CliTemporalScopeText.resolvedUpperDateBoundaryMeaning(
        selectedEffectiveDateTo, resolvedEffectiveDateTo);
  }

  static String lowerDateBoundaryLabel(@Nullable LocalDate effectiveDateFrom) {
    return CliTextDisplay.lowerDateBoundary(effectiveDateFrom);
  }

  static String upperDateBoundaryLabel(@Nullable LocalDate effectiveDateTo) {
    return CliTextDisplay.upperDateBoundary(effectiveDateTo);
  }

  static String upperDateBoundaryLabel(
      @Nullable LocalDate selectedEffectiveDateTo, @Nullable LocalDate resolvedEffectiveDateTo) {
    return CliTextDisplay.resolvedUpperDateBoundary(
        selectedEffectiveDateTo, resolvedEffectiveDateTo);
  }

  static String displayBooleanLabel(boolean value) {
    return value ? "Yes" : "No";
  }

  static String displayMoney(Money money) {
    return CliTextFormat.displayMoney(money);
  }

  static String dateRange(
      @Nullable LocalDate effectiveDateFrom, @Nullable LocalDate effectiveDateTo) {
    return CliTextDisplay.dateRange(effectiveDateFrom, effectiveDateTo);
  }

  static String absolutePath(Path bookFilePath) {
    return CliTextDisplay.path(bookFilePath);
  }
}
