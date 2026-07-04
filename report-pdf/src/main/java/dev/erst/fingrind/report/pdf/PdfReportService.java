package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.reportmodel.ReportModel;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Renders FinGrind reporting results into deterministic PDF artifacts. */
public final class PdfReportService {
  private final Clock clock;
  private final PdfDocumentFactory documentFactory;
  private final PdfReportProjector reportProjector;

  /** Creates the public PDF-report adapter service. */
  public PdfReportService(String applicationName, String applicationVersion, Clock clock) {
    this(
        Objects.requireNonNull(clock, "clock"),
        new PdfDocumentFactory(
            Objects.requireNonNull(applicationName, "applicationName"),
            Objects.requireNonNull(applicationVersion, "applicationVersion")),
        new PdfReportProjector());
  }

  PdfReportService(Clock clock, PdfDocumentFactory documentFactory) {
    this(clock, documentFactory, new PdfReportProjector());
  }

  private PdfReportService(
      Clock clock, PdfDocumentFactory documentFactory, PdfReportProjector reportProjector) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.documentFactory = Objects.requireNonNull(documentFactory, "documentFactory");
    this.reportProjector = Objects.requireNonNull(reportProjector, "reportProjector");
  }

  /** Renders one shared report model as a deterministic PDF artifact. */
  public byte[] render(ReportModel reportModel) {
    Objects.requireNonNull(reportModel, "reportModel");
    return renderReport(
        reportModel.title(),
        switch (reportModel.orientation()) {
          case PORTRAIT -> PageOrientation.PORTRAIT;
          case LANDSCAPE -> PageOrientation.LANDSCAPE;
        },
        writer -> reportProjector.render(writer, reportModel));
  }

  private byte[] renderReport(
      String reportTitle, PageOrientation orientation, PdfRenderAction renderAction) {
    Objects.requireNonNull(reportTitle, "reportTitle");
    Objects.requireNonNull(orientation, "orientation");
    Objects.requireNonNull(renderAction, "renderAction");
    Instant generatedAt = Instant.now(clock);
    try (PdfDocumentFactory.DocumentSession session =
        documentFactory.create(reportTitle, generatedAt, orientation)) {
      renderAction.render(session.pageWriter());
      return session.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to render " + reportTitle + " PDF.", exception);
    }
  }

  /** One PDF writing callback bound to one open page writer. */
  @FunctionalInterface
  private interface PdfRenderAction {
    /** Writes one report body into the supplied page writer. */
    void render(PdfPageWriter writer) throws IOException;
  }
}
