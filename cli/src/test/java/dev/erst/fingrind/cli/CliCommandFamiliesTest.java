package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Regression tests for shared command-family abstractions extracted from {@link CliCommand}. */
class CliCommandFamiliesTest {
  private static final BookAccess BOOK_ACCESS =
      new BookAccess(
          Path.of("book.db"),
          BookAccess.PassphraseSource.StandardInput.INSTANCE,
          java.util.List.of());

  @Test
  void commandFamilies_preserveTypedStateAcrossSharedCommandShapes() {
    Path requestFile = Path.of("request.json");
    OutputMode outputMode = OutputMode.CSV;
    CliReportOutput reportOutput = new CliReportOutput(OutputMode.JSON, Path.of("report.pdf"));

    CliBookRequestOutputModeCommand requestCommand =
        new CliBookRequestOutputModeCommand(BOOK_ACCESS, requestFile, outputMode) {
          @Override
          protected int executeCommand(
              CliExecutionContext executionContext,
              BookAccess bookAccess,
              Path commandRequestFile,
              OutputMode commandOutputMode) {
            return 0;
          }
        };
    CliBookQueryOutputModeCommand<String> queryCommand =
        new CliBookQueryOutputModeCommand<>(BOOK_ACCESS, "query", outputMode) {
          @Override
          protected int executeCommand(
              CliExecutionContext executionContext,
              BookAccess bookAccess,
              String query,
              OutputMode commandOutputMode) {
            return 0;
          }
        };
    CliBookQueryReportCommand<String> reportCommand =
        new CliBookQueryReportCommand<>(BOOK_ACCESS, "report-query", reportOutput) {
          @Override
          protected int executeCommand(
              CliExecutionContext executionContext,
              BookAccess bookAccess,
              String query,
              CliReportOutput output) {
            return 0;
          }
        };
    CliBookNullablePathOutputModeCommand nullablePathCommand =
        new CliBookNullablePathOutputModeCommand(BOOK_ACCESS, Path.of("rollback.zip"), outputMode) {
          @Override
          protected int executeCommand(
              CliExecutionContext executionContext,
              BookAccess bookAccess,
              @Nullable Path path,
              OutputMode commandOutputMode) {
            assertSame(BOOK_ACCESS, bookAccess);
            assertEquals(Path.of("rollback.zip"), path);
            assertSame(outputMode, commandOutputMode);
            return 23;
          }
        };

    assertSame(BOOK_ACCESS, requestCommand.bookAccess());
    assertSame(requestFile, requestCommand.requestFile());
    assertSame(outputMode, requestCommand.outputMode());

    assertSame(BOOK_ACCESS, queryCommand.bookAccess());
    assertEquals("query", queryCommand.query());
    assertSame(outputMode, queryCommand.outputMode());

    assertSame(BOOK_ACCESS, reportCommand.bookAccess());
    assertEquals("report-query", reportCommand.query());
    assertSame(reportOutput, reportCommand.output());

    assertSame(BOOK_ACCESS, nullablePathCommand.bookAccess());
    assertEquals(Path.of("rollback.zip"), nullablePathCommand.optionalArtifactPath());
    assertSame(outputMode, nullablePathCommand.outputMode());
    assertEquals(23, nullablePathCommand.execute(executionContext()));
  }

  private static CliExecutionContext executionContext() {
    CliOutputChannel outputChannel =
        new CliOutputChannel(
            new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8));
    CliFailureResponseWriter failureWriter = new CliFailureResponseWriter(outputChannel);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));
    CliBookWorkflow workflow = new CliBookWorkflowAdapter() {};
    CliMutationResponseWriter mutationResponseWriter = new CliMutationResponseWriter(outputChannel);
    CliMetadata metadata = new CliMetadata();
    CliDiscoveryCommandExecutor discoveryCommandExecutor =
        new CliDiscoveryCommandExecutor(new CliDiscoveryResponseWriter(outputChannel), metadata);
    CliAdministrativeCommandExecutor administrativeCommandExecutor =
        new CliAdministrativeCommandExecutor(
            requestReader, mutationResponseWriter, failureWriter, workflow, workflow);
    CliMutationCommandExecutor mutationCommandExecutor =
        new CliMutationCommandExecutor(
            requestReader,
            mutationResponseWriter,
            new CliPlanResponseWriter(outputChannel),
            failureWriter,
            workflow);
    CliQueryCommandExecutor queryCommandExecutor =
        new CliQueryCommandExecutor(
            new CliBookReadResponseWriter(outputChannel, CliFilesystemFixtureSupport.fixedClock()),
            failureWriter,
            workflow);
    CliReportCommandExecutor reportCommandExecutor =
        new CliReportCommandExecutor(
            new CliReportResponseWriter(outputChannel),
            failureWriter,
            workflow,
            new CliPdfReportExporter(
                new PdfReportService(
                    metadata.applicationName(),
                    metadata.version(),
                    CliFilesystemFixtureSupport.fixedClock())),
            CliFilesystemFixtureSupport.fixedClock());
    return new CliExecutionContext(
        administrativeCommandExecutor,
        discoveryCommandExecutor,
        mutationCommandExecutor,
        queryCommandExecutor,
        reportCommandExecutor);
  }
}
