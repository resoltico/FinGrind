package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.InventoryCostingDoctrine;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.junit.jupiter.api.Test;

/** Focused parser coverage for open-book identity and doctrine inputs. */
class CliOpenBookArgumentParsingTest extends CliArgumentParsingTestSupport {

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
                  "--attestation-founder-principal-id",
                  "123e4567-e89b-12d3-a456-426614174000",
                  "--attestation-founder-key-file",
                  "founder.fgatk",
                  "--attestation-founder-passphrase-file",
                  "founder.passphrase"
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
                      "--tax-profile-file",
                      taxProfileFile.toString(),
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--book-start-effective-date",
                      "2026-01-01",
                      "--attestation-founder-principal-id",
                      "123e4567-e89b-12d3-a456-426614174000",
                      "--attestation-founder-key-file",
                      "founder.fgatk",
                      "--attestation-founder-passphrase-file",
                      "founder.passphrase"
                    }));

    assertEquals("--tax-profile-file", exception.argument());
    assertEquals("Unsupported argument: --tax-profile-file", exception.getMessage());
  }

  @Test
  void openBookArgumentGuard_rejectsUnexpectedCommandArgumentsDefensively() throws Exception {
    CliOpenBookArgumentValues argumentValues = new CliOpenBookArgumentValues();
    ListIterator<String> emptyIterator = List.<String>of().listIterator();

    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliOpenBookArgumentGrammar.apply(argumentValues, "--unexpected", emptyIterator));
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
                  "--attestation-founder-principal-id",
                  "123e4567-e89b-12d3-a456-426614174000",
                  "--attestation-founder-key-file",
                  "founder.fgatk",
                  "--attestation-founder-passphrase-file",
                  "founder.passphrase"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(Path.of("book.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(bookIdentity(), command.command().bookIdentity());
  }

  @Test
  void parse_openBook_buildsNarrowDoctrinalIdentity() {
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
                  "--attestation-founder-principal-id",
                  "123e4567-e89b-12d3-a456-426614174000",
                  "--attestation-founder-key-file",
                  "founder.fgatk",
                  "--attestation-founder-passphrase-file",
                  "founder.passphrase"
                }));

    assertEquals(
        dev.erst.fingrind.core.BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        command.command().bookIdentity().bookDoctrine());
  }

  @Test
  void parse_openBook_buildsAccrualDoctrineForExplicitBasis() {
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
                  "--book-template-id",
                  "OWNER_MANAGED_SERVICE",
                  "--accounting-basis",
                  "ACCRUAL",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                  "--book-start-effective-date",
                  "2026-01-01",
                  "--attestation-founder-principal-id",
                  "123e4567-e89b-12d3-a456-426614174000",
                  "--attestation-founder-key-file",
                  "founder.fgatk",
                  "--attestation-founder-passphrase-file",
                  "founder.passphrase"
                }));

    assertEquals(
        dev.erst.fingrind.core.BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL,
        command.command().bookIdentity().bookDoctrine());
  }

  @Test
  void parse_openBook_requiresInventoryCostingOnlyForTradingBooks() {
    OpenBook tradingBook =
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
                  "Acme Store",
                  "--book-template-id",
                  "OWNER_MANAGED_TRADING",
                  "--accounting-basis",
                  "CASH",
                  "--inventory-costing",
                  "WEIGHTED_AVERAGE",
                  "--functional-currency",
                  "EUR",
                  "--fiscal-year-start",
                  "01-01",
                  "--book-start-effective-date",
                  "2026-01-01",
                  "--attestation-founder-principal-id",
                  "123e4567-e89b-12d3-a456-426614174000",
                  "--attestation-founder-key-file",
                  "founder.fgatk",
                  "--attestation-founder-passphrase-file",
                  "founder.passphrase"
                }));
    CliArgumentsException missingTradingCosting =
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
                      "Acme Store",
                      "--book-template-id",
                      "OWNER_MANAGED_TRADING",
                      "--accounting-basis",
                      "CASH",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--book-start-effective-date",
                      "2026-01-01",
                      "--attestation-founder-principal-id",
                      "123e4567-e89b-12d3-a456-426614174000",
                      "--attestation-founder-key-file",
                      "founder.fgatk",
                      "--attestation-founder-passphrase-file",
                      "founder.passphrase"
                    }));
    CliArgumentsException serviceCosting =
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
                      "--inventory-costing",
                      "WEIGHTED_AVERAGE",
                      "--functional-currency",
                      "EUR",
                      "--fiscal-year-start",
                      "01-01",
                      "--book-start-effective-date",
                      "2026-01-01",
                      "--attestation-founder-principal-id",
                      "123e4567-e89b-12d3-a456-426614174000",
                      "--attestation-founder-key-file",
                      "founder.fgatk",
                      "--attestation-founder-passphrase-file",
                      "founder.passphrase"
                    }));

    assertEquals(
        InventoryCostingDoctrine.WEIGHTED_AVERAGE,
        tradingBook.command().bookIdentity().bookDoctrine().inventoryCostingDoctrine());
    assertEquals("--inventory-costing", missingTradingCosting.argument());
    assertEquals(
        "Trading book doctrines require one inventoryCostingDoctrine.",
        missingTradingCosting.getMessage());
    assertEquals("--inventory-costing", serviceCosting.argument());
    assertEquals(
        "Service book doctrines must not declare an inventoryCostingDoctrine.",
        serviceCosting.getMessage());
  }
}
