package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.RequestFieldPresence;
import java.util.ArrayList;
import java.util.List;

/** Renders the complete field structure of one machine-authored ledger plan. */
final class CliDiscoveryPlanStructureGuidance {
  private static final int HELP_STRUCTURE_LABEL_WIDTH_CAP = 32;

  private CliDiscoveryPlanStructureGuidance() {}

  static String render(ContractRequestShapes.LedgerPlanRequestShapeDescriptor ledgerPlanShape) {
    List<List<String>> rows = new ArrayList<>();
    appendTopLevelLedgerPlanRows(rows, ledgerPlanShape.topLevelFields(), "steps");
    appendRequestFields(rows, ledgerPlanShape.stepFields(), "steps[].");
    appendRequestFields(rows, ledgerPlanShape.queryFields(), "steps[].query.");
    appendRequestFields(rows, ledgerPlanShape.assertionFields(), "steps[].assertion.");
    appendPostingRows(rows, ledgerPlanShape.postingModel());
    return CliTextFormat.renderKeyValueBlock(
        List.copyOf(rows), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH, HELP_STRUCTURE_LABEL_WIDTH_CAP);
  }

  private static void appendPostingRows(
      List<List<String>> rows,
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor postingModel) {
    appendRequestFields(rows, postingModel.topLevelFields(), "steps[].posting.");
    appendRequestFields(rows, postingModel.lineFields(), "steps[].posting.lines[].");
    appendRequestFields(
        rows, postingModel.openingBalanceFields(), "steps[].posting.openingBalances[].");
    appendRequestFields(
        rows, postingModel.recognitionIntervalFields(), "steps[].posting.recognitionInterval.");
    appendRequestFields(
        rows, postingModel.foreignExchangeFields(), "steps[].posting.foreignExchange.");
    appendRequestFields(
        rows, postingModel.quotedRateFields(), "steps[].posting.foreignExchange.quotedRate.");
    appendRequestFields(rows, postingModel.taxFields(), "steps[].posting.tax.");
    appendRequestFields(rows, postingModel.evidenceFields(), "steps[].posting.evidence.");
    appendRequestFields(
        rows, postingModel.sourceDocumentFields(), "steps[].posting.evidence.sourceDocuments[].");
    appendRequestFields(
        rows, postingModel.approvalFields(), "steps[].posting.evidence.approvals[].");
    appendRequestFields(rows, postingModel.provenanceFields(), "steps[].posting.provenance.");
    appendRequestFields(rows, postingModel.reversalFields(), "steps[].posting.reversal.");
  }

  private static void appendRequestFields(
      List<List<String>> rows,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String prefix) {
    fields.stream()
        .filter(field -> field.presence() != RequestFieldPresence.FORBIDDEN)
        .forEach(field -> rows.add(List.of(prefix + field.name(), field.description())));
  }

  private static void appendTopLevelLedgerPlanRows(
      List<List<String>> rows,
      List<ContractRequestShapes.RequestFieldDescriptor> fields,
      String arrayFieldName) {
    for (ContractRequestShapes.RequestFieldDescriptor field : fields) {
      String fieldPath = arrayFieldName.equals(field.name()) ? field.name() + "[]" : field.name();
      rows.add(List.of(fieldPath, field.description()));
    }
  }
}
