package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InaccessibleObjectException;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/** Tests for the PDFBox runtime-noise suppression seam. */
class PdfboxRuntimeNoiseFilterTest {
  @Test
  void shouldSuppress_filtersOnlyTheKnownJava26PdfboxUnmappingWarning() {
    LogRecord suppressedRecord = new LogRecord(Level.SEVERE, "Unmapping is not supported.");
    suppressedRecord.setLoggerName("org.apache.pdfbox.io.IOUtils");
    suppressedRecord.setThrown(new InaccessibleObjectException("java.nio is not open"));

    LogRecord otherMessage = new LogRecord(Level.SEVERE, "Different failure");
    otherMessage.setLoggerName("org.apache.pdfbox.io.IOUtils");
    otherMessage.setThrown(new InaccessibleObjectException("java.nio is not open"));

    LogRecord otherLogger = new LogRecord(Level.SEVERE, "Unmapping is not supported.");
    otherLogger.setLoggerName("org.apache.pdfbox.io.Other");
    otherLogger.setThrown(new InaccessibleObjectException("java.nio is not open"));

    LogRecord otherThrowable = new LogRecord(Level.SEVERE, "Unmapping is not supported.");
    otherThrowable.setLoggerName("org.apache.pdfbox.io.IOUtils");
    otherThrowable.setThrown(new IllegalStateException("different throwable"));

    assertTrue(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(suppressedRecord));
    assertFalse(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(otherMessage));
    assertFalse(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(otherLogger));
    assertFalse(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(otherThrowable));
    assertFalse(PdfDocumentFactory.PdfboxRuntimeNoiseFilter.shouldSuppress(null));
  }

  @Test
  void composeFilter_preservesExpectedLoggerFilteringBranches() {
    LogRecord harmlessUnmappingWarning = new LogRecord(Level.SEVERE, "Unmapping is not supported.");
    harmlessUnmappingWarning.setLoggerName("org.apache.pdfbox.io.IOUtils");
    harmlessUnmappingWarning.setThrown(new InaccessibleObjectException("java.nio is not open"));

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
