package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds per-command help sections for operator-facing discovery text. */
final class CliDiscoveryCommandHelpSupport {
  private CliDiscoveryCommandHelpSupport() {}

  /** Typed Support entry used to distinguish shell-command guidance from plain notes. */
  sealed interface SupportEntry permits SupportCommandEntry, SupportNoteEntry {
    /** Returns the operator-facing label shown inside the Support section. */
    String label();

    /** Creates one Support entry that renders as a copy-paste-safe shell command block. */
    static SupportEntry command(String label, String command) {
      return new SupportCommandEntry(label, command);
    }

    /** Creates one Support entry that renders as a plain note row. */
    static SupportEntry note(String label, String note) {
      return new SupportNoteEntry(label, note);
    }
  }

  private record SupportCommandEntry(String label, String command) implements SupportEntry {
    private SupportCommandEntry {
      label = requireText(label, "label");
      command = requireText(command, "command");
    }
  }

  private record SupportNoteEntry(String label, String note) implements SupportEntry {
    private SupportNoteEntry {
      label = requireText(label, "label");
      note = requireText(note, "note");
    }
  }

  static String renderCommandHelpText(HelpDescriptor helpDescriptor) {
    CommandDescriptor command = helpDescriptor.commands().getFirst();
    ProtocolOperation operation = ProtocolCatalog.operation(command.name());
    String summary = CliTextFormat.wrap(command.summary(), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        command.name().wireName(),
        CliDiscoveryTextSupport.joinSections(
            summary,
            renderGrammar(operation, command),
            CliDiscoveryCommandGuidance.renderTemporalScopeGuidance(command.name()),
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
        renderSupportEntries(
            List.of(
                SupportEntry.command(
                    "Command help",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + operationId.wireName()),
                SupportEntry.command(
                    "Machine contract",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + operationId.wireName()
                        + " --output json --detail full"),
                CliDiscoveryCommandGuidance.requestTemplateHint(operationId))));
  }

  private static String renderSupportEntries(List<SupportEntry> entries) {
    List<String> sections = new ArrayList<>();
    List<List<String>> noteRows = new ArrayList<>();
    for (SupportEntry entry : entries) {
      switch (entry) {
        case SupportCommandEntry commandEntry -> {
          flushSupportNotes(sections, noteRows);
          sections.add(renderSupportCommandEntry(commandEntry));
        }
        case SupportNoteEntry noteEntry ->
            noteRows.add(List.of(noteEntry.label(), noteEntry.note()));
      }
    }
    flushSupportNotes(sections, noteRows);
    return CliDiscoveryTextSupport.joinSections(sections.toArray(String[]::new));
  }

  private static void flushSupportNotes(List<String> sections, List<List<String>> noteRows) {
    if (noteRows.isEmpty()) {
      return;
    }
    sections.add(
        CliTextFormat.renderKeyValueBlock(
            List.copyOf(noteRows), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH));
    noteRows.clear();
  }

  private static String renderSupportCommandEntry(SupportCommandEntry commandEntry) {
    return commandEntry.label()
        + System.lineSeparator()
        + CliTextFormat.renderShellCommandBlock(
            List.of(commandEntry.command()), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
