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
                ProtocolOptions.optionalJsonOnlyDiscoveryDetailSyntax(),
                ProtocolOptions.optionalJsonOnlyOperationCategorySyntax()),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            "Print command usage, examples, and workflow guidance.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind help %s".formatted(OperationId.RECORD_SALE_SETTLED.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s --help".formatted(OperationId.RECORD_SALE_SETTLED.wireName())),
                ProtocolExampleStep.command(
                    "fingrind help %s --output json"
                        .formatted(OperationId.RECORD_SALE_SETTLED.wireName())),
                ProtocolExampleStep.command(
                    "fingrind help %s --output json --detail full"
                        .formatted(OperationId.RECORD_SALE_SETTLED.wireName())))),
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
                ProtocolOptions.optionalJsonOnlyDiscoveryDetailSyntax(),
                ProtocolOptions.optionalJsonOnlyDiscoveryFocusSyntax(),
                ProtocolOptions.optionalJsonOnlyOperationCategorySyntax()),
            ExecutionMode.JSON_ENVELOPE,
            List.of(OutputMode.JSON, OutputMode.TEXT),
            "Print the canonical machine-readable contract for commands, request shapes, and responses.",
            List.of(
                ProtocolExampleStep.command("fingrind capabilities --output json"),
                ProtocolExampleStep.command("fingrind capabilities --output json --category query"),
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
            List.of(
                ProtocolRequestTemplateTopics.syntax(),
                "["
                    + ProtocolOptions.BookDefinition.TEMPLATE_ID
                    + " <"
                    + String.join("|", dev.erst.fingrind.core.BookTemplateId.wireValues())
                    + ">]"),
            ExecutionMode.RAW_JSON,
            "Print the canonical minimal JSON scaffold for a selected structured-input topic.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s > request.json"
                        .formatted(OperationId.PRINT_REQUEST_TEMPLATE.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s %s > sale.json"
                        .formatted(
                            OperationId.PRINT_REQUEST_TEMPLATE.wireName(),
                            OperationId.RECORD_SALE_SETTLED.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s %s %s OWNER_MANAGED_TRADING > trading-sale.json"
                        .formatted(
                            OperationId.PRINT_REQUEST_TEMPLATE.wireName(),
                            OperationId.RECORD_SALE_SETTLED.wireName(),
                            ProtocolOptions.BookDefinition.TEMPLATE_ID)),
                ProtocolExampleStep.command(
                    "fingrind %s declare-account > declare-account.json"
                        .formatted(OperationId.PRINT_REQUEST_TEMPLATE.wireName())),
                ProtocolExampleStep.note(
                    "The emitted JSON is a placeholder-first scaffold. Replace every replace-before-commit token before submitting it to a live book."))),
        ProtocolOperationDefinitions.operation(
            OperationId.PRINT_PLAN_TEMPLATE,
            OperationCategory.DISCOVERY,
            "Print Plan Template",
            List.of("--print-plan-template"),
            List.of("[general|tax-setup|fixed-asset-setup|financing-setup]"),
            ExecutionMode.RAW_JSON,
            "Print a topic-specific executable ledger-plan scaffold JSON document.",
            List.of(
                ProtocolExampleStep.command(
                    "fingrind %s > plan.json"
                        .formatted(OperationId.PRINT_PLAN_TEMPLATE.wireName())),
                ProtocolExampleStep.command(
                    "fingrind %s fixed-asset-setup > fixed-asset-setup.json"
                        .formatted(OperationId.PRINT_PLAN_TEMPLATE.wireName())),
                ProtocolExampleStep.note(
                    "The emitted plan is a placeholder-first scaffold. Replace every replace-before-commit token before submitting it to a live book."))));
  }
}
