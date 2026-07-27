package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliPassphraseWorkflowTest extends CliWorkflowFixtureSupport {
  @Test
  void run_openBookAndListAccountsThroughDefaultSqliteWorkflowSupportsStandardInputPassphrase()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("stdin-books").resolve("entity.sqlite");
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(
            new ByteArrayInputStream((TEST_BOOK_KEY + "\n").getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(openOutput),
            fixedClock());
    assertEquals(
        0,
        openCli.run(jsonArguments(openBookStandardInputArguments(bookFilePath))),
        openOutput.toString(StandardCharsets.UTF_8));
    assertJsonContains(openOutput, "\"initializedAt\"");
    assertJsonContains(openOutput, "\"entityName\":\"Acme Studio\"");
    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    FinGrindCli listCli =
        cli(
            new ByteArrayInputStream((TEST_BOOK_KEY + "\n").getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(listOutput),
            fixedClock());
    assertEquals(
        0,
        listCli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-passphrase-stdin")));
    assertJsonContains(listOutput, "\"status\":\"ok\"");
  }

  @Test
  void run_openBookThroughDefaultSqliteWorkflowRejectsPromptPassphraseWithoutInteractiveConsole()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("no-console-books").resolve("entity.sqlite");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(openBookPromptArguments(bookFilePath));
    assertEquals(5, exitCode);
    String outputText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(outputText.contains("Error"), outputText);
    assertTrue(
        outputText.contains(ContractErrors.Descriptor.INTERACTIVE_PROMPT_UNAVAILABLE.code()),
        outputText);
    assertTrue(
        outputText.contains(
            "FinGrind cannot prompt for a book passphrase because no interactive console is available."),
        outputText);
    assertTrue(outputText.contains("--book-key-file"), outputText);
  }

  @Test
  void run_openBookAndListAccountsThroughDefaultSqliteWorkflowSupportsInteractivePromptPassphrase()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("prompt-books").resolve("entity.sqlite");
    CliBookPassphraseResolver.Terminal terminal =
        prompt -> ContractDecision.accepted(TEST_BOOK_KEY.toCharArray());
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(openOutput),
            fixedClock(),
            terminal);
    assertEquals(
        0,
        openCli.run(openBookPromptArguments(bookFilePath)),
        openOutput.toString(StandardCharsets.UTF_8));
    String openText = openOutput.toString(StandardCharsets.UTF_8);
    assertTrue(openText.contains("Book Initialized"));
    assertTrue(openText.contains("Acme Studio"));
    assertTrue(openText.contains("Functional currency"));
    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    FinGrindCli listCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(listOutput),
            fixedClock(),
            terminal);
    assertEquals(
        0,
        listCli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-passphrase-prompt",
              "--output",
              "text"
            }));
    String listText = listOutput.toString(StandardCharsets.UTF_8);
    assertTrue(listText.contains("Accounts"));
    assertTrue(listText.contains("Returned accounts"));
    assertFalse(listText.contains("Context"));
  }

  @Test
  void run_listAccountsWithWrongStandardInputPassphrase_doesNotEchoSecret() throws IOException {
    Path bookFilePath = tempDirectory.resolve("wrong-stdin-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0, openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    String wrongSecret = "wrong-stdin-secret";
    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    FinGrindCli listCli =
        cli(
            new ByteArrayInputStream((wrongSecret + "\n").getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(listOutput),
            fixedClock());
    assertEquals(
        6,
        listCli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-passphrase-stdin")));
    String outputText = listOutput.toString(StandardCharsets.UTF_8);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputText);
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        failureEnvelope.path("code").stringValue());
    assertFalse(outputText.contains(wrongSecret));
  }

  @Test
  void run_listAccountsWithWrongStandardInputPassphrase_emitsTextFailureInTextMode()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("wrong-stdin-text-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0, openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    String wrongSecret = "wrong-stdin-secret";
    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    FinGrindCli listCli =
        cli(
            new ByteArrayInputStream((wrongSecret + "\n").getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(listOutput),
            fixedClock());

    assertEquals(
        6,
        listCli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-passphrase-stdin",
              "--output",
              "text"
            }));

    String outputText = listOutput.toString(StandardCharsets.UTF_8);
    assertTrue(outputText.contains("Error"), outputText);
    assertTrue(
        outputText.contains(ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code()),
        outputText);
    assertFalse(outputText.contains(wrongSecret));
  }

  @Test
  void run_openBookWithUnreadableStandardInput_reportsInvalidPassphraseSource() throws IOException {
    Path bookFilePath = tempDirectory.resolve("broken-stdin-books").resolve("entity.sqlite");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new InputStream() {
              @Override
              public int read() throws IOException {
                throw new IOException("boom");
              }

              @Override
              public int read(byte[] buffer, int offset, int length) throws IOException {
                throw new IOException("boom");
              }
            },
            utf8PrintStream(outputStream),
            fixedClock());
    assertEquals(6, cli.run(jsonArguments(openBookStandardInputArguments(bookFilePath))));
    String outputText = outputStream.toString(StandardCharsets.UTF_8);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputText);
    assertEquals(
        ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.code(),
        failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("message")
            .stringValue()
            .contains("Failed to read the FinGrind book passphrase from standard input."));
    assertFalse(outputText.contains("runtime-failure"));
    assertFalse(outputText.contains("boom"));
  }

  @Test
  void run_openBookWithMissingKeyFile_exposesCanonicalPathInJsonFailure() throws IOException {
    Path bookFilePath = tempDirectory.resolve("missing-key-books").resolve("entity.sqlite");
    Path missingKeyFile = tempDirectory.resolve("private-secrets").resolve("missing.key");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            jsonArguments(
                founderAttestedArguments(
                    bookFilePath,
                    "open-book",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    missingKeyFile.toString(),
                    "--entity-name",
                    "Acme Studio",
                    "--book-template-id",
                    "OWNER_MANAGED_SERVICE",
                    "--accounting-basis",
                    "CASH",
                    "--functional-currency",
                    "EUR",
                    "--fiscal-year-start",
                    "01-01",
                    "--book-start-effective-date",
                    "2026-01-01")));

    assertEquals(6, exitCode);
    String outputText = outputStream.toString(StandardCharsets.UTF_8);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputText);
    assertEquals(
        ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.code(),
        failureEnvelope.path("code").stringValue());
    String absoluteKeyFile = CliPublicPaths.absoluteValue(missingKeyFile);
    assertEquals(absoluteKeyFile, failureEnvelope.path("path").stringValue());
    assertEquals(0, failureEnvelope.path("relatedPaths").size());
    assertFalse(
        failureEnvelope.path("message").stringValue().contains(absoluteKeyFile), outputText);
  }
}
