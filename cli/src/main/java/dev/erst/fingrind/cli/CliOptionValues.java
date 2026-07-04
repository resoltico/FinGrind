package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FiscalYearStart;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ListIterator;
import java.util.Objects;

/** Parses scalar CLI option values into canonical runtime types. */
final class CliOptionValues {
  private CliOptionValues() {}

  static int parseIntegerOption(String rawValue, String optionName) {
    try {
      return Integer.parseInt(rawValue);
    } catch (NumberFormatException exception) {
      throw CliArgumentValueParser.invalid(
          optionName, "Option must be an integer: " + optionName, exception);
    }
  }

  static int parseYearOption(String rawValue, String optionName) {
    int parsedYear = parseIntegerOption(rawValue, optionName);
    try {
      java.time.Year.of(parsedYear);
      return parsedYear;
    } catch (java.time.DateTimeException exception) {
      throw CliArgumentValueParser.invalid(
          optionName, "Option must be one canonical YYYY year: " + optionName, exception);
    }
  }

  static LocalDate parseLocalDateOption(String rawValue, String optionName) {
    try {
      return CanonicalTemporalText.parseLocalDate(rawValue, optionName);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          optionName,
          "Option must be one canonical YYYY-MM-DD local date: " + optionName,
          exception);
    }
  }

  static CurrencyUnit parseCurrencyUnitOption(String rawValue, String optionName) {
    try {
      return CurrencyUnit.of(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          optionName,
          Objects.requireNonNullElse(
              exception.getMessage(),
              "Option must be one supported ISO 4217 currency code: " + optionName),
          exception);
    }
  }

  static BookEntityName parseBookEntityNameOption(String rawValue, String optionName) {
    try {
      return new BookEntityName(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          optionName,
          Objects.requireNonNullElse(exception.getMessage(), "Invalid book entity name."),
          exception);
    }
  }

  static BookTemplateId parseBookTemplateIdOption(String rawValue, String optionName) {
    try {
      return BookTemplateId.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          optionName,
          Objects.requireNonNullElse(
              exception.getMessage(),
              "Option must be one supported book template id: " + optionName),
          exception);
    }
  }

  static AccountingBasis parseAccountingBasisOption(String rawValue, String optionName) {
    try {
      return AccountingBasis.fromWireValue(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          optionName,
          Objects.requireNonNullElse(
              exception.getMessage(),
              "Option must be one supported accounting basis: " + optionName),
          exception);
    }
  }

  static FiscalYearStart parseFiscalYearStartOption(String rawValue, String optionName) {
    try {
      return FiscalYearStart.parse(rawValue);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          optionName,
          Objects.requireNonNullElse(
              exception.getMessage(), "Option must use MM-DD for " + optionName + "."),
          exception);
    }
  }

  static String requireValue(ListIterator<String> argumentIterator, String optionName) {
    if (!argumentIterator.hasNext()) {
      throw CliArgumentValueParser.invalid(optionName, "Missing value for " + optionName + ".");
    }
    return argumentIterator.next();
  }

  static Path requirePathOptionValue(ListIterator<String> argumentIterator, String optionName) {
    return parsePathOption(requireValue(argumentIterator, optionName), optionName);
  }

  static Path parsePathOption(String rawValue, String optionName) {
    try {
      return Path.of(rawValue);
    } catch (InvalidPathException exception) {
      throw CliArgumentValueParser.invalid(
          optionName,
          "Option must be a valid filesystem path for " + optionName + ": " + rawValue,
          exception);
    }
  }
}
