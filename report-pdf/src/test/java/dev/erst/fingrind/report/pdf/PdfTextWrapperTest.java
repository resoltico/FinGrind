package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

/** Tests for the low-level PDF text wrapping helper. */
class PdfTextWrapperTest {
  @Test
  void wrapTextCoversBlankShortAndBrokenWordCases() throws IOException {
    try (PDDocument document = new PDDocument();
        InputStream fontStream =
            PdfTextWrapperTest.class.getResourceAsStream(
                "/dev/erst/fingrind/report/pdf/fonts/NotoSans-Regular.ttf")) {
      PDType0Font font = PDType0Font.load(document, Objects.requireNonNull(fontStream));
      float shortWidth = PdfTextWrapper.stringWidth("alpha", font, 8f) + 0.01f;
      float tinyWidth = PdfTextWrapper.stringWidth("abcd", font, 8f);
      float hyphenWidth = PdfTextWrapper.stringWidth("current-period-", font, 8f) + 0.01f;

      assertEquals(List.of(""), PdfTextWrapper.wrapText("", font, 8f, shortWidth));
      assertEquals(
          List.of("alpha", "beta"), PdfTextWrapper.wrapText("alpha beta", font, 8f, shortWidth));

      List<String> brokenSingleWord =
          PdfTextWrapper.wrapText(
              "SupercalifragilisticexpialidociousLedgerToken", font, 8f, tinyWidth);
      List<String> zeroWidthFragments = PdfTextWrapper.wrapText("Token", font, 8f, 0f);
      List<String> mixedWords =
          PdfTextWrapper.wrapText(
              "alpha SupercalifragilisticexpialidociousLedgerToken", font, 8f, shortWidth);
      List<String> hyphenAwareFragments =
          PdfTextWrapper.wrapText("current-period-result", font, 8f, hyphenWidth);
      List<String> trailingDelimiterFragments =
          PdfTextWrapper.wrapText("current-period-current-", font, 8f, hyphenWidth);
      List<String> oversizedSlashFragments =
          PdfTextWrapper.wrapText("supercalifragilistic/ledger", font, 8f, tinyWidth);
      float slashPrefixWidth = PdfTextWrapper.stringWidth("a/", font, 8f) + 0.01f;
      List<String> oversizedFragmentAfterDelimiter =
          PdfTextWrapper.wrapText(
              "a/supercalifragilisticexpialidocious", font, 8f, slashPrefixWidth);

      assertTrue(brokenSingleWord.size() > 1);
      assertTrue(zeroWidthFragments.size() > 1);
      assertEquals("alpha", mixedWords.getFirst());
      assertTrue(mixedWords.size() > 2);
      assertEquals(List.of("current-period-", "result"), hyphenAwareFragments);
      assertEquals(List.of("current-period-", "current-"), trailingDelimiterFragments);
      assertTrue(oversizedSlashFragments.size() > 2);
      assertEquals("a/", oversizedFragmentAfterDelimiter.getFirst());
      assertTrue(oversizedFragmentAfterDelimiter.size() > 2);
    }
  }
}
