package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplates;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolRequestTemplateTopics;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Optional;

/** Shared helper logic for discovery help text renderer tests. */
class CliDiscoveryHelpTextTestSupport {
  protected final String renderHelpText(HelpDescriptor helpDescriptor) {
    return CliDiscoveryOutputRenderer.renderHelpText(
        helpDescriptor, CliDiscoveryTestSupport.environment(), false);
  }

  protected final String renderHelpText(
      HelpDescriptor helpDescriptor,
      dev.erst.fingrind.contract.runtime.EnvironmentDescriptor environmentDescriptor,
      boolean terse) {
    return CliDiscoveryOutputRenderer.renderHelpText(helpDescriptor, environmentDescriptor, terse);
  }

  protected final void restoreRuntimeDistribution(String previousDistribution) {
    if (previousDistribution == null) {
      System.clearProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY);
      return;
    }
    System.setProperty(FinGrindCli.RUNTIME_DISTRIBUTION_PROPERTY, previousDistribution);
  }

  protected final void assertContainsShellCommandBlock(String rendered, String command) {
    String expectedShellBlock =
        CliTextFormat.renderShellCommandBlock(
            List.of(command), CliDiscoveryTextSupport.TEXT_WRAP_WIDTH);
    assertTrue(rendered.contains(expectedShellBlock), rendered);
  }

  protected final Optional<String> expectedRequestTemplateSupportCommand(
      HelpDescriptor helpDescriptor, OperationId operationId) {
    if (ProtocolRequestTemplateTopics.supports(operationId)) {
      return Optional.of(
          CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
              + " "
              + operationId.wireName());
    }
    if (helpDescriptor.planTemplate() != null) {
      return Optional.of(CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE));
    }
    return Optional.empty();
  }

  protected final HelpDescriptor helpDescriptorWithPlanTemplate(
      HelpDescriptor baseHelp, ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    return new HelpDescriptor(
        baseHelp.application(),
        baseHelp.version(),
        baseHelp.protocolVersion(),
        baseHelp.description(),
        baseHelp.usage(),
        baseHelp.bookModel(),
        baseHelp.bookkeepingKernel(),
        baseHelp.requestShapes(),
        baseHelp.requestTemplate(),
        baseHelp.declareAccountTemplate(),
        baseHelp.declareTaxRegistrationTemplate(),
        planTemplate,
        baseHelp.commands(),
        baseHelp.quickStart(),
        baseHelp.exitCodes(),
        baseHelp.preflight(),
        baseHelp.currencyModel());
  }

  protected final ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      conflictingOpeningPositionTemplate() {
    ContractPostingRequestTemplates.PostingRequestTemplateDescriptor canonical =
        MachineContract.requestTemplate();
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.OPENING_POSITION,
        "2026-01-01",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(
            new ContractTemplates.OpeningBalanceTemplateDescriptor(
                "cash", JournalLine.EntrySide.DEBIT, new MonetaryAmount("EUR", "1000")),
            new ContractTemplates.OpeningBalanceTemplateDescriptor(
                "opening-equity", JournalLine.EntrySide.CREDIT, new MonetaryAmount("EUR", "1000"))),
        canonical.evidence(),
        canonical.provenance(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
