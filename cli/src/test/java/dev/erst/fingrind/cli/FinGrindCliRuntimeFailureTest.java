package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.sqlite.ManagedSqliteRuntimeUnavailableException;
import dev.erst.fingrind.sqlite.SqliteStorageFailureException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliRuntimeFailureTest extends FinGrindCliTestSupport {
  @Test
  void run_rejectsMissingBookFile() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock());
    int exitCode = cli.run(jsonArguments("open-book"));
    assertEquals(1, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode failurePayload =
        assertDoesNotThrow(() -> new ObjectMapper().readTree(diagnosticsStream.toByteArray()));
    assertEquals("error", failurePayload.path("status").stringValue());
    assertEquals("--book-file", failurePayload.path("argument").stringValue());
    assertEquals(
        "A --book-file argument is required.", failurePayload.path("message").stringValue());
  }

  @Test
  void run_rejectsInvalidPostingCursorAsDeterministicInputFailure() throws IOException {
    Path bookFilePath = tempDirectory.resolve("invalid-cursor.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock());
    int exitCode =
        cli.run(
            new String[] {
              "list-postings",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--cursor",
              "definitely-not-a-valid-cursor",
              "--output",
              "json"
            });
    assertEquals(1, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode failureEnvelope = new ObjectMapper().readTree(diagnosticsStream.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code(),
        failureEnvelope.path("code").stringValue());
    assertEquals("--cursor", failureEnvelope.path("argument").stringValue());
    assertTrue(
        failureEnvelope.path("message").stringValue().contains("Unsupported posting page cursor"));
    assertTrue(failureEnvelope.path("hint").stringValue().contains("nextCursor"));
  }

  @Test
  void run_rendersDeterministicWorkflowContractFailuresAsRejectedTextOutput() {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("book.key");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ContractFailureException(
                    ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
                        "Protected book verification failed.",
                        "Verify the book passphrase source and try again.",
                        "--book-key-file"))));

    int exitCode =
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--output",
              "text"
            });

    assertEquals(6, exitCode);
    String outputText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(outputText.contains("Rejected"));
    assertTrue(
        outputText.contains(ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code()));
    assertTrue(outputText.contains("Verify the book passphrase source and try again."));
    assertFalse(outputText.contains("\"status\""));
  }

  @Test
  void run_mapsSqliteRuntimeFailureToRuntimeFailureWithSqliteHint() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            new ExplodingWorkflow(
                new SqliteStorageFailureException("Failed to open SQLite book connection.")));
    int exitCode =
        cli.run(
            jsonArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));
    assertEquals(4, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    assertJsonContains(diagnosticsStream, "\"code\":\"storage-runtime-failure\"");
    assertTrue(diagnosticsStream.toString(StandardCharsets.UTF_8).contains("initialization state"));
  }

  @Test
  void run_mapsManagedSqliteRuntimeFailureToRuntimeFailureWithEnvironmentHint() throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "FinGrind could not locate the managed SQLite runtime.")));
    int exitCode =
        cli.run(
            jsonArguments(
                "rekey-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--new-book-key-file",
                tempDirectory.resolve("replacement.key").toString()));
    assertEquals(5, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode failureEnvelope = new ObjectMapper().readTree(diagnosticsStream.toByteArray());
    assertEquals("managed-runtime-failure", failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains(
                "Run a supported FinGrind launcher surface: the extracted published Linux bundle launcher (bin/fingrind), the published container image"));
  }

  @Test
  void run_mapsBundleHomeRuntimeFailureToRuntimeFailureWithEnvironmentHint() throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "fingrind.bundle.home did not resolve a bundled SQLite library.")));
    int exitCode =
        cli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString()));
    assertEquals(5, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode failureEnvelope = new ObjectMapper().readTree(diagnosticsStream.toByteArray());
    assertEquals("managed-runtime-failure", failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains(
                "Run a supported FinGrind launcher surface: the extracted published Linux bundle launcher (bin/fingrind), the published container image"));
  }

  @Test
  void run_mapsBundleLauncherRuntimeFailureToRuntimeFailureWithEnvironmentHint()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "bin/fingrind must be used from the extracted bundle root.")));
    int exitCode =
        cli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString()));
    assertEquals(5, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode failureEnvelope = new ObjectMapper().readTree(diagnosticsStream.toByteArray());
    assertEquals("managed-runtime-failure", failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains(
                "Run a supported FinGrind launcher surface: the extracted published Linux bundle launcher (bin/fingrind), the published container image"));
  }

  @Test
  void run_mapsWindowsBundleLauncherRuntimeFailureToRuntimeFailureWithEnvironmentHint()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "bin\\fingrind.ps1 must be used from the extracted bundle root.")));
    int exitCode =
        cli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString()));
    assertEquals(5, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode failureEnvelope = new ObjectMapper().readTree(diagnosticsStream.toByteArray());
    assertEquals("managed-runtime-failure", failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains(
                "Run a supported FinGrind launcher surface: the extracted published Linux bundle launcher (bin/fingrind), the published container image"));
  }

  @Test
  void run_mapsGenericRuntimeFailureToInternalErrorWithOpaquePublicIdAndDiagnostics()
      throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsOutput = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsOutput),
            fixedClock(),
            new ExplodingWorkflow(new IllegalStateException("boom")));
    int exitCode =
        cli.run(
            jsonArguments(
                "post-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));
    assertEquals(70, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode failureEnvelope = new ObjectMapper().readTree(diagnosticsOutput.toByteArray());
    assertEquals("internal-error", failureEnvelope.path("code").stringValue());
    assertTrue(failureEnvelope.path("message").stringValue().contains("fg-internal-"));
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains("preserved one machine-readable error envelope on stderr"));
    assertFalse(
        diagnosticsOutput.toString(StandardCharsets.UTF_8).contains("IllegalStateException"));
  }

  @Test
  void run_mapsGenericIllegalArgumentExceptionToInternalError() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsOutput = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsOutput),
            fixedClock(),
            new IllegalArgumentWorkflow());
    int exitCode =
        cli.run(
            jsonArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));
    assertEquals(70, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode failureEnvelope = new ObjectMapper().readTree(diagnosticsOutput.toByteArray());
    assertEquals("internal-error", failureEnvelope.path("code").stringValue());
    assertTrue(failureEnvelope.path("message").stringValue().contains("fg-internal-"));
    assertFalse(diagnosticsOutput.toString(StandardCharsets.UTF_8).contains("workflow boom"));
  }

  @Test
  void run_mapsGenericRuntimeFailureToTextFailureAndDiagnosticsInTextMode() {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsOutput = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsOutput),
            fixedClock(),
            new ExplodingWorkflow(new IllegalStateException("boom")));

    int exitCode =
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--output",
              "text"
            });

    assertEquals(70, exitCode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    String diagnosticsText = diagnosticsOutput.toString(StandardCharsets.UTF_8);
    assertTrue(diagnosticsText.contains("Error"));
    assertTrue(diagnosticsText.contains("internal-error"));
    assertTrue(diagnosticsText.contains("[internal-error] fg-internal-"));
    assertTrue(diagnosticsText.contains("IllegalStateException: boom"));
  }
}
