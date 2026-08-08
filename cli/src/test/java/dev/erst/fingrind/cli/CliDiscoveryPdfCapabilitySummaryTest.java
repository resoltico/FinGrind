package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ArtifactOutputDescriptor;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.CommandCatalogDescriptor;
import dev.erst.fingrind.contract.discovery.CommandDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.ExecutionMode;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves that textual PDF discovery is an exact command-descriptor projection. */
class CliDiscoveryPdfCapabilitySummaryTest {
  @Test
  void render_usesExactZeroOneTwoAndManyCommandGrammar() {
    assertEquals(
        "No report commands can emit pdf via --pdf-out <path>.",
        CliDiscoveryPdfCapabilitySummary.render(List.of()));
    assertEquals(
        "account-balance can emit pdf via --pdf-out <path>.",
        CliDiscoveryPdfCapabilitySummary.render(List.of(pdfCommand(OperationId.ACCOUNT_BALANCE))));
    assertEquals(
        "account-balance and trial-balance can emit pdf via --pdf-out <path>.",
        CliDiscoveryPdfCapabilitySummary.render(
            List.of(
                pdfCommand(OperationId.ACCOUNT_BALANCE), pdfCommand(OperationId.TRIAL_BALANCE))));
    assertEquals(
        "account-balance, trial-balance, and account-ledger can emit pdf via --pdf-out <path>.",
        CliDiscoveryPdfCapabilitySummary.render(
            List.of(
                pdfCommand(OperationId.ACCOUNT_BALANCE),
                pdfCommand(OperationId.TRIAL_BALANCE),
                pdfCommand(OperationId.ACCOUNT_LEDGER))));
  }

  @Test
  void render_rejectsDuplicateOperationIdsInsteadOfSilentlyCollapsingThem() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliDiscoveryPdfCapabilitySummary.render(
                    List.of(
                        pdfCommand(OperationId.ACCOUNT_BALANCE),
                        pdfCommand(OperationId.ACCOUNT_BALANCE))));

    assertEquals(
        "Duplicate query report descriptor in PDF capability discovery: account-balance",
        exception.getMessage());
  }

  @Test
  void renderCapabilitiesText_usesOnlyTheSuppliedCapabilitiesDescriptor() {
    String rendered =
        CliDiscoveryOutputRenderer.renderCapabilitiesText(
            capabilitiesWithQueryCommands(
                List.of(
                    pdfCommand(OperationId.INVENTORY_VALUATION),
                    pdfCommand(OperationId.ACCRUAL_CUTOFF_SCHEDULE))));

    assertTrue(
        rendered.contains(
            "inventory-valuation and accrual-cutoff-schedule can emit pdf via --pdf-out <path>."),
        rendered);
    assertFalse(rendered.contains("account-balance, trial-balance"), rendered);
  }

  @Test
  void render_canonicalDescriptorIncludesEveryPublishedPdfCapableReportInOrder() {
    assertEquals(
        "tax-obligation, account-balance, trial-balance, account-ledger, period-summary, "
            + "financial-position, inventory-valuation, accrual-cutoff-schedule, "
            + "fixed-asset-register, financing-register, realized-foreign-exchange-register, "
            + "latvian-payroll-register, income-statement, cash-flow-statement, and "
            + "changes-in-equity can emit pdf via --pdf-out <path>.",
        CliDiscoveryPdfCapabilitySummary.render(
            MachineContract.capabilities(CliDiscoveryTestSupport.identity())));
  }

  private static CapabilitiesDescriptor capabilitiesWithQueryCommands(
      List<CommandDescriptor> queryCommands) {
    CapabilitiesDescriptor canonical =
        MachineContract.capabilities(CliDiscoveryTestSupport.identity());
    return new CapabilitiesDescriptor(
        canonical.application(),
        canonical.version(),
        canonical.protocolVersion(),
        canonical.storage(),
        new CommandCatalogDescriptor(List.of(), List.of(), queryCommands, List.of()),
        canonical.requestInput(),
        canonical.requestShapes(),
        canonical.responseModel(),
        canonical.planExecution(),
        canonical.audit(),
        canonical.accountRegistry(),
        canonical.reversals(),
        canonical.preflight(),
        canonical.currencyModel(),
        canonical.bookkeepingKernel(),
        canonical.capabilityCatalog());
  }

  private static CommandDescriptor pdfCommand(OperationId operationId) {
    return new CommandDescriptor(
        operationId,
        ProtocolCatalog.operation(operationId).displayLabel(),
        List.of(),
        List.of("--pdf-out <path>"),
        ExecutionMode.JSON_ENVELOPE,
        List.of(OutputMode.TEXT),
        List.of(new ArtifactOutputDescriptor("pdf", "--pdf-out <path>", "Write one PDF")),
        "Read one report");
  }
}
