package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.ReportingObligationStatus;
import dev.erst.fingrind.core.TaxRegistrationStatus;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
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
  void parse_supportsHumanOutputForAdministrativeAndWriteCommands() {
    GenerateBookKeyFile generateBookKeyFile =
        assertInstanceOf(
            GenerateBookKeyFile.class,
            CliArguments.parse(
                new String[] {
                  "generate-book-key-file",
                  "--book-key-file",
                  "books/entity.book-key",
                  "--output",
                  "human"
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
                  "--entity-form",
                  "COMPANY",
                  "--owner-model",
                  "MULTI_OWNER",
                  "--reporting-obligation-status",
                  "INTERNAL_MANAGEMENT_ONLY",
                  "--tax-registration-status",
                  "UNSPECIFIED",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                  "--accounting-basis",
                  "ACCRUAL",
                  "--output",
                  "human"
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
                  "human"
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
                  "human"
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
                  "human"
                }));

    assertEquals(OutputMode.HUMAN, generateBookKeyFile.outputMode());
    assertEquals(OutputMode.HUMAN, openBook.outputMode());
    assertEquals(OutputMode.HUMAN, declareAccount.outputMode());
    assertEquals(OutputMode.HUMAN, rekeyBook.outputMode());
    assertEquals(OutputMode.HUMAN, postEntry.outputMode());
    ClosePeriod closePeriod =
        assertInstanceOf(
            ClosePeriod.class,
            CliArguments.parse(
                new String[] {
                  "close-period",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--closing-equity-account",
                  "3200",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30",
                  "--output",
                  "human"
                }));
    assertEquals(OutputMode.HUMAN, closePeriod.outputMode());
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
                      "--entity-form",
                      "COMPANY",
                      "--owner-model",
                      "MULTI_OWNER",
                      "--reporting-obligation-status",
                      "INTERNAL_MANAGEMENT_ONLY",
                      "--tax-registration-status",
                      "UNSPECIFIED",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--accounting-basis",
                      "ACCRUAL",
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
                      "--entity-form",
                      "COMPANY",
                      "--owner-model",
                      "MULTI_OWNER",
                      "--reporting-obligation-status",
                      "INTERNAL_MANAGEMENT_ONLY",
                      "--tax-registration-status",
                      "UNSPECIFIED",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--accounting-basis",
                      "ACCRUAL",
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
                      "human"
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
                  "--entity-form",
                  "COMPANY",
                  "--owner-model",
                  "MULTI_OWNER",
                  "--reporting-obligation-status",
                  "INTERNAL_MANAGEMENT_ONLY",
                  "--tax-registration-status",
                  "UNSPECIFIED",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                  "--accounting-basis",
                  "ACCRUAL"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(bookIdentity(), command.command().bookIdentity());
  }

  @Test
  void parse_openBook_defaultsOptionalPolicyFieldsAndCollectsBusinessActivityTags() {
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
                  "--entity-form",
                  "COMPANY",
                  "--business-activity-tag",
                  "translation,localization",
                  "--business-activity-tag",
                  "cafe services",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                  "--accounting-basis",
                  "ACCRUAL"
                }));

    assertEquals(OwnerModel.UNKNOWN, command.command().bookIdentity().entityProfile().ownerModel());
    assertEquals(
        ReportingObligationStatus.UNSPECIFIED,
        command.command().bookIdentity().entityProfile().reportingObligationStatus());
    assertEquals(
        TaxRegistrationStatus.UNSPECIFIED,
        command.command().bookIdentity().entityProfile().taxRegistrationStatus());
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
  void parse_returnsClosePeriodForValidAdministrativeCommand() {
    ClosePeriod command =
        assertInstanceOf(
            ClosePeriod.class,
            CliArguments.parse(
                new String[] {
                  "close-period",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--closing-equity-account",
                  "3200",
                  "--effective-date-from",
                  "2026-04-01",
                  "--effective-date-to",
                  "2026-04-30"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(
        new dev.erst.fingrind.core.AccountCode("3200"), command.closingEquityAccountCode());
    assertEquals(LocalDate.parse("2026-04-01"), command.reportingPeriod().effectiveDateFrom());
    assertEquals(LocalDate.parse("2026-04-30"), command.reportingPeriod().effectiveDateTo());
    assertEquals(OutputMode.JSON, command.outputMode());
  }

  @Test
  void parse_rejectsInvalidClosePeriodArguments() {
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "close-period",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--closing-equity-account",
                  "3200",
                  "--effective-date-to",
                  "2026-04-30"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "close-period",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--closing-equity-account",
                  "3200",
                  "--effective-date-from",
                  "2026-04-01"
                }));
    assertThrows(
        CliArgumentsException.class,
        () ->
            CliArguments.parse(
                new String[] {
                  "close-period",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
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
                  "close-period",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "book.key",
                  "--closing-equity-account",
                  "3200",
                  "--closing-equity-account",
                  "3201",
                  "--effective-date-from",
                  "2026-04-30",
                  "--effective-date-to",
                  "2026-04-01"
                }));
    CliArgumentsException invalidRetainedEarningsAccount =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "close-period",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "book.key",
                      "--closing-equity-account",
                      "bad account",
                      "--effective-date-from",
                      "2026-04-01",
                      "--effective-date-to",
                      "2026-04-30"
                    }));
    assertEquals("--closing-equity-account", invalidRetainedEarningsAccount.argument());
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
                  "--entity-form",
                  "COMPANY",
                  "--owner-model",
                  "MULTI_OWNER",
                  "--reporting-obligation-status",
                  "INTERNAL_MANAGEMENT_ONLY",
                  "--tax-registration-status",
                  "UNSPECIFIED",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                  "--accounting-basis",
                  "ACCRUAL"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        BookAccess.PassphraseSource.StandardInput.INSTANCE,
        command.bookAccess().passphraseSource());
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
                  "--entity-form",
                  "COMPANY",
                  "--owner-model",
                  "MULTI_OWNER",
                  "--reporting-obligation-status",
                  "INTERNAL_MANAGEMENT_ONLY",
                  "--tax-registration-status",
                  "UNSPECIFIED",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                  "--accounting-basis",
                  "ACCRUAL"
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
                      "--entity-form",
                      "COMPANY",
                      "--owner-model",
                      "MULTI_OWNER",
                      "--reporting-obligation-status",
                      "INTERNAL_MANAGEMENT_ONLY",
                      "--tax-registration-status",
                      "UNSPECIFIED",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--accounting-basis",
                      "ACCRUAL",
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
                      "--entity-form",
                      "COMPANY",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--accounting-basis",
                      "ACCRUAL"
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
                      "--entity-form",
                      "COMPANY",
                      "--fiscal-year-start",
                      "01-01",
                      "--accounting-basis",
                      "ACCRUAL"
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
                      "--entity-form",
                      "COMPANY",
                      "--functional-currency",
                      "EUR",
                      "--accounting-basis",
                      "ACCRUAL"
                    }));
    CliArgumentsException missingEntityForm =
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
                      "--accounting-basis",
                      "ACCRUAL"
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
                      "--entity-form",
                      "COMPANY",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01"
                    }));

    assertEquals("--entity-name", missingEntityName.argument());
    assertEquals("A --entity-name argument is required.", missingEntityName.getMessage());
    assertEquals("--functional-currency", missingFunctionalCurrency.argument());
    assertEquals(
        "A --functional-currency argument is required.", missingFunctionalCurrency.getMessage());
    assertEquals("--fiscal-year-start", missingFiscalYearStart.argument());
    assertEquals(
        "A --fiscal-year-start argument is required.", missingFiscalYearStart.getMessage());
    assertEquals("--entity-form", missingEntityForm.argument());
    assertEquals("A --entity-form argument is required.", missingEntityForm.getMessage());
    assertEquals("--accounting-basis", missingAccountingBasis.argument());
    assertEquals("A --accounting-basis argument is required.", missingAccountingBasis.getMessage());
  }
}
