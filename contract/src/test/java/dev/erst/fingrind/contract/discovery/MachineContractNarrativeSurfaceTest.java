package dev.erst.fingrind.contract.discovery;

import static dev.erst.fingrind.contract.discovery.MachineContractDiscoveryTestSupport.IDENTITY;
import static dev.erst.fingrind.contract.discovery.MachineContractDiscoveryTestSupport.environment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes.LedgerPlanRequestShapeDescriptor;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused coverage for public discovery narratives and seed-template wording. */
class MachineContractNarrativeSurfaceTest {
  @Test
  void capabilitiesAndContainerQuickStartPublishUpdatedNarrativeLanguage() {
    CapabilitiesDescriptor capabilities = MachineContract.capabilities(IDENTITY);

    assertEquals(
        "Current built-in bookkeeping kernel facts for the single-entity, single-functional-currency internal-management book model with four built-in statements.",
        capabilities.bookkeepingKernel().description());
    capabilities
        .bookkeepingKernel()
        .reportCapabilities()
        .forEach(
            reportCapability ->
                assertFalse(
                    reportCapability.description().contains(" as one "),
                    reportCapability::description));

    WorkflowStepDescriptor.Note introNote =
        assertInstanceOf(
            WorkflowStepDescriptor.Note.class,
            MachineContract.quickStart(WorkflowSurface.CONTAINER_DOCKER).steps().getFirst());
    assertTrue(
        introNote
            .text()
            .contains(
                "Define a session-local fingrind wrapper backed by the published or locally built container image"));
    assertFalse(introNote.text().contains("Define one session-local"), introNote::text);
  }

  @Test
  void executePlanHelpSchemaPublishesSeedTemplateTerminology() {
    HelpDescriptor executePlanHelp =
        MachineContract.help(
            IDENTITY,
            environment(RuntimeDistribution.SOURCE_CHECKOUT_GRADLE),
            OperationId.EXECUTE_PLAN);

    LedgerPlanRequestShapeDescriptor ledgerPlan =
        Objects.requireNonNull(
            Objects.requireNonNull(executePlanHelp.requestShapes()).ledgerPlan());
    assertEquals(
        new EnsureBookDescriptions(
            "Seed template persisted on the selected protected book.",
            "Accounting basis persisted on the selected protected book."),
        ensureBookDescriptions(ledgerPlan));
    assertFalse(containsTextFragment(ledgerPlan.schema(), "Starter-chart"));
  }

  private static EnsureBookDescriptions ensureBookDescriptions(
      LedgerPlanRequestShapeDescriptor ledgerPlanRequestShape) {
    Map<String, Object> stepsSchema =
        ContractSchemaTestSupport.schemaProperty(ledgerPlanRequestShape.schema(), "steps");
    Map<String, Object> stepItemSchema =
        ContractSchemaTestSupport.objectMap(
            ContractSchemaTestSupport.requiredValue(stepsSchema, "items"));
    @SuppressWarnings("unchecked")
    List<Object> stepVariants =
        (List<Object>) ContractSchemaTestSupport.requiredValue(stepItemSchema, "oneOf");
    Map<String, Object> ensureBookVariant =
        stepVariants.stream()
            .map(ContractSchemaTestSupport::objectMap)
            .filter(
                variant ->
                    ContractSchemaTestSupport.objectMap(
                            ContractSchemaTestSupport.requiredValue(variant, "properties"))
                        .containsKey("ensureBook"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing ensureBook step schema variant"));
    Map<String, Object> ensureBookSchema =
        ContractSchemaTestSupport.objectMap(
            ContractSchemaTestSupport.requiredValue(
                ContractSchemaTestSupport.objectMap(
                    ContractSchemaTestSupport.requiredValue(ensureBookVariant, "properties")),
                "ensureBook"));
    Map<String, Object> bookTemplateSchema =
        ContractSchemaTestSupport.objectMap(
            ContractSchemaTestSupport.requiredValue(
                ContractSchemaTestSupport.objectMap(
                    ContractSchemaTestSupport.requiredValue(ensureBookSchema, "properties")),
                "bookTemplateId"));
    Map<String, Object> accountingBasisSchema =
        ContractSchemaTestSupport.objectMap(
            ContractSchemaTestSupport.requiredValue(
                ContractSchemaTestSupport.objectMap(
                    ContractSchemaTestSupport.requiredValue(ensureBookSchema, "properties")),
                "accountingBasis"));
    return new EnsureBookDescriptions(
        String.valueOf(ContractSchemaTestSupport.requiredValue(bookTemplateSchema, "description")),
        String.valueOf(
            ContractSchemaTestSupport.requiredValue(accountingBasisSchema, "description")));
  }

  private static boolean containsTextFragment(Object node, String fragment) {
    if (node instanceof String text) {
      return text.contains(fragment);
    }
    if (node instanceof Map<?, ?> values) {
      return values.values().stream().anyMatch(value -> containsTextFragment(value, fragment));
    }
    return node instanceof List<?> values
        && values.stream().anyMatch(value -> containsTextFragment(value, fragment));
  }

  private record EnsureBookDescriptions(
      String bookTemplateDescription, String accountingBasisDescription) {}
}
