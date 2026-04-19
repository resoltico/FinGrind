package dev.erst.fingrind.report.pdf;

import java.util.Objects;
import org.apache.pdfbox.pdmodel.font.PDFont;

/** Loaded font set shared by one rendered PDF document. */
record PdfFonts(PDFont regular, PDFont bold) {
  PdfFonts {
    Objects.requireNonNull(regular, "regular");
    Objects.requireNonNull(bold, "bold");
  }
}
