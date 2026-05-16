package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Focused regression tests for {@link PdfFontMetrics}. */
class PdfFontMetricsTest {
  @Test
  void fallbackMetricsHandleFontsWithoutUsableDescriptors() {
    PDFont descriptorlessFont = new DescriptorlessFont(null);

    assertEquals(8.0f, PdfFontMetrics.ascent(descriptorlessFont, 10.0f));
    assertEquals(2.5f, PdfFontMetrics.descent(descriptorlessFont, 10.0f));
  }

  /** Minimal PDFBox font double used to drive metric fallbacks in layout tests. */
  private static final class DescriptorlessFont extends PDFont {
    private final @Nullable PDFontDescriptor descriptor;

    private DescriptorlessFont(@Nullable PDFontDescriptor descriptor) {
      super(new COSDictionary());
      this.descriptor = descriptor;
    }

    @Override
    @SuppressWarnings("NullAway")
    public PDFontDescriptor getFontDescriptor() {
      return descriptor;
    }

    @Override
    public String getName() {
      return "DescriptorlessFont";
    }

    @Override
    public Matrix getFontMatrix() {
      return Matrix.getTranslateInstance(0, 0);
    }

    @Override
    public BoundingBox getBoundingBox() {
      return new BoundingBox(0, 0, 0, 0);
    }

    @Override
    public Vector getPositionVector(int code) {
      return new Vector(0, 0);
    }

    @Override
    public float getHeight(int code) {
      return 0f;
    }

    @Override
    public boolean hasExplicitWidth(int code) {
      return false;
    }

    @Override
    public float getWidthFromFont(int code) {
      return 0f;
    }

    @Override
    protected float getStandard14Width(int code) {
      return 0f;
    }

    @Override
    protected byte[] encode(int unicode) {
      return new byte[] {(byte) unicode};
    }

    @Override
    public int readCode(InputStream input) throws IOException {
      return input.read();
    }

    @Override
    public String toUnicode(int code, GlyphList customGlyphList) {
      return "";
    }

    @Override
    public boolean isVertical() {
      return false;
    }

    @Override
    public boolean isDamaged() {
      return false;
    }

    @Override
    public boolean isEmbedded() {
      return false;
    }

    @Override
    public void addToSubset(int codePoint) {}

    @Override
    public void subset() {}

    @Override
    public boolean willBeSubset() {
      return false;
    }
  }
}
