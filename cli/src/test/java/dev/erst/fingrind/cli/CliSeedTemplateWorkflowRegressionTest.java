package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression coverage for public CLI workflows layered on top of the seeded accounts. */
class CliSeedTemplateWorkflowRegressionTest extends CliBookWorkflowFixtureSupport {
  @Test
  void declareAccount_succeedsAfterOpeningOneSeededTemplateBook() throws IOException {
    Path bookFilePath = tempDirectory.resolve("books").resolve("seed-template.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path requestFile =
        writeRequest(
            """
            {
              "accountCode": "1000",
              "accountName": "Cash",
              "accountType": "ASSET",
              "accountNodeKind": "POSTABLE",
              "financialPositionLineClassification": "CURRENT_ASSET",
              "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
            }
            """);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsOutput = new ByteArrayOutputStream();
    FinGrindCli cli =
        FinGrindCli.standard(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsOutput),
            fixedClock());

    assertEquals(0, cli.run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    outputStream.reset();
    int exitCode =
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(
        0,
        exitCode,
        () ->
            "declare-account diagnostics:\n"
                + diagnosticsOutput.toString(StandardCharsets.UTF_8)
                + "\nstdout:\n"
                + outputStream.toString(StandardCharsets.UTF_8));
  }
}
