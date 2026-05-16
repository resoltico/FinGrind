package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
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
  void parse_returnsScopedHelpForHelpTopic() {
    Help command =
        assertInstanceOf(Help.class, CliArguments.parse(new String[] {"help", "post-entry"}));

    assertEquals(
        dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY, command.commandTopic());
  }

  @Test
  void parse_returnsScopedHelpForCommandHelpAlias() {
    Help command =
        assertInstanceOf(Help.class, CliArguments.parse(new String[] {"post-entry", "--help"}));

    assertEquals(
        dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY, command.commandTopic());
  }

  @Test
  void parse_returnsScopedHelpForShortCommandHelpAlias() {
    Help command =
        assertInstanceOf(Help.class, CliArguments.parse(new String[] {"post-entry", "-h"}));

    assertEquals(
        dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY, command.commandTopic());
  }

  @Test
  void parse_supportsJsonOutputForCommandHelpAlias() {
    Help command =
        assertInstanceOf(
            Help.class,
            CliArguments.parse(new String[] {"post-entry", "--help", "--output", "json"}));

    assertEquals(
        dev.erst.fingrind.contract.protocol.OperationId.POST_ENTRY, command.commandTopic());
    assertEquals(dev.erst.fingrind.contract.protocol.OutputMode.JSON, command.outputMode());
  }

  @Test
  void parse_rejectsUnsupportedAdditionalArgumentForHelpTopic() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"help", "post-entry", "--wat"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--wat", exception.argument());
    assertEquals("Unsupported argument: --wat", exception.getMessage());
    assertEquals(CliInvocationText.helpSyntaxHint(OperationId.POST_ENTRY), exception.hint());
  }

  @Test
  void parse_rejectsUnsupportedAdditionalArgumentForCommandHelpAlias() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"post-entry", "--help", "--wat"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--wat", exception.argument());
    assertEquals("Unsupported argument: --wat", exception.getMessage());
    assertEquals(CliInvocationText.helpSyntaxHint(OperationId.POST_ENTRY), exception.hint());
  }

  @Test
  void parse_rejectsUnknownHelpTopic() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class, () -> CliArguments.parse(new String[] {"help", "wat"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("wat", exception.argument());
    assertEquals("Unsupported help topic: wat", exception.getMessage());
  }

  @Test
  void parse_rejectsUnknownCommandEvenWhenSecondTokenLooksLikeHelp() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class, () -> CliArguments.parse(new String[] {"wat", "--help"}));

    assertEquals("unknown-command", exception.code());
    assertEquals("wat", exception.argument());
    assertEquals("Unsupported command: wat", exception.getMessage());
  }

  @Test
  void parse_returnsPrintRequestTemplateForCommand() {
    assertInstanceOf(
        PrintRequestTemplate.class, CliArguments.parse(new String[] {"print-request-template"}));
  }

  @Test
  void parse_returnsPrintRequestTemplateForSupportedTopics() {
    PrintRequestTemplate postEntry =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "post-entry"}));
    PrintRequestTemplate declareAccount =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "declare-account"}));
    PrintRequestTemplate preflight =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "preflight-entry"}));

    assertEquals(OperationId.POST_ENTRY, postEntry.commandTopic());
    assertEquals(OperationId.DECLARE_ACCOUNT, declareAccount.commandTopic());
    assertEquals(OperationId.PREFLIGHT_ENTRY, preflight.commandTopic());
  }

  @Test
  void parse_rejectsAdditionalArgumentsForSingleTokenCommands() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"print-request-template", "post-entry", "--unexpected"}));

    assertEquals("invalid-request", exception.failure().code());
    assertEquals("--unexpected", exception.failure().argument());
    assertTrue(
        exception
            .failure()
            .message()
            .contains("accepts at most one optional request-bearing command topic"));
    assertEquals(
        CliInvocationText.helpSyntaxHint(OperationId.PRINT_REQUEST_TEMPLATE), exception.hint());
  }

  @Test
  void parse_rejectsUnsupportedRequestTemplateTopicAndListsSupportedTopics() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"print-request-template", "list-accounts"}));

    assertEquals("invalid-request", exception.failure().code());
    assertEquals("list-accounts", exception.failure().argument());
    assertTrue(exception.failure().message().contains("Unsupported request-template topic"));
    assertTrue(exception.failure().message().contains("post-entry"));
    assertTrue(exception.failure().message().contains("preflight-entry"));
    assertTrue(exception.failure().message().contains("declare-account"));
  }

  @Test
  void parse_rejectsUnknownRequestTemplateTopic() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"print-request-template", "wat"}));

    assertEquals("invalid-request", exception.failure().code());
    assertEquals("wat", exception.failure().argument());
    assertEquals("Unsupported request-template topic: wat", exception.failure().message());
  }

  @Test
  void parse_rejectsAdditionalArgumentForPrintPlanTemplate() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"print-plan-template", "--output"}));

    assertEquals("invalid-request", exception.failure().code());
    assertEquals("--output", exception.failure().argument());
    assertTrue(exception.failure().message().contains("emits fixed raw JSON"));
    assertEquals(
        CliInvocationText.helpSyntaxHint(OperationId.PRINT_PLAN_TEMPLATE), exception.hint());
  }

  @Test
  void cliArgumentsException_buildsCliFailureWithMetadataAndCause() {
    CliArgumentsException exception =
        new CliArgumentsException(
            "invalid-request",
            "--limit",
            "Option must be an integer: --limit",
            "Run '"
                + CliInvocationText.commandExample(OperationId.HELP)
                + "' to inspect the supported command syntax.",
            new NumberFormatException("boom"));

    CliFailure failure = exception.failure();

    assertEquals("invalid-request", exception.code());
    assertEquals("--limit", exception.argument());
    assertEquals(
        "Run '"
            + CliInvocationText.commandExample(OperationId.HELP)
            + "' to inspect the supported command syntax.",
        exception.hint());
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
    assertEquals(CliInvocationText.helpSyntaxHint(OperationId.CAPABILITIES), exception.hint());
  }

  @Test
  void parse_rejectsUnknownCommand() {
    CliArgumentsException exception =
        assertThrows(CliArgumentsException.class, () -> CliArguments.parse(new String[] {"wat"}));

    assertEquals("unknown-command", exception.code());
    assertEquals("wat", exception.argument());
    assertEquals("Unsupported command: wat", exception.getMessage());
    assertEquals(CliInvocationText.helpExamplesHint(), exception.hint());
  }
}
