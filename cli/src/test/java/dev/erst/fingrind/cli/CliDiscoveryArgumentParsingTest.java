package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliArguments}. */
class CliDiscoveryArgumentParsingTest extends CliArgumentParsingTestSupport {

  @Test
  void parse_returnsHelpWhenArgumentsAreEmpty() {
    assertInstanceOf(Help.class, CliArguments.parse(new String[0]));
  }

  @Test
  void parse_returnsCapabilitiesWhenCommandIsCapabilities() {
    assertInstanceOf(Capabilities.class, CliArguments.parse(new String[] {"capabilities"}));
  }

  @Test
  void parse_returnsHelpForFlagAlias() {
    assertInstanceOf(Help.class, CliArguments.parse(new String[] {"--help"}));
  }

  @Test
  void parse_returnsVersionForFlagAlias() {
    assertInstanceOf(Version.class, CliArguments.parse(new String[] {"--version"}));
  }

  @Test
  void parse_returnsPrintRequestTemplateForCommand() {
    assertInstanceOf(
        PrintRequestTemplate.class, CliArguments.parse(new String[] {"print-request-template"}));
  }

  @Test
  void parse_rejectsAdditionalArgumentsForSingleTokenCommands() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"print-request-template", "--unexpected"}));

    assertEquals("invalid-request", exception.failure().code());
    assertEquals("--unexpected", exception.failure().argument());
    assertTrue(exception.failure().message().contains("does not accept additional arguments"));
  }

  @Test
  void cliArgumentsException_buildsCliFailureWithMetadataAndCause() {
    CliArgumentsException exception =
        new CliArgumentsException(
            "invalid-request",
            "--limit",
            "Option must be an integer: --limit",
            "Run 'fingrind help' to inspect the supported command syntax.",
            new NumberFormatException("boom"));

    CliFailure failure = exception.failure();

    assertEquals("invalid-request", exception.code());
    assertEquals("--limit", exception.argument());
    assertEquals("Run 'fingrind help' to inspect the supported command syntax.", exception.hint());
    assertEquals("invalid-request", failure.code());
    assertEquals("--limit", failure.argument());
    assertEquals("Option must be an integer: --limit", failure.message());
  }

  @Test
  void parse_rejectsAdditionalArgumentForSingleTokenCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"capabilities", "--extra"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--extra", exception.argument());
    assertEquals("Unsupported argument: --extra", exception.getMessage());
  }

  @Test
  void parse_rejectsUnknownCommand() {
    CliArgumentsException exception =
        assertThrows(CliArgumentsException.class, () -> CliArguments.parse(new String[] {"wat"}));

    assertEquals("unknown-command", exception.code());
    assertEquals("wat", exception.argument());
    assertEquals("Unsupported command: wat", exception.getMessage());
    assertEquals(
        "Run 'fingrind help' to inspect the supported commands and examples.", exception.hint());
  }
}
