package dev.erst.fingrind.report.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** Shared text measurement and wrapping helpers for PDF tables and mastheads. */
final class PdfTextWrapper {
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
  private static final String BREAKABLE_DELIMITERS = "-_/";

  private PdfTextWrapper() {}

  static List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth)
      throws IOException {
    List<String> wrappedLines = new ArrayList<>();
    if (text.isBlank()) {
      wrappedLines.add("");
      return wrappedLines;
    }
    List<String> words = WHITESPACE_PATTERN.splitAsStream(text).toList();
    StringBuilder currentLine = new StringBuilder();
    for (String word : words) {
      String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
      if (stringWidth(candidate, font, fontSize) <= maxWidth) {
        currentLine.setLength(0);
        currentLine.append(candidate);
      } else if (currentLine.isEmpty()) {
        appendBrokenWord(wrappedLines, word, font, fontSize, maxWidth);
      } else {
        wrappedLines.add(currentLine.toString());
        currentLine.setLength(0);
        if (stringWidth(word, font, fontSize) <= maxWidth) {
          currentLine.append(word);
        } else {
          appendBrokenWord(wrappedLines, word, font, fontSize, maxWidth);
        }
      }
    }
    if (!currentLine.isEmpty()) {
      wrappedLines.add(currentLine.toString());
    }
    return wrappedLines;
  }

  static float stringWidth(String text, PDFont font, float fontSize) throws IOException {
    return font.getStringWidth(text) / 1000f * fontSize;
  }

  private static void appendBrokenWord(
      List<String> wrappedLines, String word, PDFont font, float fontSize, float maxWidth)
      throws IOException {
    List<String> breakableFragments = breakableFragments(word);
    if (breakableFragments.size() > 1) {
      appendBreakableFragments(wrappedLines, breakableFragments, font, fontSize, maxWidth);
      return;
    }
    appendCharacterFragments(wrappedLines, word, font, fontSize, maxWidth);
  }

  private static List<String> breakableFragments(String word) {
    List<String> fragments = new ArrayList<>();
    StringBuilder fragment = new StringBuilder();
    for (int index = 0; index < word.length(); index++) {
      char character = word.charAt(index);
      fragment.append(character);
      if (BREAKABLE_DELIMITERS.indexOf(character) >= 0) {
        fragments.add(fragment.toString());
        fragment.setLength(0);
      }
    }
    if (!fragment.isEmpty()) {
      fragments.add(fragment.toString());
    }
    return List.copyOf(fragments);
  }

  private static void appendBreakableFragments(
      List<String> wrappedLines,
      List<String> fragments,
      PDFont font,
      float fontSize,
      float maxWidth)
      throws IOException {
    StringBuilder line = new StringBuilder();
    for (String fragment : fragments) {
      String candidate = line.toString() + fragment;
      if (stringWidth(candidate, font, fontSize) <= maxWidth || line.isEmpty()) {
        line.setLength(0);
        line.append(candidate);
        if (stringWidth(line.toString(), font, fontSize) > maxWidth) {
          appendCharacterFragments(wrappedLines, line.toString(), font, fontSize, maxWidth);
          line.setLength(0);
        }
      } else {
        wrappedLines.add(line.toString());
        line.setLength(0);
        if (stringWidth(fragment, font, fontSize) <= maxWidth) {
          line.append(fragment);
        } else {
          appendCharacterFragments(wrappedLines, fragment, font, fontSize, maxWidth);
        }
      }
    }
    if (!line.isEmpty()) {
      wrappedLines.add(line.toString());
    }
  }

  private static void appendCharacterFragments(
      List<String> wrappedLines, String word, PDFont font, float fontSize, float maxWidth)
      throws IOException {
    StringBuilder fragment = new StringBuilder();
    for (int index = 0; index < word.length(); index++) {
      String candidate = fragment.toString() + word.charAt(index);
      if (stringWidth(candidate, font, fontSize) <= maxWidth || fragment.isEmpty()) {
        fragment.setLength(0);
        fragment.append(candidate);
      } else {
        wrappedLines.add(fragment.toString());
        fragment.setLength(0);
        fragment.append(word.charAt(index));
      }
    }
    wrappedLines.add(fragment.toString());
  }
}
