package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliArguments}. */
class CliAdministrativeArgumentParsingTest extends CliArgumentParsingTestSupport {

  @Test
  void parse_returnsGenerateBookKeyFileForValidCommand() {
    GenerateBookKeyFile command =
        assertInstanceOf(
            GenerateBookKeyFile.class,
            CliArguments.parse(
                new String[] {
                  "generate-book-key-file", "--book-key-file", "books/entity.book-key"
                }));

    assertEquals(Path.of("books/entity.book-key"), command.bookKeyFilePath());
    assertEquals(OutputMode.JSON, command.outputMode());
  }

  @Test
  void parse_supportsTextOutputForAdministrativeAndWriteCommands() {
    GenerateBookKeyFile generateBookKeyFile =
        assertInstanceOf(
            GenerateBookKeyFile.class,
            CliArguments.parse(
                new String[] {
                  "generate-book-key-file",
                  "--book-key-file",
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
                  "--business-activity-tag",
                  "translation-services",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
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
                  "--replacement-book-key-file",
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
    TransferPeriodResult transferPeriod =
        assertInstanceOf(
            TransferPeriodResult.class,
            CliArguments.parse(
                new String[] {
                  "transfer-period-result",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
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
                      "--book-key-file",
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
                      "--business-activity-tag",
                      "translation-services",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--output",
                      "csv"
                    }));

    assertEquals("--output", generateCsv.argument());
    assertEquals("--output", openCsv.argument());
  }

  @Test
  void parse_openBook_usesTheBuiltInKernelWithoutExtraProfileArguments() {
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
                  "--business-activity-tag",
                  "translation-services",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                }));

    assertEquals(bookIdentity(), openBook.command().bookIdentity());
  }

  @Test
  void parse_openBook_rejectsRemovedTaxProfileFileArgument() throws Exception {
    Path taxProfileFile = writeRequest("{\"registrations\":[]}");

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
                      "--business-activity-tag",
                      "translation-services",
                      "--tax-profile-file",
                      taxProfileFile.toString(),
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                    }));

    assertEquals("--tax-profile-file", exception.argument());
    assertEquals("Unsupported argument: --tax-profile-file", exception.getMessage());
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
                      "--business-activity-tag",
                      "translation-services",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
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
                      "--output",
                      "text"
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
    assertEquals("--output", executePlanExtra.argument());
    assertEquals("Unsupported argument: --output", executePlanExtra.getMessage());
    assertEquals("--extra", declareAccountExtra.argument());
    assertEquals("Unsupported argument: --extra", declareAccountExtra.getMessage());
  }

  @Test
  void openBookArgumentGuard_rejectsUnexpectedCommandArgumentsDefensively() throws Exception {
    CliOpenBookArguments.OpenBookArgumentValues argumentValues =
        new CliOpenBookArguments.OpenBookArgumentValues();
    ListIterator<String> emptyIterator = List.<String>of().listIterator();

    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliOpenBookArguments.applyOpenBookArgument(
                    argumentValues, "--unexpected", emptyIterator));
    assertEquals("--unexpected", exception.argument());
    assertEquals("Unsupported argument: --unexpected", exception.getMessage());
  }

  @Test
  void parse_returnsOpenBookForValidBookOnlyCommand() {
    OpenBook command =
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
                  "--business-activity-tag",
                  "translation-services",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(bookIdentity(), command.command().bookIdentity());
  }

  @Test
  void parse_openBook_collectsBusinessActivityTags() {
    OpenBook command =
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
                  "--business-activity-tag",
                  "translation,localization",
                  "--business-activity-tag",
                  "cafe services",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                }));

    assertEquals(
        List.of("translation,localization", "cafe services"),
        command.command().bookIdentity().entityProfile().businessActivityTags().stream()
            .map(value -> value.value())
            .toList());
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
  void parse_returnsTransferPeriodResultForValidAdministrativeCommand() {
    TransferPeriodResult command =
        assertInstanceOf(
            TransferPeriodResult.class,
            CliArguments.parse(
                new String[] {
                  "transfer-period-result",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(LocalDate.parse("2026-04-01"), command.reportingPeriod().effectiveDateFrom());
    assertEquals(LocalDate.parse("2026-04-30"), command.reportingPeriod().effectiveDateTo());
    assertEquals(OutputMode.JSON, command.outputMode());
  }

  @Test
  void parse_rejectsInvalidTransferPeriodResultArguments() {
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "transfer-period-result",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-to",
                  "2026-04-30"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "transfer-period-result",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-from",
                  "2026-04-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "transfer-period-result",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--result-holding-account",
                  "3200",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "transfer-period-result",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-from",
                  "2026-04-30",
                  "--effective-date-to",
                  "2026-04-01"
                }));
    CliArgumentsException removedResultHoldingAccount =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "transfer-period-result",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--result-holding-account",
                      "3200",
                      "--effective-date-from",
                      "2026-04-01",
                      "--effective-date-to",
                      "2026-04-30"
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
                  "--business-activity-tag",
                  "translation-services",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        BookAccess.PassphraseSource.StandardInput.INSTANCE,
        command.bookAccess().passphraseSource());
  }

  @Test
  void parseTransferPeriodResult_acceptsTextOutputAndRejectsUnsupportedArguments() {
    TransferPeriodResult transferPeriod =
        assertInstanceOf(
            TransferPeriodResult.class,
            CliArguments.parse(
                new String[] {
                  "transfer-period-result",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "text"
                }));
    CliPeriodResultTransferArguments.ParsedTransferPeriodResultArguments parsedArguments =
        CliPeriodResultTransferArguments.parseTransferPeriodResultArguments(
            List.of("--effective-date-from", "2026-04-01", "--effective-date-to", "2026-04-30"));
    CliArgumentsException unsupportedArgument =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliPeriodResultTransferArguments.parseTransferPeriodResultArguments(
                    List.of("--unexpected", "value")));

    assertEquals(OutputMode.TEXT, transferPeriod.outputMode());
    assertEquals(
        new dev.erst.fingrind.core.ReportingPeriod(
            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        parsedArguments.reportingPeriod());
    assertEquals("--unexpected", unsupportedArgument.argument());
    assertEquals("Unsupported argument: --unexpected", unsupportedArgument.getMessage());
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
                  "--business-activity-tag",
                  "translation-services",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
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
                      "--business-activity-tag",
                      "translation-services",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
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
                    }));
    CliArgumentsException missingBusinessActivityTag =
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
                      "--business-activity-tag",
                      "translation-services",
                      "--fiscal-year-start",
                      "01-01"
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
                      "--business-activity-tag",
                      "translation-services",
                      "--functional-currency",
                      "EUR",
                    }));
    assertEquals("--entity-name", missingEntityName.argument());
    assertEquals("A --entity-name argument is required.", missingEntityName.getMessage());
    assertEquals("--business-activity-tag", missingBusinessActivityTag.argument());
    assertEquals(
        "At least one --business-activity-tag argument is required.",
        missingBusinessActivityTag.getMessage());
    assertEquals("--functional-currency", missingFunctionalCurrency.argument());
    assertEquals(
        "A --functional-currency argument is required.", missingFunctionalCurrency.getMessage());
    assertEquals("--fiscal-year-start", missingFiscalYearStart.argument());
    assertEquals(
        "A --fiscal-year-start argument is required.", missingFiscalYearStart.getMessage());
  }

  @Test
  void parse_returnsMaintenanceCommandsForValidArguments() {
    BackupBook backupBook =
        assertInstanceOf(
            BackupBook.class,
            CliArguments.parse(
                new String[] {
                  "backup-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--backup-file-out",
                  "backup/entity.sqlite",
                  "--backup-book-key-file-out",
                  "backup/entity.key",
                  "--output",
                  "text"
                }));
    RestoreBook restoreBook =
        assertInstanceOf(
            RestoreBook.class,
            CliArguments.parse(
                new String[] {
                  "restore-book",
                  "--book-file",
                  "book.sqlite",
                  "--backup-file",
                  "backup/entity.sqlite",
                  "--backup-book-key-file",
                  "backup/entity.key"
                }));
    RestoreRekeyRollback restoreRekeyRollback =
        assertInstanceOf(
            RestoreRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "restore-rekey-rollback",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--rollback-file",
                  "book.rekey-rollback.sqlite"
                }));
    DeleteRekeyRollback deleteRekeyRollback =
        assertInstanceOf(
            DeleteRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "delete-rekey-rollback",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--rollback-file",
                  "book.rekey-rollback.sqlite"
                }));

    assertEquals(Path.of("book.sqlite"), backupBook.bookAccess().bookFilePath());
    assertEquals(Path.of("backup/entity.sqlite"), backupBook.backupFilePath());
    assertEquals(Path.of("backup/entity.key"), backupBook.backupBookKeyFilePath());
    assertEquals(OutputMode.TEXT, backupBook.outputMode());

    assertEquals(Path.of("book.sqlite"), restoreBook.bookFilePath());
    assertEquals(Path.of("backup/entity.sqlite"), restoreBook.backupFilePath());
    assertEquals(Path.of("backup/entity.key"), restoreBook.backupBookKeyFilePath());
    assertEquals(OutputMode.JSON, restoreBook.outputMode());

    assertEquals(Path.of("book.sqlite"), restoreRekeyRollback.bookFilePath());
    assertEquals(
        Path.of("book.rekey-rollback.sqlite"), restoreRekeyRollback.rollbackArtifactPath());
    assertInstanceOf(
        BookAccess.PassphraseSource.KeyFile.class, restoreRekeyRollback.expectedPassphraseSource());
    assertEquals(OutputMode.JSON, restoreRekeyRollback.outputMode());

    assertEquals(Path.of("book.sqlite"), deleteRekeyRollback.bookAccess().bookFilePath());
    assertEquals(Path.of("book.rekey-rollback.sqlite"), deleteRekeyRollback.rollbackArtifactPath());
    assertInstanceOf(
        BookAccess.PassphraseSource.KeyFile.class,
        deleteRekeyRollback.bookAccess().passphraseSource());
    assertEquals(OutputMode.JSON, deleteRekeyRollback.outputMode());
  }

  @Test
  void parse_restoreRekeyRollback_acceptsStandardInputAndPromptPassphraseSources() {
    RestoreRekeyRollback standardInputRestore =
        assertInstanceOf(
            RestoreRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "restore-rekey-rollback", "--book-file", "book.sqlite", "--book-passphrase-stdin"
                }));
    RestoreRekeyRollback promptRestore =
        assertInstanceOf(
            RestoreRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "restore-rekey-rollback", "--book-file", "book.sqlite", "--book-passphrase-prompt"
                }));

    assertInstanceOf(
        BookAccess.PassphraseSource.StandardInput.class,
        standardInputRestore.expectedPassphraseSource());
    assertInstanceOf(
        BookAccess.PassphraseSource.InteractivePrompt.class,
        promptRestore.expectedPassphraseSource());
  }

  @Test
  void parse_restoreRekeyRollback_requiresOnePassphraseSource() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"restore-rekey-rollback", "--book-file", "book.sqlite"}));

    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Restore rekey rollback requires exactly one book passphrase source: --book-key-file <path>, --book-passphrase-stdin, or --book-passphrase-prompt.",
        exception.getMessage());
  }

  @Test
  void parse_deleteRekeyRollback_acceptsStandardInputAndPromptPassphraseSources() {
    DeleteRekeyRollback standardInputDelete =
        assertInstanceOf(
            DeleteRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "delete-rekey-rollback", "--book-file", "book.sqlite", "--book-passphrase-stdin"
                }));
    DeleteRekeyRollback promptDelete =
        assertInstanceOf(
            DeleteRekeyRollback.class,
            CliArguments.parse(
                new String[] {
                  "delete-rekey-rollback", "--book-file", "book.sqlite", "--book-passphrase-prompt"
                }));

    assertInstanceOf(
        BookAccess.PassphraseSource.StandardInput.class,
        standardInputDelete.bookAccess().passphraseSource());
    assertInstanceOf(
        BookAccess.PassphraseSource.InteractivePrompt.class,
        promptDelete.bookAccess().passphraseSource());
  }

  @Test
  void parse_deleteRekeyRollback_requiresOnePassphraseSource() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"delete-rekey-rollback", "--book-file", "book.sqlite"}));

    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Delete rekey rollback requires exactly one book passphrase source: --book-key-file <path>, --book-passphrase-stdin, or --book-passphrase-prompt.",
        exception.getMessage());
  }

  @Test
  void parse_inspectRekeyRollback_rejectsPassphraseSourceArguments() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "inspect-rekey-rollback",
                      "--book-file",
                      "book.sqlite",
                      "--book-passphrase-stdin"
                    }));

    assertEquals("--book-key-file", exception.argument());
    assertEquals(
        "Book passphrase source arguments are accepted only when delete-rekey-rollback or restore-rekey-rollback is selected.",
        exception.getMessage());
  }

  @Test
  void parse_rejectsMaintenancePathCollisions() {
    CliArgumentsException backupCollision =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "backup-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--backup-file-out",
                      "book.sqlite",
                      "--backup-book-key-file-out",
                      "backup.key"
                    }));
    CliArgumentsException restoreCollision =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "restore-book",
                      "--book-file",
                      "book.sqlite",
                      "--backup-file",
                      "backup.sqlite",
                      "--backup-book-key-file",
                      "backup.sqlite"
                    }));

    assertEquals("--backup-file-out", backupCollision.argument());
    assertEquals(
        "--book-file and --backup-file-out must not point to the same path.",
        backupCollision.getMessage());
    assertEquals("--backup-book-key-file", restoreCollision.argument());
    assertEquals(
        "--backup-file and --backup-book-key-file must not point to the same path.",
        restoreCollision.getMessage());
  }
}
