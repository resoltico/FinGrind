package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliDiscoveryCommonJsonModels;
import dev.erst.fingrind.cli.json.CliDiscoveryHelpJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractTemplates;
import dev.erst.fingrind.contract.discovery.HelpDescriptor;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestTopics;
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
    assertPostingGuidance(OperationId.RECORD_SALE_SETTLED);
    assertPostingGuidance(OperationId.RECORD_SALE_ON_CREDIT);
    assertPostingGuidance(OperationId.RECORD_EXPENSE_SETTLED);
    assertPostingGuidance(OperationId.RECORD_EXPENSE_ON_CREDIT);
    assertPostingGuidance(OperationId.RECORD_RECEIPT);
    assertPostingGuidance(OperationId.RECORD_PAYMENT);
    assertPostingGuidance(OperationId.RECORD_OWNER_CONTRIBUTION);
    assertPostingGuidance(OperationId.RECORD_OWNER_WITHDRAWAL);
    assertPostingGuidance(OperationId.RECORD_OPENING_POSITION);
    assertPostingGuidance(OperationId.RECORD_REVERSAL);
  }

  @Test
  void helpPayload_mapsAdministrativeAndLedgerPlanRequestGuidance() {
    CliDiscoveryHelpJsonModels.CommandHelpPayload declarePayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    OperationId.DECLARE_ACCOUNT)));
    CliDiscoveryHelpJsonModels.CommandHelpPayload declareTaxRegistrationPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    OperationId.DECLARE_TAX_REGISTRATION)));
    CliDiscoveryHelpJsonModels.CommandHelpPayload amendPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    OperationId.AMEND_ACCOUNT)));
    CliDiscoveryHelpJsonModels.CommandHelpPayload compactDeclarePayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.compactHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    OperationId.DECLARE_ACCOUNT)));
    CliDiscoveryHelpJsonModels.CommandHelpPayload compactDeclareTaxRegistrationPayload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.compactHelpPayload(
                MachineContract.help(
                    CliDiscoveryPayloadMapperTest.identity(),
                    CliDiscoveryPayloadMapperTest.environment(),
                    OperationId.DECLARE_TAX_REGISTRATION)));
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
    assertEquals(
        "Provide an account-definition JSON document through --request-file <path|->.",
        declarePayload.requestFile().description());
    assertNull(declarePayload.requestFile().postingTemplate());
    assertNotNull(declarePayload.requestFile().declareAccountTemplate());
    assertNull(declarePayload.requestFile().ledgerPlanTemplate());
    assertTrue(
        Objects.requireNonNull(declarePayload.requestFile().shortcutCommand())
            .contains("declare-account"));

    assertNotNull(amendPayload.requestFile());
    assertEquals(
        ProtocolCatalog.operation(OperationId.AMEND_ACCOUNT).usage(), amendPayload.syntax());
    assertEquals(
        "Provide an account-definition JSON document through --request-file <path|->.",
        amendPayload.requestFile().description());
    assertNotNull(amendPayload.requestFile().declareAccountTemplate());
    assertTrue(
        Objects.requireNonNull(amendPayload.requestFile().shortcutCommand())
            .contains("amend-account"));
    assertNotNull(compactDeclarePayload.requestFile());
    assertEquals(
        "Provide an account-definition JSON document through --request-file <path|->.",
        compactDeclarePayload.requestFile().description());
    assertNull(compactDeclarePayload.requestFile().postingTemplate());
    assertNull(compactDeclarePayload.requestFile().declareAccountTemplate());
    assertNull(compactDeclarePayload.requestFile().ledgerPlanTemplate());
    assertNull(compactDeclarePayload.requestFile().requestShapes());
    assertTrue(
        Objects.requireNonNull(compactDeclarePayload.requestFile().shortcutCommand())
            .contains("declare-account"));

    assertNotNull(declareTaxRegistrationPayload.requestFile());
    assertEquals(
        ProtocolCatalog.operation(OperationId.DECLARE_TAX_REGISTRATION).usage(),
        declareTaxRegistrationPayload.syntax());
    assertEquals(
        "Provide a tax-registration declaration JSON document through --request-file <path|->.",
        declareTaxRegistrationPayload.requestFile().description());
    assertNull(declareTaxRegistrationPayload.requestFile().postingTemplate());
    assertNull(declareTaxRegistrationPayload.requestFile().declareAccountTemplate());
    assertNotNull(declareTaxRegistrationPayload.requestFile().declareTaxRegistrationTemplate());
    assertNull(declareTaxRegistrationPayload.requestFile().ledgerPlanTemplate());
    assertNotNull(declareTaxRegistrationPayload.requestFile().requestShapes());
    assertNotNull(
        Objects.requireNonNull(declareTaxRegistrationPayload.requestFile().requestShapes())
            .declareTaxRegistration());
    assertTrue(
        Objects.requireNonNull(declareTaxRegistrationPayload.requestFile().shortcutCommand())
            .contains("declare-tax-registration"));

    assertNotNull(compactDeclareTaxRegistrationPayload.requestFile());
    assertEquals(
        "Provide a tax-registration declaration JSON document through --request-file <path|->.",
        compactDeclareTaxRegistrationPayload.requestFile().description());
    assertNull(compactDeclareTaxRegistrationPayload.requestFile().postingTemplate());
    assertNull(compactDeclareTaxRegistrationPayload.requestFile().declareAccountTemplate());
    assertNull(compactDeclareTaxRegistrationPayload.requestFile().declareTaxRegistrationTemplate());
    assertNull(compactDeclareTaxRegistrationPayload.requestFile().ledgerPlanTemplate());
    assertNull(compactDeclareTaxRegistrationPayload.requestFile().requestShapes());
    assertTrue(
        Objects.requireNonNull(compactDeclareTaxRegistrationPayload.requestFile().shortcutCommand())
            .contains("declare-tax-registration"));

    assertNotNull(planPayload.requestFile());
    assertEquals(ProtocolCatalog.operation(OperationId.EXECUTE_PLAN).usage(), planPayload.syntax());
    assertEquals(
        "Provide a ledger plan JSON document through --request-file <path|->.",
        planPayload.requestFile().description());
    assertNull(planPayload.requestFile().postingTemplate());
    assertNull(planPayload.requestFile().declareAccountTemplate());
    assertNotNull(planPayload.requestFile().ledgerPlanTemplate());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE),
        planPayload.requestFile().shortcutCommand());
    assertNotNull(compactPlanPayload.requestFile());
    assertEquals(
        "Provide a ledger plan JSON document through --request-file <path|->.",
        compactPlanPayload.requestFile().description());
    assertNull(compactPlanPayload.requestFile().postingTemplate());
    assertNull(compactPlanPayload.requestFile().declareAccountTemplate());
    assertNull(compactPlanPayload.requestFile().ledgerPlanTemplate());
    assertNull(compactPlanPayload.requestFile().requestShapes());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE),
        compactPlanPayload.requestFile().shortcutCommand());
  }

  @Test
  void helpPayload_omitsDeclareTaxRegistrationRequestGuidanceWhenArtifactsAreMissing() {
    HelpDescriptor declareTaxRegistration =
        MachineContract.help(
            CliDiscoveryPayloadMapperTest.identity(),
            CliDiscoveryPayloadMapperTest.environment(),
            OperationId.DECLARE_TAX_REGISTRATION);
    CliDiscoveryHelpJsonModels.CommandHelpPayload missingRequestShapes =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                helpDescriptorWithDeclareTaxRegistrationArtifacts(
                    declareTaxRegistration, false, true, true)));
    CliDiscoveryHelpJsonModels.CommandHelpPayload missingShape =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                helpDescriptorWithDeclareTaxRegistrationArtifacts(
                    declareTaxRegistration, true, false, true)));
    CliDiscoveryHelpJsonModels.CommandHelpPayload missingTemplate =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(
                helpDescriptorWithDeclareTaxRegistrationArtifacts(
                    declareTaxRegistration, true, true, false)));

    assertNull(missingRequestShapes.requestFile());
    assertNull(missingShape.requestFile());
    assertNull(missingTemplate.requestFile());
  }

  @Test
  void helpPayload_executePlanSupportsTaxOnlyPlanWithoutAPostingScaffold() {
    HelpDescriptor executePlan =
        MachineContract.help(
            CliDiscoveryPayloadMapperTest.identity(),
            CliDiscoveryPayloadMapperTest.environment(),
            OperationId.EXECUTE_PLAN);
    CliDiscoveryHelpJsonModels.CommandHelpPayload payload =
        assertInstanceOf(
            CliDiscoveryHelpJsonModels.CommandHelpPayload.class,
            CliDiscoveryPayloadMapperTest.fullHelpPayload(executePlan));

    CliDiscoveryCommonJsonModels.RequestFileGuidancePayload requestFile =
        Objects.requireNonNull(payload.requestFile());
    assertEquals("tax-setup", Objects.requireNonNull(requestFile.ledgerPlanTemplate()).planId());
    assertTrue(
        requestFile.ledgerPlanTemplate().steps().stream()
            .noneMatch(step -> step.posting() != null));
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
                    executePlan.protocolVersion(),
                    executePlan.description(),
                    executePlan.usage(),
                    executePlan.bookModel(),
                    executePlan.bookkeepingKernel(),
                    executePlan.requestShapes(),
                    conflictingOpeningPositionTemplate(),
                    executePlan.declareAccountTemplate(),
                    executePlan.declareTaxRegistrationTemplate(),
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
    assertNull(payload.requestFile().requestShapes().bookkeepingEntry());
    assertNotNull(
        Objects.requireNonNull(payload.requestFile().requestShapes().ledgerPlan()).postingModel());
    assertEquals(
        "tax-setup", Objects.requireNonNull(payload.requestFile().ledgerPlanTemplate()).planId());
    assertTrue(
        payload.requestFile().ledgerPlanTemplate().steps().stream()
            .noneMatch(step -> step.posting() != null));
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
    assertEquals(
        "Provide a posting JSON document through --request-file <path|->.",
        payload.requestFile().description());
    assertNotNull(payload.requestFile().postingTemplate());
    assertNull(payload.requestFile().declareAccountTemplate());
    assertNull(payload.requestFile().ledgerPlanTemplate());
    assertNotNull(payload.requestFile().requestShapes());
    assertEquals(
        CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
            + " "
            + operationId.wireName(),
        payload.requestFile().shortcutCommand());
    var postEntryShape =
        Objects.requireNonNull(
            Objects.requireNonNull(payload.requestFile().requestShapes()).bookkeepingEntry());
    if (operationId == OperationId.PREFLIGHT_ENTRY) {
      assertEquals(
          BookkeepingEntryKind.values().length, postEntryShape.entryKindSemantics().size());
      return;
    }
    if (operationId == OperationId.POST_ENTRY) {
      assertEquals(
          List.of(BookkeepingEntryKind.DIRECT_JOURNAL),
          postEntryShape.entryKindSemantics().stream()
              .map(
                  dev.erst.fingrind.contract.discovery.ContractRequestShapes
                          .EntryKindSemanticsDescriptor
                      ::entryKind)
              .toList());
      return;
    }
    BookkeepingEntryKind expectedEntryKind =
        ProtocolPostingRequestTopics.requiredEntryKind(operationId)
            .orElseThrow(() -> new AssertionError("Unexpected posting topic " + operationId));
    assertEquals(
        List.of(expectedEntryKind),
        postEntryShape.entryKindSemantics().stream()
            .map(
                dev.erst.fingrind.contract.discovery.ContractRequestShapes
                        .EntryKindSemanticsDescriptor
                    ::entryKind)
            .toList());
  }

  private static HelpDescriptor helpDescriptorWithDeclareTaxRegistrationArtifacts(
      HelpDescriptor baseHelp,
      boolean includeRequestShapes,
      boolean includeDeclareTaxRegistrationShape,
      boolean includeDeclareTaxRegistrationTemplate) {
    return new HelpDescriptor(
        baseHelp.application(),
        baseHelp.version(),
        baseHelp.protocolVersion(),
        baseHelp.description(),
        baseHelp.usage(),
        baseHelp.bookModel(),
        baseHelp.bookkeepingKernel(),
        includeRequestShapes
            ? new dev.erst.fingrind.contract.discovery.ContractRequestShapes
                .RequestShapesDescriptor(
                Objects.requireNonNull(baseHelp.requestShapes()).schemaDialect(),
                Objects.requireNonNull(baseHelp.requestShapes()).bookkeepingEntry(),
                Objects.requireNonNull(baseHelp.requestShapes()).declareAccount(),
                includeDeclareTaxRegistrationShape
                    ? Objects.requireNonNull(baseHelp.requestShapes()).declareTaxRegistration()
                    : null,
                Objects.requireNonNull(baseHelp.requestShapes()).ledgerPlan())
            : null,
        baseHelp.requestTemplate(),
        baseHelp.declareAccountTemplate(),
        includeDeclareTaxRegistrationTemplate ? baseHelp.declareTaxRegistrationTemplate() : null,
        baseHelp.planTemplate(),
        baseHelp.commands(),
        baseHelp.quickStart(),
        baseHelp.exitCodes(),
        baseHelp.preflight(),
        baseHelp.currencyModel());
  }

  private static ContractTemplates.PostingRequestTemplateDescriptor
      conflictingOpeningPositionTemplate() {
    ContractTemplates.PostingRequestTemplateDescriptor canonical =
        MachineContract.requestTemplate();
    return new ContractTemplates.PostingRequestTemplateDescriptor(
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
