package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Report-command tests for stdout rendering and optional PDF artifact behavior. */
class FinGrindCliReportCommandTest extends FinGrindCliTestSupport {
  @Test
  void run_writesPdfArtifactForSuccessfulTrialBalanceReport() throws IOException {
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("entity.key");
    Path pdfOutputPath =
        tempDirectory.resolve("reports odd").resolve("trial balance [office copy].pdf");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            reportingWorkflow(new TrialBalanceResult.Reported(sampleTrialBalanceReport())));

    int exitCode =
        cli.run(
            new String[] {
              "trial-balance",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--output",
              "human",
              "--pdf-out",
              pdfOutputPath.toString()
            });

    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Trial Balance"));
    assertTrue(Files.exists(pdfOutputPath));
    assertEquals(
        "%PDF-", new String(Files.readAllBytes(pdfOutputPath), 0, 5, StandardCharsets.ISO_8859_1));
    assertTrue(diagnosticsStream.toString(StandardCharsets.UTF_8).contains("pdf-exported"));
    assertTrue(
        diagnosticsStream
            .toString(StandardCharsets.UTF_8)
            .contains(CliPublicPaths.normalizedValue(pdfOutputPath)));
  }

  @Test
  void run_jsonReportWithPdfOut_publishesNormalizedArtifactPathOnStdoutAndDiagnostics()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("entity.key");
    Path pdfOutputPath = tempDirectory.resolve("reports").resolve("trial-balance.json.pdf");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            reportingWorkflow(new TrialBalanceResult.Reported(sampleTrialBalanceReport())));

    int exitCode =
        cli.run(
            new String[] {
              "trial-balance",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--output",
              "json",
              "--pdf-out",
              pdfOutputPath.toString()
            });

    assertEquals(0, exitCode);
    var envelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("pdf", envelope.path("artifacts").get(0).path("format").stringValue());
    assertEquals(
        CliPublicPaths.normalizedValue(pdfOutputPath),
        envelope.path("artifacts").get(0).path("path").stringValue());
    assertEquals("", diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  @Test
  void run_skipsPdfArtifactWhenReportCommandIsRejected() {
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("entity.key");
    Path pdfOutputPath = tempDirectory.resolve("reports").resolve("trial-balance.pdf");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            reportingWorkflow(
                new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized())));

    int exitCode =
        cli.run(
            new String[] {
              "trial-balance",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--pdf-out",
              pdfOutputPath.toString()
            });

    assertEquals(2, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"query-book-not-initialized\""));
    assertFalse(Files.exists(pdfOutputPath));
  }

  @Test
  void run_skipsPdfArtifactsForOtherRejectedReportCommands() {
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("entity.key");
    Path balancePdf = tempDirectory.resolve("reports").resolve("balance.pdf");
    Path ledgerPdf = tempDirectory.resolve("reports").resolve("ledger.pdf");
    Path summaryPdf = tempDirectory.resolve("reports").resolve("summary.pdf");
    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream ledgerOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream summaryOutput = new ByteArrayOutputStream();
    CliBookWorkflow rejectedWorkflow =
        reportingWorkflow(
            new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new TrialBalanceResult.Reported(sampleTrialBalanceReport()),
            new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized()));

    FinGrindCli balanceCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(balanceOutput),
            fixedClock(),
            rejectedWorkflow);
    FinGrindCli ledgerCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(ledgerOutput),
            fixedClock(),
            rejectedWorkflow);
    FinGrindCli summaryCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(summaryOutput),
            fixedClock(),
            rejectedWorkflow);

    int balanceExitCode =
        balanceCli.run(
            new String[] {
              "account-balance",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--account-code",
              "1000",
              "--pdf-out",
              balancePdf.toString()
            });
    int ledgerExitCode =
        ledgerCli.run(
            new String[] {
              "account-ledger",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--account-code",
              "1000",
              "--effective-date-from",
              "2026-04-01",
              "--effective-date-to",
              "2026-04-30",
              "--pdf-out",
              ledgerPdf.toString()
            });
    int summaryExitCode =
        summaryCli.run(
            new String[] {
              "period-summary",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--effective-date-from",
              "2026-04-01",
              "--effective-date-to",
              "2026-04-30",
              "--pdf-out",
              summaryPdf.toString()
            });

    assertEquals(2, balanceExitCode);
    assertEquals(2, ledgerExitCode);
    assertEquals(2, summaryExitCode);
    assertFalse(Files.exists(balancePdf));
    assertFalse(Files.exists(ledgerPdf));
    assertFalse(Files.exists(summaryPdf));
  }

  @Test
  void run_writesPdfArtifactsForOtherSuccessfulReportCommands() throws IOException {
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("entity.key");
    Path balancePdf = tempDirectory.resolve("reports").resolve("balance.pdf");
    Path ledgerPdf = tempDirectory.resolve("reports").resolve("ledger.pdf");
    Path summaryPdf = tempDirectory.resolve("reports").resolve("summary.pdf");
    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream ledgerOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream summaryOutput = new ByteArrayOutputStream();
    CliBookWorkflow successfulWorkflow =
        reportingWorkflow(
            new AccountBalanceResult.Reported(sampleAccountBalanceSnapshot()),
            new TrialBalanceResult.Reported(sampleTrialBalanceReport()),
            new AccountLedgerResult.Reported(sampleAccountLedgerReport()),
            new PeriodSummaryResult.Reported(samplePeriodSummaryReport()));

    FinGrindCli balanceCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(balanceOutput),
            fixedClock(),
            successfulWorkflow);
    FinGrindCli ledgerCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(ledgerOutput),
            fixedClock(),
            successfulWorkflow);
    FinGrindCli summaryCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(summaryOutput),
            fixedClock(),
            successfulWorkflow);

    int balanceExitCode =
        balanceCli.run(
            new String[] {
              "account-balance",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--account-code",
              "1000",
              "--pdf-out",
              balancePdf.toString()
            });
    int ledgerExitCode =
        ledgerCli.run(
            new String[] {
              "account-ledger",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--account-code",
              "1000",
              "--effective-date-from",
              "2026-04-01",
              "--effective-date-to",
              "2026-04-30",
              "--pdf-out",
              ledgerPdf.toString()
            });
    int summaryExitCode =
        summaryCli.run(
            new String[] {
              "period-summary",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--effective-date-from",
              "2026-04-01",
              "--effective-date-to",
              "2026-04-30",
              "--pdf-out",
              summaryPdf.toString()
            });

    assertEquals(0, balanceExitCode);
    assertEquals(0, ledgerExitCode);
    assertEquals(0, summaryExitCode);
    assertTrue(Files.exists(balancePdf));
    assertTrue(Files.exists(ledgerPdf));
    assertTrue(Files.exists(summaryPdf));
  }

  @Test
  void run_writesPdfArtifactsForPrimaryStatementCommands() throws IOException {
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("entity.key");
    Path positionPdf = tempDirectory.resolve("reports").resolve("financial-position.pdf");
    Path incomePdf = tempDirectory.resolve("reports").resolve("income-statement.pdf");
    Path equityPdf = tempDirectory.resolve("reports").resolve("changes-in-equity.pdf");
    CliBookWorkflow workflow =
        reportingWorkflow(
            new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new TrialBalanceResult.Reported(sampleTrialBalanceReport()),
            new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new FinancialPositionResult.Reported(sampleFinancialPositionReport()),
            new IncomeStatementResult.Reported(sampleIncomeStatementReport()),
            new ChangesInEquityResult.Reported(sampleChangesInEquityReport()));

    int positionExitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "financial-position",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--effective-date-to",
                  "2026-04-30",
                  "--pdf-out",
                  positionPdf.toString()
                });
    int incomeExitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "income-statement",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--pdf-out",
                  incomePdf.toString()
                });
    int equityExitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                workflow)
            .run(
                new String[] {
                  "changes-in-equity",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--pdf-out",
                  equityPdf.toString()
                });

    assertEquals(0, positionExitCode);
    assertEquals(0, incomeExitCode);
    assertEquals(0, equityExitCode);
    assertTrue(Files.exists(positionPdf));
    assertTrue(Files.exists(incomePdf));
    assertTrue(Files.exists(equityPdf));
  }

  @Test
  void run_skipsPdfArtifactsForRejectedPrimaryStatementCommands() {
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("entity.key");
    Path positionPdf = tempDirectory.resolve("reports").resolve("financial-position.pdf");
    Path incomePdf = tempDirectory.resolve("reports").resolve("income-statement.pdf");
    Path equityPdf = tempDirectory.resolve("reports").resolve("changes-in-equity.pdf");
    CliBookWorkflow rejectedWorkflow =
        reportingWorkflow(
            new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new TrialBalanceResult.Reported(sampleTrialBalanceReport()),
            new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new FinancialPositionResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new IncomeStatementResult.Rejected(new BookQueryRejection.BookNotInitialized()),
            new ChangesInEquityResult.Rejected(new BookQueryRejection.BookNotInitialized()));

    int positionExitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                rejectedWorkflow)
            .run(
                new String[] {
                  "financial-position",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--pdf-out",
                  positionPdf.toString()
                });
    int incomeExitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                rejectedWorkflow)
            .run(
                new String[] {
                  "income-statement",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--pdf-out",
                  incomePdf.toString()
                });
    int equityExitCode =
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock(),
                rejectedWorkflow)
            .run(
                new String[] {
                  "changes-in-equity",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--pdf-out",
                  equityPdf.toString()
                });

    assertEquals(2, positionExitCode);
    assertEquals(2, incomeExitCode);
    assertEquals(2, equityExitCode);
    assertFalse(Files.exists(positionPdf));
    assertFalse(Files.exists(incomePdf));
    assertFalse(Files.exists(equityPdf));
  }

  @Test
  void run_preservesReportStdoutWhenPdfExportFails() throws IOException {
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("keys").resolve("entity.key");
    Path blockedParent = tempDirectory.resolve("blocked output parent");
    Files.writeString(blockedParent, "not a directory", StandardCharsets.UTF_8);
    Path pdfOutputPath = blockedParent.resolve("trial-balance.pdf");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            reportingWorkflow(new TrialBalanceResult.Reported(sampleTrialBalanceReport())));

    int exitCode =
        cli.run(
            new String[] {
              "trial-balance",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--pdf-out",
              pdfOutputPath.toString()
            });

    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
    assertTrue(diagnosticsStream.toString(StandardCharsets.UTF_8).contains("pdf-export-warning"));
    assertTrue(diagnosticsStream.toString(StandardCharsets.UTF_8).contains("--pdf-out"));
  }
}
