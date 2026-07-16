package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.List;

/** Projects shared command identity and summary data for discovery help payloads. */
final class CliDiscoveryHelpCommandPayloads {
  private CliDiscoveryHelpCommandPayloads() {}

  static List<CliDiscoveryCommonJsonModels.CommandIndexPayload> commandIndexPayloads(
      List<CommandDescriptor> commands) {
    return commands.stream()
        .map(
            command ->
                new CliDiscoveryCommonJsonModels.CommandIndexPayload(
                    command.name(),
                    ProtocolCatalog.operation(command.name()).category().wireValue(),
                    command.summary()))
        .toList();
  }

  static List<CliDiscoveryCommonJsonModels.CommandNamePayload> commandNamePayloads(
      List<CommandDescriptor> commands) {
    return commands.stream()
        .map(
            command ->
                new CliDiscoveryCommonJsonModels.CommandNamePayload(
                    command.name(),
                    ProtocolCatalog.operation(command.name()).category().wireValue()))
        .toList();
  }
}
