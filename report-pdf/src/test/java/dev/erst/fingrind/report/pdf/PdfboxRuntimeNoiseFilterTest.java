package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/** Tests for the PDFBox runtime-noise suppression seam. */
class PdfboxRuntimeNoiseFilterTest {
  @Test
  void shouldSuppress_filtersOnlyTheKnownPdfboxUnmappingWarningFamily() {
    LogRecord staticAccessWarning = new LogRecord(Level.SEVERE, "Unmapping is not supported.");
    staticAccessWarning.setLoggerName("org.apache.pdfbox.io.IOUtils");

    LogRecord missingPermissionWarning =
        new LogRecord(
            Level.SEVERE,
            "Unmapping is not supported because of missing permissions. Please grant at least the following permissions: RuntimePermission(\"accessClassInPackage.sun.misc\")  and ReflectPermission(\"suppressAccessChecks\")");
    missingPermissionWarning.setLoggerName("org.apache.pdfbox.io.IOUtils");

    LogRecord directBufferWarning = new LogRecord(Level.SEVERE, "Unable to unmap ByteBuffer.");
    directBufferWarning.setLoggerName("org.apache.pdfbox.io.IOUtils");

    LogRecord mappedBufferWarning =
        new LogRecord(Level.SEVERE, "Unable to unmap the mapped buffer");
    mappedBufferWarning.setLoggerName("org.apache.pdfbox.io.IOUtils");

    LogRecord otherMessage = new LogRecord(Level.SEVERE, "Different failure");
    otherMessage.setLoggerName("org.apache.pdfbox.io.IOUtils");

    LogRecord otherLogger = new LogRecord(Level.SEVERE, "Unmapping is not supported.");
    otherLogger.setLoggerName("org.apache.pdfbox.io.Other");

    assertTrue(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(staticAccessWarning));
    assertTrue(
        PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(missingPermissionWarning));
    assertTrue(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(directBufferWarning));
    assertTrue(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(mappedBufferWarning));
    assertFalse(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(otherMessage));
    assertFalse(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(otherLogger));
    assertFalse(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(null));
  }

  @Test
  void composeFilter_preservesExpectedLoggerFilteringBranches() {
    LogRecord harmlessUnmappingWarning = new LogRecord(Level.SEVERE, "Unable to unmap ByteBuffer.");
    harmlessUnmappingWarning.setLoggerName("org.apache.pdfbox.io.IOUtils");

    LogRecord unrelatedRecord = new LogRecord(Level.INFO, "Retain me");
    unrelatedRecord.setLoggerName("org.apache.pdfbox.io.IOUtils");

    Filter noExistingFilter = PdfDocumentFactory.PdfboxRuntimeNoiseFilter.composeFilter(null);
    Filter allowingExistingFilter =
        PdfDocumentFactory.PdfboxRuntimeNoiseFilter.composeFilter(record -> true);
    Filter rejectingExistingFilter =
        PdfDocumentFactory.PdfboxRuntimeNoiseFilter.composeFilter(record -> false);

    assertFalse(noExistingFilter.isLoggable(harmlessUnmappingWarning));
    assertTrue(noExistingFilter.isLoggable(unrelatedRecord));
    assertTrue(allowingExistingFilter.isLoggable(unrelatedRecord));
    assertFalse(rejectingExistingFilter.isLoggable(unrelatedRecord));
  }
}
