package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.ApplicationIdentity;
import dev.erst.fingrind.contract.discovery.CapabilitiesDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPlanTemplates;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.discovery.PlanTemplateTopic;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.PlanFailurePolicy;
import dev.erst.fingrind.contract.protocol.PlanTransactionMode;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Unit tests for machine-contract topic-specific ledger-plan publication. */
class MachineContractPlanTemplateTest {
  @Test
  void taxSetupPlanTemplatePublishesAtomicTaxSetupInDependencyOrder() {
    ContractPlanTemplates.LedgerPlanTemplateDescriptor template =
        MachineContract.planTemplate(PlanTemplateTopic.TAX_SETUP);

    assertEquals("tax-setup", template.planId());
    assertEquals(
        List.of(
            LedgerStepKind.DECLARE_ACCOUNT,
            LedgerStepKind.DECLARE_ACCOUNT,
            LedgerStepKind.DECLARE_TAX_REGISTRATION),
        template.steps().stream()
            .map(ContractPlanTemplates.LedgerPlanStepTemplateDescriptor::kind)
            .toList());

    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor payable = template.steps().getFirst();
    assertEquals("declare-tax-payable", payable.stepId());
    assertEquals("tax-payable-vat", Objects.requireNonNull(payable.declareAccount()).accountCode());
    assertEquals(
        FinancialPositionLineClassification.CURRENT_LIABILITY,
        payable.declareAccount().financialPositionLineClassification());

    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor recoverable = template.steps().get(1);
    assertEquals("declare-tax-recoverable", recoverable.stepId());
    assertEquals(
        "tax-recoverable-vat", Objects.requireNonNull(recoverable.declareAccount()).accountCode());
    assertEquals(
        FinancialPositionLineClassification.CURRENT_ASSET,
        recoverable.declareAccount().financialPositionLineClassification());
    assertEquals(
        CashFlowAssetClassification.NON_CASH,
        recoverable.declareAccount().cashFlowAssetClassification());

    ContractPlanTemplates.LedgerPlanStepTemplateDescriptor registration =
        template.steps().getLast();
    assertEquals("declare-tax-registration", registration.stepId());
    assertNotNull(registration.declareTaxRegistration());
    assertEquals("tax-payable-vat", registration.declareTaxRegistration().payableAccountCode());
    assertEquals(
        "tax-recoverable-vat", registration.declareTaxRegistration().recoverableAccountCode());
  }

  @Test
  void generalPlanTemplateIncludesARepresentativePostingWorkflow() {
    ContractPlanTemplates.LedgerPlanTemplateDescriptor template = MachineContract.planTemplate();

    assertEquals("general-workflow", template.planId());
    assertTrue(template.steps().stream().anyMatch(step -> step.posting() != null));
    CapabilitiesDescriptor capabilities =
        MachineContract.capabilities(new ApplicationIdentity("FinGrind", "0.57.0", "test"));
    assertEquals(PlanTransactionMode.ATOMIC, capabilities.planExecution().transactionMode());
    assertEquals(
        PlanFailurePolicy.HALT_ON_FIRST_FAILURE, capabilities.planExecution().failurePolicy());
    assertNotNull(capabilities.requestShapes());
    var ledgerPlan = Objects.requireNonNull(capabilities.requestShapes()).ledgerPlan();
    assertNotNull(ledgerPlan);
    assertTrue(ledgerPlan.assertionKinds().contains(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS));
    assertEquals(LedgerStepKind.ASSERT, ledgerPlan.assertStepKind());
  }

  @Test
  void lifecycleSetupPlanTemplatesDeclareEveryRequiredAccountWithTaxonomy() {
    ContractPlanTemplates.LedgerPlanTemplateDescriptor fixedAssets =
        MachineContract.planTemplate(PlanTemplateTopic.FIXED_ASSET_SETUP);
    ContractPlanTemplates.LedgerPlanTemplateDescriptor financing =
        MachineContract.planTemplate(PlanTemplateTopic.FINANCING_SETUP);

    assertEquals("fixed-asset-setup", fixedAssets.planId());
    assertEquals(
        "delivery-van",
        Objects.requireNonNull(fixedAssets.steps().getFirst().declareAccount()).accountCode());
    assertEquals(
        "delivery-van",
        Objects.requireNonNull(fixedAssets.steps().get(1).declareAccount()).contraOfAccountCode());
    assertEquals("financing-setup", financing.planId());
    assertEquals(
        "term-loan-principal",
        Objects.requireNonNull(financing.steps().getFirst().declareAccount()).accountCode());
    assertEquals(
        FinancialPositionLineClassification.NONCURRENT_LIABILITY,
        Objects.requireNonNull(financing.steps().getFirst().declareAccount())
            .financialPositionLineClassification());
  }

  @Test
  void planTemplateTopicsHaveOneStableWireVocabulary() {
    assertEquals(
        List.of("general", "tax-setup", "fixed-asset-setup", "financing-setup"),
        PlanTemplateTopic.wireNames());
    assertEquals(PlanTemplateTopic.TAX_SETUP, PlanTemplateTopic.requireWireName("tax-setup"));
    assertThrows(IllegalArgumentException.class, () -> PlanTemplateTopic.requireWireName("tax"));
  }
}
