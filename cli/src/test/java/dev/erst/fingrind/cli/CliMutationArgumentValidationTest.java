package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliArguments}. */
class CliMutationArgumentValidationTest extends CliArgumentParsingTestSupport {

  @Test
  void parse_rejectsMissingBookKeyFileForGeneratorCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"generate-book-key-file"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--new-book-key-file", exception.argument());
    assertEquals("A --new-book-key-file argument is required.", exception.getMessage());
  }

  @Test
  void parse_rejectsDuplicateBookKeyFileForGeneratorCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "generate-book-key-file",
                      "--new-book-key-file",
                      "first.key",
                      "--new-book-key-file",
                      "second.key"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--new-book-key-file", exception.argument());
    assertEquals("Duplicate argument: --new-book-key-file", exception.getMessage());
  }

  @Test
  void parse_rejectsUnsupportedArgumentForGeneratorCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "generate-book-key-file", "--new-book-key-file", "entity.key", "--extra"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--extra", exception.argument());
    assertEquals("Unsupported argument: --extra", exception.getMessage());
  }

  @Test
  void parse_rejectsDuplicateBookFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book-a.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--book-file",
                      "book-b.sqlite",
                      "--request-file",
                      "request.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-file", exception.argument());
    assertEquals("Duplicate argument: --book-file", exception.getMessage());
    assertEquals(CliInvocationText.helpSyntaxHint(OperationId.POST_ENTRY), exception.hint());
  }

  @Test
  void parse_rejectsDuplicateRequestFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "request-a.json",
                      "--request-file",
                      "request-b.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--request-file", exception.argument());
    assertEquals("Duplicate argument: --request-file", exception.getMessage());
  }

  @Test
  void parse_rejectsDuplicateBookKeyFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "key-a.key",
                      "--book-key-file",
                      "key-b.key",
                      "--request-file",
                      "request.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Exactly one book passphrase source is permitted per command.", exception.getMessage());
  }

  @Test
  void parse_rejectsMixedBookPassphraseSources() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--book-passphrase-prompt",
                      "--request-file",
                      "request.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-passphrase-prompt", exception.argument());
    assertEquals(
        "Exactly one book passphrase source is permitted per command.", exception.getMessage());
  }

  @Test
  void parse_rejectsUnsupportedEntryArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "request.json",
                      "--wat"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--wat", exception.argument());
    assertEquals("Unsupported argument: --wat", exception.getMessage());
  }

  @Test
  void parse_rejectsUnsupportedEntryArgumentBeforeMissingRequiredBookArguments() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"post-entry", "--bogus"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--bogus", exception.argument());
    assertEquals("Unsupported argument: --bogus", exception.getMessage());
  }

  @Test
  void parse_rejectsMissingBookFileValue() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"post-entry", "--book-file"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-file", exception.argument());
    assertEquals("Missing value for --book-file.", exception.getMessage());
  }

  @Test
  void parse_rejectsMissingRequestFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry", "--book-file", "book.sqlite", "--book-key-file", "book.key"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--request-file", exception.argument());
    assertEquals("A --request-file argument is required.", exception.getMessage());
  }

  @Test
  void parse_rejectsMissingBookKeyFileArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"post-entry", "--book-file", "book.sqlite"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Exactly one book passphrase source is required: --book-key-file <path>,"
            + " --book-passphrase-stdin, or --book-passphrase-prompt.",
        exception.getMessage());
  }

  @Test
  void parse_rejectsUsingStandardInputForBothPassphraseAndRequestJson() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-passphrase-stdin",
                      "--request-file",
                      "-"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-passphrase-stdin", exception.argument());
    assertEquals(
        "Standard input cannot supply both the book passphrase and the request JSON.",
        exception.getMessage());
  }

  @Test
  void parse_acceptsRemainingTypedRecordCommands() {
    assertRecordEntry("record-purchase-settled");
    assertRecordEntry("record-purchase-on-credit");
    assertRecordEntry("record-expense-settled");
    assertRecordEntry("record-owner-contribution");
    assertRecordEntry("record-owner-withdrawal");
    assertRecordEntry("record-opening-position");
    assertRecordEntry("record-prepayment");
    assertRecordEntry("record-deferred-revenue");
    assertRecordEntry("record-accrued-expense");
    assertRecordEntry("record-accrual-cutoff-recognition");
    assertRecordEntry("record-accrued-expense-settlement");
  }

  private void assertRecordEntry(String commandName) {
    RecordEntry command =
        assertInstanceOf(
            RecordEntry.class,
            CliArguments.parse(
                new String[] {
                  commandName,
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "request.json"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("request.json"), command.requestFile());
    assertEquals(OutputMode.TEXT, command.outputMode());
  }

  @Test
  void parse_allowsRequestJsonFromStandardInputWhenPassphraseUsesPrompt() {
    PostEntry command =
        assertInstanceOf(
            PostEntry.class,
            CliArguments.parse(
                new String[] {
                  "post-entry",
                  "--book-file",
                  "book.sqlite",
                  "--book-passphrase-prompt",
                  "--request-file",
                  "-"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
        command.bookAccess().passphraseSource());
    assertEquals(Path.of("-"), command.requestFile());
  }

  @Test
  void parse_rejectsSameBookAndRequestPath() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "shared.path",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "shared.path"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--request-file", exception.argument());
    assertEquals(
        "--book-file and --request-file must not point to the same path.", exception.getMessage());
  }

  @Test
  void parse_rejectsSameBookKeyAndRequestPath() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "post-entry",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "shared.path",
                      "--request-file",
                      "shared.path"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "--book-key-file and --request-file must not point to the same path.",
        exception.getMessage());
  }

  @Test
  void parse_rejectsSameBookAndKeyPath() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "shared.path",
                      "--book-key-file",
                      "shared.path",
                      "--entity-name",
                      "Acme Studio",
                      "--book-template-id",
                      "OWNER_MANAGED_SERVICE",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "--book-file and --book-key-file must not point to the same path.", exception.getMessage());
  }

  @Test
  void parse_executePlanAcceptsResultDetailAndRejectsUnsupportedCommandArguments() {
    ExecutePlan command =
        assertInstanceOf(
            ExecutePlan.class,
            CliArguments.parse(
                new String[] {
                  "execute-plan",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "plan.json",
                  "--result-detail",
                  "full"
                }));

    assertEquals(PlanResultDetail.FULL, command.resultDetail());

    ExecutePlan defaultDetailCommand =
        assertInstanceOf(
            ExecutePlan.class,
            CliArguments.parse(
                new String[] {
                  "execute-plan",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "plan.json"
                }));
    assertEquals(PlanResultDetail.SUMMARY, defaultDetailCommand.resultDetail());

    CliArgumentsException invalidDetail =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "execute-plan",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "plan.json",
                      "--result-detail",
                      "verbose"
                    }));
    assertEquals("--result-detail", invalidDetail.argument());

    ExecutePlan jsonOutputCommand =
        assertInstanceOf(
            ExecutePlan.class,
            CliArguments.parse(
                new String[] {
                  "execute-plan",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "plan.json",
                  "--output",
                  "json"
                }));
    assertEquals(OutputMode.JSON, jsonOutputCommand.outputMode());

    CliArgumentsException unsupportedArgument =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "execute-plan",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "plan.json",
                      "--unexpected"
                    }));
    assertEquals("--unexpected", unsupportedArgument.argument());
    assertEquals("Unsupported argument: --unexpected", unsupportedArgument.getMessage());

    CliArgumentsException unknownArgument =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "execute-plan",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "plan.json",
                      "--verbose"
                    }));
    assertEquals("--verbose", unknownArgument.argument());
    assertEquals("Unsupported argument: --verbose", unknownArgument.getMessage());
  }
}
