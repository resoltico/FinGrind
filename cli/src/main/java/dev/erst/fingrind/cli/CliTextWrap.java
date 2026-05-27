package dev.erst.fingrind.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Wraps operator-facing CLI text using one canonical line-breaking policy. */
final class CliTextWrap {
  private static final String TEXT_LINE_SEPARATOR = "\n";
  private static final Pattern WRAP_WORD_BOUNDARY = Pattern.compile("\\s+");
  private static final String WRAP_PREFERRED_BREAKS = "/._-";

  private CliTextWrap() {}

  static String wrap(String text, int width) {
    return String.join(TEXT_LINE_SEPARATOR, wrapLines(text, width));
  }

  static String wrapLineBlock(List<String> lines, int width) {
    Objects.requireNonNull(lines, "lines");
    return lines.isEmpty()
        ? ""
        : lines.stream()
            .map(line -> wrap(line, width))
            .collect(Collectors.joining(TEXT_LINE_SEPARATOR));
  }

  static String renderBulletedBlock(List<String> items, int width) {
    Objects.requireNonNull(items, "items");
    return items.isEmpty()
        ? ""
        : items.stream()
            .map(item -> wrapWithPrefix(item, width, "- ", "  "))
            .collect(Collectors.joining(TEXT_LINE_SEPARATOR));
  }

  static String renderLiteralBlock(List<String> lines, String prefix) {
    Objects.requireNonNull(lines, "lines");
    Objects.requireNonNull(prefix, "prefix");
    return lines.isEmpty()
        ? ""
        : lines.stream()
            .map(line -> prefix + Objects.requireNonNull(line, "line"))
            .collect(Collectors.joining(TEXT_LINE_SEPARATOR));
  }

  static List<String> wrapLines(String text, int width) {
    Objects.requireNonNull(text, "text");
    if (width == Integer.MAX_VALUE || text.isBlank()) {
      return text.lines().toList().isEmpty() ? List.of(text) : text.lines().toList();
    }
    List<String> lines = new ArrayList<>();
    for (String sourceLine : text.lines().toList()) {
      lines.addAll(wrapSourceLine(sourceLine, width));
    }
    return List.copyOf(lines);
  }

  static List<String> splitLongToken(String token, int width) {
    if (token.length() <= width) {
      return List.of(token);
    }
    List<String> segments = new ArrayList<>();
    int segmentStart = 0;
    while (true) {
      int remaining = token.length() - segmentStart;
      if (remaining <= width) {
        segments.add(token.substring(segmentStart));
        return List.copyOf(segments);
      }
      int split = preferredSplitIndex(token, segmentStart, width);
      segments.add(token.substring(segmentStart, split));
      segmentStart = split;
    }
  }

  private static String wrapWithPrefix(
      String text, int width, String firstPrefix, String nextPrefix) {
    int firstWidth =
        width == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, width - firstPrefix.length());
    int nextWidth =
        width == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, width - nextPrefix.length());
    List<String> wrappedLines = wrapLines(text, firstWidth);
    StringBuilder builder = new StringBuilder(firstPrefix).append(wrappedLines.getFirst());
    for (int index = 1; index < wrappedLines.size(); index++) {
      builder
          .append(TEXT_LINE_SEPARATOR)
          .append(nextPrefix)
          .append(
              String.join(
                  TEXT_LINE_SEPARATOR + nextPrefix, wrapLines(wrappedLines.get(index), nextWidth)));
    }
    return builder.toString();
  }

  private static List<String> wrapSourceLine(String sourceLine, int width) {
    if (sourceLine.length() <= width) {
      return List.of(sourceLine);
    }
    List<String> lines = new ArrayList<>();
    StringBuilder currentLine = new StringBuilder();
    Matcher wordMatcher = WRAP_WORD_BOUNDARY.matcher(sourceLine);
    int cursor = 0;
    while (true) {
      cursor = skipWhitespace(sourceLine, cursor);
      if (cursor >= sourceLine.length()) {
        break;
      }
      int nextBoundary = nextBoundary(sourceLine, cursor, wordMatcher);
      appendWord(sourceLine.substring(cursor, nextBoundary), width, currentLine, lines);
      cursor = nextBoundary;
    }
    if (!currentLine.isEmpty()) {
      lines.add(currentLine.toString());
    }
    return List.copyOf(lines);
  }

  private static int skipWhitespace(String sourceLine, int cursor) {
    int index = cursor;
    while (index < sourceLine.length() && Character.isWhitespace(sourceLine.charAt(index))) {
      index++;
    }
    return index;
  }

  private static int nextBoundary(String sourceLine, int cursor, Matcher wordMatcher) {
    wordMatcher.region(cursor, sourceLine.length());
    return wordMatcher.find() ? wordMatcher.start() : sourceLine.length();
  }

  private static void appendWord(
      String word, int width, StringBuilder currentLine, List<String> lines) {
    for (String wordPart : splitLongToken(word, width)) {
      if (currentLine.isEmpty()) {
        currentLine.append(wordPart);
      } else if (currentLine.length() + 1 + wordPart.length() <= width) {
        currentLine.append(' ').append(wordPart);
      } else {
        lines.add(currentLine.toString());
        currentLine.setLength(0);
        currentLine.append(wordPart);
      }
    }
  }

  private static int preferredSplitIndex(String token, int start, int width) {
    int limit = Math.min(token.length(), start + width);
    for (int index = limit - 1; index > start; index--) {
      if (WRAP_PREFERRED_BREAKS.indexOf(token.charAt(index)) >= 0) {
        return index + 1;
      }
    }
    return limit;
  }
}
