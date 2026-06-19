package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared helper logic for discovery help text renderer tests. */
class CliDiscoveryHelpTextTestSupport {
  protected final String renderHelpText(HelpDescriptor helpDescriptor) {
    return CliDiscoveryOutputRenderer.renderHelpText(
        helpDescriptor, CliDiscoveryTestSupport.environment(), false);
  }

  protected final String renderHelpText(
      HelpDescriptor helpDescriptor,
      dev.erst.fingrind.contract.runtime.EnvironmentDescriptor environmentDescriptor,
      boolean terse) {
    return CliDiscoveryOutputRenderer.renderHelpText(helpDescriptor, environmentDescriptor, terse);
  }

  protected final void restoreRuntimeDistribution(String previousDistribution) {
    if (previousDistribution == null) {
      System.clearProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
      return;
    }
    System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, previousDistribution);
  }

  protected final void assertContainsShellCommandBlock(String rendered, String command) {
    String expectedShellBlock =
        CliTextFormat.renderShellCommandBlock(
            List.of(command), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    assertTrue(rendered.contains(expectedShellBlock), rendered);
  }

  protected final List<String> acceptedPostingFieldPaths(
      ContractRequestShapes.PostEntryRequestShapeDescriptor postingModel) {
    List<String> labels = new ArrayList<>();
    appendAcceptedFieldPaths(labels, postingModel.topLevelFields(), "");
    appendAcceptedFieldPaths(labels, postingModel.lineFields(), "lines[].");
    appendAcceptedFieldPaths(labels, postingModel.openingBalanceFields(), "openingBalances[].");
    appendAcceptedFieldPaths(labels, postingModel.evidenceFields(), "evidence.");
    appendAcceptedFieldPaths(
        labels, postingModel.sourceDocumentFields(), "evidence.sourceDocuments[].");
    appendAcceptedFieldPaths(labels, postingModel.approvalFields(), "evidence.approvals[].");
    appendAcceptedFieldPaths(labels, postingModel.provenanceFields(), "provenance.");
    appendAcceptedFieldPaths(labels, postingModel.reversalFields(), "reversal.");
    return List.copyOf(labels);
  }

  protected final List<String> extractRenderedFieldBlocks(String rendered, List<String> labels) {
    List<String> renderedLines = rendered.lines().toList();
    List<String> blocks = new ArrayList<>();
    int searchFrom = 0;
    for (int labelIndex = 0; labelIndex < labels.size(); labelIndex++) {
      String label = labels.get(labelIndex);
      int startLineIndex = indexOfLineStartingWith(renderedLines, label, searchFrom);
      if (startLineIndex < 0) {
        continue;
      }
      int nextLabelLineIndex = renderedLines.size();
      for (int nextLabelIndex = labelIndex + 1; nextLabelIndex < labels.size(); nextLabelIndex++) {
        int candidateIndex =
            indexOfLineStartingWith(renderedLines, labels.get(nextLabelIndex), startLineIndex + 1);
        if (candidateIndex >= 0) {
          nextLabelLineIndex = candidateIndex;
          break;
        }
      }
      int sectionBreakLineIndex = indexOfBlankLine(renderedLines, startLineIndex + 1);
      int endLineIndex =
          sectionBreakLineIndex >= 0
              ? Math.min(nextLabelLineIndex, sectionBreakLineIndex)
              : nextLabelLineIndex;
      blocks.add(String.join("\n", renderedLines.subList(startLineIndex, endLineIndex)));
      searchFrom = endLineIndex;
    }
    return List.copyOf(blocks);
  }

  protected final List<String> normalizeNestedPostingBlocks(List<String> blocks, String prefix) {
    return blocks.stream().map(block -> normalizeNestedPostingBlock(block, prefix)).toList();
  }

  protected final List<String> renderedFieldLabels(List<String> blocks) {
    return blocks.stream().map(CliDiscoveryHelpTextTestSupport::renderedFieldLabel).toList();
  }

  protected final List<String> normalizedFieldBlocks(List<String> blocks) {
    return blocks.stream().map(CliDiscoveryHelpTextTestSupport::normalizedFieldBlock).toList();
  }

  protected final String firstLineStartingWith(String rendered, String prefix) {
    return rendered
        .lines()
        .filter(line -> line.startsWith(prefix))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing line starting with: " + prefix));
  }

  protected final void assertContainsNestedPostingModelPaths(
      String rendered, ContractRequestShapes.PostEntryRequestShapeDescriptor postingModel) {
    assertContainsPrefixedFieldRows(rendered, postingModel.topLevelFields(), "steps[].posting.");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.lineFields(), "steps[].posting.lines[].");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.openingBalanceFields(), "steps[].posting.openingBalances[].");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.evidenceFields(), "steps[].posting.evidence.");
    assertContainsPrefixedFieldRows(
        rendered,
        postingModel.sourceDocumentFields(),
        "steps[].posting.evidence.sourceDocuments[].");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.approvalFields(), "steps[].posting.evidence.approvals[].");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.provenanceFields(), "steps[].posting.provenance.");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.reversalFields(), "steps[].posting.reversal.");
  }

  protected final Optional<String> expectedRequestTemplateSupportCommand(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    if (helpDescriptor.requestTemplate() != null
        || helpDescriptor.declareAccountTemplate() != null) {
      return Optional.of(
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + operationId.wireName());
    }
    if (helpDescriptor.planTemplate() != null) {
      return Optional.of(CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE));
    }
    return Optional.empty();
  }

  protected final HelpDescriptor helpDescriptorWithPlanTemplate(
      HelpDescriptor baseHelp, ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    return new HelpDescriptor(
        baseHelp.application(),
        baseHelp.version(),
        baseHelp.description(),
        baseHelp.usage(),
        baseHelp.bookModel(),
        baseHelp.bookkeepingKernel(),
        baseHelp.requestShapes(),
        baseHelp.requestTemplate(),
        baseHelp.declareAccountTemplate(),
        planTemplate,
        baseHelp.commands(),
        baseHelp.quickStart(),
        baseHelp.exitCodes(),
        baseHelp.preflight(),
        baseHelp.currencyModel());
  }

  protected final ContractTemplates.PostingRequestTemplateDescriptor
      conflictingOpenAccountingPositionTemplate() {
    ContractTemplates.PostingRequestTemplateDescriptor canonical =
        MachineContract.requestTemplate();
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION,
        null,
        "2026-01-01",
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(
            new ContractTemplates.OpeningBalanceTemplateDescriptor(
                "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
            new ContractTemplates.OpeningBalanceTemplateDescriptor(
                "opening-equity", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000"))),
        canonical.evidence(),
        canonical.provenance(),
        null);
  }

  private static void appendAcceptedFieldPaths(
      List<String> labels,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String prefix) {
    for (ContractRequestShapes.RequestFieldDescriptor field : fields) {
      if (field.presence() != RequestFieldPresence.FORBIDDEN) {
        labels.add(prefix + field.name());
      }
    }
  }

  private static String normalizeNestedPostingBlock(String block, String prefix) {
    List<String> lines = block.lines().toList();
    if (lines.isEmpty()) {
      return block;
    }
    String firstLine = lines.getFirst();
    String normalizedFirstLine =
        firstLine.startsWith(prefix) ? firstLine.substring(prefix.length()) : firstLine;
    if (lines.size() == 1) {
      return normalizedFirstLine;
    }
    return String.join(
        "\n", normalizedFirstLine, String.join("\n", lines.subList(1, lines.size())));
  }

  private static String renderedFieldLabel(String block) {
    String firstLine = block.lines().findFirst().orElseThrow();
    int separatorIndex = firstLine.indexOf(" : ");
    if (separatorIndex >= 0) {
      return firstLine.substring(0, separatorIndex).stripTrailing();
    }
    if (firstLine.endsWith(":")) {
      return firstLine.substring(0, firstLine.length() - 1).stripTrailing();
    }
    return firstLine;
  }

  private static String normalizedFieldBlock(String block) {
    List<String> lines = block.lines().toList();
    String firstLine = lines.getFirst();
    int separatorIndex = firstLine.indexOf(" : ");
    if (separatorIndex < 0) {
      if (firstLine.endsWith(":")) {
        String label = firstLine.substring(0, firstLine.length() - 1).stripTrailing();
        String description =
            lines.subList(1, lines.size()).stream()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return label + " : " + description;
      }
      return block.replaceAll("\\s+", " ").trim();
    }
    String label = firstLine.substring(0, separatorIndex).stripTrailing();
    List<String> descriptionLines = new ArrayList<>();
    descriptionLines.add(firstLine.substring(separatorIndex + 3));
    descriptionLines.addAll(lines.subList(1, lines.size()));
    String description = String.join(" ", descriptionLines).replaceAll("\\s+", " ").trim();
    return label + " : " + description;
  }

  private static int indexOfLineStartingWith(
      List<String> renderedLines, String label, int fromIndex) {
    for (int index = fromIndex; index < renderedLines.size(); index++) {
      if (renderedLines.get(index).startsWith(label)) {
        return index;
      }
    }
    return -1;
  }

  private static int indexOfBlankLine(List<String> renderedLines, int fromIndex) {
    for (int index = fromIndex; index < renderedLines.size(); index++) {
      if (renderedLines.get(index).isBlank()) {
        return index;
      }
    }
    return -1;
  }

  private static void assertContainsPrefixedFieldRows(
      String rendered, List<ContractRequestShapes.RequestFieldDescriptor> fields, String prefix) {
    for (ContractRequestShapes.RequestFieldDescriptor field : fields) {
      if (field.presence() == RequestFieldPresence.FORBIDDEN) {
        continue;
      }
      assertTrue(rendered.contains(prefix + field.name()), rendered);
    }
  }
}
