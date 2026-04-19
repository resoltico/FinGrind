package dev.erst.fingrind.report.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.jspecify.annotations.Nullable;

/** Creates fully initialized PDF document sessions for FinGrind reports. */
final class PdfDocumentFactory {
  private static final String REGULAR_FONT_RESOURCE =
      "/dev/erst/fingrind/report/pdf/fonts/NotoSans-Regular.ttf";
  private static final String BOLD_FONT_RESOURCE =
      "/dev/erst/fingrind/report/pdf/fonts/NotoSans-Bold.ttf";

  private final String applicationName;
  private final String applicationVersion;
  private final FontResourceLoader fontResourceLoader;

  PdfDocumentFactory(String applicationName, String applicationVersion) {
    this(
        applicationName,
        applicationVersion,
        resourcePath -> PdfDocumentFactory.class.getResourceAsStream(resourcePath));
  }

  PdfDocumentFactory(
      String applicationName, String applicationVersion, FontResourceLoader fontResourceLoader) {
    this.applicationName = Objects.requireNonNull(applicationName, "applicationName");
    this.applicationVersion = Objects.requireNonNull(applicationVersion, "applicationVersion");
    this.fontResourceLoader = Objects.requireNonNull(fontResourceLoader, "fontResourceLoader");
  }

  DocumentSession create(
      String reportTitle, Path bookFilePath, Instant generatedAt, PageOrientation orientation)
      throws IOException {
    Objects.requireNonNull(reportTitle, "reportTitle");
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(generatedAt, "generatedAt");
    Objects.requireNonNull(orientation, "orientation");
    PDDocument document = new PDDocument();
    try {
      configureInformation(document, reportTitle, bookFilePath, generatedAt);
      PdfFonts fonts =
          new PdfFonts(
              loadFont(document, REGULAR_FONT_RESOURCE), loadFont(document, BOLD_FONT_RESOURCE));
      return new DocumentSession(
          document,
          new PdfPageWriter(
              document,
              fonts,
              orientation,
              reportTitle,
              bookFilePath,
              generatedAt,
              applicationName + " " + applicationVersion));
    } catch (IOException | RuntimeException exception) {
      document.close();
      throw exception;
    }
  }

  private void configureInformation(
      PDDocument document, String reportTitle, Path bookFilePath, Instant generatedAt) {
    PDDocumentInformation information = document.getDocumentInformation();
    information.setTitle(reportTitle);
    information.setAuthor(applicationName);
    information.setCreator(applicationName + " " + applicationVersion);
    information.setSubject(PdfRenderSupport.absolutePath(bookFilePath));
    information.setCreationDate(calendar(generatedAt));
    information.setModificationDate(calendar(generatedAt));
  }

  private static Calendar calendar(Instant instant) {
    return GregorianCalendar.from(instant.atZone(ZoneOffset.UTC));
  }

  private PDType0Font loadFont(PDDocument document, String resourcePath) throws IOException {
    try (InputStream fontStream = fontResourceLoader.open(resourcePath)) {
      if (fontStream == null) {
        throw new IllegalStateException("Missing bundled font resource: " + resourcePath);
      }
      return PDType0Font.load(document, fontStream);
    }
  }

  /** One open PDF document plus its page writer. */
  static final class DocumentSession implements AutoCloseable {
    private final PdfPageWriter pageWriter;
    private final IoAction closePageWriter;
    private final IoAction closeDocument;
    private final DocumentSaver saveDocument;
    private boolean closed;

    DocumentSession(PDDocument document, PdfPageWriter pageWriter) {
      this(
          Objects.requireNonNull(pageWriter, "pageWriter"),
          Objects.requireNonNull(pageWriter, "pageWriter")::close,
          Objects.requireNonNull(document, "document")::close,
          outputStream -> document.save(outputStream));
    }

    DocumentSession(
        PdfPageWriter pageWriter,
        IoAction closePageWriter,
        IoAction closeDocument,
        DocumentSaver saveDocument) {
      this.pageWriter = Objects.requireNonNull(pageWriter, "pageWriter");
      this.closePageWriter = Objects.requireNonNull(closePageWriter, "closePageWriter");
      this.closeDocument = Objects.requireNonNull(closeDocument, "closeDocument");
      this.saveDocument = Objects.requireNonNull(saveDocument, "saveDocument");
    }

    PdfPageWriter pageWriter() {
      return pageWriter;
    }

    byte[] toByteArray() throws IOException {
      closePageWriter.run();
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      saveDocument.save(outputStream);
      return outputStream.toByteArray();
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      IOException failure = null;
      try {
        closePageWriter.run();
      } catch (IOException exception) {
        failure = exception;
      }
      try {
        closeDocument.run();
      } catch (IOException exception) {
        if (failure != null) {
          failure.addSuppressed(exception);
        } else {
          failure = exception;
        }
      }
      if (failure != null) {
        throw failure;
      }
    }
  }

  /** One strategy for opening bundled font resources. */
  @FunctionalInterface
  interface FontResourceLoader {
    /** Opens one font resource stream for the supplied classpath path. */
    @Nullable InputStream open(String resourcePath) throws IOException;
  }

  /** One checked IO action used for deterministic close orchestration. */
  @FunctionalInterface
  interface IoAction {
    /** Runs one close-like IO action. */
    void run() throws IOException;
  }

  /** One sink that serializes an open PDF document into bytes. */
  @FunctionalInterface
  interface DocumentSaver {
    /** Saves one open PDF document into the supplied byte sink. */
    void save(ByteArrayOutputStream outputStream) throws IOException;
  }
}
