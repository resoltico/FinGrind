package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import java.io.IOException;
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
  private final FinancialPositionPdfRenderer financialPositionRenderer;
  private final IncomeStatementPdfRenderer incomeStatementRenderer;
  private final ChangesInEquityPdfRenderer changesInEquityRenderer;

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
        new PeriodSummaryPdfRenderer(),
        new FinancialPositionPdfRenderer(),
        new IncomeStatementPdfRenderer(),
        new ChangesInEquityPdfRenderer());
  }

  PdfReportService(Clock clock, PdfDocumentFactory documentFactory) {
    this(
        clock,
        documentFactory,
        new AccountBalancePdfRenderer(),
        new TrialBalancePdfRenderer(),
        new AccountLedgerPdfRenderer(),
        new PeriodSummaryPdfRenderer(),
        new FinancialPositionPdfRenderer(),
        new IncomeStatementPdfRenderer(),
        new ChangesInEquityPdfRenderer());
  }

  private PdfReportService(
      Clock clock,
      PdfDocumentFactory documentFactory,
      AccountBalancePdfRenderer accountBalanceRenderer,
      TrialBalancePdfRenderer trialBalanceRenderer,
      AccountLedgerPdfRenderer accountLedgerRenderer,
      PeriodSummaryPdfRenderer periodSummaryRenderer,
      FinancialPositionPdfRenderer financialPositionRenderer,
      IncomeStatementPdfRenderer incomeStatementRenderer,
      ChangesInEquityPdfRenderer changesInEquityRenderer) {
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
    this.financialPositionRenderer =
        Objects.requireNonNull(financialPositionRenderer, "financialPositionRenderer");
    this.incomeStatementRenderer =
        Objects.requireNonNull(incomeStatementRenderer, "incomeStatementRenderer");
    this.changesInEquityRenderer =
        Objects.requireNonNull(changesInEquityRenderer, "changesInEquityRenderer");
  }

  /** Renders one account-balance snapshot as a portrait PDF artifact. */
  public byte[] renderAccountBalance(AccountBalanceSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    return renderReport(
        "Account Balance",
        PageOrientation.PORTRAIT,
        writer -> accountBalanceRenderer.render(writer, snapshot));
  }

  /** Renders one trial-balance report as a landscape PDF artifact. */
  public byte[] renderTrialBalance(TrialBalanceReport report) {
    Objects.requireNonNull(report, "report");
    return renderReport(
        "Trial Balance",
        PageOrientation.LANDSCAPE,
        writer -> trialBalanceRenderer.render(writer, report));
  }

  /** Renders one account-ledger report as a landscape PDF artifact. */
  public byte[] renderAccountLedger(AccountLedgerReport report) {
    Objects.requireNonNull(report, "report");
    return renderReport(
        "Account Ledger",
        PageOrientation.LANDSCAPE,
        writer -> accountLedgerRenderer.render(writer, report));
  }

  /** Renders one period-summary report as a landscape PDF artifact. */
  public byte[] renderPeriodSummary(PeriodSummaryReport report) {
    Objects.requireNonNull(report, "report");
    return renderReport(
        "Period Summary",
        PageOrientation.LANDSCAPE,
        writer -> periodSummaryRenderer.render(writer, report));
  }

  /** Renders one statement of financial position as a landscape PDF artifact. */
  public byte[] renderFinancialPosition(FinancialPositionReport report) {
    Objects.requireNonNull(report, "report");
    return renderReport(
        "Financial Position",
        PageOrientation.LANDSCAPE,
        writer -> financialPositionRenderer.render(writer, report));
  }

  /** Renders one income statement as a landscape PDF artifact. */
  public byte[] renderIncomeStatement(IncomeStatementReport report) {
    Objects.requireNonNull(report, "report");
    return renderReport(
        "Income Statement",
        PageOrientation.LANDSCAPE,
        writer -> incomeStatementRenderer.render(writer, report));
  }

  /** Renders one statement of changes in equity as a landscape PDF artifact. */
  public byte[] renderChangesInEquity(ChangesInEquityReport report) {
    Objects.requireNonNull(report, "report");
    return renderReport(
        "Changes In Equity",
        PageOrientation.LANDSCAPE,
        writer -> changesInEquityRenderer.render(writer, report));
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
