package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
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
                    "Command families",
                    Integer.toString(commandCatalog.discovery().size())
                        + " discovery, "
                        + commandCatalog.administration().size()
                        + " administration, "
                        + commandCatalog.query().size()
                        + " query/report, "
                        + commandCatalog.write().size()
                        + " write")),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String startHere =
        CliTextFormat.renderKeyValueBlock(
            java.util.List.of(
                java.util.List.of(
                    "Create or open one book",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + OperationId.OPEN_BOOK.wireName()),
                java.util.List.of(
                    "Declare the chart",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + OperationId.DECLARE_ACCOUNT.wireName()),
                java.util.List.of(
                    "Preflight or commit bookkeeping",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + OperationId.PREFLIGHT_ENTRY.wireName()
                        + " / "
                        + OperationId.POST_ENTRY.wireName()),
                java.util.List.of(
                    "Read accounts and statements",
                    CliInvocationText.commandExample(OperationId.HELP)
                        + " "
                        + OperationId.LIST_ACCOUNTS.wireName()
                        + " / "
                        + OperationId.TRIAL_BALANCE.wireName()),
                java.util.List.of(
                    "Browse one command",
                    CliInvocationText.commandExample(OperationId.HELP) + " <command>")),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String automation =
        CliTextFormat.renderKeyValueBlock(
            java.util.List.of(
                java.util.List.of(
                    "Machine-readable contract",
                    CliInvocationText.commandExample(OperationId.CAPABILITIES) + " --output json"),
                java.util.List.of(
                    "Live runtime evidence",
                    CliInvocationText.commandExample(OperationId.ENVIRONMENT) + " --output json"),
                java.util.List.of("PDF-capable reports", pdfCapableReportSummary())),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    String commandGroups =
        CliTextFormat.renderKeyValueBlock(
            java.util.List.of(
                java.util.List.of("Discovery", joinedCommandNames(commandCatalog.discovery())),
                java.util.List.of(
                    "Administration", joinedCommandNames(commandCatalog.administration())),
                java.util.List.of("Query and reports", joinedCommandNames(commandCatalog.query())),
                java.util.List.of("Write", joinedCommandNames(commandCatalog.write()))),
            CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    return CliTextFormat.renderTitledBlock(
        "FinGrind Capabilities",
        CliDiscoveryTextSupport.joinSections(
            header,
            CliDiscoveryTextSupport.section("What Exists", operatorOverview),
            CliDiscoveryTextSupport.section("Start Here", startHere),
            CliDiscoveryTextSupport.section("Command Groups", commandGroups),
            CliDiscoveryTextSupport.section("Automation", automation)));
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

  private static String joinedCommandNames(java.util.List<CommandDescriptor> commands) {
    return String.join(", ", commands.stream().map(command -> command.name().wireName()).toList());
  }
}
