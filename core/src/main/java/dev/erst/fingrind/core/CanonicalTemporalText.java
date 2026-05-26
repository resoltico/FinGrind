package dev.erst.fingrind.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical lexical contract for persisted and machine-facing date and UTC-instant text. */
public final class CanonicalTemporalText {
  public static final String LOCAL_DATE_PATTERN =
      "^(?:\\d{4})-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])$";
  public static final String UTC_INSTANT_PATTERN =
      "^(?:\\d{4})-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])T(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(?:\\.\\d{3}(?:\\d{3}(?:\\d{3})?)?)?Z$";

  private static final Pattern LOCAL_DATE_REGEX = Pattern.compile(LOCAL_DATE_PATTERN);
  private static final Pattern UTC_INSTANT_REGEX = Pattern.compile(UTC_INSTANT_PATTERN);

  private CanonicalTemporalText() {}

  /** Parses one canonical {@code YYYY-MM-DD} local date for the named field. */
  public static LocalDate parseLocalDate(String text, String fieldDescription) {
    String normalizedText = requireText(text, fieldDescription);
    String normalizedFieldDescription = requireFieldDescription(fieldDescription);
    if (!LOCAL_DATE_REGEX.matcher(normalizedText).matches()) {
      throw invalidLocalDate(normalizedFieldDescription);
    }
    try {
      return LocalDate.parse(normalizedText);
    } catch (DateTimeParseException exception) {
      throw invalidLocalDate(normalizedFieldDescription, exception);
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

  private static IllegalArgumentException invalidLocalDate(String fieldDescription) {
    return new IllegalArgumentException(
        "Expected one canonical YYYY-MM-DD local date for " + fieldDescription + ".");
  }

  private static IllegalArgumentException invalidLocalDate(
      String fieldDescription, Exception exception) {
    return new IllegalArgumentException(
        "Expected one canonical YYYY-MM-DD local date for " + fieldDescription + ".", exception);
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
