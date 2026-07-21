package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    try {
      FinGrindCli cli =
          FinGrindCli.standard(
              new ByteArrayInputStream(new byte[0]),
              new java.io.PrintStream(new ByteArrayOutputStream()),
              new java.io.PrintStream(new ByteArrayOutputStream()),
              CliFilesystemFixtureSupport.fixedClock());
      var executionContext = FinGrindCli.class.getDeclaredField("executionContext");
      executionContext.setAccessible(true);
      return (CliExecutionContext) executionContext.get(cli);
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError(
          "Unable to obtain a real CLI execution context for the base contract.", exception);
    }
  }
}
