package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests the generated-key contract of the `rekey-book` argument parser. */
class CliRekeyArgumentParsingTest extends CliArgumentParsingTestSupport {
  @Test
  void parse_acceptsOneCurrentPassphraseSourceAndOneAbsentGeneratedKeyTarget() {
    RekeyBook command =
        assertInstanceOf(
            RekeyBook.class,
            CliArguments.parse(
                new String[] {
                  "rekey-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-key-file",
                  "current.key",
                  "--new-book-key-file",
                  "replacement.key",
                  "--attestation-principal-id",
                  "10213243-5465-7687-98a9-babcbddceeff",
                  "--attestation-key-file",
                  "operator.fgatk",
                  "--attestation-passphrase-file",
                  "operator.passphrase"
                }));

    assertEquals(Path.of("book.sqlite"), command.bookAccess().bookFilePath());
    assertEquals(
        Path.of("current.key"), assertKeyFileSource(command.bookAccess()).bookKeyFilePath());
    assertEquals(Path.of("replacement.key"), command.newBookKeyFilePath());
  }

  @Test
  void parse_acceptsStandardInputOrPromptOnlyForTheCurrentPassphrase() {
    RekeyBook standardInputCommand =
        assertInstanceOf(
            RekeyBook.class,
            CliArguments.parse(
                new String[] {
                  "rekey-book",
                  "--book-file",
                  "book.sqlite",
                  "--book-passphrase-stdin",
                  "--new-book-key-file",
                  "replacement.key",
                  "--attestation-principal-id",
                  "10213243-5465-7687-98a9-babcbddceeff",
                  "--attestation-key-file",
                  "operator.fgatk",
                  "--attestation-passphrase-file",
                  "operator.passphrase"
                }));
    assertEquals(
        dev.erst.fingrind.contract.runtime.BookAccess.PassphraseSource.StandardInput.INSTANCE,
        standardInputCommand.bookAccess().passphraseSource());
  }

  @Test
  void parse_rejectsMissingOrDuplicateGeneratedKeyTarget() {
    CliArgumentsException missing =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "current.key",
                      "--attestation-principal-id",
                      "10213243-5465-7687-98a9-babcbddceeff",
                      "--attestation-key-file",
                      "operator.fgatk",
                      "--attestation-passphrase-file",
                      "operator.passphrase"
                    }));
    assertEquals("--new-book-key-file", missing.argument());

    CliArgumentsException duplicate =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "current.key",
                      "--new-book-key-file",
                      "replacement-a.key",
                      "--new-book-key-file",
                      "replacement-b.key",
                      "--attestation-principal-id",
                      "10213243-5465-7687-98a9-babcbddceeff",
                      "--attestation-key-file",
                      "operator.fgatk",
                      "--attestation-passphrase-file",
                      "operator.passphrase"
                    }));
    assertEquals("--new-book-key-file", duplicate.argument());
    assertEquals("Duplicate argument: --new-book-key-file", duplicate.getMessage());
  }

  @Test
  void parse_rejectsMissingBookFileAndCurrentPassphraseSource() {
    CliArgumentsException missingBookFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-key-file",
                      "current.key",
                      "--new-book-key-file",
                      "replacement.key",
                      "--attestation-principal-id",
                      "10213243-5465-7687-98a9-babcbddceeff",
                      "--attestation-key-file",
                      "operator.fgatk",
                      "--attestation-passphrase-file",
                      "operator.passphrase"
                    }));
    assertEquals("--book-file", missingBookFile.argument());

    CliArgumentsException missingCurrentPassphraseSource =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--new-book-key-file",
                      "replacement.key",
                      "--attestation-principal-id",
                      "10213243-5465-7687-98a9-babcbddceeff",
                      "--attestation-key-file",
                      "operator.fgatk",
                      "--attestation-passphrase-file",
                      "operator.passphrase"
                    }));
    assertEquals("--book-key-file", missingCurrentPassphraseSource.argument());
  }

  @Test
  void parse_rejectsAReplacementPassphraseOptionAndAliasingTheCurrentKey() {
    CliArgumentsException removedOption =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "current.key",
                      "--new-book-passphrase-prompt",
                      "--attestation-principal-id",
                      "10213243-5465-7687-98a9-babcbddceeff",
                      "--attestation-key-file",
                      "operator.fgatk",
                      "--attestation-passphrase-file",
                      "operator.passphrase"
                    }));
    assertEquals("--new-book-passphrase-prompt", removedOption.argument());

    CliArgumentsException aliasedKey =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "shared.key",
                      "--new-book-key-file",
                      "shared.key",
                      "--attestation-principal-id",
                      "10213243-5465-7687-98a9-babcbddceeff",
                      "--attestation-key-file",
                      "operator.fgatk",
                      "--attestation-passphrase-file",
                      "operator.passphrase"
                    }));
    assertEquals("--new-book-key-file", aliasedKey.argument());
    assertEquals(ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.code(), aliasedKey.code());
    assertTrue(
        java.util.Objects.requireNonNull(aliasedKey.getMessage()).contains("--book-key-file"));
  }

  @Test
  void parse_rejectsARekeyWithoutAnAttestationCredentialTriple() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "rekey-book",
                      "--book-file",
                      "book.sqlite",
                      "--book-key-file",
                      "current.key",
                      "--new-book-key-file",
                      "replacement.key"
                    }));

    assertEquals("--attestation-principal-id", exception.argument());
  }
}
