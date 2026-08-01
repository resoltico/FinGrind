package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.BookTemplateId;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses discovery-style CLI commands that do not target a selected book. */
final class CliDiscoveryArguments {
  private static final List<String> HELP_OPTIONS =
      List.of(
          ProtocolOptions.Presentation.OUTPUT,
          ProtocolOptions.Discovery.DETAIL,
          ProtocolOptions.Discovery.CATEGORY);
  private static final List<String> COMMAND_HELP_OPTIONS =
      List.of(ProtocolOptions.Presentation.OUTPUT, ProtocolOptions.Discovery.DETAIL);
  private static final List<String> CAPABILITIES_OPTIONS =
      List.of(
          ProtocolOptions.Presentation.OUTPUT,
          ProtocolOptions.Discovery.DETAIL,
          ProtocolOptions.Discovery.FOCUS,
          ProtocolOptions.Discovery.CATEGORY);
  private static final List<String> SIMPLE_DISCOVERY_OPTIONS =
      List.of(ProtocolOptions.Presentation.OUTPUT);

  private CliDiscoveryArguments() {}

  static CliCommand parseHelp(List<String> arguments) {
    @Nullable OperationId commandTopic = null;
    @Nullable OutputMode outputMode = null;
    @Nullable DiscoveryDetail detail = null;
    @Nullable OperationCategory category = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        continue;
      }
      if (ProtocolOptions.Discovery.DETAIL.equals(argument)) {
        detail = CliOptionModes.requireDiscoveryDetail(detail, argumentIterator);
        continue;
      }
      if (ProtocolOptions.Discovery.CATEGORY.equals(argument)) {
        category = CliOptionModes.requireOperationCategory(category, argumentIterator);
        continue;
      }
      if (commandTopic != null) {
        throw CliArgumentValueParser.unsupportedArgument(argument, HELP_OPTIONS);
      }
      if (argument.startsWith("-")) {
        throw CliArgumentValueParser.unsupportedArgument(argument, HELP_OPTIONS);
      }
      commandTopic = requiredCommandTopic(argument, "help");
    }
    OutputMode resolvedOutputMode = CliOptionModes.resolvedDiscoveryOutputMode(outputMode);
    requireJsonDiscoverySelections(detail, category, null, resolvedOutputMode);
    if (commandTopic != null && category != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.CATEGORY,
          ProtocolOptions.Discovery.CATEGORY
              + " applies only to top-level help discovery. Remove it when one command topic is selected.");
    }
    return new Help(
        commandTopic,
        resolvedOutputMode,
        detail == null ? DiscoveryDetail.MINIMAL : detail,
        category,
        false);
  }

  static CliCommand parseCommandHelp(OperationId commandTopic, List<String> arguments) {
    @Nullable OutputMode outputMode = null;
    @Nullable DiscoveryDetail detail = null;
    ListIterator<String> argumentIterator = arguments.listIterator(2);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        continue;
      }
      if (ProtocolOptions.Discovery.DETAIL.equals(argument)) {
        detail = CliOptionModes.requireDiscoveryDetail(detail, argumentIterator);
        continue;
      }
      throw CliArgumentValueParser.unsupportedArgument(argument, COMMAND_HELP_OPTIONS);
    }
    OutputMode resolvedOutputMode = CliOptionModes.resolvedDiscoveryOutputMode(outputMode);
    requireJsonDiscoverySelections(detail, null, null, resolvedOutputMode);
    return new Help(
        commandTopic,
        resolvedOutputMode,
        detail == null ? DiscoveryDetail.MINIMAL : detail,
        null,
        false);
  }

  static CliCommand parseVersion(List<String> arguments) {
    return parseDiscoveryCommand(arguments, Version::new);
  }

  static CliCommand parseCapabilities(List<String> arguments) {
    CapabilitiesOptionState options = parseCapabilitiesOptions(arguments);
    OutputMode resolvedOutputMode =
        CliOptionModes.resolvedDiscoveryOutputMode(options.outputMode());
    requireJsonDiscoverySelections(
        options.detail(), options.category(), options.focus(), resolvedOutputMode);
    DiscoveryFocus resolvedFocus = resolvedCapabilitiesFocus(options.focus(), options.category());
    requireCapabilitiesCategoryFocusCompatibility(
        options.category(), options.focus(), resolvedFocus);
    DiscoveryDetail resolvedDetail =
        options.detail() == null
            ? (resolvedOutputMode == OutputMode.JSON
                ? DiscoveryDetail.COMPACT
                : DiscoveryDetail.MINIMAL)
            : options.detail();
    return new Capabilities(
        resolvedOutputMode,
        resolvedDetail,
        new CliDiscoverySelections(resolvedFocus, options.category()));
  }

  static CliCommand parseEnvironment(List<String> arguments) {
    return parseDiscoveryCommand(arguments, EnvironmentCommand::new);
  }

  static CliCommand parsePrintRequestTemplate(List<String> arguments) {
    @Nullable OperationId commandTopic = null;
    @Nullable BookTemplateId bookTemplateId = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.BookDefinition.TEMPLATE_ID.equals(argument)) {
        bookTemplateId =
            CliOptionValues.parseBookTemplateIdOption(
                CliOptionValues.requireValue(
                    argumentIterator, ProtocolOptions.BookDefinition.TEMPLATE_ID),
                ProtocolOptions.BookDefinition.TEMPLATE_ID);
        continue;
      }
      if (commandTopic != null) {
        throw CliArgumentValueParser.invalid(
            argument,
            "%s accepts at most one optional request-bearing command topic."
                .formatted(arguments.getFirst()));
      }
      if (argument.startsWith("-")) {
        throw CliArgumentValueParser.unsupportedArgument(
            argument, List.of(ProtocolOptions.BookDefinition.TEMPLATE_ID));
      }
      commandTopic = CliDiscoveryRequestTemplateTopics.requireTopic(argument);
    }
    return new PrintRequestTemplate(commandTopic, bookTemplateId);
  }

  static CliCommand parsePrintPlanTemplate(List<String> arguments) {
    if (arguments.size() == 1) {
      return new PrintPlanTemplate();
    }
    if (arguments.size() == 2 && !arguments.get(1).startsWith("-")) {
      return new PrintPlanTemplate(
          CliArgumentValueParser.requireValidArgument(
              arguments.get(1),
              () ->
                  dev.erst.fingrind.contract.discovery.PlanTemplateTopic.requireWireName(
                      arguments.get(1))));
    }
    String unsupportedArgument = arguments.get(1);
    throw CliArgumentValueParser.invalid(
        unsupportedArgument,
        "%s accepts one optional plan-template topic. Use one of: %s."
            .formatted(
                arguments.getFirst(),
                String.join(
                    ", ", dev.erst.fingrind.contract.discovery.PlanTemplateTopic.wireNames())));
  }

  private static CliCommand parseDiscoveryCommand(
      List<String> arguments, DiscoveryCommandFactory commandFactory) {
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (!ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        throw CliArgumentValueParser.unsupportedArgument(argument, SIMPLE_DISCOVERY_OPTIONS);
      }
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Presentation.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    return commandFactory.create(CliOptionModes.resolvedDiscoveryOutputMode(outputMode));
  }

  private static OperationId requiredCommandTopic(String token, String surfaceName) {
    Optional<dev.erst.fingrind.contract.protocol.ProtocolOperation> operation =
        ProtocolCatalog.findByToken(token);
    if (operation.isEmpty()) {
      throw CliArgumentValueParser.invalid(
          token, "Unsupported " + surfaceName + " topic: " + token);
    }
    return operation.orElseThrow().id();
  }

  private static CapabilitiesOptionState parseCapabilitiesOptions(List<String> arguments) {
    @Nullable OutputMode outputMode = null;
    @Nullable DiscoveryDetail detail = null;
    @Nullable DiscoveryFocus focus = null;
    @Nullable OperationCategory category = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.Presentation.OUTPUT.equals(argument)) {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        continue;
      }
      if (ProtocolOptions.Discovery.DETAIL.equals(argument)) {
        detail = CliOptionModes.requireDiscoveryDetail(detail, argumentIterator);
        continue;
      }
      if (ProtocolOptions.Discovery.FOCUS.equals(argument)) {
        focus = CliOptionModes.requireDiscoveryFocus(focus, argumentIterator);
        continue;
      }
      if (ProtocolOptions.Discovery.CATEGORY.equals(argument)) {
        category = CliOptionModes.requireOperationCategory(category, argumentIterator);
        continue;
      }
      throw CliArgumentValueParser.unsupportedArgument(argument, CAPABILITIES_OPTIONS);
    }
    return new CapabilitiesOptionState(outputMode, detail, focus, category);
  }

  private static DiscoveryFocus resolvedCapabilitiesFocus(
      @Nullable DiscoveryFocus focus, @Nullable OperationCategory category) {
    return focus == null
        ? (category == null ? DiscoveryFocus.OVERVIEW : DiscoveryFocus.COMMANDS)
        : focus;
  }

  private static void requireCapabilitiesCategoryFocusCompatibility(
      @Nullable OperationCategory category,
      @Nullable DiscoveryFocus focus,
      DiscoveryFocus resolvedFocus) {
    if (category != null && focus != null && resolvedFocus != DiscoveryFocus.COMMANDS) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Discovery.CATEGORY,
          ProtocolOptions.Discovery.CATEGORY
              + " requires "
              + ProtocolOptions.Discovery.FOCUS
              + " commands on the capabilities surface.");
    }
  }

  private static void requireJsonDiscoverySelections(
      @Nullable DiscoveryDetail detail,
      @Nullable OperationCategory category,
      @Nullable DiscoveryFocus focus,
      OutputMode resolvedOutputMode) {
    if (resolvedOutputMode == OutputMode.JSON) {
      return;
    }
    if (detail == null && category == null && focus == null) {
      return;
    }
    throw CliArgumentValueParser.invalid(
        ProtocolOptions.Presentation.OUTPUT,
        ProtocolOptions.Discovery.DETAIL
            + ", "
            + ProtocolOptions.Discovery.FOCUS
            + ", and "
            + ProtocolOptions.Discovery.CATEGORY
            + " are supported only when the resolved output mode is json. "
            + "Add "
            + ProtocolOptions.Presentation.OUTPUT
            + " json or omit the JSON-only discovery selectors"
            + ".");
  }

  /** Factory for one discovery command that only varies by the selected output mode. */
  @FunctionalInterface
  private interface DiscoveryCommandFactory {
    /** Builds one parsed discovery command with the resolved output mode. */
    CliCommand create(OutputMode outputMode);
  }

  /** Parsed JSON-only selectors for one capabilities discovery invocation. */
  private record CapabilitiesOptionState(
      @Nullable OutputMode outputMode,
      @Nullable DiscoveryDetail detail,
      @Nullable DiscoveryFocus focus,
      @Nullable OperationCategory category) {}
}
