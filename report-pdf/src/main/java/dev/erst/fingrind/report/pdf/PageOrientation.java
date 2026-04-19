package dev.erst.fingrind.report.pdf;

import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Supported page orientations for FinGrind PDF reports. */
enum PageOrientation {
  PORTRAIT,
  LANDSCAPE;

  PDRectangle pageSize() {
    return switch (this) {
      case PORTRAIT -> PDRectangle.A4;
      case LANDSCAPE -> new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    };
  }
}
