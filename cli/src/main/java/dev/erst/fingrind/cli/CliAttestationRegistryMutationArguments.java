package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;

/** Parses book-bound credential-registry and authorization-policy mutation commands. */
final class CliAttestationRegistryMutationArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(ProtocolOptions.Presentation.OUTPUT), List.of());

  private CliAttestationRegistryMutationArguments() {}

  static CliCommand parseEnrollKeyCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsed = parse(arguments);
    return new EnrollAttestationKey(
        parsed.bookAccess(), parsed.optionalRequestFile().orElseThrow(), outputMode(parsed));
  }

  static CliCommand parseRolloverKeyCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsed = parse(arguments);
    return new RolloverAttestationKey(
        parsed.bookAccess(), parsed.optionalRequestFile().orElseThrow(), outputMode(parsed));
  }

  static CliCommand parseRevokeKeyCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsed = parse(arguments);
    return new RevokeAttestationKey(
        parsed.bookAccess(), parsed.optionalRequestFile().orElseThrow(), outputMode(parsed));
  }

  static CliCommand parseAlterPolicyCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsed = parse(arguments);
    return new AlterAttestationPolicy(
        parsed.bookAccess(), parsed.optionalRequestFile().orElseThrow(), outputMode(parsed));
  }

  private static CliBookArgumentParser.ParsedBookArguments parse(List<String> arguments) {
    return CliBookArgumentParser.parseRequestBoundCommandArguments(arguments, ARGUMENTS);
  }

  private static OutputMode outputMode(CliBookArgumentParser.ParsedBookArguments parsed) {
    java.util.ListIterator<String> iterator = parsed.commandArguments().listIterator();
    OutputMode outputMode = null;
    while (iterator.hasNext()) {
      iterator.next();
      outputMode =
          CliOptionModes.requireOutputMode(
              outputMode,
              CliOptionValues.requireValue(iterator, ProtocolOptions.Presentation.OUTPUT),
              CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
    }
    return CliOptionModes.resolvedOutputMode(outputMode);
  }
}
