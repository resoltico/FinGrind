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
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(new String[] {"open-book"});
    assertEquals(1, exitCode);
    JsonNode failurePayload =
        assertDoesNotThrow(() -> new ObjectMapper().readTree(outputStream.toByteArray()));
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
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
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
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code(),
        failureEnvelope.path("code").stringValue());
    assertEquals("--cursor", failureEnvelope.path("argument").stringValue());
    assertTrue(
        failureEnvelope.path("message").stringValue().contains("Unsupported posting page cursor"));
    assertTrue(failureEnvelope.path("hint").stringValue().contains("nextCursor"));
  }

  @Test
  void run_rendersDeterministicWorkflowContractFailuresAsRejectedHumanOutput() {
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
              "human"
            });

    assertEquals(2, exitCode);
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
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new SqliteStorageFailureException("Failed to open SQLite book connection.")));
    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });
    assertEquals(4, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"storage-runtime-failure\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("initialization state"));
  }

  @Test
  void run_mapsManagedSqliteRuntimeFailureToRuntimeFailureWithEnvironmentHint() throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "FinGrind could not locate the managed SQLite runtime.")));
    int exitCode =
        cli.run(
            new String[] {
              "rekey-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--replacement-book-key-file",
              tempDirectory.resolve("replacement.key").toString()
            });
    assertEquals(4, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("managed-runtime-failure", failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains(
                "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows)"));
  }

  @Test
  void run_mapsBundleHomeRuntimeFailureToRuntimeFailureWithEnvironmentHint() throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "fingrind.bundle.home did not resolve a bundled SQLite library.")));
    int exitCode =
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            });
    assertEquals(4, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("managed-runtime-failure", failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains(
                "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows)"));
  }

  @Test
  void run_mapsBundleLauncherRuntimeFailureToRuntimeFailureWithEnvironmentHint()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "bin/fingrind must be used from the extracted bundle root.")));
    int exitCode =
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            });
    assertEquals(4, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("managed-runtime-failure", failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains(
                "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows)"));
  }

  @Test
  void run_mapsWindowsBundleLauncherRuntimeFailureToRuntimeFailureWithEnvironmentHint()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(
                new ManagedSqliteRuntimeUnavailableException(
                    "bin\\fingrind.ps1 must be used from the extracted bundle root.")));
    int exitCode =
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            });
    assertEquals(4, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("managed-runtime-failure", failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains(
                "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows)"));
  }

  @Test
  void run_mapsGenericRuntimeFailureToRuntimeFailureWithGenericHint() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new ExplodingWorkflow(new IllegalStateException("boom")));
    int exitCode =
        cli.run(
            new String[] {
              "post-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });
    assertEquals(4, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"runtime-failure\""));
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains(
                "Inspect the message and rerun after fixing the underlying runtime problem."));
  }

  @Test
  void run_mapsGenericIllegalArgumentExceptionToRuntimeFailure() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new IllegalArgumentWorkflow());
    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });
    assertEquals(4, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"runtime-failure\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("workflow boom"));
  }
}
