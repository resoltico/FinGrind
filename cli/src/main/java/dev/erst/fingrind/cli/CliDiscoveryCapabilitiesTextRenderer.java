package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;

/** Renders operator-facing capability inventory text for the CLI discovery surface. */
final class CliDiscoveryCapabilitiesTextRenderer {
  private CliDiscoveryCapabilitiesTextRenderer() {}

  static String renderCapabilitiesText(CapabilitiesDescriptor capabilitiesDescriptor) {
    var storageDescriptor = capabilitiesDescriptor.storage();
    CommandCatalogDescriptor commandCatalog = capabilitiesDescriptor.commands();
    String header =
        CliTextFormat.renderKeyValueBlock(
            java.util.List.of(
                java.util.List.of("Application", capabilitiesDescriptor.application()),
                java.util.List.of("Version", capabilitiesDescriptor.version()),
                java.util.List.of("Book boundary", storageDescriptor.bookBoundary()),
                java.util.List.of(
                    "Storage",
                    CliTextFormat.joined(
                        storageDescriptor.engines().stream().map(Object::toString).toList()))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String operatorOverview =
        CliTextFormat.renderKeyValueBlock(
            java.util.List.of(
                java.util.List.of(
                    "Kernel scope",
                    displayKernelScope(capabilitiesDescriptor.bookkeepingKernel().scope())),
                java.util.List.of(
                    "Built-in statements",
                    CliTextFormat.joined(
                        capabilitiesDescriptor.bookkeepingKernel().builtInStatements())),
                java.util.List.of(
                    "Currency model",
                    capabilitiesDescriptor.currencyModel().scope()
                        + " / "
                        + capabilitiesDescriptor.currencyModel().multiCurrencyStatus()),
                java.util.List.of(
                    "Discovery commands", Integer.toString(commandCatalog.discovery().size())),
                java.util.List.of(
                    "Administration commands",
                    Integer.toString(commandCatalog.administration().size())),
                java.util.List.of(
                    "Query and report commands", Integer.toString(commandCatalog.query().size())),
                java.util.List.of(
                    "Write commands", Integer.toString(commandCatalog.write().size()))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String automation =
        CliTextFormat.renderKeyValueBlock(
            java.util.List.of(
                java.util.List.of(
                    "Operator guide", CliInvocationText.commandExample(OperationId.HELP)),
                java.util.List.of(
                    "Machine-readable contract",
                    CliInvocationText.commandExample(OperationId.CAPABILITIES) + " --output json"),
                java.util.List.of(
                    "Live runtime evidence",
                    CliInvocationText.commandExample(OperationId.ENVIRONMENT) + " --output json"),
                java.util.List.of("PDF-capable reports", pdfCapableReportSummary())),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Capabilities",
        CliDiscoveryTextSupport.joinSections(
            header,
            CliDiscoveryTextSupport.section("What Exists", operatorOverview),
            CliDiscoveryTextSupport.section("Contracts And Automation", automation)));
  }

  private static String displayKernelScope(String scope) {
    return java.util.Arrays.stream(scope.strip().replace('-', ' ').replace('_', ' ').split("\\s+"))
        .map(
            token ->
                Character.toUpperCase(token.charAt(0))
                    + token.substring(1).toLowerCase(java.util.Locale.ROOT))
        .collect(java.util.stream.Collectors.joining(" "));
  }

  private static String pdfCapableReportSummary() {
    return String.join(
            ", ",
            CliInvocationText.commandExample(OperationId.ACCOUNT_BALANCE),
            CliInvocationText.commandExample(OperationId.TRIAL_BALANCE),
            CliInvocationText.commandExample(OperationId.ACCOUNT_LEDGER),
            CliInvocationText.commandExample(OperationId.PERIOD_SUMMARY),
            CliInvocationText.commandExample(OperationId.FINANCIAL_POSITION),
            CliInvocationText.commandExample(OperationId.INCOME_STATEMENT))
        + ", and "
        + CliInvocationText.commandExample(OperationId.CHANGES_IN_EQUITY)
        + " can emit pdf via --pdf-out <path>.";
  }
}
