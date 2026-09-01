package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationCategory;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves the failure-output mode directly from raw CLI arguments before full command parsing. */
final class CliFailureOutputModeResolver {
  private CliFailureOutputModeResolver() {}

  static OutputMode resolve(String[] args) {
    Objects.requireNonNull(args, "args");
    if (args.length == 0) {
      return defaultOutputMode(CliOutputModeDefaults.OutputSurface.DISCOVERY);
    }
    ExplicitOutputSelection explicitOutputSelection = explicitOutputMode(args);
    if (explicitOutputSelection.invalidSelection()) {
      return defaultOutputMode(CliOutputModeDefaults.OutputSurface.DISCOVERY);
    }
    if (explicitOutputSelection.rawValue() != null) {
      try {
        return OutputMode.fromWireValue(explicitOutputSelection.rawValue());
      } catch (IllegalArgumentException exception) {
        return defaultOutputMode(CliOutputModeDefaults.OutputSurface.DISCOVERY);
      }
    }
    Optional<ProtocolOperation> operation = ProtocolCatalog.findByToken(args[0]);
    if (operation.isEmpty()) {
      return defaultOutputMode(CliOutputModeDefaults.OutputSurface.DISCOVERY);
    }
    OperationId resolvedOperationId =
        isCommandSpecificHelp(args) ? OperationId.HELP : operation.orElseThrow().id();
    List<OutputMode> supportedModes = ProtocolCatalog.operation(resolvedOperationId).outputModes();
    if (supportedModes.isEmpty()) {
      return defaultOutputMode(CliOutputModeDefaults.OutputSurface.DISCOVERY);
    }
    return defaultOutputMode(outputSurface(resolvedOperationId));
  }

  private static boolean isCommandSpecificHelp(String[] args) {
    return args.length >= 2 && ("--help".equals(args[1]) || "-h".equals(args[1]));
  }

  private static ExplicitOutputSelection explicitOutputMode(String[] args) {
    int outputIndex = -1;
    for (int index = 1; index < args.length; index++) {
      if (!ProtocolOptions.Presentation.OUTPUT.equals(args[index])) {
        continue;
      }
      if (outputIndex >= 0 || index + 1 >= args.length) {
        return ExplicitOutputSelection.unresolved();
      }
      outputIndex = index;
    }
    return outputIndex < 0
        ? ExplicitOutputSelection.absent()
        : ExplicitOutputSelection.present(args[outputIndex + 1]);
  }

  private static OutputMode defaultOutputMode(CliOutputModeDefaults.OutputSurface outputSurface) {
    return CliOutputModeDefaults.outputDefault(outputSurface).mode();
  }

  private static CliOutputModeDefaults.OutputSurface outputSurface(OperationId operationId) {
    return ProtocolCatalog.operation(operationId).category() == OperationCategory.DISCOVERY
        ? CliOutputModeDefaults.OutputSurface.DISCOVERY
        : CliOutputModeDefaults.OutputSurface.SELECTABLE;
  }

  private record ExplicitOutputSelection(
      boolean invalidSelection, @org.jspecify.annotations.Nullable String rawValue) {
    private static ExplicitOutputSelection absent() {
      return new ExplicitOutputSelection(false, null);
    }

    private static ExplicitOutputSelection present(String rawValue) {
      return new ExplicitOutputSelection(false, Objects.requireNonNull(rawValue, "rawValue"));
    }

    private static ExplicitOutputSelection unresolved() {
      return new ExplicitOutputSelection(true, null);
    }
  }
}
