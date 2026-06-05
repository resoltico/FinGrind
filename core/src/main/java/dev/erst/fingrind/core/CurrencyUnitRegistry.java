package dev.erst.fingrind.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/** Loads FinGrind's pinned currency-unit snapshot from the repository-owned registry resource. */
final class CurrencyUnitRegistry {
  private static final String RESOURCE_PATH = "/dev/erst/fingrind/core/currency-unit-registry.csv";
  private static final Map<String, Integer> SCALE_BY_CODE = loadScaleByCode();

  private CurrencyUnitRegistry() {}

  static OptionalInt findMinorUnitScale(String code) {
    Integer scale = SCALE_BY_CODE.get(code);
    return scale == null ? OptionalInt.empty() : OptionalInt.of(scale);
  }

  static Map<String, Integer> snapshot() {
    return SCALE_BY_CODE;
  }

  static Map<String, Integer> loadScaleByCode() {
    return loadScaleByCode(
        CurrencyUnitRegistry.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }

  static Map<String, Integer> loadScaleByCode(InputStream stream, String resourcePath) {
    if (stream == null) {
      throw new IllegalStateException(
          "Missing FinGrind currency-unit registry resource: " + resourcePath + ".");
    }
    try (InputStream ownedStream = stream;
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(ownedStream, StandardCharsets.UTF_8))) {
      return parseScaleByCode(reader);
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to load FinGrind currency-unit registry resource.", exception);
    }
  }

  static Map<String, Integer> parseScaleByCode(BufferedReader reader) throws IOException {
    Objects.requireNonNull(reader, "reader");
    var scaleByCode = new LinkedHashMap<String, Integer>();
    String previousCode = null;
    int lineNumber = 0;
    String line = reader.readLine();
    while (line != null) {
      lineNumber++;
      ParsedCurrencyScale parsedRecord = parseCurrencyScaleRecord(line, lineNumber);
      if (previousCode != null) {
        assertStrictlySorted(previousCode, parsedRecord.code());
      }
      scaleByCode.put(parsedRecord.code(), parsedRecord.scale());
      previousCode = parsedRecord.code();
      line = reader.readLine();
    }
    if (scaleByCode.isEmpty()) {
      throw new IllegalStateException("Currency-unit registry must not be empty.");
    }
    return Collections.unmodifiableMap(scaleByCode);
  }

  private static ParsedCurrencyScale parseCurrencyScaleRecord(String line, int lineNumber) {
    if (line.isEmpty()) {
      throw new IllegalStateException(blankLineMessage(lineNumber));
    }
    int commaIndex = line.indexOf(',');
    if (commaIndex <= 0 || commaIndex != line.lastIndexOf(',')) {
      throw new IllegalStateException(invalidRecordFormatMessage(lineNumber));
    }
    String code = line.substring(0, commaIndex);
    if (!code.matches("[A-Z]{3}")) {
      throw new IllegalStateException(unsupportedCodeTextMessage(lineNumber, code));
    }
    return new ParsedCurrencyScale(
        code, parseScale(line.substring(commaIndex + 1), lineNumber, code));
  }

  private static void assertStrictlySorted(String previousCode, String code) {
    if (code.compareTo(previousCode) <= 0) {
      throw new IllegalStateException(sortedWithoutDuplicatesMessage(code));
    }
  }

  private static int parseScale(String scaleText, int lineNumber, String code) {
    Objects.requireNonNull(scaleText, "scaleText");
    int scale;
    try {
      scale = Integer.parseInt(scaleText);
    } catch (NumberFormatException exception) {
      throw new IllegalStateException(
          "Currency-unit registry line " + lineNumber + " has an invalid scale for " + code + ".",
          exception);
    }
    if (scale < 0 || scale > CurrencyUnit.maxSupportedMinorUnitScale()) {
      throw new IllegalStateException(
          "Currency-unit registry line "
              + lineNumber
              + " publishes unsupported scale "
              + scale
              + " for "
              + code
              + ".");
    }
    return scale;
  }

  private static String blankLineMessage(int lineNumber) {
    return "Currency-unit registry line %s must not be blank.".formatted(lineNumber);
  }

  private static String invalidRecordFormatMessage(int lineNumber) {
    return "Currency-unit registry line %s must use one CODE,SCALE record format."
        .formatted(lineNumber);
  }

  private static String unsupportedCodeTextMessage(int lineNumber, String code) {
    return "Currency-unit registry line %s has unsupported code text: %s."
        .formatted(lineNumber, code);
  }

  private static String sortedWithoutDuplicatesMessage(String code) {
    return "Currency-unit registry must remain strictly sorted without duplicates: %s."
        .formatted(code);
  }

  private record ParsedCurrencyScale(String code, int scale) {}
}
