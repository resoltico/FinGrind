package dev.erst.fingrind.cli;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Shared section and indentation helpers for discovery-oriented text renderers. */
final class CliDiscoveryTextSupport {
  static final int TEXT_WRAP_WIDTH = 96;

  private CliDiscoveryTextSupport() {}

  static String joinSections(String... sections) {
    return Arrays.stream(sections)
        .filter(section -> !section.isBlank())
        .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
  }

  static String section(String title, String body) {
    return title
        + System.lineSeparator()
        + "-".repeat(title.length())
        + System.lineSeparator()
        + body;
  }

  static String indent(String text, String prefix) {
    return text.lines()
        .map(line -> prefix + line)
        .collect(Collectors.joining(System.lineSeparator()));
  }
}
