package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        new FinGrindCli(
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
        new FinGrindCli(
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
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              "open-book", "--book-file", bookFilePath.toString(), "--book-passphrase-prompt"
            });

    assertEquals(2, exitCode);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputStream.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.INTERACTIVE_PROMPT_UNAVAILABLE.code(),
        failureEnvelope.path("code").asString());
    assertTrue(
        failureEnvelope
            .path("message")
            .asString()
            .contains(
                "FinGrind cannot prompt for a book passphrase because no interactive console is available."));
    assertTrue(failureEnvelope.path("hint").asString().contains("--book-key-file"));
  }

  @Test
  void run_openBookAndListAccountsThroughDefaultSqliteWorkflowSupportsInteractivePromptPassphrase()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("prompt-books").resolve("entity.sqlite");
    CliBookPassphraseResolver.Terminal terminal =
        prompt -> ContractDecision.accepted(TEST_BOOK_KEY.toCharArray());

    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        new FinGrindCli(
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
        new FinGrindCli(
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
}
