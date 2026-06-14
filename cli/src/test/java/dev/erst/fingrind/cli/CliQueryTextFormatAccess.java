package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.Money;
import java.nio.file.Path;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Test-only accessors for split query scope text helpers. */
final class CliQueryTextFormatAccess {
  private CliQueryTextFormatAccess() {}

  static String noMatchesLabel(String subjectPlural) {
    return CliQueryScopeText.noMatchesLabel(subjectPlural);
  }

  static String zeroAcrossCurrenciesLabel() {
    return CliQueryScopeText.zeroAcrossCurrenciesLabel();
  }

  static String lowerDateBoundaryMeaning(@Nullable LocalDate effectiveDateFrom) {
    return CliQueryScopeText.lowerDateBoundaryMeaning(effectiveDateFrom);
  }

  static String upperDateBoundaryMeaning(@Nullable LocalDate effectiveDateTo) {
    return CliQueryScopeText.upperDateBoundaryMeaning(effectiveDateTo);
  }

  static String upperDateBoundaryMeaning(
      @Nullable LocalDate selectedEffectiveDateTo, @Nullable LocalDate resolvedEffectiveDateTo) {
    return CliQueryScopeText.upperDateBoundaryMeaning(
        selectedEffectiveDateTo, resolvedEffectiveDateTo);
  }

  static String lowerDateBoundaryLabel(@Nullable LocalDate effectiveDateFrom) {
    return CliQueryScopeText.lowerDateBoundaryLabel(effectiveDateFrom);
  }

  static String upperDateBoundaryLabel(@Nullable LocalDate effectiveDateTo) {
    return CliQueryScopeText.upperDateBoundaryLabel(effectiveDateTo);
  }

  static String upperDateBoundaryLabel(
      @Nullable LocalDate selectedEffectiveDateTo, @Nullable LocalDate resolvedEffectiveDateTo) {
    return CliQueryScopeText.upperDateBoundaryLabel(
        selectedEffectiveDateTo, resolvedEffectiveDateTo);
  }

  static String displayBooleanLabel(boolean value) {
    return CliQueryScopeText.displayBooleanLabel(value);
  }

  static String displayMoney(Money money) {
    return CliQueryScopeText.displayMoney(money);
  }

  static String dateRange(
      @Nullable LocalDate effectiveDateFrom, @Nullable LocalDate effectiveDateTo) {
    return CliQueryScopeText.dateRange(effectiveDateFrom, effectiveDateTo);
  }

  static String absolutePath(Path bookFilePath) {
    return CliQueryScopeText.absolutePath(bookFilePath);
  }
}
