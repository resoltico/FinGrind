package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.DiscoveryDetail;
import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Parses discovery-style CLI commands that do not target a selected book. */
final class CliDiscoveryArguments {
  private CliDiscoveryArguments() {}

  static CliCommand parseHelp(List<String> arguments) {
    @Nullable OperationId commandTopic = null;
    @Nullable OutputMode outputMode = null;
    @Nullable DiscoveryDetail detail = null;
    @Nullable OperationCategory category = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        continue;
      }
      if (ProtocolOptions.DETAIL.equals(argument)) {
        detail = CliOptionModes.requireDiscoveryDetail(detail, argumentIterator);
        continue;
      }
      if (ProtocolOptions.CATEGORY.equals(argument)) {
        category = CliOptionModes.requireOperationCategory(category, argumentIterator);
        continue;
      }
      if (commandTopic != null) {
        throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
      commandTopic = requiredCommandTopic(argument, "help");
    }
    OutputMode resolvedOutputMode = CliOptionModes.resolvedDiscoveryOutputMode(outputMode);
    requireJsonDiscoverySelections(detail, category, null, resolvedOutputMode);
    if (commandTopic != null && category != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.CATEGORY,
          ProtocolOptions.CATEGORY
              + " applies only to top-level help discovery. Remove it when one command topic is selected.");
    }
    return new Help(
        commandTopic,
        resolvedOutputMode,
        detail == null ? DiscoveryDetail.MINIMAL : detail,
        category);
  }

  static CliCommand parseCommandHelp(OperationId commandTopic, List<String> arguments) {
    @Nullable OutputMode outputMode = null;
    @Nullable DiscoveryDetail detail = null;
    ListIterator<String> argumentIterator = arguments.listIterator(2);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        continue;
      }
      if (ProtocolOptions.DETAIL.equals(argument)) {
        detail = CliOptionModes.requireDiscoveryDetail(detail, argumentIterator);
        continue;
      }
      throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
    }
    OutputMode resolvedOutputMode = CliOptionModes.resolvedDiscoveryOutputMode(outputMode);
    requireJsonDiscoverySelections(detail, null, null, resolvedOutputMode);
    return new Help(
        commandTopic, resolvedOutputMode, detail == null ? DiscoveryDetail.MINIMAL : detail, null);
  }

  static CliCommand parseVersion(List<String> arguments) {
    return parseDiscoveryCommand(arguments, Version::new);
  }

  static CliCommand parseCapabilities(List<String> arguments) {
    @Nullable OutputMode outputMode = null;
    @Nullable DiscoveryDetail detail = null;
    @Nullable DiscoveryFocus focus = null;
    @Nullable OperationCategory category = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        continue;
      }
      if (ProtocolOptions.DETAIL.equals(argument)) {
        detail = CliOptionModes.requireDiscoveryDetail(detail, argumentIterator);
        continue;
      }
      if (ProtocolOptions.FOCUS.equals(argument)) {
        focus = CliOptionModes.requireDiscoveryFocus(focus, argumentIterator);
        continue;
      }
      if (ProtocolOptions.CATEGORY.equals(argument)) {
        category = CliOptionModes.requireOperationCategory(category, argumentIterator);
        continue;
      }
      throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
    }
    OutputMode resolvedOutputMode = CliOptionModes.resolvedDiscoveryOutputMode(outputMode);
    requireJsonDiscoverySelections(detail, category, focus, resolvedOutputMode);
    DiscoveryFocus resolvedFocus =
        focus == null
            ? (category == null ? DiscoveryFocus.OVERVIEW : DiscoveryFocus.COMMANDS)
            : focus;
    if (category != null && focus != null && resolvedFocus != DiscoveryFocus.COMMANDS) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.CATEGORY,
          ProtocolOptions.CATEGORY
              + " requires "
              + ProtocolOptions.FOCUS
              + " commands on the capabilities surface.");
    }
    return new Capabilities(
        resolvedOutputMode,
        detail == null ? DiscoveryDetail.MINIMAL : detail,
        new CliDiscoverySelections(resolvedFocus, category));
  }

  static CliCommand parseEnvironment(List<String> arguments) {
    return parseDiscoveryCommand(arguments, EnvironmentCommand::new);
  }

  static CliCommand parsePrintRequestTemplate(List<String> arguments) {
    if (arguments.size() == 1) {
      return new PrintRequestTemplate(null);
    }
    if (arguments.size() == 2) {
      return new PrintRequestTemplate(requiredRequestTemplateTopic(arguments.get(1)));
    }
    String unsupportedArgument = arguments.get(2);
    throw CliArgumentValueParser.invalid(
        unsupportedArgument,
        "%s accepts at most one optional request-bearing command topic."
            .formatted(arguments.getFirst()));
  }

  static CliCommand parsePrintPlanTemplate(List<String> arguments) {
    return parseSingleToken(arguments, new PrintPlanTemplate());
  }

  private static CliCommand parseSingleToken(List<String> arguments, CliCommand command) {
    if (arguments.size() != 1) {
      String unsupportedArgument = arguments.get(1);
      throw CliArgumentValueParser.invalid(
          unsupportedArgument,
          "%s emits fixed raw JSON and does not accept %s. Use shell redirection if you need to save the template."
              .formatted(arguments.getFirst(), unsupportedArgument));
    }
    return command;
  }

  private static CliCommand parseDiscoveryCommand(
      List<String> arguments, DiscoveryCommandFactory commandFactory) {
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (!ProtocolOptions.OUTPUT.equals(argument)) {
        throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
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

  private static OperationId requiredRequestTemplateTopic(String token) {
    Optional<dev.erst.fingrind.contract.protocol.ProtocolOperation> operation =
        ProtocolCatalog.findByToken(token);
    if (operation.isEmpty()) {
      throw CliArgumentValueParser.invalid(token, "Unsupported request-template topic: " + token);
    }
    OperationId topic = operation.orElseThrow().id();
    if (topic == OperationId.POST_ENTRY
        || topic == OperationId.PREFLIGHT_ENTRY
        || topic == OperationId.DECLARE_ACCOUNT) {
      return topic;
    }
    throw CliArgumentValueParser.invalid(
        token,
        "Unsupported request-template topic: "
            + token
            + ". Use "
            + supportedRequestTemplateTopics()
            + ".");
  }

  private static String supportedRequestTemplateTopics() {
    return String.join(
        ", ",
        ProtocolCatalog.operationName(OperationId.POST_ENTRY),
        ProtocolCatalog.operationName(OperationId.PREFLIGHT_ENTRY),
        ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT));
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
        ProtocolOptions.OUTPUT,
        ProtocolOptions.DETAIL
            + ", "
            + ProtocolOptions.FOCUS
            + ", and "
            + ProtocolOptions.CATEGORY
            + " are supported only when the resolved output mode is json. "
            + "Add "
            + ProtocolOptions.OUTPUT
            + " json or omit the JSON-only discovery selectors"
            + ".");
  }

  /** Factory for one discovery command that only varies by the selected output mode. */
  @FunctionalInterface
  private interface DiscoveryCommandFactory {
    /** Builds one parsed discovery command with the resolved output mode. */
    CliCommand create(OutputMode outputMode);
  }
}
