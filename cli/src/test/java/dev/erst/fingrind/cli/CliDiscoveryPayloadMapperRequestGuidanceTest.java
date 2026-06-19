package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliDiscoveryHelpJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalLine;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused request-guidance coverage for {@link CliDiscoveryPayloadMapper}. */
class CliDiscoveryPayloadMapperRequestGuidanceTest extends CliResponseWriterTestSupport {
  @Test
  void helpPayload_mapsPostingRequestGuidanceForPostingCommands() {
    assertPostingGuidance(OperationId.POST_ENTRY);
    assertPostingGuidance(OperationId.PREFLIGHT_ENTRY);
  }

  @Test
  void helpPayload_mapsDeclareAccountAndLedgerPlanRequestGuidance() {
    CliDiscoveryHelpJsonModels.CommandHelpPayload declarePayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    OperationId.DECLARE_ACCOUNT)));
    CliDiscoveryHelpJsonModels.CommandHelpPayload compactDeclarePayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.compactHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    OperationId.DECLARE_ACCOUNT)));
    CliDiscoveryHelpJsonModels.CommandHelpPayload planPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    OperationId.EXECUTE_PLAN)));
    CliDiscoveryHelpJsonModels.CommandHelpPayload compactPlanPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.compactHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    OperationId.EXECUTE_PLAN)));

    assertNotNull(declarePayload.requestFile());
    assertEquals(
        ProtocolCatalog.operation(OperationId.DECLARE_ACCOUNT).usage(), declarePayload.syntax());
    assertNull(declarePayload.requestFile().postingTemplate());
    assertNotNull(declarePayload.requestFile().declareAccountTemplate());
    assertNull(declarePayload.requestFile().ledgerPlanTemplate());
    assertTrue(
        Objects.requireNonNull(declarePayload.requestFile().shortcutCommand())
            .contains("declare-account"));
    assertNotNull(compactDeclarePayload.requestFile());
    assertNull(compactDeclarePayload.requestFile().postingTemplate());
    assertNull(compactDeclarePayload.requestFile().declareAccountTemplate());
    assertNull(compactDeclarePayload.requestFile().ledgerPlanTemplate());
    assertNull(compactDeclarePayload.requestFile().requestShapes());
    assertTrue(
        Objects.requireNonNull(compactDeclarePayload.requestFile().shortcutCommand())
            .contains("declare-account"));

    assertNotNull(planPayload.requestFile());
    assertEquals(ProtocolCatalog.operation(OperationId.EXECUTE_PLAN).usage(), planPayload.syntax());
    assertNull(planPayload.requestFile().postingTemplate());
    assertNull(planPayload.requestFile().declareAccountTemplate());
    assertNotNull(planPayload.requestFile().ledgerPlanTemplate());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE),
        planPayload.requestFile().shortcutCommand());
    assertNotNull(compactPlanPayload.requestFile());
    assertNull(compactPlanPayload.requestFile().postingTemplate());
    assertNull(compactPlanPayload.requestFile().declareAccountTemplate());
    assertNull(compactPlanPayload.requestFile().ledgerPlanTemplate());
    assertNull(compactPlanPayload.requestFile().requestShapes());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE),
        compactPlanPayload.requestFile().shortcutCommand());
  }

  @Test
  void helpPayload_executePlanRejectsMissingCanonicalPostingScaffoldStep() {
    HelpDescriptor executePlan =
        MachineContract.help(
            CliDiscoveryPayloadMapperTest.identity(),
            CliDiscoveryPayloadMapperTest.environment(),
            OperationId.EXECUTE_PLAN);
    ContractPlanTemplates.LedgerPlanTemplateDescriptor baseTemplate =
        Objects.requireNonNull(executePlan.planTemplate());
    ContractPlanTemplates.LedgerPlanTemplateDescriptor withoutCanonicalPosting =
        new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
            baseTemplate.planId(),
            baseTemplate.steps().stream()
                .filter(step -> step.kind() != LedgerStepKind.POST_ENTRY)
                .toList());

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliDiscoveryPayloadMapperTest.fullHelpPayload(
                    helpDescriptorWithPlanTemplate(executePlan, withoutCanonicalPosting)));

    assertTrue(
        Objects.requireNonNull(failure.getMessage()).contains("canonical POST_ENTRY scaffold"),
        failure::getMessage);
  }

  @Test
  void helpPayload_executePlanRejectsAmbiguousCanonicalPostingScaffoldStep() {
    HelpDescriptor executePlan =
        MachineContract.help(
            CliDiscoveryPayloadMapperTest.identity(),
            CliDiscoveryPayloadMapperTest.environment(),
            OperationId.EXECUTE_PLAN);
    ContractPlanTemplates.LedgerPlanTemplateDescriptor baseTemplate =
        Objects.requireNonNull(executePlan.planTemplate());
    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor canonicalPosting =
        baseTemplate.canonicalPostingScaffoldStep();
    ContractPlanTemplates.LedgerPlanTemplateDescriptor ambiguousTemplate =
        new ContractPlanTemplates.LedgerPlanTemplateDescriptor(
            baseTemplate.planId(),
            java.util.stream.Stream.concat(
                    baseTemplate.steps().stream(), java.util.stream.Stream.of(canonicalPosting))
                .toList());

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliDiscoveryPayloadMapperTest.fullHelpPayload(
                    helpDescriptorWithPlanTemplate(executePlan, ambiguousTemplate)));

    assertTrue(
        Objects.requireNonNull(failure.getMessage()).contains("canonical POST_ENTRY scaffold"),
        failure::getMessage);
  }

  @Test
  void helpPayload_executePlanIgnoresConflictingTopLevelPostingTemplateFallback() {
    HelpDescriptor executePlan =
        MachineContract.help(
            CliDiscoveryPayloadMapperTest.identity(),
            CliDiscoveryPayloadMapperTest.environment(),
            OperationId.EXECUTE_PLAN);
    CliDiscoveryHelpJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                new HelpDescriptor(
                    executePlan.application(),
                    executePlan.version(),
                    executePlan.description(),
                    executePlan.usage(),
                    executePlan.bookModel(),
                    executePlan.bookkeepingKernel(),
                    executePlan.requestShapes(),
                    conflictingOpenAccountingPositionTemplate(),
                    executePlan.declareAccountTemplate(),
                    executePlan.planTemplate(),
                    executePlan.commands(),
                    executePlan.quickStart(),
                    executePlan.exitCodes(),
                    executePlan.preflight(),
                    executePlan.currencyModel())));

    assertNotNull(payload.requestFile());
    assertNull(payload.requestFile().postingTemplate());
    assertNotNull(payload.requestFile().ledgerPlanTemplate());
    assertNotNull(payload.requestFile().requestShapes());
    assertNull(payload.requestFile().requestShapes().postEntry());
    assertNotNull(
        Objects.requireNonNull(payload.requestFile().requestShapes().ledgerPlan()).postingModel());
    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor canonicalPostingStep =
        Objects.requireNonNull(payload.requestFile().ledgerPlanTemplate())
            .canonicalPostingScaffoldStep();
    assertEquals(
        BookkeepingEntryKind.JOURNAL,
        Objects.requireNonNull(canonicalPostingStep.posting()).entryKind());
  }

  private static void assertPostingGuidance(OperationId operationId) {
    CliDiscoveryHelpJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    operationId)));

    assertNotNull(payload.requestFile());
    assertNotNull(payload.requestFile().postingTemplate());
    assertNull(payload.requestFile().declareAccountTemplate());
    assertNull(payload.requestFile().ledgerPlanTemplate());
    assertNotNull(payload.requestFile().requestShapes());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE),
        payload.requestFile().shortcutCommand());
  }

  private static HelpDescriptor helpDescriptorWithPlanTemplate(
      HelpDescriptor baseHelp, ContractPlanTemplates.LedgerPlanTemplateDescriptor planTemplate) {
    return new HelpDescriptor(
        baseHelp.application(),
        baseHelp.version(),
        baseHelp.description(),
        baseHelp.usage(),
        baseHelp.bookModel(),
        baseHelp.bookkeepingKernel(),
        baseHelp.requestShapes(),
        baseHelp.requestTemplate(),
        baseHelp.declareAccountTemplate(),
        planTemplate,
        baseHelp.commands(),
        baseHelp.quickStart(),
        baseHelp.exitCodes(),
        baseHelp.preflight(),
        baseHelp.currencyModel());
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor
      conflictingOpenAccountingPositionTemplate() {
    ContractTemplates.PostingRequestTemplateDescriptor canonical =
        MachineContract.requestTemplate();
    return new ContractTemplates.PostingRequestTemplateDescriptor(
        BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION,
        null,
        "2026-01-01",
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
        null);
  }
}
