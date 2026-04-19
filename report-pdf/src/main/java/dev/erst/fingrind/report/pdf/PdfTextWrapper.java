package dev.erst.fingrind.report.pdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** Shared text measurement and wrapping helpers for PDF tables and mastheads. */
final class PdfTextWrapper {
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

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
