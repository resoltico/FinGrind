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
                "[<command>]",
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT)),
                ProtocolOptions.optionalJsonOnlyDiscoveryDetailSyntax()),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            "Print command usage, examples, and workflow guidance.",
            List.of(
                ProtocolExampleStep.command("fingrind help post-entry"),
                ProtocolExampleStep.command("fingrind post-entry --help"),
                ProtocolExampleStep.command("fingrind help post-entry --output json"),
                ProtocolExampleStep.command(
                    "fingrind help post-entry --output json --detail full"))),
        ProtocolOperationDefinitions.operation(
            OperationId.VERSION,
            OperationCategory.DISCOVERY,
            "Version",
            List.of("--version"),
            List.of(
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            "Print application identity, version, and description.",
            List.of()),
        ProtocolOperationDefinitions.operation(
            OperationId.CAPABILITIES,
            OperationCategory.DISCOVERY,
            "Capabilities",
            List.of(),
            List.of(
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT)),
                ProtocolOptions.optionalJsonOnlyDiscoveryDetailSyntax()),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            "Print the canonical machine-readable contract for commands, request shapes, and responses.",
            List.of(
                ProtocolExampleStep.command("fingrind capabilities --output json"),
                ProtocolExampleStep.command("fingrind capabilities --output json --detail full"))),
        ProtocolOperationDefinitions.operation(
            OperationId.ENVIRONMENT,
            OperationCategory.DISCOVERY,
            "Environment",
            List.of(),
            List.of(
                ProtocolOptions.optionalOutputSyntax(List.of(OutputMode.JSON, OutputMode.TEXT))),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            "Print live runtime, distribution, and SQLite provenance facts for this launcher instance.",
            List.of()),
        ProtocolOperationDefinitions.operation(
            OperationId.PRINT_REQUEST_TEMPLATE,
            OperationCategory.DISCOVERY,
            "Print Request Template",
            List.of("--print-request-template"),
            List.of("[post-entry|preflight-entry|declare-account]"),
            ExecutionMode.RAW_JSON,
            "Print the canonical minimal request scaffold JSON document for one request-file command.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s > request.json"
                        .formatted(OperationId.PRINT_REQUEST_TEMPLATE.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s declare-account > declare-account.json"
                        .formatted(OperationId.PRINT_REQUEST_TEMPLATE.wireName())),
                ProtocolExampleStep.note(
                    "The emitted JSON is a runnable sample document. Replace the placeholder evidence and provenance values before using it for real-world bookkeeping."))),
        ProtocolOperationDefinitions.operation(
            OperationId.PRINT_PLAN_TEMPLATE,
            OperationCategory.DISCOVERY,
            "Print Plan Template",
            List.of("--print-plan-template"),
            List.of(),
            ExecutionMode.RAW_JSON,
            "Print the canonical minimal AI-agent ledger plan scaffold JSON document.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s > plan.json"
                        .formatted(OperationId.PRINT_PLAN_TEMPLATE.wireName())),
                ProtocolExampleStep.note(
                    "The emitted plan is a runnable sample workflow for a fresh book. Replace the placeholder evidence and provenance values before using it for real-world bookkeeping."))));
  }
}
