package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared text-rendering helpers for concise operator output. */
final class CliTextDisplay {
  private static final DateTimeFormatter TEXT_INSTANT_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

  private CliTextDisplay() {}

  static String instant(Instant instant) {
    return TEXT_INSTANT_FORMATTER.format(Objects.requireNonNull(instant, "instant"));
  }

  static String path(Path path) {
    return CliPublicPaths.redactedValue(Objects.requireNonNull(path, "path"));
  }

  static String path(PublicPathHint pathHint) {
    return CliPublicPaths.redactedValue(Objects.requireNonNull(pathHint, "pathHint"));
  }

  /**
   * Renders a canonical machine-path field safely for human-readable output without changing its
   * absolute JSON representation.
   */
  static String serializedAbsolutePath(String absolutePath) {
    return path(Path.of(Objects.requireNonNull(absolutePath, "absolutePath")));
  }

  static String lowerDateBoundary(@Nullable LocalDate effectiveDateFrom) {
    return effectiveDateFrom == null ? "book start" : effectiveDateFrom.toString();
  }

  static String upperDateBoundary(@Nullable LocalDate effectiveDateTo) {
    return effectiveDateTo == null
        ? "latest effective date in the selected book"
        : effectiveDateTo.toString();
  }

  static String resolvedUpperDateBoundary(
      @Nullable LocalDate selectedEffectiveDateTo, @Nullable LocalDate resolvedEffectiveDateTo) {
    if (selectedEffectiveDateTo != null) {
      return selectedEffectiveDateTo.toString();
    }
    if (resolvedEffectiveDateTo != null) {
      return resolvedEffectiveDateTo + " (latest effective date in the selected book)";
    }
    return "no postings in selected book";
  }

  static String dateRange(
      @Nullable LocalDate effectiveDateFrom, @Nullable LocalDate effectiveDateTo) {
    return lowerDateBoundary(effectiveDateFrom) + " to " + upperDateBoundary(effectiveDateTo);
  }

  static String wireLabel(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(wireValue.split("_+"))
        .map(token -> token.substring(0, 1) + token.substring(1).toLowerCase(java.util.Locale.ROOT))
        .collect(java.util.stream.Collectors.joining(" "));
  }
}
