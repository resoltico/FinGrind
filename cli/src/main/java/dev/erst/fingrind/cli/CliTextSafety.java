package dev.erst.fingrind.cli;

import java.util.Objects;

/** Escapes untrusted human text before it becomes terminal layout content. */
final class CliTextSafety {
  private CliTextSafety() {}

  static String visible(String value) {
    String checked = Objects.requireNonNull(value, "value");
    StringBuilder escaped = new StringBuilder(checked.length());
    checked.codePoints().forEach(codePoint -> appendVisible(escaped, codePoint));
    return escaped.toString();
  }

  private static void appendVisible(StringBuilder escaped, int codePoint) {
    if (codePoint <= 0x1F
        || (codePoint >= 0x7F && codePoint <= 0x9F)
        || (codePoint >= 0x202A && codePoint <= 0x202E)
        || (codePoint >= 0x2066 && codePoint <= 0x2069)) {
      escaped.append(String.format("\\u%04X", codePoint));
      return;
    }
    escaped.appendCodePoint(codePoint);
  }
}
