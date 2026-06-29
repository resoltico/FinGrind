package dev.erst.fingrind.core;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical lexical contract for persisted and machine-facing date and UTC-instant text. */
public final class CanonicalTemporalText {
  public static final String LOCAL_DATE_PATTERN =
      "^(?:\\d{4})-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])$";
  public static final String MONTH_DAY_PATTERN = "^(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])$";
  public static final String UTC_INSTANT_PATTERN =
      "^(?:\\d{4})-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])T(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(?:\\.\\d{3}(?:\\d{3}(?:\\d{3})?)?)?Z$";

  private static final Pattern UTC_INSTANT_REGEX = Pattern.compile(UTC_INSTANT_PATTERN);
  private static final DateTimeFormatter LOCAL_DATE_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendValue(ChronoField.YEAR, 4)
          .appendLiteral('-')
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendLiteral('-')
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .toFormatter(Locale.ROOT)
          .withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter MONTH_DAY_FORMATTER =
      new DateTimeFormatterBuilder()
          .appendValue(ChronoField.MONTH_OF_YEAR, 2)
          .appendLiteral('-')
          .appendValue(ChronoField.DAY_OF_MONTH, 2)
          .parseDefaulting(ChronoField.YEAR, 2000)
          .toFormatter(Locale.ROOT)
          .withResolverStyle(ResolverStyle.STRICT);

  private CanonicalTemporalText() {}

  /** Parses one canonical {@code YYYY-MM-DD} local date for the named field. */
  public static LocalDate parseLocalDate(String text, String fieldDescription) {
    String normalizedText = requireText(text, fieldDescription);
    String normalizedFieldDescription = requireFieldDescription(fieldDescription);
    try {
      return LocalDate.from(LOCAL_DATE_FORMATTER.parse(normalizedText));
    } catch (DateTimeException exception) {
      throw invalidLocalDate(normalizedFieldDescription, exception);
    }
  }

  /** Parses one canonical {@code MM-DD} month-day for the named field. */
  public static MonthDay parseMonthDay(String text, String fieldDescription) {
    String normalizedText = requireText(text, fieldDescription);
    String normalizedFieldDescription = requireFieldDescription(fieldDescription);
    try {
      return MonthDay.from(MONTH_DAY_FORMATTER.parse(normalizedText));
    } catch (DateTimeException exception) {
      throw invalidMonthDay(normalizedFieldDescription, exception);
    }
  }

  /** Parses one canonical UTC instant for the named field. */
  public static Instant parseUtcInstant(String text, String fieldDescription) {
    String normalizedText = requireText(text, fieldDescription);
    String normalizedFieldDescription = requireFieldDescription(fieldDescription);
    if (!UTC_INSTANT_REGEX.matcher(normalizedText).matches()) {
      throw invalidUtcInstant(normalizedFieldDescription);
    }
    try {
      Instant parsed = Instant.parse(normalizedText);
      if (!formatUtcInstant(parsed).equals(normalizedText)) {
        throw invalidUtcInstant(normalizedFieldDescription);
      }
      return parsed;
    } catch (DateTimeParseException exception) {
      throw invalidUtcInstant(normalizedFieldDescription, exception);
    }
  }

  /** Formats one local date into FinGrind's canonical persisted {@code YYYY-MM-DD} form. */
  public static String formatLocalDate(LocalDate localDate) {
    return Objects.requireNonNull(localDate, "localDate").toString();
  }

  /** Formats one month-day into FinGrind's canonical {@code MM-DD} form. */
  public static String formatMonthDay(MonthDay monthDay) {
    MonthDay validatedMonthDay = Objects.requireNonNull(monthDay, "monthDay");
    return "%02d-%02d"
        .formatted(validatedMonthDay.getMonthValue(), validatedMonthDay.getDayOfMonth());
  }

  /** Formats one UTC instant into FinGrind's canonical persisted ISO-8601 {@code Z} form. */
  public static String formatUtcInstant(Instant instant) {
    return Objects.requireNonNull(instant, "instant").toString();
  }

  /** Reports whether the provided text already matches FinGrind's canonical local-date grammar. */
  public static boolean isCanonicalLocalDate(String text) {
    try {
      parseLocalDate(text, "local date");
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  /** Reports whether the provided text already matches FinGrind's canonical month-day grammar. */
  public static boolean isCanonicalMonthDay(String text) {
    try {
      parseMonthDay(text, "month-day");
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  /** Reports whether the provided text already matches FinGrind's canonical UTC-instant grammar. */
  public static boolean isCanonicalUtcInstant(String text) {
    try {
      parseUtcInstant(text, "UTC instant");
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private static String requireText(String text, String fieldDescription) {
    requireFieldDescription(fieldDescription);
    return Objects.requireNonNull(text, fieldDescription + " text");
  }

  private static String requireFieldDescription(String fieldDescription) {
    Objects.requireNonNull(fieldDescription, "fieldDescription");
    String normalized = fieldDescription.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("fieldDescription must not be blank.");
    }
    return normalized;
  }

  private static IllegalArgumentException invalidLocalDate(
      String fieldDescription, Exception exception) {
    return new IllegalArgumentException(
        "Expected one canonical YYYY-MM-DD local date for " + fieldDescription + ".", exception);
  }

  private static IllegalArgumentException invalidMonthDay(
      String fieldDescription, Exception exception) {
    return new IllegalArgumentException(
        "Expected one canonical MM-DD month-day for " + fieldDescription + ".", exception);
  }

  private static IllegalArgumentException invalidUtcInstant(String fieldDescription) {
    return new IllegalArgumentException(
        "Expected one canonical UTC instant for "
            + fieldDescription
            + " in the form YYYY-MM-DDTHH:MM:SS[.fraction]Z.");
  }

  private static IllegalArgumentException invalidUtcInstant(
      String fieldDescription, Exception exception) {
    return new IllegalArgumentException(
        "Expected one canonical UTC instant for "
            + fieldDescription
            + " in the form YYYY-MM-DDTHH:MM:SS[.fraction]Z.",
        exception);
  }
}
