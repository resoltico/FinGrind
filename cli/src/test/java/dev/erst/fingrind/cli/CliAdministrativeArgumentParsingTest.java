package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliArguments}. */
class CliAdministrativeArgumentParsingTest extends CliArgumentParsingTestSupport {

  @Test
  void parse_supportsTightenParentsForKeyGenerationAndBookOpening() {
    GenerateBookKeyFile generateBookKeyFile =
        assertInstanceOf(
            GenerateBookKeyFile.class,
            CliArguments.parse(
                new String[] {
                  "generate-book-key-file",
                  "--new-book-key-file",
                  "books/entity.book-key",
                  "--tighten-parents"
                }));
    OpenBook openBook =
        assertInstanceOf(
            OpenBook.class,
            CliArguments.parse(
                new String[] {
                  "open-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
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
                  "2026-01-01",
                  "--tighten-parents"
                }));

    assertTrue(generateBookKeyFile.tightenParents());
    assertTrue(openBook.tightenParents());
  }

  @Test
  void parse_returnsGenerateBookKeyFileForValidCommand() {
    GenerateBookKeyFile command =
        assertInstanceOf(
            GenerateBookKeyFile.class,
            CliArguments.parse(
                new String[] {
                  "generate-book-key-file", "--new-book-key-file", "books/entity.book-key"
                }));

    assertEquals(Path.of("books/entity.book-key"), command.bookKeyFilePath());
    assertEquals(OutputMode.TEXT, command.outputMode());
  }

  @Test
  void parse_supportsTextOutputForAdministrativeAndWriteCommands() {
    GenerateBookKeyFile generateBookKeyFile =
        assertInstanceOf(
            GenerateBookKeyFile.class,
            CliArguments.parse(
                new String[] {
                  "generate-book-key-file",
                  "--new-book-key-file",
                  "books/entity.book-key",
                  "--output",
                  "text"
                }));
    OpenBook openBook =
        assertInstanceOf(
            OpenBook.class,
            CliArguments.parse(
                new String[] {
                  "open-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
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
                  "2026-01-01",
                  "--output",
                  "text"
                }));
    DeclareAccount declareAccount =
        assertInstanceOf(
            DeclareAccount.class,
            CliArguments.parse(
                new String[] {
                  "declare-account",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "account.json",
                  "--output",
                  "text"
                }));
    RekeyBook rekeyBook =
        assertInstanceOf(
            RekeyBook.class,
            CliArguments.parse(
                new String[] {
                  "rekey-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--new-book-key-file",
                  "book-new.key",
                  "--output",
                  "text"
                }));
    PostEntry postEntry =
        assertInstanceOf(
            PostEntry.class,
            CliArguments.parse(
                new String[] {
                  "post-entry",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "entry.json",
                  "--output",
                  "text"
                }));

    assertEquals(OutputMode.TEXT, generateBookKeyFile.outputMode());
    assertEquals(OutputMode.TEXT, openBook.outputMode());
    assertEquals(OutputMode.TEXT, declareAccount.outputMode());
    assertEquals(OutputMode.TEXT, rekeyBook.outputMode());
    assertEquals(OutputMode.TEXT, postEntry.outputMode());
    InterimResultSweep transferPeriod =
        assertInstanceOf(
            InterimResultSweep.class,
            CliArguments.parse(
                new String[] {
                  "interim-result-sweep",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--through",
                  "2026-04-30",
                  "--output",
                  "text"
                }));
    assertEquals(OutputMode.TEXT, transferPeriod.outputMode());
  }

  @Test
  void parse_rejectsCsvOutputForAdministrativeAndWriteCommands() {
    CliArgumentsException generateCsv =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "generate-book-key-file",
                      "--new-book-key-file",
                      "books/entity.book-key",
                      "--output",
                      "csv"
                    }));
    CliArgumentsException openCsv =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
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
                      "2026-01-01",
                      "--output",
                      "csv"
                    }));

    assertEquals("--output", generateCsv.argument());
    assertEquals("--output", openCsv.argument());
  }

  @Test
  void parse_rejectsUnsupportedExtraArgumentsForBookOnlyAndStrictRequestCommands() {
    CliArgumentsException openBookExtra =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
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
                      "2026-01-01",
                      "--extra"
                    }));
    CliArgumentsException executePlanExtra =
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
                      "--extra"
                    }));
    CliArgumentsException declareAccountExtra =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "declare-account",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--request-file",
                      "declare-account.json",
                      "--extra"
                    }));

    assertEquals("--extra", openBookExtra.argument());
    assertEquals("Unsupported argument: --extra", openBookExtra.getMessage());
    assertEquals("--extra", executePlanExtra.argument());
    assertEquals("Unsupported argument: --extra", executePlanExtra.getMessage());
    assertEquals("--extra", declareAccountExtra.argument());
    assertEquals("Unsupported argument: --extra", declareAccountExtra.getMessage());
  }

  @Test
  void parse_returnsDeclareAccountForValidRequestBoundCommand() {
    DeclareAccount command =
        assertInstanceOf(
            DeclareAccount.class,
            CliArguments.parse(
                new String[] {
                  "declare-account",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "account.json"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(Path.of("account.json"), command.requestFile());
  }

  @Test
  void parse_returnsPreflightEntryForValidEntryCommand() {
    PreflightEntry command =
        assertInstanceOf(
            PreflightEntry.class,
            CliArguments.parse(
                new String[] {
                  "preflight-entry",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "request.json"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(Path.of("request.json"), command.requestFile());
  }

  @Test
  void parse_returnsPostEntryForValidEntryCommand() {
    PostEntry command =
        assertInstanceOf(
            PostEntry.class,
            CliArguments.parse(
                new String[] {
                  "post-entry",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--request-file",
                  "request.json"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(Path.of("request.json"), command.requestFile());
  }

  @Test
  void parse_returnsRecordEntryForCreditAndSettlementRecordCommands() {
    for (String commandName :
        List.of(
            "record-sale-on-credit",
            "record-expense-on-credit",
            "record-receipt",
            "record-payment")) {
      RecordEntry command = parseRecordEntry(commandName);

      assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
      assertEquals(
          Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
      assertEquals(Path.of("request.json"), command.requestFile());
      assertEquals(OutputMode.TEXT, command.outputMode());
    }
  }

  private static RecordEntry parseRecordEntry(String commandName) {
    return assertInstanceOf(
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
  }

  @Test
  void parse_returnsInterimResultSweepForValidAdministrativeCommand() {
    InterimResultSweep command =
        assertInstanceOf(
            InterimResultSweep.class,
            CliArguments.parse(
                new String[] {
                  "interim-result-sweep",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--through",
                  "2026-04-30"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(LocalDate.parse("2026-04-30"), command.throughEffectiveDate());
    assertEquals(OutputMode.TEXT, command.outputMode());
  }

  @Test
  void parse_rejectsInvalidInterimResultSweepArguments() {
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "interim-result-sweep",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "interim-result-sweep",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--through",
                  "not-a-date"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "interim-result-sweep",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--through",
                  "2026-04-29",
                  "--result-holding-account",
                  "3200",
                  "--through",
                  "2026-04-30"
                }));
    CliArgumentsException removedResultHoldingAccount =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "interim-result-sweep",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--through",
                      "2026-04-30",
                      "--result-holding-account",
                      "3200"
                    }));
    assertEquals("--result-holding-account", removedResultHoldingAccount.argument());
  }

  @Test
  void parse_returnsOpenBookForStandardInputPassphraseSource() {
    OpenBook command =
        assertInstanceOf(
            OpenBook.class,
            CliArguments.parse(
                new String[] {
                  "open-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-passphrase-stdin",
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
                  "2026-01-01",
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        BookAccess.PassphraseSource.StandardInput.INSTANCE,
        command.bookAccess().passphraseSource());
  }

  @Test
  void parseInterimResultSweep_acceptsTextOutputAndRejectsUnsupportedArguments() {
    InterimResultSweep transferPeriod =
        assertInstanceOf(
            InterimResultSweep.class,
            CliArguments.parse(
                new String[] {
                  "interim-result-sweep",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--through",
                  "2026-04-30",
                  "--output",
                  "text"
                }));
    CliInterimResultSweepArguments.ParsedInterimResultSweepArguments parsedArguments =
        CliInterimResultSweepArguments.parseInterimResultSweepArguments(
            List.of("--through", "2026-04-30"));
    CliArgumentsException unsupportedArgument =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliInterimResultSweepArguments.parseInterimResultSweepArguments(
                    List.of("--unexpected", "value")));

    assertEquals(OutputMode.TEXT, transferPeriod.outputMode());
    assertEquals(LocalDate.parse("2026-04-30"), parsedArguments.throughEffectiveDate());
    assertEquals("--unexpected", unsupportedArgument.argument());
    assertEquals("Unsupported argument: --unexpected", unsupportedArgument.getMessage());
  }

  @Test
  void parseInterimResultSweep_rejectsDuplicateThroughArgument() {
    CliArgumentsException duplicateThrough =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliInterimResultSweepArguments.parseInterimResultSweepArguments(
                    List.of("--through", "2026-04-29", "--through", "2026-04-30")));

    assertEquals("--through", duplicateThrough.argument());
    assertEquals("Duplicate argument: --through", duplicateThrough.getMessage());
  }

  @Test
  void parseInterimResultSweep_rejectsRemovedPeriodBoundaryArguments() {
    CliArgumentsException removedBoundary =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliInterimResultSweepArguments.parseInterimResultSweepArguments(
                    List.of("--period-start", "2026-04-01", "--period-end", "2026-04-30")));

    assertEquals("--period-start", removedBoundary.argument());
    assertEquals("Unsupported argument: --period-start", removedBoundary.getMessage());
  }

  @Test
  void parse_returnsOpenBookForInteractivePromptPassphraseSource() {
    OpenBook command =
        assertInstanceOf(
            OpenBook.class,
            CliArguments.parse(
                new String[] {
                  "open-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-passphrase-prompt",
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
                  "2026-01-01",
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE,
        command.bookAccess().passphraseSource());
  }

  @Test
  void parse_rejectsRequestFileOnBookOnlyCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
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
                      "2026-01-01",
                      "--request-file",
                      "oops.json"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--request-file", exception.argument());
    assertEquals("Unsupported argument: --request-file", exception.getMessage());
  }

  @Test
  void parse_rejectsMissingOpenBookIdentityArguments() {
    CliArgumentsException missingEntityName =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--book-start-effective-date",
                      "2026-01-01",
                    }));
    CliArgumentsException missingFunctionalCurrency =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--entity-name",
                      "Acme Studio",
                      "--book-template-id",
                      "OWNER_MANAGED_SERVICE",
                      "--accounting-basis",
                      "CASH",
                      "--fiscal-year-start",
                      "01-01",
                      "--book-start-effective-date",
                      "2026-01-01"
                    }));
    CliArgumentsException missingAccountingBasis =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--entity-name",
                      "Acme Studio",
                      "--book-template-id",
                      "OWNER_MANAGED_SERVICE",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01"
                    }));
    CliArgumentsException missingBookTemplateId =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--entity-name",
                      "Acme Studio",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--book-start-effective-date",
                      "2026-01-01"
                    }));
    CliArgumentsException missingFiscalYearStart =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--entity-name",
                      "Acme Studio",
                      "--book-template-id",
                      "OWNER_MANAGED_SERVICE",
                      "--accounting-basis",
                      "CASH",
                      "--functional-currency",
                      "EUR",
                    }));
    CliArgumentsException missingBookStartEffectiveDate =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "open-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--entity-name",
                      "Acme Studio",
                      "--book-template-id",
                      "OWNER_MANAGED_SERVICE",
                      "--accounting-basis",
                      "CASH",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01"
                    }));
    assertEquals("--entity-name", missingEntityName.argument());
    assertEquals("A --entity-name argument is required.", missingEntityName.getMessage());
    assertEquals("--book-template-id", missingBookTemplateId.argument());
    assertEquals("A --book-template-id argument is required.", missingBookTemplateId.getMessage());
    assertEquals("--functional-currency", missingFunctionalCurrency.argument());
    assertEquals(
        "A --functional-currency argument is required.", missingFunctionalCurrency.getMessage());
    assertEquals("--accounting-basis", missingAccountingBasis.argument());
    String missingAccountingBasisMessage = "A --accounting-basis argument is required.";
    assertEquals(missingAccountingBasisMessage, missingAccountingBasis.getMessage());
    assertEquals("--fiscal-year-start", missingFiscalYearStart.argument());
    assertEquals(
        "A --fiscal-year-start argument is required.", missingFiscalYearStart.getMessage());
    assertEquals("--book-start-effective-date", missingBookStartEffectiveDate.argument());
    assertEquals(
        "A --book-start-effective-date argument is required.",
        missingBookStartEffectiveDate.getMessage());
  }
}
