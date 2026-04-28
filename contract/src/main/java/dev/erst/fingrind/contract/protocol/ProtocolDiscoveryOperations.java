package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical discovery-operation registry for the public FinGrind protocol catalog. */
final class ProtocolDiscoveryOperations {
  private ProtocolDiscoveryOperations() {}

  static List<ProtocolOperation> operations() {
    return List.of(
        ProtocolOperationDefinitions.operation(
            OperationId.HELP,
            OperationCategory.DISCOVERY,
            "Help",
            List.of("--help", "-h"),
            List.of(
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Print command usage, examples, and workflow guidance.",
            List.of()),
        ProtocolOperationDefinitions.operation(
            OperationId.VERSION,
            OperationCategory.DISCOVERY,
            "Version",
            List.of("--version"),
            List.of(
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Print application identity, version, and description.",
            List.of()),
        ProtocolOperationDefinitions.operation(
            OperationId.CAPABILITIES,
            OperationCategory.DISCOVERY,
            "Capabilities",
            List.of(),
            List.of(
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.HUMAN))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.HUMAN),
            "Print the canonical machine-readable contract for commands, request shapes, and responses.",
            List.of()),
        ProtocolOperationDefinitions.operation(
            OperationId.PRINT_REQUEST_TEMPLATE,
            OperationCategory.DISCOVERY,
            "Print Request Template",
            List.of("--print-request-template"),
            List.of(),
            ExecutionMode.RAW_JSON,
            "Print the canonical minimal posting request scaffold JSON document.",
            List.of(
                "fingrind %s > request.json"
                    .formatted(OperationId.PRINT_REQUEST_TEMPLATE.wireName()),
                "Edit request.json and replace scaffold placeholders such as effectiveDate and every replace-before-commit-* provenance value before submission.")),
        ProtocolOperationDefinitions.operation(
            OperationId.PRINT_PLAN_TEMPLATE,
            OperationCategory.DISCOVERY,
            "Print Plan Template",
            List.of("--print-plan-template"),
            List.of(),
            ExecutionMode.RAW_JSON,
            "Print the canonical minimal AI-agent ledger plan scaffold JSON document.",
            List.of(
                "fingrind %s > plan.json".formatted(OperationId.PRINT_PLAN_TEMPLATE.wireName()),
                "Edit plan.json and replace scaffold placeholders such as effectiveDate and every nested replace-before-commit-* provenance value before submission.")));
  }
}
