package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Parses raw CLI arguments into the corresponding typed FinGrind command. */
final class CliArguments {
  private CliArguments() {}

  /** Parses the raw CLI arguments into the corresponding command model. */
  static CliCommand parse(String[] args) {
    Objects.requireNonNull(args, "args must not be null");
    List<String> arguments = List.of(args);
    if (arguments.isEmpty()) {
      return new Help(null, dev.erst.fingrind.contract.protocol.OutputMode.HUMAN);
    }
    @Nullable CliCommand commandHelp = parseCommandSpecificHelp(arguments);
    if (commandHelp != null) {
      return commandHelp;
    }
    ProtocolOperation operation =
        ProtocolCatalog.findByToken(arguments.getFirst())
            .orElseThrow(() -> CliArgumentValueParser.unknownCommand(arguments.getFirst()));
    return CliCommandParsingRegistry.parse(operation.id(), arguments);
  }

  private static @Nullable CliCommand parseCommandSpecificHelp(List<String> arguments) {
    if (arguments.size() < 2) {
      return null;
    }
    ProtocolOperation operation = ProtocolCatalog.findByToken(arguments.getFirst()).orElse(null);
    if (operation == null) {
      return null;
    }
    String secondToken = arguments.get(1);
    if (!"--help".equals(secondToken) && !"-h".equals(secondToken)) {
      return null;
    }
    return CliDiscoveryArguments.parseCommandHelp(operation.id(), arguments);
  }
}
