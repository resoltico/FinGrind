package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
@NullUnmarked
class FinGrindCliPassphraseWorkflowTest extends FinGrindCliTestSupport {
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
        openCli.run(
            new String[] {
              "open-book", "--book-file", bookFilePath.toString(), "--book-passphrase-stdin"
            }),
        openOutput.toString(StandardCharsets.UTF_8));
    assertTrue(openOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));

    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    FinGrindCli listCli =
        cli(
            new ByteArrayInputStream((TEST_BOOK_KEY + "\n").getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(listOutput),
            fixedClock());

    assertEquals(
        0,
        listCli.run(
            new String[] {
              "list-accounts", "--book-file", bookFilePath.toString(), "--book-passphrase-stdin"
            }));
    assertTrue(listOutput.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
  }

  @Test
  void run_openBookThroughDefaultSqliteWorkflowRejectsPromptPassphraseWithoutInteractiveConsole()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("no-console-books").resolve("entity.sqlite");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              "open-book", "--book-file", bookFilePath.toString(), "--book-passphrase-prompt"
            });

    assertEquals(2, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.INTERACTIVE_PROMPT_UNAVAILABLE.code(),
        failureEnvelope.path("code").stringValue());
    assertTrue(
        failureEnvelope
            .path("message")
            .stringValue()
            .contains(
                "FinGrind cannot prompt for a book passphrase because no interactive console is available."));
    assertTrue(failureEnvelope.path("hint").stringValue().contains("--book-key-file"));
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
        openCli.run(
            new String[] {
              "open-book", "--book-file", bookFilePath.toString(), "--book-passphrase-prompt"
            }),
        openOutput.toString(StandardCharsets.UTF_8));
    assertTrue(openOutput.toString(StandardCharsets.UTF_8).contains("\"initializedAt\""));

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
              "list-accounts", "--book-file", bookFilePath.toString(), "--book-passphrase-prompt"
            }));
    assertTrue(listOutput.toString(StandardCharsets.UTF_8).contains("\"status\":\"ok\""));
  }

  @Test
  void run_listAccountsWithWrongStandardInputPassphrase_doesNotEchoSecret() throws IOException {
    Path bookFilePath = tempDirectory.resolve("wrong-stdin-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);

    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        openCli.run(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString()
            }));

    String wrongSecret = "wrong-stdin-secret";
    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    FinGrindCli listCli =
        cli(
            new ByteArrayInputStream((wrongSecret + "\n").getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(listOutput),
            fixedClock());

    assertEquals(
        2,
        listCli.run(
            new String[] {
              "list-accounts", "--book-file", bookFilePath.toString(), "--book-passphrase-stdin"
            }));

    String outputText = listOutput.toString(StandardCharsets.UTF_8);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputText);
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        failureEnvelope.path("code").stringValue());
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

    assertEquals(
        2,
        cli.run(
            new String[] {
              "open-book", "--book-file", bookFilePath.toString(), "--book-passphrase-stdin"
            }));

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
}
