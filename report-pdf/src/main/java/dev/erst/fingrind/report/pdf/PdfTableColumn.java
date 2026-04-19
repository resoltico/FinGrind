package dev.erst.fingrind.report.pdf;

import java.util.Objects;

/** One rendered PDF table column. */
record PdfTableColumn(String header, float widthWeight, CellAlignment alignment) {
  PdfTableColumn {
    Objects.requireNonNull(header, "header");
    Objects.requireNonNull(alignment, "alignment");
    if (widthWeight <= 0f) {
      throw new IllegalArgumentException("widthWeight must be greater than zero.");
    }
  }

  /** Horizontal alignment choices for one rendered PDF table cell. */
  enum CellAlignment {
    LEFT,
    RIGHT
  }
}
