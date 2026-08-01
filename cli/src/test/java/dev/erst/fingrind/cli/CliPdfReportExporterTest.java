package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.accountBalanceSnapshot;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.accountLedgerReport;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.assertPdfFile;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.exporterWithoutNativeDirectoryForce;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.periodSummaryReport;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.privatePdfOutputDirectory;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.trialBalanceReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests PDF artifacts generated for each CLI report model. */
class CliPdfReportExporterTest {
  @TempDir Path tempDirectory;

  @Test
  void exportMethodsWritePdfArtifacts() throws IOException {
    CliPdfReportExporter exporter = exporterWithoutNativeDirectoryForce();
    Path outputDirectory = privatePdfOutputDirectory(tempDirectory, "pdf-artifacts");

    Path accountBalancePdf = outputDirectory.resolve("balance.pdf");
    Path trialBalancePdf = outputDirectory.resolve("trial.pdf");
    Path accountLedgerPdf = outputDirectory.resolve("ledger.pdf");
    Path periodSummaryPdf = outputDirectory.resolve("summary.pdf");
    Path cashFlowPdf = outputDirectory.resolve("cash-flow.pdf");

    ArtifactPublicationResult accountBalancePublication =
        exporter.export(
            accountBalancePdf,
            AccountBalanceReportModelBuilder.buildModel(accountBalanceSnapshot()));
    ArtifactPublicationResult trialBalancePublication =
        exporter.export(
            trialBalancePdf, TrialBalanceReportModelBuilder.buildModel(trialBalanceReport()));
    ArtifactPublicationResult accountLedgerPublication =
        exporter.export(
            accountLedgerPdf, AccountLedgerReportModelBuilder.buildModel(accountLedgerReport()));
    ArtifactPublicationResult periodSummaryPublication =
        exporter.export(
            periodSummaryPdf, PeriodSummaryReportModelBuilder.buildModel(periodSummaryReport()));
    ArtifactPublicationResult cashFlowPublication =
        exporter.export(
            cashFlowPdf,
            CashFlowStatementReportModelBuilder.buildModel(
                CliFixtureSupport.sampleCashFlowStatementReport()));

    assertPdfFile(accountBalancePdf);
    assertPdfFile(trialBalancePdf);
    assertPdfFile(accountLedgerPdf);
    assertPdfFile(periodSummaryPdf);
    assertPdfFile(cashFlowPdf);
    assertRetainedPublication(accountBalancePdf, accountBalancePublication);
    assertRetainedPublication(trialBalancePdf, trialBalancePublication);
    assertRetainedPublication(accountLedgerPdf, accountLedgerPublication);
    assertRetainedPublication(periodSummaryPdf, periodSummaryPublication);
    assertRetainedPublication(cashFlowPdf, cashFlowPublication);
  }

  private static void assertRetainedPublication(
      Path finalArtifact, ArtifactPublicationResult publication) throws IOException {
    Path retainedStage = publication.retention().retainedStagePath();
    assertEquals(finalArtifact.toRealPath(), publication.publishedArtifactPath());
    assertEquals(
        java.util.Objects.requireNonNull(finalArtifact.getParent(), "final artifact parent")
            .toRealPath(),
        java.util.Objects.requireNonNull(retainedStage.getParent(), "retained stage parent")
            .toRealPath());
    assertTrue(Files.isSameFile(finalArtifact, retainedStage));
    assertPdfFile(retainedStage);
  }
}
