package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical discovery-operation registry for the public FinGrind protocol catalog. */
final class ProtocolDiscoveryOperations {
  private ProtocolDiscoveryOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        ProtocolOperationSupport.operation(
            OperationId.HELP,
            OperationCategory.DISCOVERY,
            "Help",
            List.of("--help", "-h"),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            "Print command usage, examples, and workflow guidance.",
            List.of()),
        ProtocolOperationSupport.operation(
            OperationId.VERSION,
            OperationCategory.DISCOVERY,
            "Version",
            List.of("--version"),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            "Print application identity, version, and description.",
            List.of()),
        ProtocolOperationSupport.operation(
            OperationId.CAPABILITIES,
            OperationCategory.DISCOVERY,
            "Capabilities",
            List.of(),
            List.of(),
            ExecutionMode.JSON_ENVELOPE,
            "Print the canonical machine-readable contract for commands, request shapes, and responses.",
            List.of()),
        ProtocolOperationSupport.operation(
            OperationId.PRINT_REQUEST_TEMPLATE,
            OperationCategory.DISCOVERY,
            "Print Request Template",
            List.of("--print-request-template"),
            List.of(),
            ExecutionMode.RAW_JSON,
            "Print a minimal valid posting request JSON document.",
            List.of(
                "fingrind %s > request.json"
                    .formatted(OperationId.PRINT_REQUEST_TEMPLATE.wireName()))),
        ProtocolOperationSupport.operation(
            OperationId.PRINT_PLAN_TEMPLATE,
            OperationCategory.DISCOVERY,
            "Print Plan Template",
            List.of("--print-plan-template"),
            List.of(),
            ExecutionMode.RAW_JSON,
            "Print a minimal valid AI-agent ledger plan JSON document.",
            List.of(
                "fingrind %s > plan.json".formatted(OperationId.PRINT_PLAN_TEMPLATE.wireName()))));
  }
}
