package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.ExitCodeDescriptor;
import java.util.List;

/** Builds preparation, temporal-scope, and exit guidance shared by command help. */
final class CliDiscoveryOperationalGuidance {
  private CliDiscoveryOperationalGuidance() {}

  static String renderPreparation(OperationId operationId) {
    List<List<String>> rows = preparationRows(operationId);
    return rows.isEmpty()
        ? ""
        : CliDiscoveryTextSupport.section("Preparation", CliTextFormat.renderKeyValueBlock(rows));
  }

  static String renderExitBehavior(List<ExitCodeDescriptor> exitCodes) {
    List<List<String>> rows =
        exitCodes.stream()
            .<List<String>>map(
                exitCode -> List.of(Integer.toString(exitCode.code()), exitCode.meaning()))
            .toList();
    return rows.isEmpty()
        ? ""
        : CliDiscoveryTextSupport.section("Exit Behavior", CliTextFormat.renderKeyValueBlock(rows));
  }

  static String renderTemporalScope(OperationId operationId) {
    if (!CliDiscoveryOperationFamilies.hasTemporalScope(operationId)) {
      return "";
    }
    return CliDiscoveryTextSupport.section(
        "Temporal Scope",
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Scope kind", CliTemporalScopeText.scopeKind(operationId)),
                List.of(
                    "Boundary flags",
                    String.join(", ", CliTemporalScopeText.optionNames(operationId))),
                List.of(
                    "Boundary behavior", CliTemporalScopeText.boundarySemantics(operationId)))));
  }

  private static List<List<String>> preparationRows(OperationId operationId) {
    if (operationId == OperationId.DECLARE_ACCOUNT) {
      return List.of(
          List.of("Needs", initializedBookText()),
          List.of(
              "Next step after success",
              CliDiscoveryCommandExamples.primaryCommandExample(OperationId.LIST_ACCOUNTS)));
    }
    if (operationId == OperationId.DECLARE_TAX_REGISTRATION) {
      return List.of(
          List.of("Needs", initializedBookText()),
          List.of(
              "Next step after success",
              CliDiscoveryCommandExamples.primaryCommandExample(
                  OperationId.LIST_TAX_REGISTRATIONS)));
    }
    if (CliDiscoveryOperationFamilies.isEntryRequest(operationId)) {
      return List.of(
          List.of("Needs", initializedBookWithDeclaredAccountsText()),
          List.of(
              "Next step after success",
              CliDiscoveryCommandExamples.primaryCommandExample(OperationId.TRIAL_BALANCE)));
    }
    if (CliDiscoveryOperationFamilies.isBookRead(operationId)) {
      return List.of(List.of("Needs", initializedBookText()));
    }
    if (operationId == OperationId.EXECUTE_PLAN) {
      return List.of(
          List.of("Needs", "A ledger plan JSON document passed through --request-file."),
          List.of(
              "Attestation credentials",
              "Required exactly when the decoded plan contains a mutating step; forbidden for a query-only or assertion-only plan. Use "
                  + ProtocolOptions.Attestation.CUSTODIAN
                  + " with one through 64 aligned credential triplets only for a mutating plan."),
          List.of(
              "Starter file", CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
    }
    return List.of();
  }

  private static String initializedBookText() {
    return "A protected book initialized with "
        + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
        + ".";
  }

  private static String initializedBookWithDeclaredAccountsText() {
    return "A protected book initialized with "
        + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
        + " and every referenced account declared.";
  }
}
