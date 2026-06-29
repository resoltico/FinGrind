package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.StorageEngine;

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
                java.util.List.of("Protocol version", capabilitiesDescriptor.protocolVersion()),
                java.util.List.of(
                    "Book boundary", displayBookBoundary(storageDescriptor.bookBoundary())),
                java.util.List.of(
                    "Storage engine",
                    CliTextFormat.joined(
                        storageDescriptor.engines().stream()
                            .map(CliDiscoveryCapabilitiesTextRenderer::displayStorageEngine)
                            .toList()))),
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
                java.util.List.of("Currency model", displayCurrencyModel(capabilitiesDescriptor)),
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
    String operatorNextSteps =
        CliTextFormat.renderKeyValueBlock(
            java.util.List.of(
                java.util.List.of(
                    "Operator guide", CliInvocationText.commandExample(OperationId.HELP)),
                java.util.List.of(
                    "First report path",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + OperationId.TRIAL_BALANCE.wireName()),
                java.util.List.of("PDF-capable reports", pdfCapableReportSummary())),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Capabilities",
        CliDiscoveryTextSupport.joinSections(
            header,
            CliDiscoveryTextSupport.section("Operator Overview", operatorOverview),
            CliDiscoveryTextSupport.section("Next Steps", operatorNextSteps)));
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

  private static String displayCurrencyModel(CapabilitiesDescriptor capabilitiesDescriptor) {
    String scope = displayKernelScope(capabilitiesDescriptor.currencyModel().scope());
    String multiCurrency =
        switch (capabilitiesDescriptor.currencyModel().multiCurrencyStatus()) {
          case "not-supported" -> "additional transaction currencies are not available";
          case "owned-foreign-exchange-only" ->
              "foreign-currency business events are supported through owned foreign-exchange facts while mixed-currency journal lines remain unavailable";
          case "supported" -> "additional transaction currencies are available";
          default -> capabilitiesDescriptor.currencyModel().multiCurrencyStatus().replace('-', ' ');
        };
    return scope + "; " + multiCurrency + ".";
  }

  private static String displayBookBoundary(String wireValue) {
    if ("protected-book-file".equals(wireValue)) {
      return "One protected book per file.";
    }
    if ("single-sqlite-file".equals(wireValue)) {
      return "One SQLite-backed protected book per file.";
    }
    return displayKernelScope(wireValue) + ".";
  }

  private static String displayStorageEngine(StorageEngine ignored) {
    return "SQLite";
  }
}
