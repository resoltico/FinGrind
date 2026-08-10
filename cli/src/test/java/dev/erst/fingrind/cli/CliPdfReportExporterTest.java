package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.accountBalanceSnapshot;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.accountLedgerReport;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.exporterWith;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.periodSummaryReport;
import static dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.trialBalanceReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.CliPdfReportExporterTestSupport.RecordingPublicationTransactions;
import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.core.PublicationMode;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionMemberRequest;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests PDF artifacts generated for each CLI report model. */
class CliPdfReportExporterTest {
  @Test
  void exportMethodsRenderPdfPayloadsAndDelegateEachToTheTransactionOwner() {
    assertTransactionPublication(
        Path.of("balance.pdf"),
        AccountBalanceReportModelBuilder.buildModel(accountBalanceSnapshot()));
    assertTransactionPublication(
        Path.of("trial.pdf"), TrialBalanceReportModelBuilder.buildModel(trialBalanceReport()));
    assertTransactionPublication(
        Path.of("ledger.pdf"), AccountLedgerReportModelBuilder.buildModel(accountLedgerReport()));
    assertTransactionPublication(
        Path.of("summary.pdf"), PeriodSummaryReportModelBuilder.buildModel(periodSummaryReport()));
    assertTransactionPublication(
        Path.of("cash-flow.pdf"),
        CashFlowStatementReportModelBuilder.buildModel(
            CliFixtureSupport.sampleCashFlowStatementReport()));
  }

  private static void assertTransactionPublication(
      Path outputPath, dev.erst.fingrind.contract.reportmodel.ReportModel reportModel) {
    RecordingPublicationTransactions publicationTransactions =
        new RecordingPublicationTransactions();
    PublicationTransactionArtifact artifact =
        exporterWith(publicationTransactions).export(outputPath, reportModel);

    PublicationTransactionMemberRequest member =
        java.util.Objects.requireNonNull(
                publicationTransactions.publishedRequest, "publishedRequest")
            .members()
            .getFirst();
    assertEquals(outputPath.toAbsolutePath().normalize(), artifact.publishedArtifactPath());
    assertTrue(artifact.transactionResult().successful());
    assertEquals("pdf-report", member.memberId());
    assertEquals(PublicationTransactionMemberRole.PDF_REPORT, member.role());
    assertEquals(PublicationMode.NO_REPLACE_LINK, member.publicationMode());
    assertEquals(outputPath.toAbsolutePath().normalize(), member.finalPath());
    assertTrue(member.toString().contains("secretPayload=<redacted>"));
  }
}
