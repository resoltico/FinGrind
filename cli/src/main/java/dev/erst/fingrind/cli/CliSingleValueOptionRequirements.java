package dev.erst.fingrind.cli;

import java.time.LocalDate;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Guards single-value CLI options against duplicates while parsing typed command arguments. */
final class CliSingleValueOptionRequirements {
  private CliSingleValueOptionRequirements() {}

  static String requireSingleTextOption(
      @Nullable String currentValue, String optionName, ListIterator<String> argumentIterator) {
    if (currentValue != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.requireValue(argumentIterator, optionName);
  }

  static LocalDate requireSingleDateOption(
      @Nullable LocalDate currentValue, String optionName, ListIterator<String> argumentIterator) {
    if (currentValue != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.parseLocalDateOption(
        CliOptionValues.requireValue(argumentIterator, optionName), optionName);
  }

  static Integer requireSingleIntegerOption(
      @Nullable Integer currentValue, String optionName, ListIterator<String> argumentIterator) {
    if (currentValue != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.parseIntegerOption(
        CliOptionValues.requireValue(argumentIterator, optionName), optionName);
  }
}
