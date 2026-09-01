package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.PlanTemplateTopic;
import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliArguments}. */
class CliDiscoveryArgumentParsingTest extends CliArgumentParsingTestSupport {

  @Test
  void parse_returnsHelpWhenArgumentsAreEmpty() {
    Help command = assertInstanceOf(Help.class, CliArguments.parse(new String[0]));
    assertEquals(DiscoveryDetail.MINIMAL, command.detail());
  }

  @Test
  void parse_returnsCapabilitiesWhenCommandIsCapabilities() {
    Capabilities command =
        assertInstanceOf(Capabilities.class, CliArguments.parse(new String[] {"capabilities"}));
    assertEquals(DiscoveryDetail.MINIMAL, command.detail());
    assertEquals(DiscoveryFocus.OVERVIEW, command.selections().focus());
    assertNull(command.selections().category());
  }

  @Test
  void parse_returnsHelpForFlagAlias() {
    Help command = assertInstanceOf(Help.class, CliArguments.parse(new String[] {"--help"}));
    assertEquals(DiscoveryDetail.MINIMAL, command.detail());
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
  void parse_supportsFullDetailForJsonCommandHelpAlias() {
    Help command =
        assertInstanceOf(
            Help.class,
            CliArguments.parse(
                new String[] {"post-entry", "--help", "--output", "json", "--detail", "full"}));

    assertEquals(OperationId.POST_ENTRY, command.commandTopic());
    assertEquals(OutputMode.JSON, command.outputMode());
    assertEquals(DiscoveryDetail.FULL, command.detail());
  }

  @Test
  void parse_supportsJsonCategoryFilterForTopLevelHelp() {
    Help command =
        assertInstanceOf(
            Help.class,
            CliArguments.parse(new String[] {"help", "--output", "json", "--category", "query"}));

    assertEquals(OutputMode.JSON, command.outputMode());
    assertEquals(DiscoveryDetail.MINIMAL, command.detail());
    assertEquals(OperationCategory.QUERY, command.category());
  }

  @Test
  void parse_rejectsHelpCategoryWhenCommandTopicIsSelected() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "help", "post-entry", "--output", "json", "--category", "query"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--category", exception.argument());
    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("applies only to top-level help discovery"));
  }

  @Test
  void parse_rejectsHelpCategoryWhenResolvedOutputModeIsText() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"help", "--output", "text", "--category", "query"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--output", exception.argument());
    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("supported only when the resolved output mode is json"));
  }

  @Test
  void parse_rejectsDiscoveryDetailWhenCommandHelpResolvesToTextOutput() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"post-entry", "--help", "--output", "text", "--detail", "full"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--output", exception.argument());
    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage())
            .contains("supported only when the resolved output mode is json"));
  }

  @Test
  void parse_supportsCapabilitiesFocusAndCategorySelectors() {
    Capabilities command =
        assertInstanceOf(
            Capabilities.class,
            CliArguments.parse(
                new String[] {
                  "capabilities",
                  "--output",
                  "json",
                  "--detail",
                  "compact",
                  "--focus",
                  "commands",
                  "--category",
                  "write"
                }));

    assertEquals(OutputMode.JSON, command.outputMode());
    assertEquals(DiscoveryDetail.COMPACT, command.detail());
    assertEquals(DiscoveryFocus.COMMANDS, command.selections().focus());
    assertEquals(OperationCategory.WRITE, command.selections().category());
  }

  @Test
  void parse_supportsCapabilitiesCategorySelectorWithoutExplicitCommandFocus() {
    Capabilities command =
        assertInstanceOf(
            Capabilities.class,
            CliArguments.parse(
                new String[] {"capabilities", "--output", "json", "--category", "query"}));

    assertEquals(OutputMode.JSON, command.outputMode());
    assertEquals(DiscoveryDetail.COMPACT, command.detail());
    assertEquals(DiscoveryFocus.COMMANDS, command.selections().focus());
    assertEquals(OperationCategory.QUERY, command.selections().category());
  }

  @Test
  void parse_supportsCapabilitiesTextOutputWithoutJsonOnlySelectors() {
    Capabilities command =
        assertInstanceOf(
            Capabilities.class,
            CliArguments.parse(new String[] {"capabilities", "--output", "text"}));

    assertEquals(OutputMode.TEXT, command.outputMode());
    assertEquals(DiscoveryFocus.OVERVIEW, command.selections().focus());
    assertNull(command.selections().category());
  }

  @Test
  void parse_rejectsCapabilitiesFocusSelectorWhenResolvedOutputModeIsText() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"capabilities", "--output", "text", "--focus", "commands"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--output", exception.argument());
    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("supported only when the resolved output mode is json"));
  }

  @Test
  void parse_rejectsCapabilitiesCategoryWithoutCommandFocus() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "capabilities",
                      "--output",
                      "json",
                      "--focus",
                      "storage",
                      "--category",
                      "query"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--category", exception.argument());
    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("--category requires --focus commands"));
  }

  @Test
  void parse_rejectsCapabilitiesSelectorsWhenResolvedOutputModeIsText() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "capabilities",
                      "--output",
                      "text",
                      "--focus",
                      "commands",
                      "--category",
                      "query"
                    }));

    assertEquals("invalid-request", exception.code());
    assertEquals("--output", exception.argument());
    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("supported only when the resolved output mode is json"));
  }

  @Test
  void parse_rejectsUnsupportedDiscoveryFocusAndCategoryValues() {
    CliArgumentsException focusException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"capabilities", "--output", "json", "--focus", "bad-focus"}));
    CliArgumentsException categoryException =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"help", "--output", "json", "--category", "bad-category"}));

    assertEquals("--focus", focusException.argument());
    assertTrue(Objects.requireNonNull(focusException.getMessage()).contains("Accepted values"));
    assertEquals("--category", categoryException.argument());
    assertTrue(Objects.requireNonNull(categoryException.getMessage()).contains("Accepted values"));
  }

  @Test
  void parse_rejectsDuplicateCapabilitiesFocusAndCategorySelectors() {
    CliArgumentsException duplicateFocus =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "capabilities",
                      "--output",
                      "json",
                      "--focus",
                      "commands",
                      "--focus",
                      "storage"
                    }));
    CliArgumentsException duplicateCategory =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "capabilities",
                      "--output",
                      "json",
                      "--focus",
                      "commands",
                      "--category",
                      "query",
                      "--category",
                      "write"
                    }));

    assertEquals("--focus", duplicateFocus.argument());
    assertTrue(Objects.requireNonNull(duplicateFocus.getMessage()).contains("Duplicate argument"));
    assertEquals("--category", duplicateCategory.argument());
    assertTrue(
        Objects.requireNonNull(duplicateCategory.getMessage()).contains("Duplicate argument"));
  }

  @Test
  void parse_supportsOutputSelectionForEnvironmentDiscoveryCommand() {
    EnvironmentCommand command =
        assertInstanceOf(
            EnvironmentCommand.class,
            CliArguments.parse(new String[] {"environment", "--output", "json"}));

    assertEquals(OutputMode.JSON, command.outputMode());
  }

  @Test
  void parse_rejectsUnsupportedArgumentForVersionDiscoveryCommand() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"version", "--detail", "full"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--detail", exception.argument());
    assertEquals("Unsupported argument: --detail", exception.getMessage());
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
  void parse_rejectsFlagLookingHelpTopicAsUnsupportedArgument() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"help", "--outpt", "json"}));

    assertEquals("invalid-request", exception.code());
    assertEquals("--outpt", exception.argument());
    assertEquals("Unsupported argument: --outpt. Did you mean --output?", exception.getMessage());
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
    PrintRequestTemplate declareTaxRegistration =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(
                new String[] {"print-request-template", "declare-tax-registration"}));
    PrintRequestTemplate preflight =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "preflight-entry"}));
    PrintRequestTemplate recordSale =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "record-sale-settled"}));
    PrintRequestTemplate recordPurchaseSettled =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "record-purchase-settled"}));
    PrintRequestTemplate recordPurchaseOnCredit =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(
                new String[] {"print-request-template", "record-purchase-on-credit"}));
    PrintRequestTemplate recordExpense =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "record-expense-settled"}));
    PrintRequestTemplate recordOwnerContribution =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(
                new String[] {"print-request-template", "record-owner-contribution"}));
    PrintRequestTemplate recordOwnerWithdrawal =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "record-owner-withdrawal"}));
    PrintRequestTemplate recordOpeningPosition =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "record-opening-position"}));
    PrintRequestTemplate recordReversal =
        assertInstanceOf(
            PrintRequestTemplate.class,
            CliArguments.parse(new String[] {"print-request-template", "record-reversal"}));

    assertEquals(OperationId.POST_ENTRY, postEntry.commandTopic());
    assertEquals(OperationId.DECLARE_ACCOUNT, declareAccount.commandTopic());
    assertEquals(OperationId.DECLARE_TAX_REGISTRATION, declareTaxRegistration.commandTopic());
    assertEquals(OperationId.PREFLIGHT_ENTRY, preflight.commandTopic());
    assertEquals(OperationId.RECORD_SALE_SETTLED, recordSale.commandTopic());
    assertEquals(OperationId.RECORD_PURCHASE_SETTLED, recordPurchaseSettled.commandTopic());
    assertEquals(OperationId.RECORD_PURCHASE_ON_CREDIT, recordPurchaseOnCredit.commandTopic());
    assertEquals(OperationId.RECORD_EXPENSE_SETTLED, recordExpense.commandTopic());
    assertEquals(OperationId.RECORD_OWNER_CONTRIBUTION, recordOwnerContribution.commandTopic());
    assertEquals(OperationId.RECORD_OWNER_WITHDRAWAL, recordOwnerWithdrawal.commandTopic());
    assertEquals(OperationId.RECORD_OPENING_POSITION, recordOpeningPosition.commandTopic());
    assertEquals(OperationId.RECORD_REVERSAL, recordReversal.commandTopic());
  }

  @Test
  void parse_rejectsUnsupportedOptionsAfterARequestTemplateTopic() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {"print-request-template", "post-entry", "--unexpected"}));

    assertEquals("invalid-request", exception.failure().code());
    assertEquals("--unexpected", exception.failure().argument());
    assertEquals("Unsupported argument: --unexpected", exception.failure().message());
    assertEquals(
        CliInvocationText.helpSyntaxHint(OperationId.PRINT_REQUEST_TEMPLATE), exception.hint());
  }

  @Test
  void parse_rejectsASecondRequestTemplateTopic() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliArguments.parse(
                    new String[] {
                      "print-request-template", "record-sale-settled", "record-expense-settled"
                    }));

    assertEquals("record-expense-settled", exception.failure().argument());
    assertTrue(
        exception
            .failure()
            .message()
            .contains("accepts at most one optional request-bearing command topic"));
  }

  @Test
  void parse_rejectsUnsupportedRequestTemplateTopicWithACompactRecoveryAction() {
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"print-request-template", "list-accounts"}));

    assertEquals("invalid-request", exception.failure().code());
    assertEquals("list-accounts", exception.failure().argument());
    assertTrue(exception.failure().message().contains("Unsupported request-template topic"));
    assertTrue(exception.failure().message().contains("without a topic"));
    assertTrue(exception.failure().message().contains("request-bearing command topic"));
    assertFalse(exception.failure().message().contains("record-purchase-settled"));
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
  void parse_acceptsOneNamedPlanTemplateTopicAndRejectsAdditionalArguments() {
    assertEquals(new PrintPlanTemplate(), CliArguments.parse(new String[] {"print-plan-template"}));
    assertEquals(
        new PrintPlanTemplate(PlanTemplateTopic.TAX_SETUP),
        CliArguments.parse(new String[] {"print-plan-template", "tax-setup"}));

    CliArgumentsException unsupportedOption =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"print-plan-template", "--unexpected"}));
    assertEquals("--unexpected", unsupportedOption.argument());

    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> CliArguments.parse(new String[] {"print-plan-template", "tax-setup", "extra"}));

    assertEquals("invalid-request", exception.failure().code());
    assertEquals("tax-setup", exception.failure().argument());
    assertTrue(exception.failure().message().contains("one optional plan-template topic"));
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
