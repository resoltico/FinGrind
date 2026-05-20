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

/** Shared human-facing formatting helpers for concise operator output. */
final class CliHumanDisplay {
  private static final DateTimeFormatter HUMAN_INSTANT_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

  private CliHumanDisplay() {}

  static String instant(Instant instant) {
    return HUMAN_INSTANT_FORMATTER.format(Objects.requireNonNull(instant, "instant"));
  }

  static String path(Path path) {
    Path normalizedPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    int segmentCount = normalizedPath.getNameCount();
    if (segmentCount == 0) {
      return normalizedPath.toString();
    }
    if (segmentCount == 1) {
      return normalizedPath.getFileName().toString();
    }
    String tail =
        normalizedPath.getName(segmentCount - 2) + "/" + normalizedPath.getFileName().toString();
    return segmentCount == 2 ? tail : ".../" + tail;
  }

  static String path(PublicPathHint pathHint) {
    return CliPublicPaths.redactedValue(Objects.requireNonNull(pathHint, "pathHint"));
  }

  static String lowerDateBoundary(@Nullable LocalDate effectiveDateFrom) {
    return effectiveDateFrom == null ? "book start" : effectiveDateFrom.toString();
  }

  static String upperDateBoundary(@Nullable LocalDate effectiveDateTo) {
    return effectiveDateTo == null ? "latest committed posting date" : effectiveDateTo.toString();
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
