package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.TrialBalanceReport;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Renders FinGrind reporting results into deterministic PDF artifacts. */
public final class PdfReportService {
  private final Clock clock;
  private final PdfDocumentFactory documentFactory;
  private final AccountBalancePdfRenderer accountBalanceRenderer;
  private final TrialBalancePdfRenderer trialBalanceRenderer;
  private final AccountLedgerPdfRenderer accountLedgerRenderer;
  private final PeriodSummaryPdfRenderer periodSummaryRenderer;

  /** Creates the public PDF-report adapter service. */
  public PdfReportService(String applicationName, String applicationVersion, Clock clock) {
    this(
        Objects.requireNonNull(clock, "clock"),
        new PdfDocumentFactory(
            Objects.requireNonNull(applicationName, "applicationName"),
            Objects.requireNonNull(applicationVersion, "applicationVersion")),
        new AccountBalancePdfRenderer(),
        new TrialBalancePdfRenderer(),
        new AccountLedgerPdfRenderer(),
        new PeriodSummaryPdfRenderer());
  }

  PdfReportService(Clock clock, PdfDocumentFactory documentFactory) {
    this(
        clock,
        documentFactory,
        new AccountBalancePdfRenderer(),
        new TrialBalancePdfRenderer(),
        new AccountLedgerPdfRenderer(),
        new PeriodSummaryPdfRenderer());
  }

  private PdfReportService(
      Clock clock,
      PdfDocumentFactory documentFactory,
      AccountBalancePdfRenderer accountBalanceRenderer,
      TrialBalancePdfRenderer trialBalanceRenderer,
      AccountLedgerPdfRenderer accountLedgerRenderer,
      PeriodSummaryPdfRenderer periodSummaryRenderer) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.documentFactory = Objects.requireNonNull(documentFactory, "documentFactory");
    this.accountBalanceRenderer =
        Objects.requireNonNull(accountBalanceRenderer, "accountBalanceRenderer");
    this.trialBalanceRenderer =
        Objects.requireNonNull(trialBalanceRenderer, "trialBalanceRenderer");
    this.accountLedgerRenderer =
        Objects.requireNonNull(accountLedgerRenderer, "accountLedgerRenderer");
    this.periodSummaryRenderer =
        Objects.requireNonNull(periodSummaryRenderer, "periodSummaryRenderer");
  }

  /** Renders one account-balance snapshot as a portrait PDF artifact. */
  public byte[] renderAccountBalance(Path bookFilePath, AccountBalanceSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    return renderReport(
        "Account Balance",
        bookFilePath,
        PageOrientation.PORTRAIT,
        writer -> accountBalanceRenderer.render(writer, snapshot));
  }

  /** Renders one trial-balance report as a landscape PDF artifact. */
  public byte[] renderTrialBalance(Path bookFilePath, TrialBalanceReport report) {
    Objects.requireNonNull(report, "report");
    return renderReport(
        "Trial Balance",
        bookFilePath,
        PageOrientation.LANDSCAPE,
        writer -> trialBalanceRenderer.render(writer, report));
  }

  /** Renders one account-ledger report as a landscape PDF artifact. */
  public byte[] renderAccountLedger(Path bookFilePath, AccountLedgerReport report) {
    Objects.requireNonNull(report, "report");
    return renderReport(
        "Account Ledger",
        bookFilePath,
        PageOrientation.LANDSCAPE,
        writer -> accountLedgerRenderer.render(writer, report));
  }

  /** Renders one period-summary report as a landscape PDF artifact. */
  public byte[] renderPeriodSummary(Path bookFilePath, PeriodSummaryReport report) {
    Objects.requireNonNull(report, "report");
    return renderReport(
        "Period Summary",
        bookFilePath,
        PageOrientation.LANDSCAPE,
        writer -> periodSummaryRenderer.render(writer, report));
  }

  private byte[] renderReport(
      String reportTitle,
      Path bookFilePath,
      PageOrientation orientation,
      PdfRenderAction renderAction) {
    Objects.requireNonNull(reportTitle, "reportTitle");
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(orientation, "orientation");
    Objects.requireNonNull(renderAction, "renderAction");
    Instant generatedAt = Instant.now(clock);
    try (PdfDocumentFactory.DocumentSession session =
        documentFactory.create(reportTitle, bookFilePath, generatedAt, orientation)) {
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
