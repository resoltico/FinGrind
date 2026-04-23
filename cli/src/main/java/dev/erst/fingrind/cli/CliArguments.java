package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.List;
import java.util.Objects;

/** Parses raw CLI arguments into the corresponding typed FinGrind command. */
final class CliArguments {
  private CliArguments() {}

  /** Parses the raw CLI arguments into the corresponding command model. */
  static CliCommand parse(String[] args) {
    Objects.requireNonNull(args, "args must not be null");
    List<String> arguments = List.of(args);
    if (arguments.isEmpty()) {
      return new Help(dev.erst.fingrind.contract.protocol.OutputMode.HUMAN);
    }
    ProtocolOperation operation =
        ProtocolCatalog.findByToken(arguments.getFirst())
            .orElseThrow(() -> CliArgumentValueParser.unknownCommand(arguments.getFirst()));
    return CliCommandParsingRegistry.parse(operation.id(), arguments);
  }
}
