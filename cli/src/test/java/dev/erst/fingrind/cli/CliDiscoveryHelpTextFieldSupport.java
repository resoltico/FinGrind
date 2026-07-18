package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import java.util.ArrayList;
import java.util.List;

/** Parsing and assertion helpers for rendered discovery request-field rows. */
final class CliDiscoveryHelpTextFieldSupport {
  private CliDiscoveryHelpTextFieldSupport() {}

  static List<String> acceptedPostingFieldPaths(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel) {
    List<String> labels = new ArrayList<>();
    appendAcceptedFieldPaths(labels, postingModel.topLevelFields(), "");
    appendAcceptedFieldPaths(labels, postingModel.lineFields(), "lines[].");
    appendAcceptedFieldPaths(labels, postingModel.openingBalanceFields(), "openingBalances[].");
    appendAcceptedFieldPaths(
        labels, postingModel.recognitionIntervalFields(), "recognitionInterval.");
    appendAcceptedFieldPaths(labels, postingModel.foreignExchangeFields(), "foreignExchange.");
    appendAcceptedFieldPaths(
        labels, postingModel.quotedRateFields(), "foreignExchange.quotedRate.");
    appendAcceptedFieldPaths(labels, postingModel.taxFields(), "tax.");
    appendAcceptedFieldPaths(labels, postingModel.evidenceFields(), "evidence.");
    appendAcceptedFieldPaths(
        labels, postingModel.sourceDocumentFields(), "evidence.sourceDocuments[].");
    appendAcceptedFieldPaths(labels, postingModel.approvalFields(), "evidence.approvals[].");
    appendAcceptedFieldPaths(labels, postingModel.provenanceFields(), "provenance.");
    appendAcceptedFieldPaths(labels, postingModel.reversalFields(), "reversal.");
    return List.copyOf(labels);
  }

  static List<String> extractRenderedFieldBlocks(String rendered, List<String> labels) {
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

  static List<String> normalizeNestedPostingBlocks(List<String> blocks, String prefix) {
    return blocks.stream().map(block -> normalizeNestedPostingBlock(block, prefix)).toList();
  }

  static List<String> renderedFieldLabels(List<String> blocks) {
    return blocks.stream().map(CliDiscoveryHelpTextFieldSupport::renderedFieldLabel).toList();
  }

  static List<String> normalizedFieldBlocks(List<String> blocks) {
    return blocks.stream().map(CliDiscoveryHelpTextFieldSupport::normalizedFieldBlock).toList();
  }

  static String firstLineStartingWith(String rendered, String prefix) {
    return rendered
        .lines()
        .filter(line -> line.startsWith(prefix))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing line starting with: " + prefix));
  }

  static void assertContainsNestedPostingModelPaths(
      String rendered, ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel) {
    assertContainsPrefixedFieldRows(rendered, postingModel.topLevelFields(), "steps[].posting.");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.lineFields(), "steps[].posting.lines[].");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.openingBalanceFields(), "steps[].posting.openingBalances[].");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.recognitionIntervalFields(), "steps[].posting.recognitionInterval.");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.foreignExchangeFields(), "steps[].posting.foreignExchange.");
    assertContainsPrefixedFieldRows(
        rendered, postingModel.quotedRateFields(), "steps[].posting.foreignExchange.quotedRate.");
    assertContainsPrefixedFieldRows(rendered, postingModel.taxFields(), "steps[].posting.tax.");
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
