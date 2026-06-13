package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.ArrayList;
import java.util.List;

/** Builds per-command help sections for operator-facing discovery text. */
final class CliDiscoveryCommandHelpSupport {
  private CliDiscoveryCommandHelpSupport() {}

  static String renderCommandHelpText(HelpDescriptor helpDescriptor) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    String summary = CliTextFormat.wrap(command.summary(), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        command.name().wireName(),
        CliDiscoveryTextSupport.joinSections(
            summary,
            renderGrammar(operation, command),
            CliDiscoveryCommandGuidance.renderRequestGuidance(helpDescriptor, command.name()),
            CliDiscoveryCommandGuidance.renderPreparation(command.name()),
            renderOutputContract(command),
            CliDiscoveryCommandGuidance.renderExitBehavior(helpDescriptor.exitCodes()),
            CliDiscoveryTextSupport.section(
                "Examples", CliDiscoveryCommandExamples.renderCommandExamples(operation)),
            renderRepairGuidance(command.name())));
  }

  static String primaryCommandExample(OperationId operationId) {
    return CliDiscoveryCommandExamples.primaryCommandExample(operationId);
  }

  static String primaryStarterRequestCommand(OperationId operationId) {
    return CliDiscoveryCommandExamples.primaryStarterRequestCommand(operationId);
  }

  private static String renderOutputContract(CommandDescriptor command) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Stdout contract", command.stdoutContractSummary()));
    if (command.outputModes().contains(dev.erst.fingrind.contract.protocol.OutputMode.CSV)) {
      rows.add(
          List.of(
              "CSV contract",
              "The exportFamily column identifies which CSV row grammar the command produced."));
    }
    if (!command.artifactOutputs().isEmpty()) {
      rows.add(
          List.of(
              "Artifact outputs",
              String.join(
                  ", ",
                  command.artifactOutputs().stream()
                      .map(artifact -> artifact.format() + " via " + artifact.option())
                      .toList())));
    }
    return CliDiscoveryTextSupport.section(
        "Output Contract", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  private static String renderGrammar(ProtocolOperation operation, CommandDescriptor command) {
    String syntaxBlock =
        CliTextFormat.renderLiteralBlock(
            List.of(CliInvocationText.rewriteInvocationPrefix(operation.usage())), "");
    String optionsBlock =
        command.options().isEmpty()
            ? ""
            : "Options"
                + System.lineSeparator()
                + CliTextFormat.renderLiteralBlock(command.options(), "")
                + System.lineSeparator()
                + System.lineSeparator();
    return CliDiscoveryTextSupport.section(
        "Grammar",
        "Canonical syntax"
            + System.lineSeparator()
            + syntaxBlock
            + (optionsBlock.isEmpty()
                ? ""
                : System.lineSeparator() + System.lineSeparator() + optionsBlock.trim()));
  }

  private static String renderRepairGuidance(OperationId operationId) {
    return CliDiscoveryTextSupport.section(
        "Support",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Command help",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + operationId.wireName()),
                List.of(
                    "Machine contract",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + operationId.wireName()
                        + " --output json"),
                List.of(
                    "Request template",
                    CliDiscoveryCommandGuidance.requestTemplateHint(operationId))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
  }
}
