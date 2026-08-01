package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Validates CLI-owned failure and input parsing transport boundaries. */
class CliInputJsonModelValidationTest {
  @Test
  void cliFailure_normalizesTextAndRejectsBlankFields() {
    CliFailure failure =
        new CliFailure(
            " invalid-request ",
            " Message ",
            null,
            " --limit ",
            new CliErrorJsonModels.InvalidRequestDetails(List.of("One problem.")));
    assertEquals("invalid-request", failure.code());
    assertEquals("Message", failure.message());
    assertEquals("--limit", failure.argument());
    assertEquals(
        List.of("One problem."),
        assertInstanceOf(CliErrorJsonModels.InvalidRequestDetails.class, failure.details())
            .violations());
    assertThrows(IllegalArgumentException.class, () -> new CliFailure(" ", "message", null, null));
    assertThrows(
        IllegalArgumentException.class, () -> new CliFailure("invalid-request", " ", null, null));
    CliFailure blankOptionalFields = new CliFailure("invalid-request", "message", " ", " ");
    assertEquals("invalid-request", blankOptionalFields.code());
    assertEquals("message", blankOptionalFields.message());
    assertEquals(null, blankOptionalFields.hint());
    assertEquals(null, blankOptionalFields.argument());
  }

  @Test
  void cliFailure_preservesTypedInvalidJsonDetails() {
    CliFailure failure =
        new CliFailure(
            " invalid-request ",
            " Message ",
            null,
            null,
            new CliErrorJsonModels.InvalidJsonDetails(" Unexpected token ", 3, 14));
    assertEquals("invalid-request", failure.code());
    CliErrorJsonModels.InvalidJsonDetails details =
        assertInstanceOf(CliErrorJsonModels.InvalidJsonDetails.class, failure.details());
    assertEquals("Unexpected token", details.parseMessage());
    assertEquals(3, details.line());
    assertEquals(14, details.column());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.InvalidJsonDetails("Unexpected token", 0, 14));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliErrorJsonModels.InvalidJsonDetails("Unexpected token", 3, 0));
  }

  @Test
  void parsedBookArguments_rejectNullCommandArguments() {
    assertEquals(
        "commandArguments",
        assertThrows(
                NullPointerException.class,
                () ->
                    new CliBookArgumentParser.ParsedBookArguments(
                        new BookAccess(
                            Path.of("book.sqlite"),
                            BookAccess.PassphraseSource.StandardInput.INSTANCE,
                            java.util.List.of()),
                        nullOf(),
                        nullOf()))
            .getMessage());
  }

  @Test
  void scalarParsers_rejectUnsupportedAndParserFailureCases() {
    IllegalArgumentException unsupportedValue =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliJsonScalarParsers.parseWireValue(
                    "BAD", "pricingMode", List.of("GOOD"), value -> value));
    IllegalArgumentException parserFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliJsonScalarParsers.parseWireValue(
                    "GOOD",
                    "pricingMode",
                    List.of("GOOD"),
                    value -> {
                      throw new IllegalArgumentException("parser failed");
                    }));

    assertEquals(
        "Unsupported value for pricingMode: BAD. Accepted values: GOOD.",
        unsupportedValue.getMessage());
    assertEquals(
        "Unsupported value for pricingMode: GOOD. Accepted values: GOOD.",
        parserFailure.getMessage());
    assertInstanceOf(IllegalArgumentException.class, parserFailure.getCause());
  }
}
